package io.consenthub.sdui;

import static io.consenthub.sdui.SduiModels.SCHEMA_VERSION_1_0;
import static io.consenthub.sdui.SduiModels.propsOf;

import io.consenthub.sdui.SduiModels.CompositionInput;
import io.consenthub.sdui.SduiModels.ExperimentVariantSpec;
import io.consenthub.sdui.SduiModels.PolicySpec;
import io.consenthub.sdui.SduiModels.PurposeToggleSpec;
import io.consenthub.sdui.SduiModels.RegionRuleSpec;
import io.consenthub.sdui.SduiModels.SduiAction;
import io.consenthub.sdui.SduiModels.SduiExperimentContext;
import io.consenthub.sdui.SduiModels.SduiNode;
import io.consenthub.sdui.SduiModels.SduiScreen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Dynamically composes versioned, regulatory-aware Server-Driven UI (SDUI) component trees
 * for {@code CONSENT_BANNER}, {@code PREFERENCE_CENTER}, and {@code PRIVACY_OVERVIEW}.
 *
 * <p>Enforces privacy-by-design and zero-dark-pattern guardrails ({@code PRIV-02}, {@code PRIV-03},
 * {@code PRIV-09}, and {@code SEC-G03}):
 * <ul>
 *   <li>Essential purposes are unconditionally locked ({@code checked: true, required: true}).</li>
 *   <li>Under {@code OPT_IN} (GDPR), optional purposes default strictly to {@code false} when no
 *       prior consent exists, and {@code BannerShell} is non-dismissible ({@code dismissible: false}).</li>
 *   <li>Under {@code OPT_OUT} (CCPA), optional purposes default to {@code true} when no prior
 *       record exists, paired with a prominent 1-click {@code Do Not Sell or Share} opt-out CTA.</li>
 *   <li>A/B experiment variants may customize copy and CTA labels, but can NEVER suppress the
 *       symmetric reject/opt-out button or pre-check optional purposes under {@code OPT_IN}.</li>
 *   <li>Every composed {@link SduiScreen} is validated via {@link SduiSchemaValidator} before return.</li>
 * </ul>
 */
@Component
public class SduiTreeComposer {

    private final SduiSchemaValidator validator;

    public SduiTreeComposer(SduiSchemaValidator validator) {
        this.validator = validator;
    }

    /**
     * Composes and validates an {@link SduiScreen} for the requested screen and runtime context.
     */
    public SduiScreen compose(CompositionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("CompositionInput cannot be null");
        }
        if (input.activePolicy() == null) {
            throw new IllegalArgumentException("activePolicy cannot be null");
        }
        if (input.regionRule() == null) {
            throw new IllegalArgumentException("regionRule cannot be null");
        }

        String screenName = input.screenName();
        SduiNode root = switch (screenName) {
            case "CONSENT_BANNER" -> composeConsentBanner(input);
            case "PREFERENCE_CENTER" -> composePreferenceCenter(input);
            case "PRIVACY_OVERVIEW" -> composePrivacyOverview(input);
            default -> throw new IllegalArgumentException("Unsupported ScreenName: " + screenName);
        };

        SduiScreen screen = new SduiScreen(
                SCHEMA_VERSION_1_0,
                screenName,
                input.activePolicy().versionLabel(),
                input.needsReconsent(),
                input.regionRule().regionCode(),
                input.regionRule().regimeName(),
                input.experimentContext(),
                root
        );

