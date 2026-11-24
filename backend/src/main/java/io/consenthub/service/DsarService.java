package io.consenthub.service;

import io.consenthub.domain.dto.CreateDataRequestInput;
import io.consenthub.domain.dto.DataRequestDto;
import io.consenthub.domain.dto.PrivacyExportResponse;
import io.consenthub.domain.dto.ProcessDataRequestInput;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.entity.ConsentHistoryEntity;
import io.consenthub.domain.entity.ConsentRecordEntity;
import io.consenthub.domain.entity.DataRequestEntity;
import io.consenthub.domain.entity.ExperimentAssignmentEntity;
import io.consenthub.domain.entity.ExperimentEntity;
import io.consenthub.domain.entity.UserEntity;
import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.DataRequestType;
import io.consenthub.domain.enums.UserRole;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ConflictException;
import io.consenthub.domain.exception.ForbiddenException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.repository.ConsentHistoryRepository;
import io.consenthub.repository.ConsentRecordRepository;
import io.consenthub.repository.DataRequestRepository;
import io.consenthub.repository.ExperimentAssignmentRepository;
import io.consenthub.repository.ExperimentRepository;
import io.consenthub.repository.UserRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-Service DSAR (Data Subject Access, Portability & Erasure) Service.
 *
 * <p>Implements GDPR Art. 15/17/20 and CCPA/CPRA Right to Know & Right to Delete (§1798.105, §1798.110),
 * including strict PII pseudonymization and historical snapshot scrubbing on erasure (ADR-007, PRIV-07).
 */
@Service
public class DsarService {

