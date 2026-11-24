package io.consenthub.domain.dto;

import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.ScreenName;
import java.util.List;

public record ExperimentSummaryDto(
        String id,
        String slug,
        String name,
        String description,
        ScreenName targetScreen,
        RegionCode targetRegion,
        ExperimentStatus status,
        int totalExposures,
        int totalConversions,
        double overallConsentRate,
        List<ExperimentVariantMetricDto> variants,
        String createdAt,
        String updatedAt
) {
}
