package io.consenthub.domain.dto;

import io.consenthub.domain.enums.LegalBasis;

public record PurposeConsentStateDto(
        String purposeId,
        String slug,
        String name,
        String description,
        LegalBasis legalBasis,
        boolean isEssential,
        boolean granted,
        String source,
        String updatedAt
) {
    public boolean getIsEssential() {
        return isEssential;
    }
}
