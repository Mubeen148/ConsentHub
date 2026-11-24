package io.consenthub.domain.dto;

import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.ScreenName;
import java.util.List;

public record CreateExperimentInput(
        String slug,
        String name,
        String description,
        ScreenName targetScreen,
        RegionCode targetRegion,
        ExperimentStatus status,
        List<ExperimentVariantInput> variants
) {
}