        return validator.validate(screen);
    }

    private SduiNode composeConsentBanner(CompositionInput input) {
        RegionRuleSpec rule = input.regionRule();
        PolicySpec policy = input.activePolicy();
        ExperimentVariantSpec variant = input.activeVariant();
        SduiExperimentContext exp = input.experimentContext();

        String regime = rule.regimeName();
        String regionCode = rule.regionCode();
        String consentModel = rule.consentModel();
        String bannerStyle = resolveBannerStyle(rule);
        boolean isOptIn = "OPT_IN".equals(consentModel) || "EU".equals(regionCode);
        boolean dismissible = !isOptIn && !"MODAL".equals(bannerStyle);
        boolean isMobile = "MOBILE".equalsIgnoreCase(input.deviceType());

        List<SduiNode> topRowChildren = new ArrayList<>();
        topRowChildren.add(SduiNode.leaf(
                "banner-regime-badge",
                "Badge",
                propsOf(
                        "label", resolveRegimeBadgeLabel(regime, consentModel),
                        "variant", regime
                )
        ));
        topRowChildren.add(SduiNode.leaf(
                "banner-policy-version-text",
                "Text",
                propsOf(
                        "content", exp != null
                                ? "Policy " + policy.versionLabel() + " · " + exp.variantKey()
                                : "Policy " + policy.versionLabel(),
                        "variant", "mono-xs",
                        "tone", "muted",
                        "weight", "medium"
                )
        ));

        SduiNode topRow = SduiNode.container(
                "banner-top-row",
                "Stack",
                propsOf(
                        "direction", "horizontal",
                        "gap", "sm",
                        "align", "between",
                        "wrap", true
                ),
                topRowChildren
        );

        String headline = resolveBannerHeadline(regime, variant);
        String body = resolveBannerBody(regime, variant);

        SduiNode header = SduiNode.leaf(
                "banner-header",
                "Header",
                propsOf(
                        "title", headline,
                        "subtitle", body,
                        "level", 2
                )
        );

        SduiNode policyAccordion = SduiNode.leaf(
                "banner-policy-summary",
                "PolicySummaryAccordion",
                propsOf(
                        "versionLabel", policy.versionLabel(),
                        "title", policy.title(),
                        "summary", policy.summary(),
                        "publishedAt", policy.publishedAt(),
                        "defaultExpanded", false
                )
        );

        String acceptLabel = (variant != null && nonBlank(variant.primaryCtaLabel()))
                ? variant.primaryCtaLabel()
                : "Accept All";

        boolean showRejectButton = rule.showRejectAll()
                || "OPT_IN".equals(consentModel)
                || "GDPR".equals(regime)
                || "CCPA".equals(regime);

        List<SduiNode> actionButtons = new ArrayList<>();
        actionButtons.add(SduiNode.leafWithActions(
                "btn-accept-all",
                "Button",
                propsOf(
                        "label", acceptLabel,
                        "variant", "primary",
                        "fullWidth", isMobile,
                        "testId", "sdui-accept-all"
                ),
                List.of(SduiAction.onClick(
                        "ACCEPT_ALL",
                        buildConsentActionPayload("BANNER", regionCode, exp)
                ))
        ));

        if (showRejectButton) {
            String rejectLabel = resolveRejectCtaLabel(regime, variant);
            actionButtons.add(SduiNode.leafWithActions(
                    "btn-reject-optional",
                    "Button",
                    propsOf(
                            "label", rejectLabel,
                            "variant", "secondary",
                            "fullWidth", isMobile,
                            "testId", "sdui-reject-optional"
                    ),
                    List.of(SduiAction.onClick(
                            "REJECT_OPTIONAL",
                            buildConsentActionPayload("BANNER", regionCode, exp)
                    ))
            ));
        }

        actionButtons.add(SduiNode.leafWithActions(
                "btn-manage-preferences",
                "Button",
                propsOf(
                        "label", "Manage Preferences",
                        "variant", "ghost",
                        "fullWidth", isMobile,
                        "testId", "sdui-open-preferences"
                ),
                List.of(SduiAction.onClick(
                        "OPEN_PREFERENCES",
                        propsOf("to", "/privacy/preferences")
                ))
        ));

        SduiNode actionBar = SduiNode.container(
                "banner-actions",
                "ActionBar",
                propsOf(
                        "layout", isMobile ? "stacked" : "symmetric-inline",
                        "sticky", false
                ),
                actionButtons
        );

        List<SduiNode> mainChildren = new ArrayList<>();
        mainChildren.add(topRow);
        if (input.needsReconsent() && input.hasConsented() && nonBlank(input.consentedPolicyVersion())) {
            mainChildren.add(SduiNode.leaf(
                    "banner-reconsent-callout",
                    "NoticeCallout",
                    propsOf(
                            "title", "Policy Updated to " + policy.versionLabel(),
                            "message", "Material updates were published since your prior consent under "
                                    + input.consentedPolicyVersion()
                                    + ". Please review and confirm your preferences.",
                            "tone", "warning"
                    )
            ));
        }
        mainChildren.add(header);
        mainChildren.add(policyAccordion);
        mainChildren.add(actionBar);

        SduiNode mainStack = SduiNode.container(
                "banner-main-stack",
                "Stack",
                propsOf("direction", "vertical", "gap", "md"),
                mainChildren
        );

        return SduiNode.container(
                "banner-shell",
                "BannerShell",
                propsOf(
                        "bannerStyle", bannerStyle,
                        "ariaLabel", resolveBannerAriaLabel(regime),
                        "dismissible", dismissible
                ),
                List.of(mainStack)
        );
    }

    private SduiNode composePreferenceCenter(CompositionInput input) {
        RegionRuleSpec rule = input.regionRule();
        PolicySpec policy = input.activePolicy();
        ExperimentVariantSpec variant = input.activeVariant();
        SduiExperimentContext exp = input.experimentContext();

        String regime = rule.regimeName();
        String regionCode = rule.regionCode();
        String consentModel = rule.consentModel();
        boolean isMobile = "MOBILE".equalsIgnoreCase(input.deviceType());
        String cardTone = (variant != null && "SUBTLE".equalsIgnoreCase(variant.layoutTone()))
                ? "subtle"
                : "surface";

        String headerTitle = (variant != null && nonBlank(variant.headline()))
                ? variant.headline()
                : "Granular Privacy & Consent Preferences";
        String headerSubtitle = (variant != null && nonBlank(variant.body()))
                ? variant.body()
                : resolvePreferenceCenterSubtitle(regime, consentModel, rule.retentionDays());

        List<SduiNode> headerCardItems = new ArrayList<>();
        headerCardItems.add(SduiNode.leaf(
                "pref-regime-badge",
                "Badge",
                propsOf(
                        "label", resolveRegimeBadgeLabel(regime, consentModel)
                                + " · " + rule.retentionDays() + "d Retention",
                        "variant", regime
                )
        ));
        headerCardItems.add(SduiNode.leaf(
                "pref-header",
                "Header",
                propsOf(
                        "eyebrow", "Data Processing Controls",
                        "title", headerTitle,
                        "subtitle", headerSubtitle,
                        "level", 1
                )
        ));

        if (input.needsReconsent()) {
            headerCardItems.add(SduiNode.leaf(
                    "pref-status-callout",
                    "NoticeCallout",
                    propsOf(
                            "title", input.hasConsented()
                                    ? "Re-Consent Required for Policy " + policy.versionLabel()
                                    : "Default Regional Protections Active (" + regime + ")",
                            "message", input.hasConsented()
                                    ? "Policy " + policy.versionLabel()
                                            + " requires fresh confirmation. Review each category below and save your preferences."
                                    : resolveFirstVisitCalloutMessage(regime, consentModel),
                            "tone", input.hasConsented() ? "warning" : "info"
                    )
            ));
        } else {
            headerCardItems.add(SduiNode.leaf(
                    "pref-status-callout",
                    "NoticeCallout",
                    propsOf(
                            "title", "Consent Receipt Verified (" + policy.versionLabel() + ")",
                            "message", "Your granular preferences are active and recorded in the SHA-256 Consent Ledger. Withdrawing consent is always one click.",
                            "tone", "success"
                    )
            ));
        }

        SduiNode headerCard = SduiNode.container(
                "pref-header-card",
                "Card",
                propsOf("padding", "default", "tone", cardTone),
                List.of(SduiNode.container(
                        "pref-header-stack",
                        "Stack",
                        propsOf("direction", "vertical", "gap", "sm"),
                        headerCardItems
                ))
        );

        List<SduiNode> purposeNodes = new ArrayList<>();
        purposeNodes.add(SduiNode.leaf(
                "pref-purposes-section-header",
                "Header",
                propsOf(
                        "title", "Consent Categories",
                        "subtitle", "Toggle individual purposes below. Strictly necessary security controls remain active.",
                        "level", 2
                )
        ));
        purposeNodes.add(SduiNode.leaf(
                "pref-purposes-divider",
                "Divider",
                propsOf("spacing", "sm")
        ));

        for (PurposeToggleSpec purpose : input.purposes()) {
            boolean effectiveChecked = resolveEffectiveToggleState(
                    purpose,
                    input.hasConsented(),
                    consentModel
            );
            boolean isRequired = purpose.isEssential();
            String legalBasis = isRequired ? "STRICTLY_NECESSARY" : purpose.legalBasis();
            String legalBasisLabel = resolveLegalBasisLabel(legalBasis, regime);
            String badgeLabel = isRequired
                    ? "Always Active"
                    : ("OPT_IN".equals(consentModel)
                            ? "Explicit Opt-In"
                            : ("OPT_OUT".equals(consentModel) ? "Opt-Out Control" : "Optional"));

            List<SduiAction> rowActions = isRequired
                    ? List.of()
                    : List.of(SduiAction.onToggle(
                            "TOGGLE_PURPOSE",
                            propsOf(
                                    "purposeId", purpose.purposeId(),
                                    "purposeSlug", purpose.slug()
                            )
                    ));

            purposeNodes.add(new SduiNode(
                    "purpose-row-" + purpose.slug(),
                    "PurposeToggleRow",
                    propsOf(
                            "purposeId", purpose.purposeId(),
                            "purposeSlug", purpose.slug(),
                            "title", purpose.name(),
                            "description", purpose.description(),
                            "legalBasis", legalBasis,
                            "legalBasisLabel", legalBasisLabel,
                            "checked", effectiveChecked,
                            "required", isRequired,
                            "badgeLabel", badgeLabel
                    ),
                    List.of(),
                    rowActions
            ));
        }

        purposeNodes.add(SduiNode.leaf(
                "pref-ledger-footnote",
                "Text",
                propsOf(
                        "content", "Every preference change generates an immutable SHA-256 receipt under Policy "
                                + policy.versionLabel() + ".",
                        "variant", "caption-xs",
                        "tone", "muted"
                )
        ));

        SduiNode purposesCard = SduiNode.container(
                "pref-purposes-card",
                "Card",
                propsOf("padding", "default", "tone", "surface"),
                List.of(SduiNode.container(
                        "pref-purposes-stack",
                        "Stack",
                        propsOf("direction", "vertical", "gap", "md"),
                        purposeNodes
                ))
        );

        SduiNode policyAccordion = SduiNode.leaf(
                "pref-policy-accordion",
                "PolicySummaryAccordion",
                propsOf(
                        "versionLabel", policy.versionLabel(),
                        "title", policy.title(),
                        "summary", policy.summary(),
                        "contentMarkdown", policy.contentMarkdown(),
                        "publishedAt", policy.publishedAt(),
                        "defaultExpanded", false
                )
        );

        boolean showRejectButton = rule.showRejectAll()
                || "OPT_IN".equals(consentModel)
                || "GDPR".equals(regime)
                || "CCPA".equals(regime);

        List<SduiNode> actionButtons = new ArrayList<>();
        actionButtons.add(SduiNode.leafWithActions(
                "pref-btn-save",
                "Button",
                propsOf(
                        "label", (variant != null && nonBlank(variant.primaryCtaLabel()))
                                ? variant.primaryCtaLabel()
                                : "Save Preferences",
                        "variant", "primary",
                        "fullWidth", isMobile,
                        "testId", "sdui-save-preferences"
                ),
                List.of(SduiAction.onClick(
                        "SUBMIT_CONSENT",
                        buildConsentActionPayload("PREFERENCE_CENTER", regionCode, exp)
                ))
        ));
        actionButtons.add(SduiNode.leafWithActions(
                "pref-btn-accept-all",
                "Button",
                propsOf(
                        "label", "Accept All",
                        "variant", "secondary",
                        "fullWidth", isMobile,
                        "testId", "sdui-pref-accept-all"
                ),
                List.of(SduiAction.onClick(
                        "ACCEPT_ALL",
                        buildConsentActionPayload("PREFERENCE_CENTER", regionCode, exp)
                ))
        ));
        if (showRejectButton) {
            actionButtons.add(SduiNode.leafWithActions(
                    "pref-btn-reject-optional",
                    "Button",
                    propsOf(
                            "label", resolveRejectCtaLabel(regime, variant),
                            "variant", "secondary",
                            "fullWidth", isMobile,
                            "testId", "sdui-pref-reject-optional"
                    ),
                    List.of(SduiAction.onClick(
                            "REJECT_OPTIONAL",
                            buildConsentActionPayload("PREFERENCE_CENTER", regionCode, exp)
                    ))
            ));
        }

        SduiNode actionBar = SduiNode.container(
                "pref-action-bar",
                "ActionBar",
                propsOf(
                        "layout", isMobile ? "stacked" : "symmetric-inline",
                        "sticky", true
                ),
                actionButtons
        );

        SduiNode rootStack = SduiNode.container(
                "pref-center-stack",
                "Stack",
                propsOf("direction", "vertical", "gap", "lg"),
                List.of(headerCard, purposesCard, policyAccordion, actionBar)
        );

        return SduiNode.container(
                "pref-center-container",
                "Container",
                propsOf("maxWidth", "portal", "padding", "none"),
                List.of(rootStack)
        );
    }

    private SduiNode composePrivacyOverview(CompositionInput input) {
        RegionRuleSpec rule = input.regionRule();
        PolicySpec policy = input.activePolicy();
        ExperimentVariantSpec variant = input.activeVariant();

        String regime = rule.regimeName();
        String consentModel = rule.consentModel();
        boolean isMobile = "MOBILE".equalsIgnoreCase(input.deviceType());

        int totalPurposes = input.purposes().size();
        int grantedPurposes = 0;
        for (PurposeToggleSpec purpose : input.purposes()) {
            if (resolveEffectiveToggleState(purpose, input.hasConsented(), consentModel)) {
                grantedPurposes++;
            }
        }

        String overviewTitle = (variant != null && nonBlank(variant.headline()))
                ? variant.headline()
                : "Your Privacy & Data Rights Overview";
        String overviewSubtitle = (variant != null && nonBlank(variant.body()))
                ? variant.body()
                : "Inspect your active consent posture, cryptographic proof ledger, and self-service GDPR/CCPA data portability and erasure controls.";

        List<SduiNode> heroItems = new ArrayList<>();
        heroItems.add(SduiNode.leaf(
                "overview-regime-badge",
                "Badge",
                propsOf(
                        "label", resolveRegimeBadgeLabel(regime, consentModel),
                        "variant", regime
                )
        ));
        heroItems.add(SduiNode.leaf(
                "overview-header",
                "Header",
                propsOf(
                        "eyebrow", "ConsentHub Transparency Portal",
                        "title", overviewTitle,
                        "subtitle", overviewSubtitle,
                        "level", 1
                )
        ));
        heroItems.add(SduiNode.leaf(
                "overview-status-callout",
                "NoticeCallout",
                propsOf(
                        "title", input.needsReconsent()
                                ? "Action Needed: Review Policy " + policy.versionLabel()
                                : "Privacy Posture Current (" + policy.versionLabel() + ")",
                        "message", input.needsReconsent()
                                ? "Your consent profile has not yet confirmed active Policy "
                                        + policy.versionLabel() + "."
                                : "Your regional protections (" + regime
                                        + ") and consent choices are active and cryptographically verifiable.",
                        "tone", input.needsReconsent() ? "warning" : "success"
                )
        ));

        SduiNode heroCard = SduiNode.container(
                "overview-hero-card",
                "Card",
                propsOf("padding", "default", "tone", "surface"),
                List.of(SduiNode.container(
                        "overview-hero-stack",
                        "Stack",
                        propsOf("direction", "vertical", "gap", "sm"),
                        heroItems
                ))
        );

        SduiNode statGrid = SduiNode.container(
                "overview-stat-grid",
                "StatGrid",
                propsOf("columns", 4),
                List.of(
                        SduiNode.leaf(
                                "stat-enabled-purposes",
                                "StatItem",
                                propsOf(
                                        "label", "Enabled Purposes",
                                        "value", grantedPurposes + " / " + totalPurposes,
                                        "sublabel", "Essential + active choices",
                                        "tone", "brand"
                                )
                        ),
                        SduiNode.leaf(
                                "stat-active-policy",
                                "StatItem",
                                propsOf(
                                        "label", "Policy Version",
                                        "value", policy.versionLabel(),
                                        "sublabel", input.needsReconsent() ? "Re-consent pending" : "Confirmed",
                                        "tone", input.needsReconsent() ? "warning" : "success"
                                )
                        ),
                        SduiNode.leaf(
                                "stat-regulatory-regime",
                                "StatItem",
                                propsOf(
                                        "label", "Active Regime",
                                        "value", regime,
                                        "sublabel", consentModel.replace('_', ' ')
                                                + " · " + rule.retentionDays() + "d",
                                        "tone", "default"
                                )
                        ),
                        SduiNode.leaf(
                                "stat-ledger-proofs",
                                "StatItem",
                                propsOf(
                                        "label", "Ledger Receipts",
                                        "value", String.valueOf(Math.max(0, input.historyEntriesCount())),
                                        "sublabel", input.pendingDsarCount() > 0
                                                ? input.pendingDsarCount() + " open DSAR ticket(s)"
                                                : "SHA-256 verifiable proofs",
                                        "tone", "success"
                                )
                        )
                )
        );

        SduiNode policyAccordion = SduiNode.leaf(
                "overview-policy-summary",
                "PolicySummaryAccordion",
                propsOf(
                        "versionLabel", policy.versionLabel(),
                        "title", policy.title(),
                        "summary", policy.summary(),
                        "publishedAt", policy.publishedAt(),
                        "defaultExpanded", false
                )
        );

        SduiNode navActionsCard = SduiNode.container(
                "overview-actions-card",
                "Card",
                propsOf("padding", "compact", "tone", "subtle"),
                List.of(SduiNode.container(
                        "overview-action-bar",
                        "ActionBar",
                        propsOf(
                                "layout", isMobile ? "stacked" : "symmetric-inline",
                                "sticky", false
                        ),
                        List.of(
                                SduiNode.leafWithActions(
                                        "overview-btn-preferences",
                                        "Button",
                                        propsOf(
                                                "label", "Configure Preferences",
                                                "variant", "primary",
                                                "fullWidth", isMobile,
                                                "testId", "sdui-overview-preferences"
                                        ),
                                        List.of(SduiAction.onClick(
                                                "NAVIGATE",
                                                propsOf("to", "/privacy/preferences")
                                        ))
                                ),
                                SduiNode.leafWithActions(
                                        "overview-btn-history",
                                        "Button",
                                        propsOf(
                                                "label", "Inspect Consent Ledger",
                                                "variant", "secondary",
                                                "fullWidth", isMobile,
                                                "testId", "sdui-overview-ledger"
                                        ),
                                        List.of(SduiAction.onClick(
                                                "NAVIGATE",
                                                propsOf("to", "/privacy/history")
                                        ))
                                ),
                                SduiNode.leafWithActions(
                                        "overview-btn-dsar",
                                        "Button",
                                        propsOf(
                                                "label", "Exercise Data Rights (DSAR)",
                                                "variant", "secondary",
                                                "fullWidth", isMobile,
                                                "testId", "sdui-overview-dsar"
                                        ),
                                        List.of(SduiAction.onClick(
                                                "NAVIGATE",
                                                propsOf("to", "/privacy/requests")
                                        ))
                                )
                        )
                ))
        );

        SduiNode mainStack = SduiNode.container(
                "overview-main-stack",
                "Stack",
                propsOf("direction", "vertical", "gap", "lg"),
                List.of(heroCard, statGrid, policyAccordion, navActionsCard)
        );

        return SduiNode.container(
                "overview-container",
                "Container",
                propsOf("maxWidth", "portal", "padding", "none"),
                List.of(mainStack)
        );
    }

    /**
     * Resolves effective boolean toggle state enforcing regulatory privacy-by-default rules:
     * <ul>
     *   <li>Essential purposes are strictly {@code true}.</li>
     *   <li>When {@code hasConsented} is {@code false}, {@code OPT_IN} forces optional purposes
     *       to {@code false}, while {@code OPT_OUT} defaults optional purposes to {@code true}.</li>
     *   <li>When {@code hasConsented} is {@code true}, returns the persisted {@code granted} choice.</li>
     * </ul>
     */
    public static boolean resolveEffectiveToggleState(
            PurposeToggleSpec purpose,
            boolean hasConsented,
            String consentModel
    ) {
        if (purpose.isEssential()) {
            return true;
        }
        if (!hasConsented) {
            if ("OPT_IN".equals(consentModel)) {
                return false;
            }
            if ("OPT_OUT".equals(consentModel)) {
                return true;
            }
        }
        return purpose.granted();
    }

    private static String resolveBannerStyle(RegionRuleSpec rule) {
        String configured = rule.bannerStyle();
        if (configured != null && SduiSchemaValidator.ALLOWED_SCREEN_NAMES != null
                && ("MODAL".equals(configured) || "BANNER".equals(configured) || "COMPACT".equals(configured))) {
            return configured;
        }
        return switch (rule.regionCode()) {
            case "EU" -> "MODAL";
            case "US_CA" -> "BANNER";
            default -> "COMPACT";
        };
    }

    private static String resolveRegimeBadgeLabel(String regime, String consentModel) {
        return switch (regime) {
            case "GDPR" -> "GDPR · EU Strict Opt-In";
            case "CCPA" -> "CCPA · California Opt-Out";
            default -> "OPT_IN".equals(consentModel)
                    ? "Global · Explicit Opt-In"
                    : "Global Privacy Standard";
        };
    }

    private static String resolveBannerAriaLabel(String regime) {
        return switch (regime) {
            case "GDPR" -> "GDPR Privacy & Data Processing Consent";
            case "CCPA" -> "California CCPA Privacy & Opt-Out Notice";
            default -> "Privacy & Cookie Consent Preferences";
        };
    }

    private static String resolveBannerHeadline(String regime, ExperimentVariantSpec variant) {
        if (variant != null && nonBlank(variant.headline())) {
            return variant.headline();
        }
        return switch (regime) {
            case "GDPR" -> "Your Privacy Choices";
            case "CCPA" -> "California Privacy & Right to Opt-Out";
            default -> "Transparent Privacy Controls";
        };
    }

    private static String resolveBannerBody(String regime, ExperimentVariantSpec variant) {
        if (variant != null && nonBlank(variant.body())) {
            return variant.body();
        }
        return switch (regime) {
            case "GDPR" -> "We use strictly necessary cookies to operate ConsentHub securely, and optional analytics, personalization, or marketing only with your explicit prior opt-in consent.";
            case "CCPA" -> "Under the California Consumer Privacy Act (CCPA/CPRA), you may opt out of cross-context targeted advertising and non-essential data sharing in one click.";
            default -> "Choose which optional analytics, personalization, and communication features you want enabled. Essential security protections remain active.";
        };
    }

    private static String resolveRejectCtaLabel(String regime, ExperimentVariantSpec variant) {
        if (variant != null && nonBlank(variant.secondaryCtaLabel())) {
            if ("CCPA".equals(regime) && "Reject Optional".equalsIgnoreCase(variant.secondaryCtaLabel().trim())) {
                return "Do Not Sell or Share";
            }
            return variant.secondaryCtaLabel();
        }
        return "CCPA".equals(regime) ? "Do Not Sell or Share" : "Reject Optional";
    }

    private static String resolvePreferenceCenterSubtitle(String regime, String consentModel, int retentionDays) {
        return switch (regime) {
            case "GDPR" -> "Under GDPR & ePrivacy strict opt-in rules, optional categories remain off unless explicitly enabled. Consent records are retained for "
                    + retentionDays + " days.";
            case "CCPA" -> "Under CCPA/CPRA (" + consentModel.replace('_', '-')
                    + "), you can exercise your right to opt out of cross-context targeted ads and optional processing at any time.";
            default -> "Customize your data processing preferences per category. Changes take effect immediately across sessions.";
        };
    }

    private static String resolveFirstVisitCalloutMessage(String regime, String consentModel) {
        if ("OPT_IN".equals(consentModel) || "GDPR".equals(regime)) {
            return "No optional processing is active. Non-essential purposes remain disabled until you explicitly opt in.";
        }
        if ("OPT_OUT".equals(consentModel) || "CCPA".equals(regime)) {
            return "California Opt-Out protections apply: click 'Do Not Sell or Share' or toggle categories off to opt out immediately.";
        }
        return "Review our default privacy settings below and save your customized choices at any time.";
    }

    private static String resolveLegalBasisLabel(String legalBasis, String regime) {
        return switch (legalBasis) {
            case "STRICTLY_NECESSARY" -> "Strictly Necessary · Essential Security";
            case "LEGITIMATE_INTEREST_OPT_OUT" -> "CCPA".equals(regime)
                    ? "Right to Opt-Out of Sale/Sharing (CCPA §1798.120)"
                    : "Legitimate Interest · 1-Click Opt-Out";
            default -> "GDPR".equals(regime)
                    ? "Explicit Prior Consent (GDPR Art. 6(1)(a))"
                    : "Explicit User Choice";
        };
    }

    private static Map<String, Object> buildConsentActionPayload(
            String source,
            String regionCode,
            SduiExperimentContext experiment
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", source);
        payload.put("region", regionCode);
        if (experiment != null) {
            payload.put("experimentId", experiment.experimentId());
            payload.put("variantKey", experiment.variantKey());
        }
        return payload;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
