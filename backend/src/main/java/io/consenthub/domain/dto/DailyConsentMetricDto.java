package io.consenthub.domain.dto;

public record DailyConsentMetricDto(
        String date,
        int acceptAllCount,
        int rejectOptionalCount,
        int customCount,
        int totalInteractions,
        double optInRate
) {
}
