package io.consenthub.repository;

import io.consenthub.domain.entity.DataRequestEntity;
import io.consenthub.domain.enums.DataRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataRequestRepository extends JpaRepository<DataRequestEntity, UUID> {

    List<DataRequestEntity> findByUser_IdOrderByRequestedAtDesc(UUID userId);

    List<DataRequestEntity> findAllByOrderByRequestedAtDesc();

    List<DataRequestEntity> findByStatusOrderByRequestedAtDesc(DataRequestStatus status);

    long countByUser_IdAndStatusIn(UUID userId, Collection<DataRequestStatus> statuses);

    long countByStatusIn(Collection<DataRequestStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DataRequestEntity d SET d.exportPayloadJson = null WHERE d.user.id = :userId")
    int clearExportPayloadsByUserId(@Param("userId") UUID userId);
}
