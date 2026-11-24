package io.consenthub.domain.entity;

/**
 * JSON structure stored inside experiments.variants_json.
 */
public record ExperimentVariantConfig(
        String key,
        String name,
        int weight,
        String headline,
        String body,
        String primaryCtaLabel,
        String secondaryCtaLabel,
        String layoutTone
) {
}
