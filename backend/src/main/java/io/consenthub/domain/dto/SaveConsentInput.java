package io.consenthub.domain.dto;

import io.consenthub.domain.enums.RegionCode;
import java.util.List;

public record SaveConsentInput(
        String subjectKey,
        RegionCode region,
        String source,
        String experimentId,
        String variantKey,
        List<PurposeDecisionInput> decisions
) {
}
