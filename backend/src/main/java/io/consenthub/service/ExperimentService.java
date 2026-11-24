package io.consenthub.service;

import io.consenthub.domain.dto.CreateExperimentInput;
import io.consenthub.domain.dto.ExperimentAssignmentContextDto;
import io.consenthub.domain.dto.ExperimentSummaryDto;
import io.consenthub.domain.dto.ExperimentVariantInput;
import io.consenthub.domain.dto.ExperimentVariantMetricDto;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.entity.ExperimentAssignmentEntity;
import io.consenthub.domain.entity.ExperimentEntity;
import io.consenthub.domain.entity.ExperimentVariantConfig;
import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.ScreenName;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ConflictException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.repository.ExperimentAssignmentRepository;
import io.consenthub.repository.ExperimentRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages sticky A/B experiments on SDUI screens with deterministic SHA-256 bucketing (ADR-005)
 * and isolated preview overrides (SEC-G02).
 */
@Service
public class ExperimentService {

    private static final Pattern EXPERIMENT_SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{2,79}$");

    private final ExperimentRepository experimentRepository;
    private final ExperimentAssignmentRepository assignmentRepository;
    private final AuditLogService auditLogService;

    public ExperimentService(
            ExperimentRepository experimentRepository,
            ExperimentAssignmentRepository assignmentRepository,
            AuditLogService auditLogService
    ) {
        this.experimentRepository = experimentRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ExperimentSummaryDto> listAdminExperiments() {
        SecurityUtils.requireAdminUser();
        return experimentRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::buildSummaryDto)
                .toList();
    }

