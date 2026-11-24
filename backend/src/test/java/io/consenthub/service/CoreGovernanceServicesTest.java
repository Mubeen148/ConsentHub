package io.consenthub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.consenthub.config.AppProperties;
import io.consenthub.domain.dto.AuditChainVerificationDto;
import io.consenthub.domain.dto.ConsentStatusBundleDto;
import io.consenthub.domain.dto.CreateDataRequestInput;
import io.consenthub.domain.dto.ProcessDataRequestInput;
import io.consenthub.domain.dto.PurposeDecisionInput;
import io.consenthub.domain.dto.SaveConsentInput;
import io.consenthub.domain.entity.AuditLogEntity;
import io.consenthub.domain.entity.ConsentPurposeEntity;
import io.consenthub.domain.entity.DataRequestEntity;
import io.consenthub.domain.entity.ExperimentVariantConfig;
import io.consenthub.domain.entity.PolicyVersionEntity;
import io.consenthub.domain.entity.RegionRuleEntity;
import io.consenthub.domain.entity.UserEntity;
import io.consenthub.domain.enums.BannerStyle;
import io.consenthub.domain.enums.ConsentModel;
import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.DataRequestType;
import io.consenthub.domain.enums.LegalBasis;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;
import io.consenthub.domain.enums.UserRole;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ForbiddenException;
import io.consenthub.repository.AuditLogRepository;
import io.consenthub.repository.ConsentHistoryRepository;
import io.consenthub.repository.ConsentRecordRepository;
import io.consenthub.repository.DataRequestRepository;
import io.consenthub.repository.ExperimentAssignmentRepository;
import io.consenthub.repository.ExperimentRepository;
import io.consenthub.repository.UserRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.JwtTokenProvider;
import io.consenthub.security.SecurityUtils;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class CoreGovernanceServicesTest {

    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties(
                "1.0.0",
                new AppProperties.Jwt("unit-test-jwt-secret-key-minimum-32-bytes-long-2026!", 3600L),
                new AppProperties.Cookie(false, "ch_access_token", "ch_csrf_token"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Privacy("unit-test-ip-salt"),
                new AppProperties.Seed(false),
                new AppProperties.RateLimit(30, 10, 120)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consentServiceForcesEssentialTrueEvenWhenCallerAttemptsToDisable() {
        ConsentRecordRepository recordRepo = mock(ConsentRecordRepository.class);
        ConsentHistoryRepository historyRepo = mock(ConsentHistoryRepository.class);
        PolicyService policyService = mock(PolicyService.class);
        RegionRuleService regionRuleService = mock(RegionRuleService.class);
        RegionResolverService regionResolver = new RegionResolverService();
        ExperimentService experimentService = mock(ExperimentService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        PolicyVersionEntity activePolicy = new PolicyVersionEntity();
        activePolicy.setId(UUID.randomUUID());
        activePolicy.setVersionNumber(2);
        activePolicy.setVersionLabel("v2.0");
        activePolicy.setTitle("Policy v2");
        activePolicy.setSummary("Summary");
        activePolicy.setContentMarkdown("# Markdown");
        activePolicy.setRequiresReconsent(true);
        activePolicy.setActive(true);
        activePolicy.setPublishedAt(Instant.now());
        activePolicy.setCreatedAt(Instant.now());

        RegionRuleEntity euRule = new RegionRuleEntity();
        euRule.setId(UUID.randomUUID());
        euRule.setRegionCode(RegionCode.EU);
        euRule.setRegimeName(RegulatoryRegime.GDPR);
        euRule.setConsentModel(ConsentModel.OPT_IN);
        euRule.setBannerStyle(BannerStyle.MODAL);
        euRule.setShowRejectAll(true);
        euRule.setRetentionDays(365);
        euRule.setUpdatedAt(Instant.now());

        ConsentPurposeEntity essential = new ConsentPurposeEntity();
        essential.setId(UUID.randomUUID());
        essential.setSlug("essential");
        essential.setName("Essential Security");
        essential.setDescription("Required");
        essential.setLegalBasis(LegalBasis.STRICTLY_NECESSARY);
        essential.setEssential(true);
        essential.setActive(true);
        essential.setDisplayOrder(1);

        ConsentPurposeEntity analytics = new ConsentPurposeEntity();
        analytics.setId(UUID.randomUUID());
        analytics.setSlug("analytics");
        analytics.setName("Analytics");
        analytics.setDescription("Optional");
        analytics.setLegalBasis(LegalBasis.CONSENT);
        analytics.setEssential(false);
        analytics.setActive(true);
        analytics.setDisplayOrder(2);

        List<io.consenthub.domain.entity.ConsentRecordEntity> stored = new ArrayList<>();
        when(policyService.getActivePolicyVersionEntity()).thenReturn(activePolicy);
        when(policyService.listActivePurposeEntities()).thenReturn(List.of(essential, analytics));
        when(regionRuleService.getRuleEntityByRegion(RegionCode.EU)).thenReturn(euRule);
        when(recordRepo.findBySubjectKey("anon:visitor_unit_0001")).thenAnswer(inv -> stored);
        when(recordRepo.save(any())).thenAnswer(inv -> {
            io.consenthub.domain.entity.ConsentRecordEntity r = inv.getArgument(0);
            stored.add(r);
            return r;
        });

        ConsentService consentService = new ConsentService(
                recordRepo,
                historyRepo,
                policyService,
                regionRuleService,
                regionResolver,
                experimentService,
                auditLogService,
                appProperties
        );

        // Attempt to pass granted=false for essential purpose
        SaveConsentInput input = new SaveConsentInput(
                "anon:visitor_unit_0001",
                RegionCode.EU,
                "PREFERENCE_CENTER",
                null,
                null,
                List.of(
                        new PurposeDecisionInput("essential", false),
                        new PurposeDecisionInput("analytics", true)
                )
        );

        ConsentStatusBundleDto result = consentService.saveConsentPreferences(input);

        assertTrue(result.hasConsented());
        assertFalse(result.needsReconsent());
        assertEquals(2, result.decisions().size());
        assertTrue(result.decisions().get(0).granted(), "Essential purpose must remain locked true");
        assertTrue(result.decisions().get(1).granted(), "Analytics should reflect explicit opt-in");
        verify(historyRepo).saveAll(any());
    }

    @Test
    void deterministicExperimentBucketingIsStickyAcrossRepeatedCalls() {
        UUID expId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        List<ExperimentVariantConfig> variants = List.of(
                new ExperimentVariantConfig("control", "Control", 50, "H1", "B1", "Accept", "Reject", "STANDARD"),
                new ExperimentVariantConfig("concise_trust", "Variant B", 50, "H2", "B2", "Accept", "Reject", "EDITORIAL")
        );

        int firstBucket = ExperimentService.computeDeterministicBucket(expId, "anon:sticky_subject_12345");
        int secondBucket = ExperimentService.computeDeterministicBucket(expId, "anon:sticky_subject_12345");
        assertEquals(firstBucket, secondBucket);
        assertTrue(firstBucket >= 0 && firstBucket < 100);

        ExperimentVariantConfig chosen1 = ExperimentService.selectVariantByBucket(expId, "anon:sticky_subject_12345", variants);
        ExperimentVariantConfig chosen2 = ExperimentService.selectVariantByBucket(expId, "anon:sticky_subject_12345", variants);
        assertEquals(chosen1.key(), chosen2.key());
    }

    @Test
    void auditLogHashChainDetectsTamperingImmediately() throws Exception {
        AuditLogRepository auditRepo = mock(AuditLogRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DataSource dataSource = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("H2");

        List<AuditLogEntity> ledger = new ArrayList<>();
        when(auditRepo.findFirstByOrderBySequenceNumDesc()).thenAnswer(inv ->
                ledger.isEmpty() ? Optional.empty() : Optional.of(ledger.getLast()));
        when(auditRepo.saveAndFlush(any())).thenAnswer(inv -> {
            AuditLogEntity e = inv.getArgument(0);
            e.setSequenceNum((long) (ledger.size() + 1));
            ledger.add(e);
            return e;
        });
        when(auditRepo.findAllByOrderBySequenceNumAsc()).thenAnswer(inv -> new ArrayList<>(ledger));

        AuditLogService auditService = new AuditLogService(auditRepo, jdbcTemplate, dataSource);

        UUID adminId = UUID.randomUUID();
        auditService.append(adminId, "admin@consenthub.io", "ADMIN", "POLICY_PUBLISHED", "POLICY", "v2.0", Map.of("v", 2));
        auditService.append(adminId, "admin@consenthub.io", "ADMIN", "API_KEY_CREATED", "API_KEY", "k1", Map.of("active", true));

        AuditChainVerificationDto intact = auditService.verifyAuditChainInternal();
        assertTrue(intact.chainValid());
        assertEquals(2, intact.totalEntriesChecked());

        // Simulate unauthorized out-of-band DB mutation on entry #1
        ledger.getFirst().setAction("TAMPERED_ACTION");

        AuditChainVerificationDto tampered = auditService.verifyAuditChainInternal();
        assertFalse(tampered.chainValid());
        assertEquals(1, tampered.brokenAtSequenceNum());
    }

    @Test
    void dsarDeleteRequiresTypedConfirmationAndRedactsPiiUponCompletion() {
        DataRequestRepository dsarRepo = mock(DataRequestRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        ConsentRecordRepository recordRepo = mock(ConsentRecordRepository.class);
        ConsentHistoryRepository historyRepo = mock(ConsentHistoryRepository.class);
        ExperimentAssignmentRepository assignRepo = mock(ExperimentAssignmentRepository.class);
        ExperimentRepository expRepo = mock(ExperimentRepository.class);
        ConsentService consentService = mock(ConsentService.class);
        AuditLogService auditService = mock(AuditLogService.class);
        PasswordEncoder encoder = new BCryptPasswordEncoder(4);

        DsarService dsarService = new DsarService(
                dsarRepo, userRepo, recordRepo, historyRepo, assignRepo, expRepo, consentService, auditService, encoder
        );

        UUID userId = UUID.randomUUID();
        UserEntity targetUser = new UserEntity();
        targetUser.setId(userId);
        targetUser.setEmail("marcus@example.com");
        targetUser.setFullName("Marcus Lindqvist");
        targetUser.setRole(UserRole.USER);
        targetUser.setRegion(RegionCode.EU);
        targetUser.setPasswordHash(encoder.encode("UserPass123!"));

        setAuthContext(userId, "marcus@example.com", UserRole.USER);
        when(userRepo.findByIdAndDeletedAtIsNull(userId)).thenReturn(Optional.of(targetUser));
        when(dsarRepo.findByUser_IdOrderByRequestedAtDesc(userId)).thenReturn(List.of());
        when(dsarRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Missing typed phrase -> BadRequestException
        assertThrows(BadRequestException.class, () ->
                dsarService.createDataRequest(new CreateDataRequestInput(DataRequestType.DELETE, "Leave", "wrong")));

        // Valid phrase -> PENDING ticket created
        var created = dsarService.createDataRequest(
                new CreateDataRequestInput(DataRequestType.DELETE, "Erasure test", "DELETE MY DATA")
        );
        assertEquals(DataRequestStatus.PENDING, created.status());

        // Admin completes DELETE -> user PII pseudonymized & deleted_at populated
        UUID adminId = UUID.randomUUID();
        setAuthContext(adminId, "admin@consenthub.io", UserRole.ADMIN);

        UUID reqId = UUID.fromString(created.id());
        DataRequestEntity pendingEntity = new DataRequestEntity();
        pendingEntity.setId(reqId);
        pendingEntity.setUser(targetUser);
        pendingEntity.setRequestType(DataRequestType.DELETE);
        pendingEntity.setStatus(DataRequestStatus.PENDING);
        pendingEntity.setRequestedAt(Instant.now());
        pendingEntity.setUpdatedAt(Instant.now());

        when(dsarRepo.findById(reqId)).thenReturn(Optional.of(pendingEntity));
        when(dsarRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(assignRepo.findByUserIdOrderByAssignedAtDesc(userId)).thenReturn(List.of());

        var completed = dsarService.processDataRequest(
                new ProcessDataRequestInput(reqId.toString(), DataRequestStatus.COMPLETED, "Verified & erased")
        );

        assertEquals(DataRequestStatus.COMPLETED, completed.status());
        assertEquals("Redacted User", targetUser.getFullName());
        assertTrue(targetUser.getEmail().startsWith("deleted-" + userId));
        assertNotNull(targetUser.getDeletedAt());
        verify(dsarRepo).clearExportPayloadsByUserId(userId);
        verify(consentService).revokeOptionalConsentsForErasure(targetUser);
    }

    @Test
    void namespaceIsolationAndJwtSigningWorkStrictly() {
        // SEC-G01: Unauthenticated caller spoofing user:* namespace must be blocked with ForbiddenException
        assertThrows(ForbiddenException.class, () ->
                SecurityUtils.resolveEffectiveSubjectKey("user:00000000-0000-4000-8000-000000000002", false));
        assertThrows(ForbiddenException.class, () ->
                SecurityUtils.validateSdkAnonSubjectKey("user:00000000-0000-4000-8000-000000000002"));
        assertEquals("anon:valid_visitor_12345", SecurityUtils.validateSdkAnonSubjectKey("anon:valid_visitor_12345"));

        JwtTokenProvider jwtProvider = new JwtTokenProvider(appProperties);
        UserEntity user = new UserEntity();
        UUID uid = UUID.randomUUID();
        user.setId(uid);
        user.setEmail("user@consenthub.io");
        user.setRole(UserRole.USER);
        user.setRegion(RegionCode.EU);

        String token = jwtProvider.createAccessToken(user);
        assertEquals(Optional.of(uid), jwtProvider.parseUserId(token));
        assertEquals(Optional.empty(), jwtProvider.parseUserId(token + "tampered"));
    }

    private static void setAuthContext(UUID id, String email, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(id, email, "Test Actor", role, RegionCode.EU);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
