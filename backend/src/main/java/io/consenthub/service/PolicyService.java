package io.consenthub.service;

import io.consenthub.domain.dto.ConsentPurposeDto;
import io.consenthub.domain.dto.PolicyVersionDto;
import io.consenthub.domain.dto.PublishPolicyVersionInput;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.dto.UpsertPurposeInput;
import io.consenthub.domain.entity.ConsentPurposeEntity;
import io.consenthub.domain.entity.PolicyVersionEntity;
import io.consenthub.domain.entity.UserEntity;
import io.consenthub.domain.enums.LegalBasis;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ConflictException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.repository.ConsentPurposeRepository;
import io.consenthub.repository.PolicyVersionRepository;
import io.consenthub.repository.UserRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages granular consent purposes and versioned privacy policies (including re-consent triggers).
 */
@Service
public class PolicyService {

    private static final Pattern PURPOSE_SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    private final ConsentPurposeRepository purposeRepository;
    private final PolicyVersionRepository policyVersionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PolicyService(
            ConsentPurposeRepository purposeRepository,
            PolicyVersionRepository policyVersionRepository,
            UserRepository userRepository,
            AuditLogService auditLogService
    ) {
        this.purposeRepository = purposeRepository;
        this.policyVersionRepository = policyVersionRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ConsentPurposeDto> listPurposes(Boolean includeInactive) {
        // SEC-G02: honor includeInactive=true ONLY when caller has ROLE_ADMIN
        boolean showInactive = Boolean.TRUE.equals(includeInactive) && SecurityUtils.isCurrentUserAdmin();
        List<ConsentPurposeEntity> entities = showInactive
                ? purposeRepository.findAllByOrderByDisplayOrderAsc()
                : purposeRepository.findByActiveTrueOrderByDisplayOrderAsc();
        return entities.stream().map(PolicyService::toPurposeDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ConsentPurposeEntity> listActivePurposeEntities() {
        return purposeRepository.findByActiveTrueOrderByDisplayOrderAsc();
    }

    @Transactional
    public ConsentPurposeDto upsertPurpose(UpsertPurposeInput input) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (input == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Purpose input is required.");
        }
        String slug = input.slug() != null ? input.slug().trim() : "";
        if (!PURPOSE_SLUG_PATTERN.matcher(slug).matches()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "slug must match '^[a-z][a-z0-9_]{1,63}$'."
            );
        }
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 120) {
            throw new BadRequestException("VALIDATION_ERROR", "Purpose name must be between 1 and 120 characters.");
        }
        if (input.description() == null || input.description().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Purpose description is required.");
        }
        if (input.legalBasis() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "legalBasis is required.");
        }

