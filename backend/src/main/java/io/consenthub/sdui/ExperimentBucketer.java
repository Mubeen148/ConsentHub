package io.consenthub.sdui;

import io.consenthub.sdui.SduiModels.BucketResult;
import io.consenthub.sdui.SduiModels.ExperimentVariantSpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Deterministic sticky A/B experiment bucketing engine ({@code ADR-005}).
 *
 * <p>Computes {@code SHA-256(experimentId + ":" + subjectKey)}, reads the first 4 bytes as an
 * unsigned 32-bit big-endian integer {@code mod 100} ({@code 0..99}), and maps the bucket across
 * cumulative variant {@code weight} ranges. Supports explicit {@code previewVariantKey} overrides
 * while marking {@code previewOverride = true} so callers never persist preview assignments
 * ({@code SEC-G02}).
 */
@Component
public class ExperimentBucketer {

    /**
     * Computes a deterministic bucket in {@code [0, 99]} from
     * {@code SHA-256(experimentId + ":" + subjectKey)} interpreted as an unsigned big-endian int.
     */
    public int computeBucket(String experimentId, String subjectKey) {
        if (experimentId == null || experimentId.isBlank()) {
            throw new IllegalArgumentException("experimentId must be non-blank");
        }
        if (subjectKey == null || subjectKey.isBlank()) {
            throw new IllegalArgumentException("subjectKey must be non-blank");
        }

        String preImage = experimentId + ":" + subjectKey;
        byte[] digest = sha256(preImage.getBytes(StandardCharsets.UTF_8));
        long unsignedBigEndianInt = ((digest[0] & 0xFFL) << 24)
                | ((digest[1] & 0xFFL) << 16)
                | ((digest[2] & 0xFFL) << 8)
                | (digest[3] & 0xFFL);
        return (int) (unsignedBigEndianInt % 100L);
    }

    /**
     * Resolves the active {@link ExperimentVariantSpec} for a subject, honoring an optional
     * {@code previewVariantKey} override without side effects.
     */
    public BucketResult assignVariant(
            String experimentId,
            String subjectKey,
            List<ExperimentVariantSpec> variants,
            String previewVariantKey
    ) {
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("Experiment must define at least one variant");
        }

        int bucket = computeBucket(experimentId, subjectKey);

        if (previewVariantKey != null && !previewVariantKey.isBlank()) {
            String trimmedKey = previewVariantKey.trim();
            for (ExperimentVariantSpec variant : variants) {
                if (trimmedKey.equals(variant.key())) {
                    return new BucketResult(variant, bucket, true);
                }
            }
            throw new IllegalArgumentException(
                    "Unknown previewVariantKey '" + trimmedKey + "' for experiment " + experimentId
            );
        }

        int cumulativeWeight = 0;
        ExperimentVariantSpec lastPositiveVariant = variants.getLast();
        for (ExperimentVariantSpec variant : variants) {
            if (variant.weight() < 0) {
                throw new IllegalArgumentException("Variant weight cannot be negative: " + variant.key());
            }
            if (variant.weight() > 0) {
                lastPositiveVariant = variant;
            }
            cumulativeWeight += variant.weight();
            if (bucket < cumulativeWeight) {
                return new BucketResult(variant, bucket, false);
            }
        }

        return new BucketResult(lastPositiveVariant, bucket, false);
    }

    /**
     * Resolves an already-persisted sticky variant key if still present in {@code variants},
     * otherwise falls back to deterministic SHA-256 bucketing.
     */
    public BucketResult resolveStickyOrBucket(
            String experimentId,
            String subjectKey,
            List<ExperimentVariantSpec> variants,
            String persistedVariantKey,
            String previewVariantKey
    ) {
        if (previewVariantKey != null && !previewVariantKey.isBlank()) {
            return assignVariant(experimentId, subjectKey, variants, previewVariantKey);
        }
        if (persistedVariantKey != null && !persistedVariantKey.isBlank()) {
            int bucket = computeBucket(experimentId, subjectKey);
            for (ExperimentVariantSpec variant : variants) {
                if (persistedVariantKey.equals(variant.key())) {
                    return new BucketResult(variant, bucket, false);
                }
            }
        }
        return assignVariant(experimentId, subjectKey, variants, null);
    }

    /**
     * Validates that an experiment's variants list contains at least 2 unique keys whose
     * weights are positive/non-negative and sum strictly to 100.
     */
    public void validateVariantSplit(List<ExperimentVariantSpec> variants) {
        if (variants == null || variants.size() < 2) {
            throw new IllegalArgumentException("An experiment must contain at least 2 variants.");
        }
        Set<String> keys = new HashSet<>();
        int totalWeight = 0;
        for (ExperimentVariantSpec variant : variants) {
            if (variant.key() == null || variant.key().isBlank()) {
                throw new IllegalArgumentException("Each experiment variant must have a non-blank key.");
            }
            if (!keys.add(variant.key())) {
                throw new IllegalArgumentException("Duplicate experiment variant key: " + variant.key());
            }
            if (variant.weight() < 0 || variant.weight() > 100) {
                throw new IllegalArgumentException(
                        "Variant '" + variant.key() + "' weight must be between 0 and 100."
                );
            }
            totalWeight += variant.weight();
        }
        if (totalWeight != 100) {
            throw new IllegalArgumentException(
                    "Experiment variant weights must sum to 100 (actual sum: " + totalWeight + ")."
            );
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 MessageDigest algorithm unavailable", ex);
        }
    }
}
