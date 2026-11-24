package io.consenthub.domain.dto;

import io.consenthub.domain.enums.RegionCode;
import java.util.Map;

/**
 * Response contract for GET & POST /api/v1/sdk/consent (docs/contracts/rest-endpoints.md §4.3-4.4).
 */
public record SdkConsentResponse(
        String subjectKey,
        RegionCode regionCode,
        String policyVersion,
        boolean hasConsented,
        boolean needsReconsent,
        Map<String, Boolean> purposes,
        String updatedAt
) {
}
