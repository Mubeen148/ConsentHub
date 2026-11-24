package io.consenthub.repository;

import io.consenthub.domain.entity.ConsentRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecordEntity, UUID> {

    List<ConsentRecordEntity> findBySubjectKey(String subjectKey);

    Optional<ConsentRecordEntity> findBySubjectKeyAndPurpose_Id(String subjectKey, UUID purposeId);

    boolean existsBySubjectKey(String subjectKey);

    @Query("SELECT COUNT(DISTINCT c.subjectKey) FROM ConsentRecordEntity c")
    long countDistinctSubjects();

    @Query("SELECT COUNT(DISTINCT c.subjectKey) FROM ConsentRecordEntity c WHERE c.policyVersion.id = :policyVersionId")
    long countDistinctSubjectsByPolicyVersionId(UUID policyVersionId);
}
