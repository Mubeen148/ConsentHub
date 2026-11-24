package io.consenthub.domain.dto;

import io.consenthub.domain.enums.RegionCode;

public record ConsentHistoryEntryDto(
        String id,
        String subjectKey,
        String purposeSlug,
        String purposeName,
        Boolean previousGranted,
        boolean newGranted,
        String policyVersionLabel,
        RegionCode regionCode,
        String source,
        String ipHash,
        String userAgentSummary,
        String experimentVariant,
        String proofHash,
        String occurredAt
) {
}
