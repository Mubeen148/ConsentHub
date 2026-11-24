package io.consenthub.service;

import io.consenthub.domain.dto.ConsentAnalyticsDto;
import io.consenthub.domain.dto.DailyConsentMetricDto;
import io.consenthub.domain.dto.PurposeOptInMetricDto;
import io.consenthub.domain.dto.RegionConsentMetricDto;
import io.consenthub.domain.entity.ConsentHistoryEntity;
import io.consenthub.domain.entity.ConsentPurposeEntity;
import io.consenthub.domain.entity.ConsentRecordEntity;
import io.consenthub.domain.entity.PolicyVersionEntity;
import io.consenthub.domain.entity.RegionRuleEntity;
import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;
import io.consenthub.repository.ConsentHistoryRepository;
import io.consenthub.repository.ConsentRecordRepository;
import io.consenthub.repository.DataRequestRepository;
import io.consenthub.repository.ExperimentRepository;
import io.consenthub.security.SecurityUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregates live consent rate analytics over time, by region, and per purpose for the Admin Dashboard.
 */
@Service
public class AnalyticsService {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<DataRequestStatus> PENDING_QUEUE_STATUSES = Set.of(
            DataRequestStatus.PENDING,
            DataRequestStatus.IN_REVIEW
    );

    private final ConsentRecordRepository consentRecordRepository;
    private final ConsentHistoryRepository consentHistoryRepository;
    private final PolicyService policyService;
    private final RegionRuleService regionRuleService;
    private final DataRequestRepository dataRequestRepository;
    private final ExperimentRepository experimentRepository;

    public AnalyticsService(
            ConsentRecordRepository consentRecordRepository,
            ConsentHistoryRepository consentHistoryRepository,
            PolicyService policyService,
            RegionRuleService regionRuleService,
            DataRequestRepository dataRequestRepository,
            ExperimentRepository experimentRepository
    ) {
        this.consentRecordRepository = consentRecordRepository;
        this.consentHistoryRepository = consentHistoryRepository;
        this.policyService = policyService;
        this.regionRuleService = regionRuleService;
        this.dataRequestRepository = dataRequestRepository;
        this.experimentRepository = experimentRepository;
    }

    @Transactional(readOnly = true)
    public ConsentAnalyticsDto getAdminAnalytics(Integer requestedDays) {
        SecurityUtils.requireAdminUser();
        int days = requestedDays == null ? 30 : Math.max(7, Math.min(90, requestedDays));

        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = todayUtc.minusDays(days - 1L);
        Instant sinceInstant = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        PolicyVersionEntity activePolicy = policyService.getActivePolicyVersionEntity();
        List<ConsentPurposeEntity> activePurposes = policyService.listActivePurposeEntities();
        List<ConsentRecordEntity> allRecords = consentRecordRepository.findAll();
        List<ConsentHistoryEntity> recentHistory =
                consentHistoryRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(sinceInstant);

        // Group current consent records by subject_key
        Map<String, List<ConsentRecordEntity>> recordsBySubject = new HashMap<>();
        for (ConsentRecordEntity record : allRecords) {
            recordsBySubject.computeIfAbsent(record.getSubjectKey(), k -> new ArrayList<>()).add(record);
        }

        int totalSubjects = recordsBySubject.size();
        int subjectsOptedInOptional = 0;
        int subjectsOnActivePolicy = 0;

        Map<RegionCode, int[]> regionCounters = new EnumMap<>(RegionCode.class);
        for (RegionCode rc : RegionCode.values()) {
            regionCounters.put(rc, new int[]{0, 0, 0}); // [totalSubjects, optInCount, optOutCount]
        }

        for (List<ConsentRecordEntity> subjectRecords : recordsBySubject.values()) {
            if (subjectRecords.isEmpty()) {
                continue;
            }
            RegionCode region = subjectRecords.getFirst().getRegionCode() != null
                    ? subjectRecords.getFirst().getRegionCode()
                    : RegionCode.GLOBAL;

            boolean anyOptionalGranted = subjectRecords.stream()
                    .anyMatch(r -> !r.getPurpose().isEssential() && r.isGranted());
            boolean onActive = subjectRecords.stream()
                    .allMatch(r -> r.getPolicyVersion().getId().equals(activePolicy.getId()));

            if (anyOptionalGranted) {
                subjectsOptedInOptional++;
            }
            if (onActive) {
                subjectsOnActivePolicy++;
            }

            int[] rcStats = regionCounters.get(region);
            rcStats[0]++;
            if (anyOptionalGranted) {
                rcStats[1]++;
            } else {
                rcStats[2]++;
            }
        }

        double overallOptInRate = totalSubjects > 0
                ? roundTwoDecimals((subjectsOptedInOptional * 100.0) / totalSubjects)
                : 0.0;
        double reconsentCompletionRate = totalSubjects > 0
                ? roundTwoDecimals((subjectsOnActivePolicy * 100.0) / totalSubjects)
                : 100.0;

        // Per-purpose opt-in breakdown
        Map<UUID, int[]> countsByPurpose = new HashMap<>();
        for (ConsentRecordEntity record : allRecords) {
            int[] pair = countsByPurpose.computeIfAbsent(record.getPurpose().getId(), k -> new int[]{0, 0});
            if (record.isGranted()) {
                pair[0]++;
            } else {
                pair[1]++;
            }
        }

        List<PurposeOptInMetricDto> purposeMetrics = activePurposes.stream()
                .sorted(Comparator.comparingInt(ConsentPurposeEntity::getDisplayOrder))
                .map(purpose -> {
                    int[] pair = countsByPurpose.getOrDefault(purpose.getId(), new int[]{0, 0});
                    int granted = pair[0];
                    int denied = pair[1];
                    int denom = granted + denied;
                    double rate = purpose.isEssential()
                            ? 100.0
                            : (denom > 0 ? roundTwoDecimals((granted * 100.0) / denom) : 0.0);
                    return new PurposeOptInMetricDto(
                            purpose.getSlug(),
                            purpose.getName(),
                            purpose.isEssential(),
                            granted,
                            denied,
                            rate
                    );
                })
                .toList();

        // Regional breakdown ordered EU -> US_CA -> GLOBAL
        Map<RegionCode, RegulatoryRegime> regimeByRegion = new EnumMap<>(RegionCode.class);
        for (RegionRuleEntity rule : regionRuleService.listRegionRules().stream()
                .map(dto -> regionRuleService.getRuleEntityByRegion(dto.regionCode()))
                .toList()) {
            regimeByRegion.put(rule.getRegionCode(), rule.getRegimeName());
        }

        List<RegionConsentMetricDto> regionalBreakdown = List.of(RegionCode.EU, RegionCode.US_CA, RegionCode.GLOBAL)
                .stream()
                .map(rc -> {
                    int[] stats = regionCounters.get(rc);
                    int total = stats[0];
                    int optIn = stats[1];
                    int optOut = stats[2];
                    double rate = total > 0 ? roundTwoDecimals((optIn * 100.0) / total) : 0.0;
                    RegulatoryRegime regime = regimeByRegion.getOrDefault(rc, defaultRegime(rc));
                    return new RegionConsentMetricDto(rc, regime, total, optIn, optOut, rate);
                })
                .toList();

        // Group consent_history into interaction batches (subjectKey + truncated second) to classify daily interactions
        Map<LocalDate, Map<String, List<ConsentHistoryEntity>>> dailyBatches = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            dailyBatches.put(startDate.plusDays(i), new LinkedHashMap<>());
        }

