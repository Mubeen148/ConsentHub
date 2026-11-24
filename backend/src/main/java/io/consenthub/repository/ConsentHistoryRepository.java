package io.consenthub.repository;

import io.consenthub.domain.entity.ConsentHistoryEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Strictly append-only repository for consent_history (delete methods intentionally omitted per ADR-006).
 */
public interface ConsentHistoryRepository extends Repository<ConsentHistoryEntity, UUID> {

    ConsentHistoryEntity save(ConsentHistoryEntity entity);

    List<ConsentHistoryEntity> saveAll(Iterable<ConsentHistoryEntity> entities);

    List<ConsentHistoryEntity> findBySubjectKeyOrderByOccurredAtDesc(String subjectKey, Pageable pageable);

    List<ConsentHistoryEntity> findBySubjectKeyOrderByOccurredAtDesc(String subjectKey);

    long countBySubjectKey(String subjectKey);

    List<ConsentHistoryEntity> findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(Instant since);

    long countByOccurredAtGreaterThanEqual(Instant since);

    long count();
}
