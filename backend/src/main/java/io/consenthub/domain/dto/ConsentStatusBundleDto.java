package io.consenthub.domain.dto;

import java.util.List;

public record ConsentStatusBundleDto(
        String subjectKey,
        boolean hasConsented,
        boolean needsReconsent,
        boolean shouldShowBanner,
        String consentedPolicyVersion,
        PolicyVersionDto activePolicyVersion,
        RegionRuleDto regionRule,
        List<PurposeConsentStateDto> decisions,
        String updatedAt
) {
}
