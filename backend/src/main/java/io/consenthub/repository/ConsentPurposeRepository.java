package io.consenthub.repository;

import io.consenthub.domain.entity.ConsentPurposeEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentPurposeRepository extends JpaRepository<ConsentPurposeEntity, UUID> {

    List<ConsentPurposeEntity> findByActiveTrueOrderByDisplayOrderAsc();

    List<ConsentPurposeEntity> findAllByOrderByDisplayOrderAsc();

    Optional<ConsentPurposeEntity> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
