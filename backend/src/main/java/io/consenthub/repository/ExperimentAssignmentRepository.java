package io.consenthub.repository;

import io.consenthub.domain.entity.ExperimentAssignmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentAssignmentRepository extends JpaRepository<ExperimentAssignmentEntity, UUID> {

    Optional<ExperimentAssignmentEntity> findByExperimentIdAndSubjectKey(UUID experimentId, String subjectKey);

    List<ExperimentAssignmentEntity> findBySubjectKey(String subjectKey);

    List<ExperimentAssignmentEntity> findByExperimentId(UUID experimentId);

    List<ExperimentAssignmentEntity> findByUserIdOrderByAssignedAtDesc(UUID userId);
}
