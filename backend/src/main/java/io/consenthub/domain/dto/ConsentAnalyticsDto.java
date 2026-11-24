package io.consenthub.domain.dto;

import java.util.List;

public record ConsentAnalyticsDto(
        int days,
        int totalSubjects,
        int totalConsentEvents,
        double overallOptInRate,
        double reconsentCompletionRate,
        int pendingDsarCount,
        int activeExperimentsCount,
        List<DailyConsentMetricDto> dailyMetrics,
        List<RegionConsentMetricDto> regionalBreakdown,
        List<PurposeOptInMetricDto> purposeOptInRates
) {
}