    public static final String DELETE_CONFIRMATION_PHRASE = "DELETE MY DATA";
    private static final Set<DataRequestStatus> OPEN_STATUSES = Set.of(
            DataRequestStatus.PENDING,
            DataRequestStatus.IN_REVIEW,
            DataRequestStatus.APPROVED
    );
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DataRequestRepository dataRequestRepository;
    private final UserRepository userRepository;
    private final ConsentRecordRepository consentRecordRepository;
    private final ConsentHistoryRepository consentHistoryRepository;
    private final ExperimentAssignmentRepository assignmentRepository;
    private final ExperimentRepository experimentRepository;
    private final ConsentService consentService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    public DsarService(
            DataRequestRepository dataRequestRepository,
            UserRepository userRepository,
            ConsentRecordRepository consentRecordRepository,
            ConsentHistoryRepository consentHistoryRepository,
            ExperimentAssignmentRepository assignmentRepository,
            ExperimentRepository experimentRepository,
            ConsentService consentService,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder
    ) {
        this.dataRequestRepository = dataRequestRepository;
        this.userRepository = userRepository;
        this.consentRecordRepository = consentRecordRepository;
        this.consentHistoryRepository = consentHistoryRepository;
        this.assignmentRepository = assignmentRepository;
        this.experimentRepository = experimentRepository;
        this.consentService = consentService;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<DataRequestDto> listMyDataRequests() {
        AuthenticatedUser principal = SecurityUtils.requireAuthenticatedUser();
        return dataRequestRepository.findByUser_IdOrderByRequestedAtDesc(principal.id())
                .stream()
                .map(DsarService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public int countMyOpenDataRequests(UUID userId) {
        if (userId == null) {
            return 0;
        }
        return (int) dataRequestRepository.countByUser_IdAndStatusIn(userId, OPEN_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<DataRequestDto> listAdminDataRequests(DataRequestStatus statusFilter) {
        SecurityUtils.requireAdminUser();
        List<DataRequestEntity> rows = statusFilter != null
                ? dataRequestRepository.findByStatusOrderByRequestedAtDesc(statusFilter)
                : dataRequestRepository.findAllByOrderByRequestedAtDesc();
        return rows.stream().map(DsarService::toDto).toList();
    }

    @Transactional
    public DataRequestDto createDataRequest(CreateDataRequestInput input) {
        AuthenticatedUser principal = SecurityUtils.requireAuthenticatedUser();
        if (input == null || input.requestType() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "requestType is required.");
        }

        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(principal.id())
                .orElseThrow(() -> new NotFoundException("Active user account not found."));

        if (input.requestType() == DataRequestType.DELETE) {
            if (user.getRole() == UserRole.ADMIN) {
                throw new ForbiddenException(
                        "ADMIN_DELETE_BLOCKED",
                        "Administrator governance accounts cannot self-erase while holding active ADMIN privileges."
                );
            }
            String confirmation = input.confirmationText() != null ? input.confirmationText().trim() : "";
            if (!DELETE_CONFIRMATION_PHRASE.equals(confirmation)) {
                throw new BadRequestException(
                        "CONFIRMATION_REQUIRED",
                        "Account deletion requires confirmationText equal to 'DELETE MY DATA'."
                );
            }
            boolean hasOpenDelete = dataRequestRepository.findByUser_IdOrderByRequestedAtDesc(user.getId())
                    .stream()
                    .anyMatch(r -> r.getRequestType() == DataRequestType.DELETE && OPEN_STATUSES.contains(r.getStatus()));
            if (hasOpenDelete) {
                throw new ConflictException(
                        "DUPLICATE_OPEN_REQUEST",
                        "An active account deletion request is already open for your account."
                );
            }
        }

        Instant now = Instant.now();
        DataRequestEntity entity = new DataRequestEntity();
        entity.setId(UUID.randomUUID());
        entity.setUser(user);
        entity.setRequestType(input.requestType());
        entity.setStatus(DataRequestStatus.PENDING);
        entity.setReason(input.reason() != null && !input.reason().isBlank() ? input.reason().trim() : null);
        entity.setRequestedAt(now);
        entity.setUpdatedAt(now);

        DataRequestEntity saved = dataRequestRepository.save(entity);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("requestType", saved.getRequestType().name());
        auditDetails.put("status", saved.getStatus().name());
        auditLogService.append(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                "DSAR_REQUEST_CREATED",
                "DATA_REQUEST",
                saved.getId().toString(),
                auditDetails
        );

        return toDto(saved);
    }

    /**
     * Generates a complete machine-readable JSON portability archive for the authenticated user
     * and logs a COMPLETED EXPORT DSAR record + audit entry (GDPR Art. 15/20, CCPA §1798.110).
     */
    @Transactional
    public PrivacyExportResponse generateSelfServiceExport() {
        AuthenticatedUser principal = SecurityUtils.requireAuthenticatedUser();
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(principal.id())
                .orElseThrow(() -> new NotFoundException("Active user account not found."));

        String subjectKey = user.getSubjectKey();
        Instant now = Instant.now();

        DataRequestEntity exportTicket = new DataRequestEntity();
        exportTicket.setId(UUID.randomUUID());
        exportTicket.setUser(user);
        exportTicket.setRequestType(DataRequestType.EXPORT);
        exportTicket.setStatus(DataRequestStatus.COMPLETED);
        exportTicket.setReason("Self-service JSON data portability download.");
        exportTicket.setAdminNotes("Fulfilled automatically via self-service portability endpoint.");
        exportTicket.setRequestedAt(now);
        exportTicket.setUpdatedAt(now);
        exportTicket.setCompletedAt(now);

        Map<String, Object> minimalSummary = new LinkedHashMap<>();
        minimalSummary.put("schemaVersion", "1.0");
        minimalSummary.put("platform", "ConsentHub");
        minimalSummary.put("generatedAt", TimestampFormatter.format(now));
        minimalSummary.put("regulatoryBasis", List.of("GDPR_ARTICLE_15_20", "CCPA_RIGHT_TO_KNOW"));
        exportTicket.setExportPayloadJson(minimalSummary);
        DataRequestEntity savedTicket = dataRequestRepository.saveAndFlush(exportTicket);

        List<ConsentRecordEntity> currentRecords = consentRecordRepository.findBySubjectKey(subjectKey);
        List<ConsentHistoryEntity> historyLedger = consentHistoryRepository.findBySubjectKeyOrderByOccurredAtDesc(subjectKey);
        List<ExperimentAssignmentEntity> assignments = assignmentRepository.findBySubjectKey(subjectKey);
        Map<UUID, String> slugByExpId = experimentRepository.findAll().stream()
                .collect(Collectors.toMap(ExperimentEntity::getId, ExperimentEntity::getSlug, (a, b) -> a));
        List<DataRequestEntity> allRequests = dataRequestRepository.findByUser_IdOrderByRequestedAtDesc(user.getId());

        List<PrivacyExportResponse.ExportedConsentRecord> exportedRecords = currentRecords.stream()
                .map(r -> new PrivacyExportResponse.ExportedConsentRecord(
                        r.getPurpose().getSlug(),
                        r.getPurpose().getName(),
                        r.getPurpose().getLegalBasis(),
                        r.isGranted(),
                        r.getPolicyVersion().getVersionLabel(),
                        r.getRegionCode(),
                        r.getSource().name(),
                        TimestampFormatter.format(r.getUpdatedAt())
                ))
                .toList();

        List<PrivacyExportResponse.ExportedConsentHistoryItem> exportedHistory = historyLedger.stream()
                .map(h -> new PrivacyExportResponse.ExportedConsentHistoryItem(
                        h.getId().toString(),
                        h.getPurposeSlug(),
                        h.getPurposeName(),
                        h.getPreviousGranted(),
                        h.isNewGranted(),
                        h.getPolicyVersionLabel(),
                        h.getRegionCode(),
                        h.getSource().name(),
                        h.getExperimentVariant(),
                        h.getProofHash(),
                        TimestampFormatter.format(h.getOccurredAt())
                ))
                .toList();

        List<PrivacyExportResponse.ExportedExperimentAssignment> exportedAssignments = assignments.stream()
                .map(a -> new PrivacyExportResponse.ExportedExperimentAssignment(
                        slugByExpId.getOrDefault(a.getExperimentId(), a.getExperimentId().toString()),
                        a.getVariantKey(),
                        a.isConverted(),
                        TimestampFormatter.format(a.getAssignedAt()),
                        TimestampFormatter.format(a.getConvertedAt())
                ))
                .toList();

        List<PrivacyExportResponse.ExportedDataSubjectRequest> exportedRequests = allRequests.stream()
                .map(d -> new PrivacyExportResponse.ExportedDataSubjectRequest(
                        d.getId().toString(),
                        d.getRequestType(),
                        d.getStatus(),
                        TimestampFormatter.format(d.getRequestedAt()),
                        TimestampFormatter.format(d.getCompletedAt())
                ))
                .toList();

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("requestId", savedTicket.getId().toString());
        auditDetails.put("consentRecordsExported", exportedRecords.size());
        auditDetails.put("historyEntriesExported", exportedHistory.size());
        auditLogService.append(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                "PRIVACY_EXPORT_DOWNLOADED",
                "DATA_REQUEST",
                savedTicket.getId().toString(),
                auditDetails
        );

        return new PrivacyExportResponse(
                new PrivacyExportResponse.ExportMetadata(
                        "1.0",
                        TimestampFormatter.format(now),
                        "ConsentHub",
                        List.of("GDPR_ARTICLE_15_20", "CCPA_RIGHT_TO_KNOW"),
                        savedTicket.getId().toString()
                ),
                AuthService.toProfileDto(user),
                exportedRecords,
                exportedHistory,
                exportedAssignments,
                exportedRequests
        );
    }

    @Transactional
    public DataRequestDto processDataRequest(ProcessDataRequestInput input) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (input == null || input.id() == null || input.id().isBlank() || input.status() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Data request id and target status are required.");
        }

        UUID requestId = parseUuid(input.id(), "Invalid data request ID.");
        DataRequestEntity requestEntity = dataRequestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Data request not found: " + requestId));

        if (requestEntity.getStatus() == DataRequestStatus.COMPLETED
                || requestEntity.getStatus() == DataRequestStatus.REJECTED) {
            throw new ConflictException(
                    "DSAR_ALREADY_FINALIZED",
                    "Data request is already finalized with status " + requestEntity.getStatus()
            );
        }

        UserEntity adminEntity = userRepository.findById(admin.id()).orElse(null);
        DataRequestStatus previousStatus = requestEntity.getStatus();
        DataRequestStatus targetStatus = input.status();
        Instant now = Instant.now();

        requestEntity.setStatus(targetStatus);
        if (input.adminNotes() != null && !input.adminNotes().isBlank()) {
            requestEntity.setAdminNotes(input.adminNotes().trim());
        }
        requestEntity.setProcessedBy(adminEntity);
        requestEntity.setUpdatedAt(now);

        if (targetStatus == DataRequestStatus.COMPLETED || targetStatus == DataRequestStatus.REJECTED) {
            requestEntity.setCompletedAt(now);
        }

        if (targetStatus == DataRequestStatus.COMPLETED && requestEntity.getRequestType() == DataRequestType.DELETE) {
            executeAccountErasure(requestEntity.getUser(), now);
        }

        DataRequestEntity saved = dataRequestRepository.saveAndFlush(requestEntity);

        // PRIV-07: Never record pre-erasure email or fullName in DSAR_DELETE_COMPLETED audit details
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("requestType", saved.getRequestType().name());
        auditDetails.put("previousStatus", previousStatus.name());
        auditDetails.put("newStatus", targetStatus.name());
        auditDetails.put("targetUserId", saved.getUser().getId().toString());
        if (targetStatus == DataRequestStatus.COMPLETED && saved.getRequestType() == DataRequestType.DELETE) {
            auditDetails.put("piiRedacted", true);
            auditDetails.put("optionalConsentsRevoked", true);
            auditDetails.put("historicalExportsScrubbed", true);
        }

        String auditAction = (targetStatus == DataRequestStatus.COMPLETED && saved.getRequestType() == DataRequestType.DELETE)
                ? "DSAR_DELETE_COMPLETED"
                : "DSAR_STATUS_UPDATED";

        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                auditAction,
                "DATA_REQUEST",
                saved.getId().toString(),
                auditDetails
        );

        return toDto(saved);
    }

    private void executeAccountErasure(UserEntity targetUser, Instant now) {
        if (targetUser.getDeletedAt() != null) {
            return;
        }
        // 1. Scrub any historical export_payload_json snapshots for this user (PRIV-07 guardrail)
        dataRequestRepository.clearExportPayloadsByUserId(targetUser.getId());

        // 2. Revoke optional consent purposes & detach userId in consent_records
        consentService.revokeOptionalConsentsForErasure(targetUser);

        // 3. Detach userId from experiment_assignments
        for (ExperimentAssignmentEntity assignment : assignmentRepository.findByUserIdOrderByAssignedAtDesc(targetUser.getId())) {
            assignment.setUserId(null);
            assignmentRepository.save(assignment);
        }

        // 4. Redact PII in users table and mark deleted_at so active JWT cookies immediately become invalid (SEC-G08)
        byte[] randomSecret = new byte[24];
        SECURE_RANDOM.nextBytes(randomSecret);
        targetUser.setEmail("deleted-" + targetUser.getId() + "@redacted.consenthub.invalid");
        targetUser.setFullName("Redacted User");
        targetUser.setPasswordHash(passwordEncoder.encode(HexFormat.of().formatHex(randomSecret)));
        targetUser.setDeletedAt(now);
        targetUser.setUpdatedAt(now);
        userRepository.saveAndFlush(targetUser);
    }

    private static UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ex) {
            throw new BadRequestException("VALIDATION_ERROR", message);
        }
    }

    public static DataRequestDto toDto(DataRequestEntity entity) {
        UserEntity user = entity.getUser();
        String processedByEmail = entity.getProcessedBy() != null ? entity.getProcessedBy().getEmail() : null;
        return new DataRequestDto(
                entity.getId().toString(),
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                entity.getRequestType(),
                entity.getStatus(),
                entity.getReason(),
                entity.getAdminNotes(),
                processedByEmail,
                TimestampFormatter.format(entity.getRequestedAt()),
                TimestampFormatter.format(entity.getUpdatedAt()),
                TimestampFormatter.format(entity.getCompletedAt())
        );
    }
}
