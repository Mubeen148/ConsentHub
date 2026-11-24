package io.consenthub.service;

import io.consenthub.config.AppProperties;
import io.consenthub.domain.dto.ConsentHistoryEntryDto;
import io.consenthub.domain.dto.ConsentStatusBundleDto;
import io.consenthub.domain.dto.PurposeConsentStateDto;
import io.consenthub.domain.dto.PurposeDecisionInput;
import io.consenthub.domain.dto.SaveConsentInput;
import io.consenthub.domain.dto.SdkConfigResponse;
import io.consenthub.domain.dto.SdkConsentRequest;
import io.consenthub.domain.dto.SdkConsentResponse;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.entity.ConsentHistoryEntity;
import io.consenthub.domain.entity.ConsentPurposeEntity;
import io.consenthub.domain.entity.ConsentRecordEntity;
import io.consenthub.domain.entity.PolicyVersionEntity;
import io.consenthub.domain.entity.RegionRuleEntity;
import io.consenthub.domain.entity.UserEntity;
import io.consenthub.domain.enums.ConsentModel;
import io.consenthub.domain.enums.ConsentSource;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.repository.ConsentHistoryRepository;
import io.consenthub.repository.ConsentRecordRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.RequestMetadataUtil;
import io.consenthub.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core Consent Collection & Cryptographic Receipt Ledger Service.
 *
 * <p>Enforces:
 * <ul>
 *   <li>Granular per-purpose consent persistence ({@code consent_records}) + immutable append-only
 *       SHA-256 receipts ({@code consent_history}).</li>
 *   <li>Regional privacy-by-default semantics ({@code OPT_IN} for {@code EU/GDPR}, {@code OPT_OUT} for
 *       {@code US_CA/CCPA}, {@code NOTICE_ONLY} for {@code GLOBAL/DEFAULT}).</li>
 *   <li>Strictly locked {@code essential} purpose ({@code PRIV-02}).</li>
 *   <li>Namespace isolation ({@code SEC-G01}): {@code user:<uuid>} vs {@code anon:<id>}.</li>
 *   <li>Sticky A/B experiment conversion attribution ({@code ADR-005}).</li>
 * </ul>
 */
@Service
public class ConsentService {

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT = 200;

    private final ConsentRecordRepository consentRecordRepository;
    private final ConsentHistoryRepository consentHistoryRepository;
    private final PolicyService policyService;
    private final RegionRuleService regionRuleService;
    private final RegionResolverService regionResolverService;
    private final ExperimentService experimentService;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;

