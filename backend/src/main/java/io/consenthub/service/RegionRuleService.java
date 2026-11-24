package io.consenthub.service;

import io.consenthub.domain.dto.RegionRuleDto;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.dto.UpdateRegionRuleInput;
import io.consenthub.domain.entity.RegionRuleEntity;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.repository.RegionRuleRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages regional regulatory rules (EU/GDPR, US_CA/CCPA, GLOBAL/DEFAULT).
 */
@Service
public class RegionRuleService {

    private final RegionRuleRepository regionRuleRepository;
    private final AuditLogService auditLogService;

    public RegionRuleService(RegionRuleRepository regionRuleRepository, AuditLogService auditLogService) {
        this.regionRuleRepository = regionRuleRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<RegionRuleDto> listRegionRules() {
        return regionRuleRepository.findAll()
                .stream()
                .sorted(Comparator.comparingInt(r -> regionSortRank(r.getRegionCode())))
                .map(RegionRuleService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RegionRuleDto getRuleByRegion(RegionCode regionCode) {
        return toDto(getRuleEntityByRegion(regionCode));
    }

    @Transactional(readOnly = true)
    public RegionRuleEntity getRuleEntityByRegion(RegionCode regionCode) {
        RegionCode effective = regionCode != null ? regionCode : RegionCode.GLOBAL;
        return regionRuleRepository.findByRegionCode(effective)
                .or(() -> regionRuleRepository.findByRegionCode(RegionCode.GLOBAL))
                .orElseThrow(() -> new NotFoundException("Regional rule not found for " + effective));
    }

    @Transactional
    public RegionRuleDto updateRegionRule(UpdateRegionRuleInput input) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (input == null || input.regionCode() == null || input.regimeName() == null
                || input.consentModel() == null || input.bannerStyle() == null || input.retentionDays() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "All regional rule fields are required.");
        }
        if (input.retentionDays() < 30 || input.retentionDays() > 3650) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "retentionDays must be between 30 and 3650 days."
            );
        }

        RegionRuleEntity entity = regionRuleRepository.findByRegionCode(input.regionCode())
                .orElseThrow(() -> new NotFoundException("Regional rule not found for " + input.regionCode()));

        entity.setRegimeName(input.regimeName());
        entity.setConsentModel(input.consentModel());
        entity.setBannerStyle(input.bannerStyle());
        entity.setShowRejectAll(input.showRejectAll() == null || input.showRejectAll());
        entity.setRetentionDays(input.retentionDays());
        entity.setUpdatedAt(Instant.now());

        RegionRuleEntity saved = regionRuleRepository.save(entity);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("regionCode", saved.getRegionCode().name());
        auditDetails.put("regimeName", saved.getRegimeName().name());
        auditDetails.put("consentModel", saved.getConsentModel().name());
        auditDetails.put("bannerStyle", saved.getBannerStyle().name());
        auditDetails.put("showRejectAll", saved.isShowRejectAll());
        auditDetails.put("retentionDays", saved.getRetentionDays());

        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "REGION_RULE_UPDATED",
                "REGION_RULE",
                saved.getRegionCode().name(),
                auditDetails
        );

        return toDto(saved);
    }

    private static int regionSortRank(RegionCode regionCode) {
        return switch (regionCode) {
            case EU -> 1;
            case US_CA -> 2;
            case GLOBAL -> 3;
        };
    }

    public static RegionRuleDto toDto(RegionRuleEntity entity) {
        return new RegionRuleDto(
                entity.getId().toString(),
                entity.getRegionCode(),
                entity.getRegimeName(),
                entity.getConsentModel(),
                entity.getBannerStyle(),
                entity.isShowRejectAll(),
                entity.getRetentionDays(),
                TimestampFormatter.format(entity.getUpdatedAt())
        );
    }
}