        for (ConsentHistoryEntity entry : recentHistory) {
            LocalDate day = entry.getOccurredAt().atZone(ZoneOffset.UTC).toLocalDate();
            Map<String, List<ConsentHistoryEntity>> dayMap = dailyBatches.get(day);
            if (dayMap != null) {
                String batchKey = entry.getSubjectKey()
                        + "|" + entry.getOccurredAt().truncatedTo(ChronoUnit.SECONDS).getEpochSecond();
                dayMap.computeIfAbsent(batchKey, k -> new ArrayList<>()).add(entry);
            }
        }

        List<DailyConsentMetricDto> dailyMetrics = new ArrayList<>(days);
        for (Map.Entry<LocalDate, Map<String, List<ConsentHistoryEntity>>> dayEntry : dailyBatches.entrySet()) {
            int acceptAll = 0;
            int rejectOptional = 0;
            int custom = 0;

            for (List<ConsentHistoryEntity> batch : dayEntry.getValue().values()) {
                List<ConsentHistoryEntity> optionalItems = batch.stream()
                        .filter(h -> !"essential".equalsIgnoreCase(h.getPurposeSlug()))
                        .toList();
                if (optionalItems.isEmpty()) {
                    continue;
                }
                long grantedCount = optionalItems.stream().filter(ConsentHistoryEntity::isNewGranted).count();
                if (grantedCount == optionalItems.size() && optionalItems.size() >= 2) {
                    acceptAll++;
                } else if (grantedCount == 0) {
                    rejectOptional++;
                } else {
                    custom++;
                }
            }

            int totalInteractions = acceptAll + rejectOptional + custom;
            double dayOptInRate = totalInteractions > 0
                    ? roundTwoDecimals(((acceptAll + custom) * 100.0) / totalInteractions)
                    : 0.0;

            dailyMetrics.add(new DailyConsentMetricDto(
                    ISO_DATE.format(dayEntry.getKey()),
                    acceptAll,
                    rejectOptional,
                    custom,
                    totalInteractions,
                    dayOptInRate
            ));
        }

        int pendingDsarCount = (int) dataRequestRepository.countByStatusIn(PENDING_QUEUE_STATUSES);
        int activeExperimentsCount = (int) experimentRepository.countByStatus(ExperimentStatus.RUNNING);

        return new ConsentAnalyticsDto(
                days,
                totalSubjects,
                recentHistory.size(),
                overallOptInRate,
                reconsentCompletionRate,
                pendingDsarCount,
                activeExperimentsCount,
                dailyMetrics,
                regionalBreakdown,
                purposeMetrics
        );
    }

    private static RegulatoryRegime defaultRegime(RegionCode rc) {
        return switch (rc) {
            case EU -> RegulatoryRegime.GDPR;
            case US_CA -> RegulatoryRegime.CCPA;
            case GLOBAL -> RegulatoryRegime.DEFAULT;
        };
    }

    private static double roundTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
