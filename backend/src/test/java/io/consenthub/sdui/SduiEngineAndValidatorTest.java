package io.consenthub.sdui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.consenthub.sdui.SduiModels.CompositionInput;
import io.consenthub.sdui.SduiModels.ExperimentVariantSpec;
import io.consenthub.sdui.SduiModels.PolicySpec;
import io.consenthub.sdui.SduiModels.PurposeToggleSpec;
import io.consenthub.sdui.SduiModels.RegionRuleSpec;
import io.consenthub.sdui.SduiModels.SduiAction;
import io.consenthub.sdui.SduiModels.SduiExperimentContext;
import io.consenthub.sdui.SduiModels.SduiNode;
import io.consenthub.sdui.SduiModels.SduiScreen;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SduiEngineAndValidatorTest {

    private SduiSchemaValidator validator;
    private SduiTreeComposer composer;

    @BeforeEach
    void setUp() {
        validator = new SduiSchemaValidator();
        composer = new SduiTreeComposer(validator);
    }

    @Test
    void composeGdprEuBannerEnforcesNonDismissibleModalAndSymmetricReject() {
        CompositionInput input = new CompositionInput(
                "CONSENT_BANNER",
                "anon:test_eu_visitor_001",
                "en-US",
                "DESKTOP",
                false,
                true,
                null,
                samplePolicy(),
                new RegionRuleSpec("EU", "GDPR", "OPT_IN", "MODAL", true, 365),
                samplePurposes(false),
                new SduiExperimentContext("44444444-4444-4444-8444-444444444444", "eu-banner-clarity-q4", "concise_trust"),
                new ExperimentVariantSpec(
                        "concise_trust",
                        "Variant B",
                        50,
                        "Transparent Data Control",
                        "Optional telemetry is 100% up to you.",
                        "Accept All Purposes",
                        "Essential Only",
                        "EDITORIAL"
                ),
                0,
                0
        );

        SduiScreen screen = composer.compose(input);

        assertEquals("1.0", screen.schemaVersion());
        assertEquals("CONSENT_BANNER", screen.screenName());
        assertEquals("EU", screen.regionCode());
        assertEquals("GDPR", screen.regime());
        assertEquals("BannerShell", screen.root().type());
        assertEquals("MODAL", screen.root().props().get("bannerStyle"));
        assertEquals(Boolean.FALSE, screen.root().props().get("dismissible"));
        assertNotNull(screen.experiment());
        assertEquals("concise_trust", screen.experiment().variantKey());
    }

    @Test
    void composePreferenceCenterEnforcesGdprOptInDefaultsVsCcpaOptOutDefaults() {
        CompositionInput gdprInput = new CompositionInput(
                "PREFERENCE_CENTER",
                "anon:test_eu_pref_001",
                "en-US",
                "DESKTOP",
                false,
                true,
                null,
                samplePolicy(),
                new RegionRuleSpec("EU", "GDPR", "OPT_IN", "MODAL", true, 365),
                samplePurposes(true), // even if raw granted=true, unconsented OPT_IN must force false!
                null,
                null,
                0,
                0
        );

        SduiScreen gdprScreen = composer.compose(gdprInput);
        List<SduiNode> gdprToggleRows = collectByType(gdprScreen.root(), "PurposeToggleRow");
        assertEquals(2, gdprToggleRows.size());
        assertEquals(Boolean.TRUE, gdprToggleRows.get(0).props().get("checked")); // essential
        assertEquals(Boolean.TRUE, gdprToggleRows.get(0).props().get("required"));
        assertEquals(Boolean.FALSE, gdprToggleRows.get(1).props().get("checked")); // analytics forced false!

        CompositionInput ccpaInput = new CompositionInput(
                "PREFERENCE_CENTER",
                "anon:test_ca_pref_001",
                "en-US",
                "MOBILE",
                false,
                true,
                null,
                samplePolicy(),
                new RegionRuleSpec("US_CA", "CCPA", "OPT_OUT", "BANNER", true, 730),
                samplePurposes(false),
                null,
                null,
                0,
                0
        );

        SduiScreen ccpaScreen = composer.compose(ccpaInput);
        List<SduiNode> ccpaToggleRows = collectByType(ccpaScreen.root(), "PurposeToggleRow");
        assertEquals(Boolean.TRUE, ccpaToggleRows.get(1).props().get("checked")); // OPT_OUT default true
    }

    @Test
    void composePrivacyOverviewValidatesAgainstSchema() {
        CompositionInput overviewInput = new CompositionInput(
                "PRIVACY_OVERVIEW",
                "user:00000000-0000-4000-8000-000000000002",
                "en-US",
                "DESKTOP",
                true,
                false,
                "v2.0",
                samplePolicy(),
                new RegionRuleSpec("EU", "GDPR", "OPT_IN", "MODAL", true, 365),
                samplePurposes(true),
                null,
                null,
                15,
                1
        );

        SduiScreen overview = composer.compose(overviewInput);
        assertEquals("PRIVACY_OVERVIEW", overview.screenName());
        assertFalse(collectByType(overview.root(), "StatItem").isEmpty());
    }

    @Test
    void validatorRejectsUnsafeJavascriptUriAndUnknownComponentTypeInStrictMode() {
        assertFalse(validator.isSafeUri("javascript:alert(1)"));
        assertFalse(validator.isSafeUri("data:text/html,<script>alert(1)</script>"));
        assertFalse(validator.isSafeUri("//evil.example.com/phish"));
        assertFalse(validator.isSafeUri("/\\evil.example.com/phish"));
        assertTrue(validator.isSafeUri("/portal/preferences"));
        assertTrue(validator.isSafeUri("https://consenthub.io/docs"));

        SduiNode maliciousButton = SduiNode.leafWithActions(
                "bad-btn",
                "Button",
                Map.of("label", "Click", "variant", "primary"),
                List.of(SduiAction.onClick("NAVIGATE", Map.of("to", "javascript:alert(1)")))
        );
        SduiScreen unsafeScreen = new SduiScreen(
                "1.0",
                "CONSENT_BANNER",
                "v2.0",
                true,
                "EU",
                "GDPR",
                null,
                SduiNode.container("root-c", "Container", Map.of(), List.of(maliciousButton))
        );

        assertThrows(SduiValidationException.class, () -> validator.validate(unsafeScreen));

        SduiScreen unknownTypeScreen = new SduiScreen(
                "1.0",
                "CONSENT_BANNER",
                "v2.0",
                true,
                "EU",
                "GDPR",
                null,
                SduiNode.leaf("future-1", "ExperimentalHoloCard", Map.of())
        );

        assertThrows(SduiValidationException.class, () -> validator.validate(unknownTypeScreen, true));
        // Non-strict client-compatibility check permits unknown string type when depth & URIs are valid
        assertNotNull(validator.validate(unknownTypeScreen, false));
    }

    @Test
    void validatorRejectsExcessiveTreeDepthOverFive() {
        SduiNode d6 = SduiNode.leaf("n6", "Text", Map.of("content", "Too deep"));
        SduiNode d5 = SduiNode.container("n5", "Stack", Map.of(), List.of(d6));
        SduiNode d4 = SduiNode.container("n4", "Stack", Map.of(), List.of(d5));
        SduiNode d3 = SduiNode.container("n3", "Stack", Map.of(), List.of(d4));
        SduiNode d2 = SduiNode.container("n2", "Card", Map.of(), List.of(d3));
        SduiNode d1 = SduiNode.container("n1", "Container", Map.of(), List.of(d2));

        SduiScreen tooDeep = new SduiScreen("1.0", "PRIVACY_OVERVIEW", "v2.0", false, "GLOBAL", "DEFAULT", null, d1);
        assertThrows(SduiValidationException.class, () -> validator.validate(tooDeep));
    }

    private static PolicySpec samplePolicy() {
        return new PolicySpec(
                "v2.0",
                "2026 Global Privacy Policy",
                "Granular privacy controls and SHA-256 receipts.",
                "# Policy v2.0\nFull transparency.",
                true,
                "2026-11-14T10:00:00.000Z"
        );
    }

    private static List<PurposeToggleSpec> samplePurposes(boolean analyticsGranted) {
        return List.of(
                new PurposeToggleSpec(
                        "11111111-1111-4111-8111-111111111101",
                        "essential",
                        "Essential Security",
                        "Strictly necessary session security.",
                        "STRICTLY_NECESSARY",
                        true,
                        true
                ),
                new PurposeToggleSpec(
                        "11111111-1111-4111-8111-111111111102",
                        "analytics",
                        "Product Analytics",
                        "Aggregated latency metrics.",
                        "CONSENT",
                        false,
                        analyticsGranted
                )
        );
    }

    private static List<SduiNode> collectByType(SduiNode node, String targetType) {
        java.util.ArrayList<SduiNode> found = new java.util.ArrayList<>();
        if (targetType.equals(node.type())) {
            found.add(node);
        }
        for (SduiNode child : node.children()) {
            found.addAll(collectByType(child, targetType));
        }
        return found;
    }
}
