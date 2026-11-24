package io.consenthub.sdui;

import io.consenthub.domain.dto.ConsentStatusBundleDto;
import io.consenthub.domain.dto.ExperimentAssignmentContextDto;
import io.consenthub.domain.dto.PolicyVersionDto;
import io.consenthub.domain.dto.PurposeConsentStateDto;
import io.consenthub.domain.dto.RegionRuleDto;
import io.consenthub.domain.entity.ExperimentVariantConfig;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.ScreenName;
import io.consenthub.graphql.GraphQlDtos.ScreenContextInput;
import io.consenthub.sdui.SduiModels.CompositionInput;
import io.consenthub.sdui.SduiModels.ExperimentVariantSpec;
import io.consenthub.sdui.SduiModels.PolicySpec;
import io.consenthub.sdui.SduiModels.PurposeToggleSpec;
import io.consenthub.sdui.SduiModels.RegionRuleSpec;
import io.consenthub.sdui.SduiModels.SduiExperimentContext;
import io.consenthub.sdui.SduiModels.SduiScreen;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import io.consenthub.service.ConsentService;
import io.consenthub.service.DsarService;
import io.consenthub.service.ExperimentService;
import io.consenthub.service.RegionResolverService;
import io.consenthub.service.SduiScreenProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates live Server-Driven UI (SDUI) screen composition across GraphQL {@code screen(name, context)}
 * and the public embeddable SDK {@code GET /api/v1/sdk/config} endpoint.
 */
@Service
public class SduiEngineService implements SduiScreenProvider {

    private final ConsentService consentService;
    private final ExperimentService experimentService;
    private final DsarService dsarService;
    private final RegionResolverService regionResolverService;
    private final SduiTreeComposer treeComposer;

    public SduiEngineService(
            ConsentService consentService,
            ExperimentService experimentService,
            DsarService dsarService,
            RegionResolverService regionResolverService,
            SduiTreeComposer treeComposer
    ) {
        this.consentService = consentService;
        this.experimentService = experimentService;
        this.dsarService = dsarService;
        this.regionResolverService = regionResolverService;
        this.treeComposer = treeComposer;
    }

    @Transactional
    public SduiScreen composeScreen(ScreenName screenName, ScreenContextInput context) {
        String requestedSubjectKey = context != null ? context.subjectKey() : null;
        RegionCode explicitRegion = context != null ? context.region() : null;
        String locale = context != null ? context.locale() : "en-US";
        String deviceType = context != null ? context.deviceType() : "DESKTOP";
        String previewVariantKey = context != null ? context.previewVariantKey() : null;

        String effectiveSubjectKey = SecurityUtils.resolveEffectiveSubjectKey(requestedSubjectKey, true);
        RegionCode resolvedRegion = regionResolverService.resolveRegion(explicitRegion);

        return composeInternal(
                screenName,
                effectiveSubjectKey,
                resolvedRegion,
                locale,
                deviceType,
                previewVariantKey
        );
    }

    @Override
    @Transactional
    public Map<String, Object> buildBannerScreenMap(
            String validatedSubjectKey,
            String regionCodeRaw,
            String previewVariantKey
    ) {
        RegionCode regionCode = regionResolverService.parseOptionalOrDefault(regionCodeRaw, RegionCode.GLOBAL);
        SduiScreen screen = composeInternal(
                ScreenName.CONSENT_BANNER,
                validatedSubjectKey,
                regionCode,
                "en-US",
                "DESKTOP",
                previewVariantKey
        );
        return SduiModels.toMap(screen);
    }

    private SduiScreen composeInternal(
            ScreenName screenName,
            String subjectKey,
            RegionCode regionCode,
            String locale,
            String deviceType,
            String previewVariantKey
    ) {
        ConsentStatusBundleDto bundle = consentService.getConsentStateForValidatedSubject(subjectKey, regionCode);

        Optional<ExperimentAssignmentContextDto> expCtxOpt = experimentService.resolveOrAssignVariant(
                screenName,
                regionCode,
                subjectKey,
                previewVariantKey
        );

        SduiExperimentContext sduiExpCtx = null;
        ExperimentVariantSpec variantSpec = null;
        if (expCtxOpt.isPresent()) {
            ExperimentAssignmentContextDto ctx = expCtxOpt.get();
            sduiExpCtx = new SduiExperimentContext(ctx.experimentId(), ctx.experimentSlug(), ctx.variantKey());
            ExperimentVariantConfig v = ctx.variant();
            variantSpec = new ExperimentVariantSpec(
                    v.key(),
                    v.name(),
                    v.weight(),
                    v.headline(),
                    v.body(),
                    v.primaryCtaLabel(),
                    v.secondaryCtaLabel(),
                    v.layoutTone()
            );
        }

        PolicyVersionDto activePolicyDto = bundle.activePolicyVersion();
        PolicySpec policySpec = new PolicySpec(
                activePolicyDto.versionLabel(),
                activePolicyDto.title(),
                activePolicyDto.summary(),
                activePolicyDto.contentMarkdown(),
                activePolicyDto.requiresReconsent(),
                activePolicyDto.publishedAt()
        );

        RegionRuleDto ruleDto = bundle.regionRule();
        RegionRuleSpec ruleSpec = new RegionRuleSpec(
                ruleDto.regionCode().name(),
                ruleDto.regimeName().name(),
                ruleDto.consentModel().name(),
                ruleDto.bannerStyle().name(),
                ruleDto.showRejectAll(),
                ruleDto.retentionDays()
        );

        List<PurposeToggleSpec> purposeSpecs = bundle.decisions().stream()
                .map(d -> new PurposeToggleSpec(
                        d.purposeId(),
                        d.slug(),
                        d.name(),
                        d.description(),
                        d.legalBasis().name(),
                        d.isEssential(),
                        d.granted()
                ))
                .toList();

        int historyCount = 0;
        int openDsarCount = 0;
        if (screenName == ScreenName.PRIVACY_OVERVIEW) {
            historyCount = (int) consentService.countHistoryEntries(subjectKey);
            openDsarCount = SecurityUtils.getCurrentUser()
                    .map(AuthenticatedUser::id)
                    .map(dsarService::countMyOpenDataRequests)
                    .orElse(0);
        }

        CompositionInput compositionInput = new CompositionInput(
                screenName.name(),
                subjectKey,
                locale,
                deviceType,
                bundle.hasConsented(),
                bundle.needsReconsent(),
                bundle.consentedPolicyVersion(),
                policySpec,
                ruleSpec,
                purposeSpecs,
                sduiExpCtx,
                variantSpec,
                historyCount,
                openDsarCount
        );

        return treeComposer.compose(compositionInput);
    }
}