    public ConsentService(
            ConsentRecordRepository consentRecordRepository,
            ConsentHistoryRepository consentHistoryRepository,
            PolicyService policyService,
            RegionRuleService regionRuleService,
            RegionResolverService regionResolverService,
            ExperimentService experimentService,
            AuditLogService auditLogService,
            AppProperties appProperties
    ) {
        this.consentRecordRepository = consentRecordRepository;
        this.consentHistoryRepository = consentHistoryRepository;
        this.policyService = policyService;
        this.regionRuleService = regionRuleService;
        this.regionResolverService = regionResolverService;
        this.experimentService = experimentService;
        this.auditLogService = auditLogService;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public ConsentStatusBundleDto getConsentState(String requestedSubjectKey, RegionCode explicitRegion) {
        String subjectKey = SecurityUtils.resolveEffectiveSubjectKey(requestedSubjectKey, true);
        RegionCode regionCode = regionResolverService.resolveRegion(explicitRegion);
        return buildConsentBundle(subjectKey, regionCode);
    }

    @Transactional(readOnly = true)
    public ConsentStatusBundleDto getConsentStateForValidatedSubject(String validatedSubjectKey, RegionCode regionCode) {
        RegionCode effectiveRegion = regionCode != null ? regionCode : regionResolverService.resolveRegion(null);
        return buildConsentBundle(validatedSubjectKey, effectiveRegion);
    }

    @Transactional(readOnly = true)
    public List<ConsentHistoryEntryDto> listConsentHistory(String requestedSubjectKey, Integer limit) {
        String subjectKey = SecurityUtils.resolveEffectiveSubjectKey(requestedSubjectKey, true);
        if (subjectKey.startsWith("anon:ephemeral-")) {
            return List.of();
        }
        int clamped = limit == null ? DEFAULT_HISTORY_LIMIT : Math.max(1, Math.min(MAX_HISTORY_LIMIT, limit));
        return consentHistoryRepository
                .findBySubjectKeyOrderByOccurredAtDesc(subjectKey, PageRequest.of(0, clamped))
                .stream()
                .map(ConsentService::toHistoryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countHistoryEntries(String subjectKey) {
        if (subjectKey == null || subjectKey.startsWith("anon:ephemeral-")) {
            return 0L;
        }
        return consentHistoryRepository.countBySubjectKey(subjectKey);
    }

    @Transactional
    public ConsentStatusBundleDto saveConsentPreferences(SaveConsentInput input) {
        if (input == null || input.decisions() == null || input.decisions().isEmpty()) {
            throw new BadRequestException("VALIDATION_ERROR", "At least one purpose decision is required.");
        }
        String subjectKey = SecurityUtils.resolveEffectiveSubjectKey(input.subjectKey(), false);
        RegionCode regionCode = regionResolverService.resolveRegion(input.region());
        ConsentSource source = ConsentSource.fromOrDefault(input.source(), ConsentSource.PREFERENCE_CENTER);

        Map<String, Boolean> explicitChoices = new LinkedHashMap<>();
        for (PurposeDecisionInput item : input.decisions()) {
            if (item == null || item.purposeSlug() == null || item.purposeSlug().isBlank() || item.granted() == null) {
                throw new BadRequestException("VALIDATION_ERROR", "Each decision requires purposeSlug and granted.");
            }
            explicitChoices.put(item.purposeSlug().trim().toLowerCase(Locale.ROOT), item.granted());
        }

        UUID experimentUuid = parseOptionalUuid(input.experimentId());
        return applyConsentMutation(
                subjectKey,
                regionCode,
                source,
                explicitChoices,
                null,
                experimentUuid,
                sanitizeVariantKey(input.variantKey()),
                "CONSENT_PREFERENCES_SAVED"
        );
    }

    @Transactional
    public ConsentStatusBundleDto acceptAllConsent(
            String requestedSubjectKey,
            RegionCode explicitRegion,
            String rawSource,
            String experimentIdRaw,
            String variantKey
    ) {
        String subjectKey = SecurityUtils.resolveEffectiveSubjectKey(requestedSubjectKey, false);
        RegionCode regionCode = regionResolverService.resolveRegion(explicitRegion);
        ConsentSource source = ConsentSource.fromOrDefault(rawSource, ConsentSource.BANNER);
        UUID experimentUuid = parseOptionalUuid(experimentIdRaw);

        return applyConsentMutation(
                subjectKey,
                regionCode,
                source,
                Map.of(),
                Boolean.TRUE,
                experimentUuid,
                sanitizeVariantKey(variantKey),
                "CONSENT_ACCEPTED_ALL"
        );
    }

    @Transactional
    public ConsentStatusBundleDto rejectOptionalConsent(
            String requestedSubjectKey,
            RegionCode explicitRegion,
            String rawSource,
            String experimentIdRaw,
            String variantKey
    ) {
        String subjectKey = SecurityUtils.resolveEffectiveSubjectKey(requestedSubjectKey, false);
        RegionCode regionCode = regionResolverService.resolveRegion(explicitRegion);
        ConsentSource source = ConsentSource.fromOrDefault(rawSource, ConsentSource.BANNER);
        UUID experimentUuid = parseOptionalUuid(experimentIdRaw);

        return applyConsentMutation(
                subjectKey,
                regionCode,
                source,
                Map.of(),
                Boolean.FALSE,
                experimentUuid,
                sanitizeVariantKey(variantKey),
                "CONSENT_REJECTED_OPTIONAL"
        );
    }

    @Transactional
    public SdkConfigResponse getSdkConfig(
            String rawSubjectKey,
            String rawRegion,
            HttpServletRequest request,
            SduiScreenProvider sduiScreenProvider
    ) {
        String subjectKey = SecurityUtils.validateSdkAnonSubjectKey(rawSubjectKey);
        RegionCode regionCode = regionResolverService.resolveRegion(rawRegion, request);
        ConsentStatusBundleDto bundle = buildConsentBundle(subjectKey, regionCode);
        PolicyVersionEntity activePolicy = policyService.getActivePolicyVersionEntity();

        List<SdkConfigResponse.SdkPurposeState> sdkPurposes = bundle.decisions().stream()
                .map(d -> new SdkConfigResponse.SdkPurposeState(
                        d.slug(),
                        d.name(),
                        d.description(),
                        d.legalBasis(),
                        d.isEssential(),
                        d.granted()
                ))
                .toList();

        Map<String, Boolean> consentMap = new LinkedHashMap<>();
        for (PurposeConsentStateDto d : bundle.decisions()) {
            consentMap.put(d.slug(), d.granted());
        }

        Map<String, Object> bannerScreen = sduiScreenProvider != null
                ? sduiScreenProvider.buildBannerScreenMap(subjectKey, regionCode.name(), null)
                : Map.of();

        return new SdkConfigResponse(
                subjectKey,
                new SdkConfigResponse.SdkRegionRule(
                        bundle.regionRule().regionCode(),
                        bundle.regionRule().regimeName(),
                        bundle.regionRule().consentModel(),
                        bundle.regionRule().bannerStyle(),
                        bundle.regionRule().showRejectAll(),
                        bundle.regionRule().retentionDays()
                ),
                new SdkConfigResponse.SdkActivePolicy(
                        activePolicy.getVersionNumber(),
                        activePolicy.getVersionLabel(),
                        activePolicy.getTitle(),
                        activePolicy.getSummary(),
                        activePolicy.isRequiresReconsent(),
                        TimestampFormatter.format(activePolicy.getPublishedAt())
                ),
                bundle.hasConsented(),
                bundle.needsReconsent(),
                bundle.shouldShowBanner(),
                sdkPurposes,
                consentMap,
                bannerScreen
        );
    }

    @Transactional(readOnly = true)
    public SdkConsentResponse getSdkConsent(String rawSubjectKey, String rawRegion, HttpServletRequest request) {
        String subjectKey = SecurityUtils.validateSdkAnonSubjectKey(rawSubjectKey);
        RegionCode regionCode = regionResolverService.resolveRegion(rawRegion, request);
        ConsentStatusBundleDto bundle = buildConsentBundle(subjectKey, regionCode);
        return toSdkConsentResponse(bundle);
    }

    @Transactional
    public SdkConsentResponse recordSdkConsent(SdkConsentRequest requestBody, HttpServletRequest httpRequest) {
        if (requestBody == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Request body is required.");
        }
        String subjectKey = SecurityUtils.validateSdkAnonSubjectKey(requestBody.subjectKey());
        RegionCode regionCode = regionResolverService.resolveRegion(requestBody.region(), httpRequest);
        String action = requestBody.action() != null
                ? requestBody.action().trim().toUpperCase(Locale.ROOT)
                : "";

        UUID experimentUuid = parseOptionalUuid(requestBody.experimentId());
        String variantKey = sanitizeVariantKey(requestBody.variantKey());

        ConsentStatusBundleDto updated = switch (action) {
            case "ACCEPT_ALL" -> applyConsentMutation(
                    subjectKey,
                    regionCode,
                    ConsentSource.SDK,
                    Map.of(),
                    Boolean.TRUE,
                    experimentUuid,
                    variantKey,
                    "SDK_CONSENT_ACCEPTED_ALL"
            );
            case "REJECT_OPTIONAL" -> applyConsentMutation(
                    subjectKey,
                    regionCode,
                    ConsentSource.SDK,
                    Map.of(),
                    Boolean.FALSE,
                    experimentUuid,
                    variantKey,
                    "SDK_CONSENT_REJECTED_OPTIONAL"
            );
            case "CUSTOM" -> {
                if (requestBody.decisions() == null || requestBody.decisions().isEmpty()) {
                    throw new BadRequestException(
                            "VALIDATION_ERROR",
                            "decisions map is required when action is CUSTOM."
                    );
                }
                Map<String, Boolean> normalized = new LinkedHashMap<>();
                requestBody.decisions().forEach((k, v) -> {
                    if (k != null && v != null) {
                        normalized.put(k.trim().toLowerCase(Locale.ROOT), v);
                    }
                });
                yield applyConsentMutation(
                        subjectKey,
                        regionCode,
                        ConsentSource.SDK,
                        normalized,
                        null,
                        experimentUuid,
                        variantKey,
                        "SDK_CONSENT_CUSTOM_SAVED"
                );
            }
            default -> throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Unsupported SDK action '" + action + "'. Expected ACCEPT_ALL, REJECT_OPTIONAL, or CUSTOM."
            );
        };

        return toSdkConsentResponse(updated);
    }

    /**
     * Migrates anonymous visitor consent into a newly created or unconsented user namespace on signup/login (ADR-008).
     */
    @Transactional
    public void linkAnonymousConsentIfPresent(String rawAnonymousKey, UserEntity user) {
        if (rawAnonymousKey == null || rawAnonymousKey.isBlank() || user == null) {
            return;
        }
        String trimmed = rawAnonymousKey.trim();
        if (!SecurityUtils.ANON_SUBJECT_KEY_PATTERN.matcher(trimmed).matches()) {
            return;
        }
        String userSubjectKey = user.getSubjectKey();
        if (consentRecordRepository.existsBySubjectKey(userSubjectKey)) {
            return;
        }
        List<ConsentRecordEntity> anonRecords = consentRecordRepository.findBySubjectKey(trimmed);
        if (anonRecords.isEmpty()) {
            return;
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (ConsentRecordEntity anon : anonRecords) {
            ConsentRecordEntity copy = new ConsentRecordEntity();
            copy.setId(UUID.randomUUID());
            copy.setUserId(user.getId());
            copy.setSubjectKey(userSubjectKey);
            copy.setPurpose(anon.getPurpose());
            copy.setGranted(anon.getPurpose().isEssential() || anon.isGranted());
            copy.setPolicyVersion(anon.getPolicyVersion());
            copy.setRegionCode(user.getRegion() != null ? user.getRegion() : anon.getRegionCode());
            copy.setSource(anon.getSource());
            copy.setExperimentId(anon.getExperimentId());
            copy.setVariantKey(anon.getVariantKey());
            copy.setUpdatedAt(now);
            consentRecordRepository.save(copy);

            String proofHash = ConsentProofHasher.computeProofHash(
                    userSubjectKey,
                    copy.getPurpose().getSlug(),
                    copy.isGranted(),
                    copy.getPolicyVersion().getVersionLabel(),
                    copy.getRegionCode(),
                    copy.getSource(),
                    now
            );

            ConsentHistoryEntity history = new ConsentHistoryEntity();
            history.setId(UUID.randomUUID());
            history.setUserId(user.getId());
            history.setSubjectKey(userSubjectKey);
            history.setPurposeSlug(copy.getPurpose().getSlug());
            history.setPurposeName(copy.getPurpose().getName());
            history.setPreviousGranted(null);
            history.setNewGranted(copy.isGranted());
            history.setPolicyVersionLabel(copy.getPolicyVersion().getVersionLabel());
            history.setRegionCode(copy.getRegionCode());
            history.setSource(copy.getSource());
            history.setExperimentVariant(copy.getVariantKey());
            history.setProofHash(proofHash);
            history.setOccurredAt(now);
            consentHistoryRepository.save(history);
        }
    }

    /**
     * Revokes all optional consent purposes and detaches userId when completing a DELETE DSAR (PRIV-07).
     */
    @Transactional
    public void revokeOptionalConsentsForErasure(UserEntity user) {
        String subjectKey = user.getSubjectKey();
        PolicyVersionEntity activePolicy = policyService.getActivePolicyVersionEntity();
        List<ConsentRecordEntity> records = consentRecordRepository.findBySubjectKey(subjectKey);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        for (ConsentRecordEntity record : records) {
            Boolean previous = record.isGranted();
            boolean targetGranted = record.getPurpose().isEssential();
            record.setUserId(null);
            record.setGranted(targetGranted);
            record.setSource(ConsentSource.SYSTEM_ERASURE);
            record.setUpdatedAt(now);
            consentRecordRepository.save(record);

            if (!Boolean.valueOf(targetGranted).equals(previous)) {
                RegionCode regionCode = record.getRegionCode() != null ? record.getRegionCode() : RegionCode.GLOBAL;
                String policyLabel = activePolicy.getVersionLabel();
                String proofHash = ConsentProofHasher.computeProofHash(
                        subjectKey,
                        record.getPurpose().getSlug(),
                        targetGranted,
                        policyLabel,
                        regionCode,
                        ConsentSource.SYSTEM_ERASURE,
                        now
                );

                ConsentHistoryEntity history = new ConsentHistoryEntity();
                history.setId(UUID.randomUUID());
                history.setUserId(null);
                history.setSubjectKey(subjectKey);
                history.setPurposeSlug(record.getPurpose().getSlug());
                history.setPurposeName(record.getPurpose().getName());
                history.setPreviousGranted(previous);
                history.setNewGranted(targetGranted);
                history.setPolicyVersionLabel(policyLabel);
                history.setRegionCode(regionCode);
                history.setSource(ConsentSource.SYSTEM_ERASURE);
                history.setProofHash(proofHash);
                history.setOccurredAt(now);
                consentHistoryRepository.save(history);
            }
        }
    }

    private ConsentStatusBundleDto applyConsentMutation(
            String subjectKey,
            RegionCode regionCode,
            ConsentSource source,
            Map<String, Boolean> explicitDecisions,
            Boolean uniformOptionalDecision,
            UUID experimentUuid,
            String variantKey,
            String auditAction
    ) {
        PolicyVersionEntity activePolicy = policyService.getActivePolicyVersionEntity();
        RegionRuleEntity regionRule = regionRuleService.getRuleEntityByRegion(regionCode);
        List<ConsentPurposeEntity> activePurposes = policyService.listActivePurposeEntities();

        UUID userId = SecurityUtils.getCurrentUser().map(AuthenticatedUser::id).orElse(null);
        HttpServletRequest httpRequest = RequestMetadataUtil.currentHttpRequestOrNull();
        String ipHash = RequestMetadataUtil.hashClientIp(httpRequest, appProperties.privacy().ipHashSalt());
        String uaSummary = RequestMetadataUtil.summarizeUserAgent(httpRequest);

        List<ConsentRecordEntity> existingRows = consentRecordRepository.findBySubjectKey(subjectKey);
        Map<UUID, ConsentRecordEntity> existingByPurposeId = new HashMap<>();
        for (ConsentRecordEntity row : existingRows) {
            existingByPurposeId.put(row.getPurpose().getId(), row);
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<ConsentHistoryEntity> newHistoryEntries = new ArrayList<>();
        boolean grantedAnyOptional = false;
        int grantedCount = 0;

        for (ConsentPurposeEntity purpose : activePurposes) {
            ConsentRecordEntity existing = existingByPurposeId.get(purpose.getId());
            Boolean previousGranted = existing != null ? existing.isGranted() : null;

            boolean nextGranted;
            if (purpose.isEssential()) {
                // PRIV-02: Essential purpose is unconditionally true
                nextGranted = true;
            } else if (uniformOptionalDecision != null) {
                nextGranted = uniformOptionalDecision;
            } else if (explicitDecisions.containsKey(purpose.getSlug().toLowerCase(Locale.ROOT))) {
                nextGranted = explicitDecisions.get(purpose.getSlug().toLowerCase(Locale.ROOT));
            } else if (existing != null) {
                nextGranted = existing.isGranted();
            } else {
                nextGranted = regionRule.getConsentModel() != ConsentModel.OPT_IN;
            }

            if (!purpose.isEssential() && nextGranted) {
                grantedAnyOptional = true;
            }
            if (nextGranted) {
                grantedCount++;
            }

            ConsentRecordEntity record = existing != null ? existing : new ConsentRecordEntity();
            if (record.getId() == null) {
                record.setId(UUID.randomUUID());
            }
            record.setUserId(userId);
            record.setSubjectKey(subjectKey);
            record.setPurpose(purpose);
            record.setGranted(nextGranted);
            record.setPolicyVersion(activePolicy);
            record.setRegionCode(regionCode);
            record.setSource(source);
            record.setExperimentId(experimentUuid);
            record.setVariantKey(variantKey);
            record.setUpdatedAt(now);
            consentRecordRepository.save(record);

            String proofHash = ConsentProofHasher.computeProofHash(
                    subjectKey,
                    purpose.getSlug(),
                    nextGranted,
                    activePolicy.getVersionLabel(),
                    regionCode,
                    source,
                    now
            );

            ConsentHistoryEntity history = new ConsentHistoryEntity();
            history.setId(UUID.randomUUID());
            history.setUserId(userId);
            history.setSubjectKey(subjectKey);
            history.setPurposeSlug(purpose.getSlug());
            history.setPurposeName(purpose.getName());
            history.setPreviousGranted(previousGranted);
            history.setNewGranted(nextGranted);
            history.setPolicyVersionLabel(activePolicy.getVersionLabel());
            history.setRegionCode(regionCode);
            history.setSource(source);
            history.setIpHash(ipHash);
            history.setUserAgentSummary(uaSummary);
            history.setExperimentVariant(variantKey);
            history.setProofHash(proofHash);
            history.setOccurredAt(now);
            newHistoryEntries.add(history);
        }

        consentHistoryRepository.saveAll(newHistoryEntries);

        experimentService.attributeConversionIfApplicable(
                subjectKey,
                userId,
                experimentUuid,
                grantedAnyOptional
        );

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("subjectNamespace", subjectKey.startsWith("user:") ? "USER" : "ANONYMOUS");
        auditDetails.put("policyVersion", activePolicy.getVersionLabel());
        auditDetails.put("regionCode", regionCode.name());
        auditDetails.put("source", source.name());
        auditDetails.put("grantedPurposesCount", grantedCount);
        auditDetails.put("totalPurposesCount", activePurposes.size());
        if (variantKey != null) {
            auditDetails.put("experimentVariant", variantKey);
        }
        auditLogService.appendForCurrentActor(auditAction, "CONSENT_SUBJECT", subjectKey, auditDetails);

        return buildConsentBundle(subjectKey, regionCode);
    }

    private ConsentStatusBundleDto buildConsentBundle(String subjectKey, RegionCode regionCode) {
        PolicyVersionEntity activePolicy = policyService.getActivePolicyVersionEntity();
        RegionRuleEntity regionRule = regionRuleService.getRuleEntityByRegion(regionCode);
        List<ConsentPurposeEntity> activePurposes = policyService.listActivePurposeEntities();

        List<ConsentRecordEntity> records = subjectKey.startsWith("anon:ephemeral-")
                ? List.of()
                : consentRecordRepository.findBySubjectKey(subjectKey);

        Map<UUID, ConsentRecordEntity> byPurposeId = new HashMap<>();
        PolicyVersionEntity latestConsentedPolicy = null;
        Instant latestUpdated = null;

        for (ConsentRecordEntity record : records) {
            byPurposeId.put(record.getPurpose().getId(), record);
            if (latestConsentedPolicy == null
                    || record.getPolicyVersion().getVersionNumber() > latestConsentedPolicy.getVersionNumber()) {
                latestConsentedPolicy = record.getPolicyVersion();
            }
            if (latestUpdated == null
                    || (record.getUpdatedAt() != null && record.getUpdatedAt().isAfter(latestUpdated))) {
                latestUpdated = record.getUpdatedAt();
            }
        }

        boolean hasConsented = !records.isEmpty();
        boolean needsReconsent = !hasConsented
                || policyService.requiresReconsentSince(latestConsentedPolicy, activePolicy);
        boolean shouldShowBanner = needsReconsent;

        List<PurposeConsentStateDto> decisionDtos = activePurposes.stream()
                .sorted(Comparator.comparingInt(ConsentPurposeEntity::getDisplayOrder))
                .map(purpose -> {
                    ConsentRecordEntity rec = byPurposeId.get(purpose.getId());
                    boolean effectiveGranted;
                    if (purpose.isEssential()) {
                        effectiveGranted = true;
                    } else if (rec != null) {
                        effectiveGranted = rec.isGranted();
                    } else {
                        effectiveGranted = regionRule.getConsentModel() != ConsentModel.OPT_IN;
                    }
                    return new PurposeConsentStateDto(
                            purpose.getId().toString(),
                            purpose.getSlug(),
                            purpose.getName(),
                            purpose.getDescription(),
                            purpose.getLegalBasis(),
                            purpose.isEssential(),
                            effectiveGranted,
                            rec != null ? rec.getSource().name() : null,
                            rec != null ? TimestampFormatter.format(rec.getUpdatedAt()) : null
                    );
                })
                .toList();

        return new ConsentStatusBundleDto(
                subjectKey,
                hasConsented,
                needsReconsent,
                shouldShowBanner,
                latestConsentedPolicy != null ? latestConsentedPolicy.getVersionLabel() : null,
                PolicyService.toPolicyDto(activePolicy),
                RegionRuleService.toDto(regionRule),
                decisionDtos,
                TimestampFormatter.format(latestUpdated)
        );
    }

    private static SdkConsentResponse toSdkConsentResponse(ConsentStatusBundleDto bundle) {
        Map<String, Boolean> map = new LinkedHashMap<>();
        for (PurposeConsentStateDto decision : bundle.decisions()) {
            map.put(decision.slug(), decision.granted());
        }
        return new SdkConsentResponse(
                bundle.subjectKey(),
                bundle.regionRule().regionCode(),
                bundle.activePolicyVersion().versionLabel(),
                bundle.hasConsented(),
                bundle.needsReconsent(),
                map,
                bundle.updatedAt()
        );
    }

    public static ConsentHistoryEntryDto toHistoryDto(ConsentHistoryEntity entity) {
        return new ConsentHistoryEntryDto(
                entity.getId().toString(),
                entity.getSubjectKey(),
                entity.getPurposeSlug(),
                entity.getPurposeName(),
                entity.getPreviousGranted(),
                entity.isNewGranted(),
                entity.getPolicyVersionLabel(),
                entity.getRegionCode(),
                entity.getSource().name(),
                entity.getIpHash(),
                entity.getUserAgentSummary(),
                entity.getExperimentVariant(),
                entity.getProofHash(),
                TimestampFormatter.format(entity.getOccurredAt())
        );
    }

    private static UUID parseOptionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("VALIDATION_ERROR", "Invalid experimentId UUID format.");
        }
    }

    private static String sanitizeVariantKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }
}
