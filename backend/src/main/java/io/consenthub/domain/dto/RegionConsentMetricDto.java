package io.consenthub.domain.dto;

import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;

public record RegionConsentMetricDto(
        RegionCode regionCode,
        RegulatoryRegime regimeName,
        int totalSubjects,
        int optInCount,
        int optOutCount,
        double consentRate
) {
}
