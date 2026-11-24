package io.consenthub.repository;

import io.consenthub.domain.entity.ExperimentEntity;
import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.ScreenName;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<ExperimentEntity, UUID> {

    List<ExperimentEntity> findAllByOrderByCreatedAtDesc();

    Optional<ExperimentEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<ExperimentEntity> findByStatusAndTargetScreenOrderByCreatedAtDesc(ExperimentStatus status, ScreenName targetScreen);

    List<ExperimentEntity> findByTargetScreenOrderByCreatedAtDesc(ScreenName targetScreen);

    long countByStatus(ExperimentStatus status);
}