        boolean isEssential = Boolean.TRUE.equals(input.isEssential());
        if (isEssential && input.legalBasis() != LegalBasis.STRICTLY_NECESSARY) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Essential purposes must use legalBasis = STRICTLY_NECESSARY."
            );
        }

        ConsentPurposeEntity entity;
        if (input.id() != null && !input.id().isBlank()) {
            UUID id = parseUuid(input.id(), "Invalid purpose id.");
            entity = purposeRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Consent purpose not found: " + id));
            Optional<ConsentPurposeEntity> slugOwner = purposeRepository.findBySlug(slug);
            if (slugOwner.isPresent() && !slugOwner.get().getId().equals(id)) {
                throw new ConflictException("SLUG_ALREADY_EXISTS", "Another purpose already uses slug '" + slug + "'.");
            }
        } else {
            entity = purposeRepository.findBySlug(slug).orElseGet(() -> {
                ConsentPurposeEntity created = new ConsentPurposeEntity();
                created.setId(UUID.randomUUID());
                created.setCreatedAt(Instant.now());
                return created;
            });
        }

        entity.setSlug(slug);
        entity.setName(input.name().trim());
        entity.setDescription(input.description().trim());
        entity.setLegalBasis(input.legalBasis());
        entity.setEssential(isEssential);
        entity.setActive(isEssential || (input.active() == null || input.active()));
        entity.setDisplayOrder(input.displayOrder() != null ? input.displayOrder() : 0);
        entity.setUpdatedAt(Instant.now());

        ConsentPurposeEntity saved = purposeRepository.save(entity);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("slug", saved.getSlug());
        details.put("legalBasis", saved.getLegalBasis().name());
        details.put("isEssential", saved.isEssential());
        details.put("active", saved.isActive());
        details.put("displayOrder", saved.getDisplayOrder());

        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "PURPOSE_UPSERTED",
                "CONSENT_PURPOSE",
                saved.getId().toString(),
                details
        );

        return toPurposeDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PolicyVersionDto> listPolicyVersions() {
        return policyVersionRepository.findAllByOrderByVersionNumberDesc()
                .stream()
                .map(PolicyService::toPolicyDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PolicyVersionDto getActivePolicyVersion() {
        return toPolicyDto(getActivePolicyVersionEntity());
    }

    @Transactional(readOnly = true)
    public PolicyVersionEntity getActivePolicyVersionEntity() {
        return policyVersionRepository.findByActiveTrue()
                .orElseThrow(() -> new NotFoundException("No active privacy policy version configured."));
    }

    @Transactional(readOnly = true)
    public boolean requiresReconsentSince(PolicyVersionEntity consentedPolicy, PolicyVersionEntity activePolicy) {
        if (consentedPolicy == null || activePolicy == null) {
            return true;
        }
        if (consentedPolicy.getId().equals(activePolicy.getId())) {
            return false;
        }
        if (consentedPolicy.getVersionNumber() >= activePolicy.getVersionNumber()) {
            return false;
        }
        return policyVersionRepository.existsReconsentRequiredBetween(
                consentedPolicy.getVersionNumber(),
                activePolicy.getVersionNumber()
        );
    }

    @Transactional
    public PolicyVersionDto publishPolicyVersion(PublishPolicyVersionInput input) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (input == null || input.versionLabel() == null || input.versionLabel().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "versionLabel is required.");
        }
        String versionLabel = input.versionLabel().trim();
        if (versionLabel.length() > 32) {
            throw new BadRequestException("VALIDATION_ERROR", "versionLabel must be 32 characters or fewer.");
        }
        if (policyVersionRepository.existsByVersionLabel(versionLabel)) {
            throw new ConflictException(
                    "POLICY_VERSION_EXISTS",
                    "Policy version label '" + versionLabel + "' already exists."
            );
        }
        if (input.title() == null || input.title().isBlank() || input.title().trim().length() > 200) {
            throw new BadRequestException("VALIDATION_ERROR", "Policy title must be between 1 and 200 characters.");
        }
        if (input.summary() == null || input.summary().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Policy summary is required.");
        }
        if (input.contentMarkdown() == null || input.contentMarkdown().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Policy contentMarkdown is required.");
        }

        boolean activateImmediately = input.activateImmediately() == null || input.activateImmediately();
        boolean requiresReconsent = input.requiresReconsent() == null || input.requiresReconsent();

        if (activateImmediately) {
            policyVersionRepository.deactivateAllActivePolicies();
        }

        int nextVersionNumber = policyVersionRepository.findMaxVersionNumber() + 1;
        UserEntity publisher = userRepository.findById(admin.id()).orElse(null);
        Instant now = Instant.now();

        PolicyVersionEntity entity = new PolicyVersionEntity();
        entity.setId(UUID.randomUUID());
        entity.setVersionNumber(nextVersionNumber);
        entity.setVersionLabel(versionLabel);
        entity.setTitle(input.title().trim());
        entity.setSummary(input.summary().trim());
        entity.setContentMarkdown(input.contentMarkdown().trim());
        entity.setRequiresReconsent(requiresReconsent);
        entity.setActive(activateImmediately);
        entity.setPublishedBy(publisher);
        entity.setPublishedAt(now);
        entity.setCreatedAt(now);

        PolicyVersionEntity saved = policyVersionRepository.saveAndFlush(entity);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("versionNumber", saved.getVersionNumber());
        details.put("versionLabel", saved.getVersionLabel());
        details.put("requiresReconsent", saved.isRequiresReconsent());
        details.put("activated", saved.isActive());

        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "POLICY_PUBLISHED",
                "POLICY_VERSION",
                saved.getId().toString(),
                details
        );

        return toPolicyDto(saved);
    }

    private static UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new BadRequestException("VALIDATION_ERROR", message);
        }
    }

    public static ConsentPurposeDto toPurposeDto(ConsentPurposeEntity entity) {
        return new ConsentPurposeDto(
                entity.getId().toString(),
                entity.getSlug(),
                entity.getName(),
                entity.getDescription(),
                entity.getLegalBasis(),
                entity.isEssential(),
                entity.isActive(),
                entity.getDisplayOrder(),
                TimestampFormatter.format(entity.getCreatedAt()),
                TimestampFormatter.format(entity.getUpdatedAt())
        );
    }

    public static PolicyVersionDto toPolicyDto(PolicyVersionEntity entity) {
        String publishedByEmail = entity.getPublishedBy() != null ? entity.getPublishedBy().getEmail() : null;
        return new PolicyVersionDto(
                entity.getId().toString(),
                entity.getVersionNumber(),
                entity.getVersionLabel(),
                entity.getTitle(),
                entity.getSummary(),
                entity.getContentMarkdown(),
                entity.isRequiresReconsent(),
                entity.isActive(),
                publishedByEmail,
                TimestampFormatter.format(entity.getPublishedAt()),
                TimestampFormatter.format(entity.getCreatedAt())
        );
    }
}
