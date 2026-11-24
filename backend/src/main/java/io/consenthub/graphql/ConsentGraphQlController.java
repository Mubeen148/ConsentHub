package io.consenthub.graphql;

import io.consenthub.domain.dto.ApiKeyInfoDto;
import io.consenthub.domain.dto.AuditChainVerificationDto;
import io.consenthub.domain.dto.AuditLogEntryDto;
import io.consenthub.domain.dto.ConsentAnalyticsDto;
import io.consenthub.domain.dto.ConsentHistoryEntryDto;
import io.consenthub.domain.dto.ConsentPurposeDto;
import io.consenthub.domain.dto.ConsentStatusBundleDto;
import io.consenthub.domain.dto.CreateApiKeyInput;
import io.consenthub.domain.dto.CreateDataRequestInput;
import io.consenthub.domain.dto.CreateExperimentInput;
import io.consenthub.domain.dto.CreatedApiKeyResultDto;
import io.consenthub.domain.dto.DataRequestDto;
import io.consenthub.domain.dto.ExperimentSummaryDto;
import io.consenthub.domain.dto.PolicyVersionDto;
import io.consenthub.domain.dto.ProcessDataRequestInput;
import io.consenthub.domain.dto.PublishPolicyVersionInput;
import io.consenthub.domain.dto.RegionRuleDto;
import io.consenthub.domain.dto.SaveConsentInput;
import io.consenthub.domain.dto.UpdateProfileInput;
import io.consenthub.domain.dto.UpdateRegionRuleInput;
import io.consenthub.domain.dto.UpsertPurposeInput;
import io.consenthub.domain.dto.UserProfileDto;
import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.ExperimentStatus;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.ScreenName;
import io.consenthub.graphql.GraphQlDtos.AuditLogEntryView;
import io.consenthub.graphql.GraphQlDtos.PolicyVersionView;
import io.consenthub.graphql.GraphQlDtos.ScreenContextInput;
import io.consenthub.sdui.SduiEngineService;
import io.consenthub.sdui.SduiModels.SduiScreen;
import io.consenthub.service.AnalyticsService;
import io.consenthub.service.ApiKeyService;
import io.consenthub.service.AuthService;
import io.consenthub.service.AuditLogService;
import io.consenthub.service.ConsentService;
import io.consenthub.service.DsarService;
import io.consenthub.service.ExperimentService;
import io.consenthub.service.PolicyService;
import io.consenthub.service.RegionRuleService;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

/**
 * Spring for GraphQL controller implementing 100% of queries and mutations frozen in
 * {@code docs/contracts/graphql-schema.graphqls}.
 */
@Controller
public class ConsentGraphQlController {

    private final AuthService authService;
    private final ConsentService consentService;
    private final SduiEngineService sduiEngineService;
    private final PolicyService policyService;
    private final RegionRuleService regionRuleService;
    private final ExperimentService experimentService;
    private final DsarService dsarService;
    private final AnalyticsService analyticsService;
    private final AuditLogService auditLogService;
    private final ApiKeyService apiKeyService;

    public ConsentGraphQlController(
            AuthService authService,
            ConsentService consentService,
            SduiEngineService sduiEngineService,
            PolicyService policyService,
            RegionRuleService regionRuleService,
            ExperimentService experimentService,
            DsarService dsarService,
            AnalyticsService analyticsService,
            AuditLogService auditLogService,
            ApiKeyService apiKeyService
    ) {
        this.authService = authService;
        this.consentService = consentService;
        this.sduiEngineService = sduiEngineService;
        this.policyService = policyService;
        this.regionRuleService = regionRuleService;
        this.experimentService = experimentService;
        this.dsarService = dsarService;
        this.analyticsService = analyticsService;
        this.auditLogService = auditLogService;
        this.apiKeyService = apiKeyService;
    }

    // =========================================================================
    // Visitor & Authenticated User Queries
    // =========================================================================

    @QueryMapping
    public UserProfileDto me() {
        return authService.getCurrentUserProfileOrNull();
    }

    @QueryMapping
    public SduiScreen screen(
            @Argument ScreenName name,
            @Argument ScreenContextInput context
    ) {
        return sduiEngineService.composeScreen(name, context);
    }

    @QueryMapping
    public ConsentStatusBundleDto myConsentState(
            @Argument String subjectKey,
            @Argument RegionCode region
    ) {
        return consentService.getConsentState(subjectKey, region);
    }

