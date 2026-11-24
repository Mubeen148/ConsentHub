package io.consenthub.graphql;

import io.consenthub.domain.enums.BannerStyle;
import io.consenthub.domain.enums.ConsentModel;
import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.DataRequestType;
import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.LegalBasis;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.RegulatoryRegime;
import io.consenthub.domain.enums.ScreenName;
import io.consenthub.domain.enums.UserRole;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Strongly typed Java 21 GraphQL output and input records matching 1:1 with
 * {@code docs/contracts/graphql-schema.graphqls}.
 */
public final class GraphQlDtos {

    private static final DateTimeFormatter ISO_UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private GraphQlDtos() {
    }

    public static String formatInstant(Instant instant) {
        return instant == null ? null : ISO_UTC_MILLIS.format(instant);
    }

    // -------------------------------------------------------------------------
    // User & Governance Domain Output Types
    // -------------------------------------------------------------------------

    public record UserProfileView(
            String id,
            String email,
            String fullName,
            UserRole role,
            RegionCode region,
            String subjectKey,
            String createdAt,
            String updatedAt
    ) {
    }

    public record ConsentPurposeView(
            String id,
            String slug,
            String name,
            String description,
            LegalBasis legalBasis,
            boolean isEssential,
            boolean active,
            int displayOrder,
            String createdAt,
            String updatedAt
    ) {
        public boolean getIsEssential() {
            return isEssential;
        }
    }

    public record PolicyVersionView(
            String id,
            int versionNumber,
            String versionLabel,
            String title,
            String summary,
            String contentMarkdown,
            boolean requiresReconsent,
            boolean isActive,
            String publishedByEmail,
            String publishedAt,
            String createdAt
    ) {
        public boolean getIsActive() {
            return isActive;
        }
    }

    public record RegionRuleView(
            String id,
            RegionCode regionCode,
            RegulatoryRegime regimeName,
            ConsentModel consentModel,
            BannerStyle bannerStyle,
            boolean showRejectAll,
            int retentionDays,
            String updatedAt
    ) {
    }

    // -------------------------------------------------------------------------
    // Effective Consent State & Immutable Ledger Output Types
    // -------------------------------------------------------------------------

    public record ConsentStatusBundleView(
            String subjectKey,
            boolean hasConsented,
            boolean needsReconsent,
            boolean shouldShowBanner,
            String consentedPolicyVersion,
            PolicyVersionView activePolicyVersion,
            RegionRuleView regionRule,
            List<PurposeConsentStateView> decisions,
            String updatedAt
    ) {
    }

    public record PurposeConsentStateView(
            String purposeId,
            String slug,
            String name,
            String description,
            LegalBasis legalBasis,
            boolean isEssential,
            boolean granted,
            String source,
            String updatedAt
    ) {
        public boolean getIsEssential() {
            return isEssential;
        }
    }

    public record ConsentHistoryEntryView(
            String id,
            String subjectKey,
            String purposeSlug,
            String purposeName,
            Boolean previousGranted,
            boolean newGranted,
            String policyVersionLabel,
            RegionCode regionCode,
            String source,
            String ipHash,
            String userAgentSummary,
            String experimentVariant,
            String proofHash,
            String occurredAt
    ) {
    }

    // -------------------------------------------------------------------------
    // DSAR Output Type
    // -------------------------------------------------------------------------

    public record DataRequestView(
            String id,
            String userId,
            String userEmail,
            String userFullName,
            DataRequestType requestType,
            DataRequestStatus status,
            String reason,
            String adminNotes,
            String processedByEmail,
            String requestedAt,
            String updatedAt,
            String completedAt
    ) {
    }

    // -------------------------------------------------------------------------
    // Experimentation, Analytics, API Keys & Audit Chain Output Types
    // -------------------------------------------------------------------------

