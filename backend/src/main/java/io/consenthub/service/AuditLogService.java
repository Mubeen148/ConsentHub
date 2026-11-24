package io.consenthub.service;

import io.consenthub.domain.dto.AuditChainVerificationDto;
import io.consenthub.domain.dto.AuditLogEntryDto;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.entity.AuditLogEntity;
import io.consenthub.repository.AuditLogRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only tamper-evident SHA-256 hash-chained governance & security audit service (ADR-006, ADR-010, SEC-G09).
 */
@Service
public class AuditLogService {

    public static final String GENESIS_PREV_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";
    public static final long ADVISORY_LOCK_KEY = 948271L;
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;

    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbcTemplate;
    private final boolean postgresDatabase;
    private final ReentrantLock jvmFallbackLock = new ReentrantLock();

    public AuditLogService(
            AuditLogRepository auditLogRepository,
            JdbcTemplate jdbcTemplate,
            DataSource dataSource
    ) {
        this.auditLogRepository = auditLogRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.postgresDatabase = detectPostgres(dataSource);
    }

    @Transactional
    public AuditLogEntryDto appendForCurrentActor(
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details
    ) {
        Optional<AuthenticatedUser> current = SecurityUtils.getCurrentUser();
        if (current.isPresent()) {
            AuthenticatedUser actor = current.get();
            return append(
                    actor.id(),
                    actor.email(),
                    actor.role().name(),
                    action,
                    entityType,
                    entityId,
                    details
            );
        }
        return append(null, "anonymous@visitor.consenthub.local", "VISITOR", action, entityType, entityId, details);
    }

    @Transactional
    public AuditLogEntryDto append(
            UUID actorId,
            String actorEmail,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details
    ) {
        return appendAtTimestamp(
                actorId,
                actorEmail,
                actorRole,
                action,
                entityType,
                entityId,
                details,
                Instant.now()
        );
    }

