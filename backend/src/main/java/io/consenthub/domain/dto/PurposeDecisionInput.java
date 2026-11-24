package io.consenthub.domain.dto;

public record PurposeDecisionInput(
        String purposeSlug,
        Boolean granted
) {
}
