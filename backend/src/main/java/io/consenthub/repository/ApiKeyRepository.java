package io.consenthub.repository;

import io.consenthub.domain.entity.ApiKeyEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, UUID> {

    List<ApiKeyEntity> findAllByOrderByCreatedAtDesc();

    Optional<ApiKeyEntity> findByKeyHashAndActiveTrue(String keyHash);

    @Modifying
    @Query("UPDATE ApiKeyEntity k SET k.lastUsedAt = :usedAt WHERE k.id = :id")
    int updateLastUsedAt(@Param("id") UUID id, @Param("usedAt") Instant usedAt);
}
