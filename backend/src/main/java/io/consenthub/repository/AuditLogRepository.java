package io.consenthub.repository;

import io.consenthub.domain.entity.AuditLogEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Strictly append-only repository for audit_logs (delete methods intentionally omitted per ADR-006).
 */
public interface AuditLogRepository extends Repository<AuditLogEntity, UUID> {

    AuditLogEntity saveAndFlush(AuditLogEntity entity);

    Optional<AuditLogEntity> findFirstByOrderBySequenceNumDesc();

    List<AuditLogEntity> findAllByOrderBySequenceNumAsc();

    List<AuditLogEntity> findAllByOrderBySequenceNumDesc(Pageable pageable);

    List<AuditLogEntity> findByActionIgnoreCaseOrderBySequenceNumDesc(String action, Pageable pageable);

    long count();
}