    @Transactional
    public AuditLogEntryDto appendAtTimestamp(
            UUID actorId,
            String actorEmail,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details,
            Instant timestamp
    ) {
        jvmFallbackLock.lock();
        try {
            if (postgresDatabase) {
                jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + ADVISORY_LOCK_KEY + ")");
            }

            String prevHash = auditLogRepository.findFirstByOrderBySequenceNumDesc()
                    .map(AuditLogEntity::getEntryHash)
                    .orElse(GENESIS_PREV_HASH);

            UUID id = UUID.randomUUID();
            Instant truncatedCreatedAt = (timestamp != null ? timestamp : Instant.now())
                    .truncatedTo(ChronoUnit.MILLIS);
            String safeEmail = (actorEmail != null && !actorEmail.isBlank()) ? actorEmail.trim() : "system@consenthub.io";
            String safeRole = (actorRole != null && !actorRole.isBlank()) ? actorRole.trim() : "SYSTEM";
            String safeAction = action.trim();
            String safeEntityType = entityType.trim();
            String safeEntityId = entityId.trim();
            Map<String, Object> safeDetails = details != null ? new LinkedHashMap<>(details) : new LinkedHashMap<>();

            String entryHash = computeEntryHash(
                    prevHash,
                    id,
                    actorId,
                    safeEmail,
                    safeRole,
                    safeAction,
                    safeEntityType,
                    safeEntityId,
                    truncatedCreatedAt,
                    safeDetails
            );

            AuditLogEntity entity = new AuditLogEntity();
            entity.setId(id);
            entity.setActorId(actorId);
            entity.setActorEmail(safeEmail);
            entity.setActorRole(safeRole);
            entity.setAction(safeAction);
            entity.setEntityType(safeEntityType);
            entity.setEntityId(safeEntityId);
            entity.setDetailsJson(safeDetails);
            entity.setPrevHash(prevHash);
            entity.setEntryHash(entryHash);
            entity.setCreatedAt(truncatedCreatedAt);

            AuditLogEntity saved = auditLogRepository.saveAndFlush(entity);
            return toDto(saved);
        } finally {
            jvmFallbackLock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntryDto> listAuditLogs(Integer limit, String actionFilter) {
        SecurityUtils.requireAdminUser();
        int clampedLimit = clampLimit(limit);
        PageRequest page = PageRequest.of(0, clampedLimit);

        List<AuditLogEntity> rows = (actionFilter != null && !actionFilter.isBlank())
                ? auditLogRepository.findByActionIgnoreCaseOrderBySequenceNumDesc(actionFilter.trim(), page)
                : auditLogRepository.findAllByOrderBySequenceNumDesc(page);

        return rows.stream().map(AuditLogService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AuditChainVerificationDto verifyAuditChain() {
        SecurityUtils.requireAdminUser();
        return verifyAuditChainInternal();
    }

    @Transactional(readOnly = true)
    public AuditChainVerificationDto verifyAuditChainInternal() {
        List<AuditLogEntity> entries = auditLogRepository.findAllByOrderBySequenceNumAsc();
        Instant verifiedAt = Instant.now();

        String expectedPrevHash = GENESIS_PREV_HASH;
        int checked = 0;

        for (AuditLogEntity entry : entries) {
            checked++;
            int seq = entry.getSequenceNum() != null ? entry.getSequenceNum().intValue() : checked;

            if (!expectedPrevHash.equals(entry.getPrevHash())) {
                return new AuditChainVerificationDto(
                        false,
                        checked,
                        seq,
                        "prev_hash linkage mismatch detected at sequence_num=" + seq + ".",
                        TimestampFormatter.format(verifiedAt)
                );
            }

            String recomputedEntryHash = computeEntryHash(
                    entry.getPrevHash(),
                    entry.getId(),
                    entry.getActorId(),
                    entry.getActorEmail(),
                    entry.getActorRole(),
                    entry.getAction(),
                    entry.getEntityType(),
                    entry.getEntityId(),
                    entry.getCreatedAt(),
                    entry.getDetailsJson()
            );

            if (!recomputedEntryHash.equals(entry.getEntryHash())) {
                return new AuditChainVerificationDto(
                        false,
                        checked,
                        seq,
                        "SHA-256 entry_hash integrity mismatch detected at sequence_num=" + seq + ".",
                        TimestampFormatter.format(verifiedAt)
                );
            }

            expectedPrevHash = entry.getEntryHash();
        }

        return new AuditChainVerificationDto(
                true,
                checked,
                null,
                "Cryptographic SHA-256 hash chain verified intact across all " + checked + " ledger entries.",
                TimestampFormatter.format(verifiedAt)
        );
    }

    public static String computeEntryHash(
            String prevHash,
            UUID id,
            UUID actorId,
            String actorEmail,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            Instant createdAt,
            Map<String, Object> detailsJson
    ) {
        long epochMillis = createdAt.truncatedTo(ChronoUnit.MILLIS).toEpochMilli();
        String actorIdStr = actorId != null ? actorId.toString() : "ANONYMOUS";
        String canonicalJson = CanonicalJsonUtil.toCanonicalJson(detailsJson);

        String preImage = prevHash
                + "|" + id
                + "|" + actorIdStr
                + "|" + actorEmail
                + "|" + actorRole
                + "|" + action
                + "|" + entityType
                + "|" + entityId
                + "|" + epochMillis
                + "|" + canonicalJson;
        return HashUtil.sha256Hex(preImage);
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    private static boolean detectPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase(Locale.ROOT).contains("postgres");
        } catch (Exception ex) {
            return false;
        }
    }

    public static AuditLogEntryDto toDto(AuditLogEntity entity) {
        int seq = entity.getSequenceNum() != null ? entity.getSequenceNum().intValue() : 0;
        return new AuditLogEntryDto(
                entity.getId().toString(),
                seq,
                entity.getActorId() != null ? entity.getActorId().toString() : null,
                entity.getActorEmail(),
                entity.getActorRole(),
                entity.getAction(),
                entity.getEntityType(),
                entity.getEntityId(),
                entity.getDetailsJson() != null ? entity.getDetailsJson() : Map.of(),
                entity.getPrevHash(),
                entity.getEntryHash(),
                TimestampFormatter.format(entity.getCreatedAt())
        );
    }
}