    @QueryMapping
    public List<ConsentHistoryEntryDto> myConsentHistory(
            @Argument String subjectKey,
            @Argument Integer limit
    ) {
        return consentService.listConsentHistory(subjectKey, limit);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<DataRequestDto> myDataRequests() {
        return dsarService.listMyDataRequests();
    }

    @QueryMapping
    public List<ConsentPurposeDto> purposes(@Argument Boolean includeInactive) {
        return policyService.listPurposes(includeInactive);
    }

    @QueryMapping
    public List<PolicyVersionView> policyVersions() {
        return policyService.listPolicyVersions().stream()
                .map(ConsentGraphQlController::toPolicyView)
                .toList();
    }

    @QueryMapping
    public List<RegionRuleDto> regionRules() {
        return regionRuleService.listRegionRules();
    }

    // =========================================================================
    // Admin Governance Queries (SEC-G02: ROLE_ADMIN Required)
    // =========================================================================

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ExperimentSummaryDto> adminExperiments() {
        return experimentService.listAdminExperiments();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<DataRequestDto> adminDataRequests(@Argument DataRequestStatus status) {
        return dsarService.listAdminDataRequests(status);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConsentAnalyticsDto adminAnalytics(@Argument Integer days) {
        return analyticsService.getAdminAnalytics(days);
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<AuditLogEntryView> adminAuditLogs(
            @Argument Integer limit,
            @Argument String actionFilter
    ) {
        return auditLogService.listAuditLogs(limit, actionFilter).stream()
                .map(ConsentGraphQlController::toAuditView)
                .toList();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AuditChainVerificationDto verifyAuditChain() {
        return auditLogService.verifyAuditChain();
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<ApiKeyInfoDto> adminApiKeys() {
        return apiKeyService.listApiKeys();
    }

    // =========================================================================
    // Visitor & Authenticated User Mutations
    // =========================================================================

    @MutationMapping
    public ConsentStatusBundleDto saveConsentPreferences(@Argument SaveConsentInput input) {
        return consentService.saveConsentPreferences(input);
    }

    @MutationMapping
    public ConsentStatusBundleDto acceptAllConsent(
            @Argument String subjectKey,
            @Argument RegionCode region,
            @Argument String source,
            @Argument String experimentId,
            @Argument String variantKey
    ) {
        return consentService.acceptAllConsent(subjectKey, region, source, experimentId, variantKey);
    }

    @MutationMapping
    public ConsentStatusBundleDto rejectOptionalConsent(
            @Argument String subjectKey,
            @Argument RegionCode region,
            @Argument String source,
            @Argument String experimentId,
            @Argument String variantKey
    ) {
        return consentService.rejectOptionalConsent(subjectKey, region, source, experimentId, variantKey);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public UserProfileDto updateMyProfile(@Argument UpdateProfileInput input) {
        return authService.updateMyProfile(input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public DataRequestDto createDataRequest(@Argument CreateDataRequestInput input) {
        return dsarService.createDataRequest(input);
    }

    // =========================================================================
    // Admin Governance Mutations (SEC-G02: ROLE_ADMIN Required)
    // =========================================================================

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ConsentPurposeDto upsertPurpose(@Argument UpsertPurposeInput input) {
        return policyService.upsertPurpose(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PolicyVersionView publishPolicyVersion(@Argument PublishPolicyVersionInput input) {
        return toPolicyView(policyService.publishPolicyVersion(input));
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public RegionRuleDto updateRegionRule(@Argument UpdateRegionRuleInput input) {
        return regionRuleService.updateRegionRule(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ExperimentSummaryDto createExperiment(@Argument CreateExperimentInput input) {
        return experimentService.createExperiment(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ExperimentSummaryDto updateExperimentStatus(
            @Argument String id,
            @Argument ExperimentStatus status
    ) {
        return experimentService.updateExperimentStatus(id, status);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DataRequestDto processDataRequest(@Argument ProcessDataRequestInput input) {
        return dsarService.processDataRequest(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public CreatedApiKeyResultDto createApiKey(@Argument CreateApiKeyInput input) {
        return apiKeyService.createApiKey(input);
    }

    @MutationMapping
    @PreAuthorize("hasRole('ADMIN')")
    public boolean revokeApiKey(@Argument String id) {
        return apiKeyService.revokeApiKey(id);
    }

    // =========================================================================
    // Nested Schema Field Mappings Ensuring 100% SDL Field Parity
    // =========================================================================

    @SchemaMapping(typeName = "ConsentStatusBundle", field = "activePolicyVersion")
    public PolicyVersionView activePolicyVersion(ConsentStatusBundleDto bundle) {
        return toPolicyView(bundle.activePolicyVersion());
    }

    @SchemaMapping(typeName = "ConsentPurpose", field = "isEssential")
    public boolean purposeIsEssential(ConsentPurposeDto dto) {
        return dto.isEssential();
    }

    private static PolicyVersionView toPolicyView(PolicyVersionDto dto) {
        return new PolicyVersionView(
                dto.id(),
                dto.versionNumber(),
                dto.versionLabel(),
                dto.title(),
                dto.summary(),
                dto.contentMarkdown(),
                dto.requiresReconsent(),
                dto.isActive(),
                dto.publishedByEmail(),
                dto.publishedAt(),
                dto.createdAt()
        );
    }

    private static AuditLogEntryView toAuditView(AuditLogEntryDto dto) {
        return new AuditLogEntryView(
                dto.id(),
                dto.sequenceNum(),
                dto.actorId(),
                dto.actorEmail(),
                dto.actorRole(),
                dto.action(),
                dto.entityType(),
                dto.entityId(),
                dto.details(),
                dto.prevHash(),
                dto.entryHash(),
                dto.createdAt()
        );
    }
}