    @Transactional
    public ExperimentSummaryDto createExperiment(CreateExperimentInput input) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (input == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Experiment input is required.");
        }
        String slug = input.slug() != null ? input.slug().trim() : "";
        if (!EXPERIMENT_SLUG_PATTERN.matcher(slug).matches()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Experiment slug must match '^[a-z0-9][a-z0-9_-]{2,79}$'."
            );
        }
        if (experimentRepository.existsBySlug(slug)) {
            throw new ConflictException("EXPERIMENT_SLUG_EXISTS", "Experiment slug '" + slug + "' already exists.");
        }
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 160) {
            throw new BadRequestException("VALIDATION_ERROR", "Experiment name must be between 1 and 160 characters.");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Experiment description is required.");
        }
        if (input.targetScreen() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "targetScreen is required.");
        }

        List<ExperimentVariantConfig> variants = validateAndMapVariants(input.variants());
        ExperimentStatus status = input.status() != null ? input.status() : ExperimentStatus.DRAFT;

        Instant now = Instant.now();
        ExperimentEntity entity = new ExperimentEntity();
        entity.setId(UUID.randomUUID());
        entity.setSlug(slug);
        entity.setName(input.name().trim());
        entity.setDescription(input.description().trim());
        entity.setTargetScreen(input.targetScreen());
        entity.setTargetRegion(input.targetRegion());
        entity.setStatus(status);
        entity.setVariantsJson(variants);
        entity.setCreatedBy(admin.id());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        ExperimentEntity saved = experimentRepository.save(entity);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("slug", saved.getSlug());
        details.put("targetScreen", saved.getTargetScreen().name());
        details.put("targetRegion", saved.getTargetRegion() != null ? saved.getTargetRegion().name() : "ALL");
        details.put("status", saved.getStatus().name());
        details.put("variantCount", variants.size());

        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "EXPERIMENT_CREATED",
                "EXPERIMENT",
                saved.getId().toString(),
                details
        );

        return buildSummaryDto(saved);
    }

    @Transactional
    public ExperimentSummaryDto updateExperimentStatus(String idRaw, ExperimentStatus status) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (status == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Experiment status is required.");
        }
        UUID id = parseUuid(idRaw, "Invalid experiment ID.");
        ExperimentEntity entity = experimentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Experiment not found: " + id));

        ExperimentStatus previousStatus = entity.getStatus();
        entity.setStatus(status);
        entity.setUpdatedAt(Instant.now());
        ExperimentEntity saved = experimentRepository.save(entity);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("slug", saved.getSlug());
        details.put("previousStatus", previousStatus.name());
        details.put("newStatus", status.name());

        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "EXPERIMENT_STATUS_UPDATED",
                "EXPERIMENT",
                saved.getId().toString(),
                details
        );

        return buildSummaryDto(saved);
    }

    /**
     * Resolves or stickily assigns an A/B experiment variant for a given SDUI screen and subject.
     * SEC-G02: When {@code previewVariantKey} is supplied, resolves the requested variant WITHOUT
     * writing to {@code experiment_assignments}.
     */
    @Transactional
    public Optional<ExperimentAssignmentContextDto> resolveOrAssignVariant(
            ScreenName screenName,
            RegionCode regionCode,
            String subjectKey,
            String previewVariantKey
    ) {
        if (previewVariantKey != null && !previewVariantKey.isBlank()) {
            String trimmedPreview = previewVariantKey.trim();
            List<ExperimentEntity> candidates = experimentRepository.findByTargetScreenOrderByCreatedAtDesc(screenName);
            for (ExperimentEntity exp : candidates) {
                if (exp.getTargetRegion() == null || exp.getTargetRegion() == regionCode) {
                    for (ExperimentVariantConfig variant : exp.getVariantsJson()) {
                        if (trimmedPreview.equals(variant.key())) {
                            return Optional.of(new ExperimentAssignmentContextDto(
                                    exp.getId().toString(),
                                    exp.getSlug(),
                                    variant.key(),
                                    variant,
                                    true
                            ));
                        }
                    }
                }
            }
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Unknown previewVariantKey '" + trimmedPreview + "' for screen " + screenName
            );
        }

        Optional<ExperimentEntity> runningExpOpt = findRunningExperiment(screenName, regionCode);
        if (runningExpOpt.isEmpty() || subjectKey == null || subjectKey.isBlank()) {
            return Optional.empty();
        }

        ExperimentEntity experiment = runningExpOpt.get();
        List<ExperimentVariantConfig> variants = experiment.getVariantsJson();
        if (variants == null || variants.isEmpty()) {
            return Optional.empty();
        }

        // Ephemeral unpersisted fallback keys do not pollute experiment_assignments
        if (subjectKey.startsWith("anon:ephemeral-")) {
            ExperimentVariantConfig bucketed = selectVariantByBucket(experiment.getId(), subjectKey, variants);
            return Optional.of(new ExperimentAssignmentContextDto(
                    experiment.getId().toString(),
                    experiment.getSlug(),
                    bucketed.key(),
                    bucketed,
                    true
            ));
        }

        Optional<ExperimentAssignmentEntity> existing =
                assignmentRepository.findByExperimentIdAndSubjectKey(experiment.getId(), subjectKey);
        if (existing.isPresent()) {
            String persistedKey = existing.get().getVariantKey();
            ExperimentVariantConfig matched = variants.stream()
                    .filter(v -> v.key().equals(persistedKey))
                    .findFirst()
                    .orElseGet(() -> selectVariantByBucket(experiment.getId(), subjectKey, variants));
            return Optional.of(new ExperimentAssignmentContextDto(
                    experiment.getId().toString(),
                    experiment.getSlug(),
                    matched.key(),
                    matched,
                    false
            ));
        }

        ExperimentVariantConfig chosen = selectVariantByBucket(experiment.getId(), subjectKey, variants);
        UUID userId = SecurityUtils.getCurrentUser().map(AuthenticatedUser::id).orElse(null);

        try {
            ExperimentAssignmentEntity assignment = new ExperimentAssignmentEntity();
            assignment.setId(UUID.randomUUID());
            assignment.setExperimentId(experiment.getId());
            assignment.setSubjectKey(subjectKey);
            assignment.setUserId(userId);
            assignment.setVariantKey(chosen.key());
            assignment.setConverted(false);
            assignment.setAssignedAt(Instant.now());
            assignmentRepository.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException raceEx) {
            Optional<ExperimentAssignmentEntity> raced =
                    assignmentRepository.findByExperimentIdAndSubjectKey(experiment.getId(), subjectKey);
            if (raced.isPresent()) {
                String racedKey = raced.get().getVariantKey();
                ExperimentVariantConfig matched = variants.stream()
                        .filter(v -> v.key().equals(racedKey))
                        .findFirst()
                        .orElse(chosen);
                return Optional.of(new ExperimentAssignmentContextDto(
                        experiment.getId().toString(),
                        experiment.getSlug(),
                        matched.key(),
                        matched,
                        false
                ));
            }
        }

        return Optional.of(new ExperimentAssignmentContextDto(
                experiment.getId().toString(),
                experiment.getSlug(),
                chosen.key(),
                chosen,
                false
        ));
    }

    /**
     * Marks experiment assignment(s) as converted when the subject grants at least one optional purpose.
     */
    @Transactional
    public void attributeConversionIfApplicable(
            String subjectKey,
            UUID userId,
            UUID explicitExperimentId,
            boolean grantedOptionalPurpose
    ) {
        if (!grantedOptionalPurpose || subjectKey == null || subjectKey.isBlank()) {
            return;
        }
        Instant now = Instant.now();
        if (explicitExperimentId != null) {
            assignmentRepository.findByExperimentIdAndSubjectKey(explicitExperimentId, subjectKey)
                    .ifPresent(assignment -> {
                        if (!assignment.isConverted()) {
                            assignment.setConverted(true);
                            assignment.setConvertedAt(now);
                            if (assignment.getUserId() == null && userId != null) {
                                assignment.setUserId(userId);
                            }
                            assignmentRepository.save(assignment);
                        }
                    });
            return;
        }

        List<ExperimentAssignmentEntity> assignments = assignmentRepository.findBySubjectKey(subjectKey);
        for (ExperimentAssignmentEntity assignment : assignments) {
            if (!assignment.isConverted()) {
                assignment.setConverted(true);
                assignment.setConvertedAt(now);
                if (assignment.getUserId() == null && userId != null) {
                    assignment.setUserId(userId);
                }
                assignmentRepository.save(assignment);
            }
        }
    }

    /**
     * Computes deterministic bucket in [0..99] matching ExperimentBucketer (unsigned big-endian int mod 100).
     */
    public static int computeDeterministicBucket(UUID experimentId, String subjectKey) {
        byte[] digest = HashUtil.sha256Bytes(experimentId + ":" + subjectKey);
        long unsignedBigEndianInt = ((digest[0] & 0xFFL) << 24)
                | ((digest[1] & 0xFFL) << 16)
                | ((digest[2] & 0xFFL) << 8)
                | (digest[3] & 0xFFL);
        return (int) (unsignedBigEndianInt % 100L);
    }

    public static ExperimentVariantConfig selectVariantByBucket(
            UUID experimentId,
            String subjectKey,
            List<ExperimentVariantConfig> variants
    ) {
        int bucket = computeDeterministicBucket(experimentId, subjectKey);
        int cumulative = 0;
        ExperimentVariantConfig fallback = variants.getLast();
        for (ExperimentVariantConfig variant : variants) {
            if (variant.weight() > 0) {
                fallback = variant;
            }
            cumulative += variant.weight();
            if (bucket < cumulative) {
                return variant;
            }
        }
        return fallback;
    }

    private Optional<ExperimentEntity> findRunningExperiment(ScreenName screenName, RegionCode regionCode) {
        List<ExperimentEntity> running =
                experimentRepository.findByStatusAndTargetScreenOrderByCreatedAtDesc(ExperimentStatus.RUNNING, screenName);
        for (ExperimentEntity exp : running) {
            if (exp.getTargetRegion() == regionCode) {
                return Optional.of(exp);
            }
        }
        for (ExperimentEntity exp : running) {
            if (exp.getTargetRegion() == null) {
                return Optional.of(exp);
            }
        }
        return Optional.empty();
    }

    private ExperimentSummaryDto buildSummaryDto(ExperimentEntity experiment) {
        List<ExperimentAssignmentEntity> assignments = assignmentRepository.findByExperimentId(experiment.getId());
        Map<String, int[]> countsByVariant = new HashMap<>();
        for (ExperimentAssignmentEntity assignment : assignments) {
            int[] pair = countsByVariant.computeIfAbsent(assignment.getVariantKey(), k -> new int[2]);
            pair[0]++;
            if (assignment.isConverted()) {
                pair[1]++;
            }
        }

        int totalExposures = 0;
        int totalConversions = 0;
        List<ExperimentVariantMetricDto> variantMetrics = new ArrayList<>();

        for (ExperimentVariantConfig variant : experiment.getVariantsJson()) {
            int[] pair = countsByVariant.getOrDefault(variant.key(), new int[]{0, 0});
            int exposures = pair[0];
            int conversions = pair[1];
            double rate = exposures > 0 ? roundTwoDecimals((conversions * 100.0) / exposures) : 0.0;
            totalExposures += exposures;
            totalConversions += conversions;

            variantMetrics.add(new ExperimentVariantMetricDto(
                    variant.key(),
                    variant.name(),
                    variant.weight(),
                    variant.headline(),
                    variant.body(),
                    variant.primaryCtaLabel(),
                    variant.secondaryCtaLabel(),
                    variant.layoutTone() != null ? variant.layoutTone() : "STANDARD",
                    exposures,
                    conversions,
                    rate
            ));
        }

        double overallConsentRate = totalExposures > 0
                ? roundTwoDecimals((totalConversions * 100.0) / totalExposures)
                : 0.0;

        return new ExperimentSummaryDto(
                experiment.getId().toString(),
                experiment.getSlug(),
                experiment.getName(),
                experiment.getDescription(),
                experiment.getTargetScreen(),
                experiment.getTargetRegion(),
                experiment.getStatus(),
                totalExposures,
                totalConversions,
                overallConsentRate,
                variantMetrics,
                TimestampFormatter.format(experiment.getCreatedAt()),
                TimestampFormatter.format(experiment.getUpdatedAt())
        );
    }

    private static List<ExperimentVariantConfig> validateAndMapVariants(List<ExperimentVariantInput> inputs) {
        if (inputs == null || inputs.size() < 2) {
            throw new BadRequestException("VALIDATION_ERROR", "At least 2 experiment variants are required.");
        }
        Set<String> seenKeys = new HashSet<>();
        int weightSum = 0;
        List<ExperimentVariantConfig> result = new ArrayList<>();

        for (ExperimentVariantInput item : inputs) {
            if (item == null || item.key() == null || item.key().isBlank()) {
                throw new BadRequestException("VALIDATION_ERROR", "Each variant must specify a non-blank key.");
            }
            String key = item.key().trim();
            if (!seenKeys.add(key)) {
                throw new BadRequestException("VALIDATION_ERROR", "Duplicate variant key: " + key);
            }
            if (item.name() == null || item.name().isBlank()) {
                throw new BadRequestException("VALIDATION_ERROR", "Variant '" + key + "' requires a name.");
            }
            if (item.weight() == null || item.weight() < 0 || item.weight() > 100) {
                throw new BadRequestException("VALIDATION_ERROR", "Variant '" + key + "' weight must be 0..100.");
            }
            if (item.headline() == null || item.headline().isBlank()
                    || item.body() == null || item.body().isBlank()
                    || item.primaryCtaLabel() == null || item.primaryCtaLabel().isBlank()
                    || item.secondaryCtaLabel() == null || item.secondaryCtaLabel().isBlank()) {
                throw new BadRequestException(
                        "VALIDATION_ERROR",
                        "Variant '" + key + "' requires headline, body, primaryCtaLabel, and secondaryCtaLabel."
                );
            }

            weightSum += item.weight();
            String tone = (item.layoutTone() != null && !item.layoutTone().isBlank())
                    ? item.layoutTone().trim()
                    : "STANDARD";

            result.add(new ExperimentVariantConfig(
                    key,
                    item.name().trim(),
                    item.weight(),
                    item.headline().trim(),
                    item.body().trim(),
                    item.primaryCtaLabel().trim(),
                    item.secondaryCtaLabel().trim(),
                    tone
            ));
        }

        if (weightSum != 100) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Experiment variant weights must sum to 100 (actual sum: " + weightSum + ")."
            );
        }
        return result;
    }

    private static UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new BadRequestException("VALIDATION_ERROR", message);
        }
    }

    private static double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
