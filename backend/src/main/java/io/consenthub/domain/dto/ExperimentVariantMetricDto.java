package io.consenthub.domain.dto;

public record ExperimentVariantMetricDto(
        String key,
        String name,
        int weight,
        String headline,
        String body,
        String primaryCtaLabel,
        String secondaryCtaLabel,
        String layoutTone,
        int exposures,
        int conversions,
        double consentRate
) {
}
