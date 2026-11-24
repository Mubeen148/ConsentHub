package io.consenthub.domain.dto;

import io.consenthub.domain.enums.BannerStyle;
import io.consenthub.domain.enums.ConsentModel;
import io.consenthub.domain.enums.LegalBasis;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;
import java.util.List;
import java.util.Map;

/**
 * Response contract for GET /api/v1/sdk/config (docs/contracts/rest-endpoints.md §4.2).
 */
public record SdkConfigResponse(
        String subjectKey,
        SdkRegionRule regionRule,
        SdkActivePolicy activePolicyVersion,
        boolean hasConsented,
        boolean needsReconsent,
        boolean shouldShowBanner,
        List<SdkPurposeState> purposes,
        Map<String, Boolean> consentMap,
        Map<String, Object> sduiBanner
) {
    public record SdkRegionRule(
            RegionCode regionCode,
            RegulatoryRegime regimeName,
            ConsentModel consentModel,
            BannerStyle bannerStyle,
            boolean showRejectAll,
            int retentionDays
    ) {
    }

    public record SdkActivePolicy(
            int versionNumber,
            String versionLabel,
            String title,
            String summary,
            boolean requiresReconsent,
            String publishedAt
    ) {
    }

    public record SdkPurposeState(
            String slug,
            String name,
            String description,
            LegalBasis legalBasis,
            boolean isEssential,
            boolean granted
    ) {
    }
}
