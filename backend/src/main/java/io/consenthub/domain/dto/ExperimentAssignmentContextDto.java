package io.consenthub.domain.dto;

import io.consenthub.domain.entity.ExperimentVariantConfig;

public record ExperimentAssignmentContextDto(
        String experimentId,
        String experimentSlug,
        String variantKey,
        ExperimentVariantConfig variant,
        boolean previewOverride
) {
}
