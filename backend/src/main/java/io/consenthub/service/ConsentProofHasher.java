package io.consenthub.service;

import io.consenthub.domain.enums.ConsentSource;
import io.consenthub.domain.enums.RegionCode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Computes regulatory consent receipt SHA-256 proof_hash per database-schema.sql lines 230-233:
 * subject_key || '|' || purpose_slug || '|' || new_granted || '|' ||
 * policy_version_label || '|' || region_code || '|' || source || '|' || epoch_millis(occurred_at)
 */
public final class ConsentProofHasher {

    private ConsentProofHasher() {
    }

    public static String computeProofHash(
            String subjectKey,
            String purposeSlug,
            boolean newGranted,
            String policyVersionLabel,
            RegionCode regionCode,
            ConsentSource source,
            Instant occurredAt
    ) {
        long epochMillis = occurredAt.truncatedTo(ChronoUnit.MILLIS).toEpochMilli();
        String preImage = subjectKey
                + "|" + purposeSlug
                + "|" + newGranted
                + "|" + policyVersionLabel
                + "|" + regionCode.name()
                + "|" + source.name()
                + "|" + epochMillis;
        return HashUtil.sha256Hex(preImage);
    }
}
