package io.consenthub.domain.dto;

public record ExperimentVariantInput(
        String key,
        String name,
        Integer weight,
        String headline,
        String body,
        String primaryCtaLabel,
        String secondaryCtaLabel,
        String layoutTone
) {
}
