package io.consenthub.domain.dto;

import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.DataRequestType;
import io.consenthub.domain.enums.LegalBasis;
import io.consenthub.domain.enums.RegionCode;
import java.util.List;

/**
 * GDPR Art. 15/20 & CCPA Right-to-Know machine-readable JSON portability archive.
 * Exact parity with docs/contracts/rest-endpoints.md §3.1.
 */
public record PrivacyExportResponse(
        ExportMetadata exportMetadata,
        UserProfileDto profile,
        List<ExportedConsentRecord> currentConsentRecords,
        List<ExportedConsentHistoryItem> consentHistoryLedger,
        List<ExportedExperimentAssignment> experimentAssignments,
        List<ExportedDataSubjectRequest> dataSubjectRequests
) {
    public record ExportMetadata(
            String schemaVersion,
            String generatedAt,
            String platform,
            List<String> regulatoryBasis,
            String requestId
    ) {
    }

    public record ExportedConsentRecord(
            String purposeSlug,
            String purposeName,
            LegalBasis legalBasis,
            boolean granted,
            String policyVersionLabel,
            RegionCode regionCode,
            String source,
            String updatedAt
    ) {
    }

    public record ExportedConsentHistoryItem(
            String id,
            String purposeSlug,
            String purposeName,
            Boolean previousGranted,
            boolean newGranted,
            String policyVersionLabel,
            RegionCode regionCode,
            String source,
            String experimentVariant,
            String proofHash,
            String occurredAt
    ) {
    }

    public record ExportedExperimentAssignment(
            String experimentSlug,
            String variantKey,
            boolean converted,
            String assignedAt,
            String convertedAt
    ) {
    }

    public record ExportedDataSubjectRequest(
            String id,
            DataRequestType requestType,
            DataRequestStatus status,
            String requestedAt,
            String completedAt
    ) {
    }
}
