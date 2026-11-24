package io.consenthub.domain.dto;

public record PurposeOptInMetricDto(
        String purposeSlug,
        String purposeName,
        boolean isEssential,
        int grantedCount,
        int deniedCount,
        double optInRate
) {
    public boolean getIsEssential() {
        return isEssential;
    }
}
