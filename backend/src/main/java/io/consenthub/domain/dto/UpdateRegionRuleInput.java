package io.consenthub.domain.dto;

import io.consenthub.domain.enums.BannerStyle;
import io.consenthub.domain.enums.ConsentModel;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;

public record UpdateRegionRuleInput(
        RegionCode regionCode,
        RegulatoryRegime regimeName,
        ConsentModel consentModel,
        BannerStyle bannerStyle,
        Boolean showRejectAll,
        Integer retentionDays
) {
}
