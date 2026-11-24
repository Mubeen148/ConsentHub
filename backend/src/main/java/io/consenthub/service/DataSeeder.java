package io.consenthub.service;

import io.consenthub.config.AppProperties;
import io.consenthub.domain.entity.ConsentHistoryEntity;
import io.consenthub.domain.entity.ConsentPurposeEntity;
import io.consenthub.domain.entity.ConsentRecordEntity;
import io.consenthub.domain.entity.ExperimentAssignmentEntity;
import io.consenthub.domain.entity.ExperimentEntity;
import io.consenthub.domain.entity.ExperimentVariantConfig;
import io.consenthub.domain.entity.PolicyVersionEntity;
import io.consenthub.domain.entity.UserEntity;
import io.consenthub.domain.enums.ConsentSource;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.repository.AuditLogRepository;
import io.consenthub.repository.ConsentHistoryRepository;
import io.consenthub.repository.ConsentPurposeRepository;
import io.consenthub.repository.ConsentRecordRepository;
import io.consenthub.repository.ExperimentAssignmentRepository;
import io.consenthub.repository.ExperimentRepository;
import io.consenthub.repository.PolicyVersionRepository;
import io.consenthub.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent startup bootstrapper that complements {@code V2__seed_demo_data.sql}:
 * <ul>
 *   <li>Ensures demo accounts ({@code admin@consenthub.io} / {@code AdminPass123!} and
 *       {@code user@consenthub.io} / {@code UserPass123!}) have valid BCrypt(12) password hashes.</li>
 *   <li>Populates realistic 14-day multi-region {@code consent_records}, {@code consent_history},
 *       {@code experiment_assignments}, and cryptographically valid {@code audit_logs} entries
 *       on initial boot so Admin Analytics, Experiment metrics, and {@code verifyAuditChain}
 *       work immediately after {@code docker compose up}.</li>
 * </ul>
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AppProperties appProperties;
    private final UserRepository userRepository;
    private final ConsentPurposeRepository purposeRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final ConsentHistoryRepository consentHistoryRepository;
    private final ExperimentRepository experimentRepository;
    private final ExperimentAssignmentRepository assignmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(
            AppProperties appProperties,
            UserRepository userRepository,
            ConsentPurposeRepository purposeRepository,
            PolicyVersionRepository policyVersionRepository,
            ConsentRecordRepository consentRecordRepository,
            ConsentHistoryRepository consentHistoryRepository,
            ExperimentRepository experimentRepository,
            ExperimentAssignmentRepository assignmentRepository,
            AuditLogRepository auditLogRepository,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder
    ) {
        this.appProperties = appProperties;
        this.userRepository = userRepository;
        this.purposeRepository = purposeRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.consentRecordRepository = consentRecordRepository;
        this.consentHistoryRepository = consentHistoryRepository;
        this.experimentRepository = experimentRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appProperties.seed() == null || !appProperties.seed().enabled()) {
            log.info("Demo seed bootstrapper disabled via consenthub.seed.enabled=false");
            return;
        }

        ensureDemoPasswordHashes();
        seedDemoTelemetryIfEmpty();
        seedInitialAuditChainIfEmpty();
    }

    private void ensureDemoPasswordHashes() {
        ensureUserPassword("admin@consenthub.io", "AdminPass123!");
        ensureUserPassword("user@consenthub.io", "UserPass123!");
        ensureUserPassword("alex.rivera@example.com", "AlexPass123!");
    }

    private void ensureUserPassword(String email, String rawPassword) {
        userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (user.getDeletedAt() != null) {
                return;
            }
            String existing = user.getPasswordHash();
            boolean needsRefresh = existing == null
                    || existing.contains("SEED_BOOTSTRAP")
                    || !passwordEncoder.matches(rawPassword, existing);
            if (needsRefresh) {
                user.setPasswordHash(passwordEncoder.encode(rawPassword));
                userRepository.saveAndFlush(user);
                log.info("Verified BCrypt(12) demo password hash for {}", email);
            }
        });
    }

    private void seedDemoTelemetryIfEmpty() {
        if (consentRecordRepository.count() > 0) {
            return;
        }

        Optional<PolicyVersionEntity> activePolicyOpt = policyVersionRepository.findByActiveTrue();
        List<ConsentPurposeEntity> purposes = purposeRepository.findByActiveTrueOrderByDisplayOrderAsc();
        if (activePolicyOpt.isEmpty() || purposes.isEmpty()) {
            return;
        }

        PolicyVersionEntity activePolicy = activePolicyOpt.get();
        Optional<ExperimentEntity> experimentOpt = experimentRepository.findBySlug("eu-banner-clarity-q4");
        UUID expId = experimentOpt.map(ExperimentEntity::getId).orElse(null);

        // 1. Seed primary demo user (user@consenthub.io) with realistic granular choices on v2.0
        userRepository.findByEmailIgnoreCase("user@consenthub.io").ifPresent(demoUser -> {
            Instant baseTime = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
            seedSubjectChoices(
                    demoUser.getId(),
                    demoUser.getSubjectKey(),
                    RegionCode.EU,
                    ConsentSource.PREFERENCE_CENTER,
                    activePolicy,
                    purposes,
                    Map.of(
                            "essential", true,
                            "analytics", true,
                            "personalization", true,
                            "marketing", false,
                            "ads", false
                    ),
                    expId,
                    "concise_trust",
                    baseTime
            );
        });

        // 2. Seed secondary user (alex.rivera@example.com) in US_CA
        userRepository.findByEmailIgnoreCase("alex.rivera@example.com").ifPresent(alex -> {
            Instant baseTime = Instant.now().minus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MILLIS);
            seedSubjectChoices(
                    alex.getId(),
                    alex.getSubjectKey(),
                    RegionCode.US_CA,
                    ConsentSource.BANNER,
                    activePolicy,
                    purposes,
                    Map.of(
                            "essential", true,
                            "analytics", true,
                            "personalization", false,
                            "marketing", false,
                            "ads", false
                    ),
                    expId,
                    "control",
                    baseTime
            );
        });

        // 3. Seed 24 pseudonymous visitor subjects across EU, US_CA, and GLOBAL over the past 14 days
        RegionCode[] regions = {RegionCode.EU, RegionCode.US_CA, RegionCode.GLOBAL};
        ConsentSource[] sources = {ConsentSource.BANNER, ConsentSource.PREFERENCE_CENTER, ConsentSource.SDK};

        for (int i = 1; i <= 24; i++) {
            String anonKey = String.format("anon:demo_visitor_%03d_2026", i);
            RegionCode region = regions[i % regions.length];
            ConsentSource source = sources[i % sources.length];
            int daysAgo = (i % 14);
            Instant occurredAt = Instant.now()
                    .minus(daysAgo, ChronoUnit.DAYS)
                    .minus(i * 11L, ChronoUnit.MINUTES)
                    .truncatedTo(ChronoUnit.MILLIS);

            String variant = (i % 2 == 0) ? "concise_trust" : "control";
            // Make Variant B (concise_trust) convert slightly higher than Control for realistic A/B chart
            boolean acceptAll = "concise_trust".equals(variant) ? (i % 3 != 0) : (i % 2 == 0);
            boolean customPartial = !acceptAll && (i % 5 == 0);

            Map<String, Boolean> decisions = new LinkedHashMap<>();
            decisions.put("essential", true);
            decisions.put("analytics", acceptAll || customPartial);
            decisions.put("personalization", acceptAll);
            decisions.put("marketing", acceptAll && region != RegionCode.EU);
            decisions.put("ads", acceptAll && region == RegionCode.GLOBAL);

            seedSubjectChoices(
                    null,
                    anonKey,
                    region,
                    source,
                    activePolicy,
                    purposes,
                    decisions,
                    expId,
                    variant,
                    occurredAt
            );

            if (experimentOpt.isPresent()) {
                ExperimentEntity exp = experimentOpt.get();
                List<ExperimentVariantConfig> variants = exp.getVariantsJson();
                if (variants != null && !variants.isEmpty()
                        && assignmentRepository.findByExperimentIdAndSubjectKey(exp.getId(), anonKey).isEmpty()) {
                    boolean converted = acceptAll || customPartial;
                    ExperimentAssignmentEntity assignment = new ExperimentAssignmentEntity();
                    assignment.setId(UUID.randomUUID());
                    assignment.setExperimentId(exp.getId());
                    assignment.setSubjectKey(anonKey);
                    assignment.setVariantKey(variant);
                    assignment.setConverted(converted);
                    assignment.setAssignedAt(occurredAt.minus(15, ChronoUnit.SECONDS));
                    assignment.setConvertedAt(converted ? occurredAt : null);
                    assignmentRepository.save(assignment);
                }
            }
        }

        log.info("Seeded 14-day multi-region consent records, history ledger receipts, and A/B experiment assignments.");
    }

    private void seedSubjectChoices(
            UUID userId,
            String subjectKey,
            RegionCode regionCode,
            ConsentSource source,
            PolicyVersionEntity policy,
            List<ConsentPurposeEntity> purposes,
            Map<String, Boolean> decisions,
            UUID experimentId,
            String variantKey,
            Instant timestamp
    ) {
        List<ConsentHistoryEntity> historyBatch = new ArrayList<>();
        String ipHash = HashUtil.sha256Hex(appProperties.privacy().ipHashSalt() + ":seed:" + subjectKey);

        for (ConsentPurposeEntity purpose : purposes) {
            boolean granted = purpose.isEssential() || Boolean.TRUE.equals(decisions.get(purpose.getSlug()));

            ConsentRecordEntity record = new ConsentRecordEntity();
            record.setId(UUID.randomUUID());
            record.setUserId(userId);
            record.setSubjectKey(subjectKey);
            record.setPurpose(purpose);
            record.setGranted(granted);
            record.setPolicyVersion(policy);
            record.setRegionCode(regionCode);
            record.setSource(source);
            record.setExperimentId(experimentId);
            record.setVariantKey(variantKey);
            record.setUpdatedAt(timestamp);
            consentRecordRepository.save(record);

            String proofHash = ConsentProofHasher.computeProofHash(
                    subjectKey,
                    purpose.getSlug(),
                    granted,
                    policy.getVersionLabel(),
                    regionCode,
                    source,
                    timestamp
            );

            ConsentHistoryEntity history = new ConsentHistoryEntity();
            history.setId(UUID.randomUUID());
            history.setUserId(userId);
            history.setSubjectKey(subjectKey);
            history.setPurposeSlug(purpose.getSlug());
            history.setPurposeName(purpose.getName());
            history.setPreviousGranted(null);
            history.setNewGranted(granted);
            history.setPolicyVersionLabel(policy.getVersionLabel());
            history.setRegionCode(regionCode);
            history.setSource(source);
            history.setIpHash(ipHash);
            history.setUserAgentSummary("Chrome / macOS");
            history.setExperimentVariant(variantKey);
            history.setProofHash(proofHash);
            history.setOccurredAt(timestamp);
            historyBatch.add(history);
        }

        consentHistoryRepository.saveAll(historyBatch);
    }

    private void seedInitialAuditChainIfEmpty() {
        if (auditLogRepository.count() > 0) {
            return;
        }

        UUID adminId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID userId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        Instant now = Instant.now();

        auditLogService.appendAtTimestamp(
                adminId,
                "admin@consenthub.io",
                "ADMIN",
                "POLICY_PUBLISHED",
                "POLICY_VERSION",
                "22222222-2222-4222-8222-222222222202",
                Map.of("versionLabel", "v2.0", "requiresReconsent", true, "activated", true),
                now.minus(10, ChronoUnit.DAYS)
        );
        auditLogService.appendAtTimestamp(
                adminId,
                "admin@consenthub.io",
                "ADMIN",
                "EXPERIMENT_CREATED",
                "EXPERIMENT",
                "44444444-4444-4444-8444-444444444444",
                Map.of("slug", "eu-banner-clarity-q4", "targetScreen", "CONSENT_BANNER", "status", "RUNNING"),
                now.minus(9, ChronoUnit.DAYS)
        );
        auditLogService.appendAtTimestamp(
                adminId,
                "admin@consenthub.io",
                "ADMIN",
                "API_KEY_CREATED",
                "API_KEY",
                "66666666-6666-4666-8666-666666666601",
                Map.of("keyPrefix", "ch_live_demo_", "scopes", "sdk:read,sdk:write"),
                now.minus(8, ChronoUnit.DAYS)
        );
        auditLogService.appendAtTimestamp(
                userId,
                "user@consenthub.io",
                "USER",
                "CONSENT_PREFERENCES_SAVED",
                "CONSENT_SUBJECT",
                "user:" + userId,
                Map.of("policyVersion", "v2.0", "regionCode", "EU", "grantedPurposesCount", 3),
                now.minus(3, ChronoUnit.DAYS)
        );
        auditLogService.appendAtTimestamp(
                userId,
                "user@consenthub.io",
                "USER",
                "PRIVACY_EXPORT_DOWNLOADED",
                "DATA_REQUEST",
                "55555555-5555-4555-8555-555555555501",
                Map.of("requestType", "EXPORT", "status", "COMPLETED"),
                now.minus(2, ChronoUnit.DAYS)
        );

        log.info("Seeded cryptographic SHA-256 audit hash chain entries.");
    }
}
