package io.consenthub.domain.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request body for POST /api/v1/sdk/consent (docs/contracts/rest-endpoints.md §4.4).
 */
public record SdkConsentRequest(
        @NotBlank(message = "subjectKey is required")
        String subjectKey,
        String region,
        @NotBlank(message = "action is required")
        String action,
        Map<String, Boolean> decisions,
        String experimentId,
        String variantKey
) {
}
