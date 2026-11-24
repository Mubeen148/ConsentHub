package io.consenthub.repository;

import io.consenthub.domain.entity.PolicyVersionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersionEntity, UUID> {

    List<PolicyVersionEntity> findAllByOrderByVersionNumberDesc();

    Optional<PolicyVersionEntity> findByActiveTrue();

    Optional<PolicyVersionEntity> findByVersionLabel(String versionLabel);

    boolean existsByVersionLabel(String versionLabel);

    @Query("SELECT COALESCE(MAX(p.versionNumber), 0) FROM PolicyVersionEntity p")
    int findMaxVersionNumber();

    @Query("SELECT COUNT(p) > 0 FROM PolicyVersionEntity p WHERE p.versionNumber > :fromVersion AND p.versionNumber <= :toVersion AND p.requiresReconsent = true")
    boolean existsReconsentRequiredBetween(int fromVersion, int toVersion);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PolicyVersionEntity p SET p.active = false WHERE p.active = true")
    int deactivateAllActivePolicies();
}
