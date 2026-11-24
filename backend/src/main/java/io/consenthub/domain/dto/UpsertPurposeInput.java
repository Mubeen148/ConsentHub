package io.consenthub.domain.dto;

import io.consenthub.domain.enums.LegalBasis;

public record UpsertPurposeInput(
        String id,
        String slug,
        String name,
        String description,
        LegalBasis legalBasis,
        Boolean isEssential,
        Boolean active,
        Integer displayOrder
) {
}
