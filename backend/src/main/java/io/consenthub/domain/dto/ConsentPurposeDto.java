package io.consenthub.domain.dto;

import io.consenthub.domain.enums.LegalBasis;

public record ConsentPurposeDto(
        String id,
        String slug,
        String name,
        String description,
        LegalBasis legalBasis,
        boolean isEssential,
        boolean active,
        int displayOrder,
        String createdAt,
        String updatedAt
) {
    public boolean getIsEssential() {
        return isEssential;
    }
}