    public record ExperimentSummaryView(
            String id,
            String slug,
            String name,
            String description,
            ScreenName targetScreen,
            RegionCode targetRegion,
            ExperimentStatus status,
            int totalExposures,
            int totalConversions,
            double overallConsentRate,
            List<ExperimentVariantMetricView> variants,
            String createdAt,
            String updatedAt
    ) {
    }

    public record ExperimentVariantMetricView(
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

    public record ConsentAnalyticsView(
            int days,
            int totalSubjects,
            int totalConsentEvents,
            double overallOptInRate,
            double reconsentCompletionRate,
            int pendingDsarCount,
            int activeExperimentsCount,
            List<DailyConsentMetricView> dailyMetrics,
            List<RegionConsentMetricView> regionalBreakdown,
            List<PurposeOptInMetricView> purposeOptInRates
    ) {
    }

    public record DailyConsentMetricView(
            String date,
            int acceptAllCount,
            int rejectOptionalCount,
            int customCount,
            int totalInteractions,
            double optInRate
    ) {
    }

    public record RegionConsentMetricView(
            RegionCode regionCode,
            RegulatoryRegime regimeName,
            int totalSubjects,
            int optInCount,
            int optOutCount,
            double consentRate
    ) {
    }

    public record PurposeOptInMetricView(
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

    public record AuditLogEntryView(
            String id,
            int sequenceNum,
            String actorId,
            String actorEmail,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> details,
            String prevHash,
            String entryHash,
            String createdAt
    ) {
    }

    public record AuditChainVerificationView(
            boolean chainValid,
            int totalEntriesChecked,
            Integer brokenAtSequenceNum,
            String message,
            String verifiedAt
    ) {
    }

    public record ApiKeyInfoView(
            String id,
            String name,
            String keyPrefix,
            String allowedOrigins,
            String scopes,
            boolean active,
            String lastUsedAt,
            String createdAt
    ) {
    }

    public record CreatedApiKeyResultView(
            String plainTextKey,
            ApiKeyInfoView apiKey
    ) {
        @Override
        public String toString() {
            return "CreatedApiKeyResultView[plainTextKey=[REDACTED], apiKey=" + apiKey + "]";
        }
    }

    // -------------------------------------------------------------------------
    // GraphQL Input Records
    // -------------------------------------------------------------------------

    public record ScreenContextInput(
            String subjectKey,
            RegionCode region,
            String locale,
            String deviceType,
            String previewVariantKey
    ) {
    }

    public record SaveConsentInput(
            String subjectKey,
            RegionCode region,
            String source,
            String experimentId,
            String variantKey,
            List<PurposeDecisionInput> decisions
    ) {
    }

    public record PurposeDecisionInput(
            String purposeSlug,
            Boolean granted
    ) {
    }

    public record UpdateProfileInput(
            String fullName,
            RegionCode region
    ) {
    }

    public record CreateDataRequestInput(
            DataRequestType requestType,
            String reason,
            String confirmationText
    ) {
    }

    public record UpsertPurposeInput(
            String id,
            String slug,
            String name,
            String description,
            LegalBasis legalBasis,
            Boolean isEssential,
            Boolean active,
            Integer displayOrder
    ) {
    }

    public record PublishPolicyVersionInput(
            String versionLabel,
            String title,
            String summary,
            String contentMarkdown,
            Boolean requiresReconsent,
            Boolean activateImmediately
    ) {
    }

    public record UpdateRegionRuleInput(
            RegionCode regionCode,
            RegulatoryRegime regimeName,
            ConsentModel consentModel,
            BannerStyle bannerStyle,
            Boolean showRejectAll,
            Integer retentionDays
    ) {
    }

    public record CreateExperimentInput(
            String slug,
            String name,
            String description,
            ScreenName targetScreen,
            RegionCode targetRegion,
            ExperimentStatus status,
            List<ExperimentVariantInput> variants
    ) {
    }

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

    public record ProcessDataRequestInput(
            String id,
            DataRequestStatus status,
            String adminNotes
    ) {
    }

    public record CreateApiKeyInput(
            String name,
            String allowedOrigins,
            String scopes
    ) {
    }
}
