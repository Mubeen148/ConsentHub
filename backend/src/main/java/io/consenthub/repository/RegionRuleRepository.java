package io.consenthub.repository;

import io.consenthub.domain.entity.RegionRuleEntity;
import io.consenthub.domain.enums.RegionCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRuleRepository extends JpaRepository<RegionRuleEntity, UUID> {

    Optional<RegionRuleEntity> findByRegionCode(RegionCode regionCode);
}
