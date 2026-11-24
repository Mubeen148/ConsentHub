package io.consenthub.sdui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable Java 21 Server-Driven UI (SDUI) domain records matching
 * {@code docs/contracts/sdui-schema.json} (schemaVersion 1.0) and
 * {@code docs/contracts/graphql-schema.graphqls}, plus JSON/Map conversion utilities.
 */
public final class SduiModels {

    public static final String SCHEMA_VERSION_1_0 = "1.0";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SduiModels() {
    }

    /**
     * Top-level versioned SDUI response envelope returned by {@code Query.screen(name, context)}
     * and embedded in {@code GET /api/v1/sdk/config}.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SduiScreen(
            String schemaVersion,
            String screenName,
            String policyVersion,
            boolean needsReconsent,
            String regionCode,
            String regime,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            SduiExperimentContext experiment,
            SduiNode root
    ) {
        public Map<String, Object> toMap() {
            return SduiModels.toMap(this);
        }
    }

    /**
     * Sticky A/B experiment metadata attached when an experiment applies to the screen.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SduiExperimentContext(
            String experimentId,
            String experimentSlug,
            String variantKey
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("experimentId", experimentId);
            map.put("experimentSlug", experimentSlug);
            map.put("variantKey", variantKey);
            return Collections.unmodifiableMap(map);
        }
    }

    /**
     * Recursive component node in an SDUI tree (capped at depth &lt;= 5 by {@link SduiSchemaValidator}).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SduiNode(
            String id,
            String type,
            Map<String, Object> props,
            List<SduiNode> children,
            List<SduiAction> actions
    ) {
        public SduiNode {
            props = props == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(props));
            children = children == null
                    ? List.of()
                    : List.copyOf(children);
            actions = actions == null
                    ? List.of()
                    : List.copyOf(actions);
        }

        public static SduiNode leaf(String id, String type, Map<String, Object> props) {
            return new SduiNode(id, type, props, List.of(), List.of());
        }

        public static SduiNode leafWithActions(
                String id,
                String type,
                Map<String, Object> props,
                List<SduiAction> actions
        ) {
            return new SduiNode(id, type, props, List.of(), actions);
        }

        public static SduiNode container(
                String id,
                String type,
                Map<String, Object> props,
                List<SduiNode> children
        ) {
            return new SduiNode(id, type, props, children, List.of());
        }
    }

    /**
     * Declarative interaction binding attached to an {@link SduiNode}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SduiAction(
            String trigger,
            String type,
            Map<String, Object> payload
    ) {
        public SduiAction {
            payload = payload == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        }

        public static SduiAction onClick(String type, Map<String, Object> payload) {
            return new SduiAction("onClick", type, payload);
        }

        public static SduiAction onToggle(String type, Map<String, Object> payload) {
            return new SduiAction("onToggle", type, payload);
        }
    }

    /**
     * Experiment variant configuration matching {@code experiments.variants_json} and
     * {@code ExperimentVariantMetric} / {@code ExperimentVariantInput}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExperimentVariantSpec(
            String key,
            String name,
            int weight,
            String headline,
            String body,
            String primaryCtaLabel,
            String secondaryCtaLabel,
            String layoutTone
    ) {
        public ExperimentVariantSpec {
            layoutTone = (layoutTone == null || layoutTone.isBlank()) ? "STANDARD" : layoutTone;
        }
    }

    /**
     * Deterministic bucketing outcome from {@link ExperimentBucketer}.
     * When {@code previewOverride} is true, callers MUST NOT persist an assignment row.
     */
    public record BucketResult(
            ExperimentVariantSpec variant,
            int bucket,
            boolean previewOverride
    ) {
    }

    /**
     * Purpose state input for SDUI screen composition.
     */
    public record PurposeToggleSpec(
            String purposeId,
            String slug,
            String name,
            String description,
            String legalBasis,
            boolean isEssential,
            boolean granted
    ) {
    }

    /**
     * Active privacy policy version snapshot for SDUI screen composition.
     */
    public record PolicySpec(
            String versionLabel,
            String title,
            String summary,
            String contentMarkdown,
            boolean requiresReconsent,
            String publishedAt
    ) {
    }

    /**
     * Resolved regional regulatory rule snapshot for SDUI screen composition.
     */
    public record RegionRuleSpec(
            String regionCode,
            String regimeName,
            String consentModel,
            String bannerStyle,
            boolean showRejectAll,
            int retentionDays
    ) {
    }

    /**
     * Complete domain context passed to {@link SduiTreeComposer} to compose any of the
     * 3 target screens ({@code CONSENT_BANNER}, {@code PREFERENCE_CENTER}, {@code PRIVACY_OVERVIEW}).
     */
    public record CompositionInput(
            String screenName,
            String subjectKey,
            String locale,
            String deviceType,
            boolean hasConsented,
            boolean needsReconsent,
            String consentedPolicyVersion,
            PolicySpec activePolicy,
            RegionRuleSpec regionRule,
            List<PurposeToggleSpec> purposes,
            SduiExperimentContext experimentContext,
            ExperimentVariantSpec activeVariant,
            int historyEntriesCount,
            int pendingDsarCount
    ) {
        public CompositionInput {
            locale = (locale == null || locale.isBlank()) ? "en-US" : locale;
            deviceType = (deviceType == null || deviceType.isBlank()) ? "DESKTOP" : deviceType.toUpperCase();
            purposes = purposes == null ? List.of() : List.copyOf(purposes);
        }
    }

    /**
     * Builds an immutable insertion-ordered map from alternating key/value pairs, skipping
     * entries whose value is {@code null}.
     */
    public static Map<String, Object> propsOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("propsOf requires an even number of key/value arguments.");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put(key, value);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Converts an {@link SduiScreen} into a pure nested {@code Map<String, Object>} matching
     * {@code sdui-schema.json} (preserving explicit {@code "experiment": null} when absent).
     */
    public static Map<String, Object> toMap(SduiScreen screen) {
        if (screen == null) {
            throw new IllegalArgumentException("SduiScreen cannot be null.");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", screen.schemaVersion());
        map.put("screenName", screen.screenName());
        map.put("policyVersion", screen.policyVersion());
        map.put("needsReconsent", screen.needsReconsent());
        map.put("regionCode", screen.regionCode());
        map.put("regime", screen.regime());
        map.put("experiment", screen.experiment() != null ? screen.experiment().toMap() : null);
        map.put("root", screen.root() != null ? nodeToMap(screen.root()) : null);
        return Collections.unmodifiableMap(map);
    }

    public static Map<String, Object> nodeToMap(SduiNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.id());
        map.put("type", node.type());
        map.put("props", new LinkedHashMap<>(node.props()));
        List<Map<String, Object>> childMaps = new ArrayList<>(node.children().size());
        for (SduiNode child : node.children()) {
            childMaps.add(nodeToMap(child));
        }
        map.put("children", Collections.unmodifiableList(childMaps));
        List<Map<String, Object>> actionMaps = new ArrayList<>(node.actions().size());
        for (SduiAction action : node.actions()) {
            actionMaps.add(actionToMap(action));
        }
        map.put("actions", Collections.unmodifiableList(actionMaps));
        return Collections.unmodifiableMap(map);
    }

    public static Map<String, Object> actionToMap(SduiAction action) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("trigger", action.trigger());
        map.put("type", action.type());
        map.put("payload", action.payload() == null ? Collections.emptyMap() : new LinkedHashMap<>(action.payload()));
        return Collections.unmodifiableMap(map);
    }

    public static String toJson(SduiScreen screen) {
        try {
            return MAPPER.writeValueAsString(toMap(screen));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize SduiScreen to JSON", ex);
        }
    }

    public static SduiScreen fromJson(String json) {
        try {
            return MAPPER.readValue(json, SduiScreen.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid SduiScreen JSON payload", ex);
        }
    }

    public static SduiScreen fromMap(Map<String, Object> map) {
        return MAPPER.convertValue(map, SduiScreen.class);
    }

    public static List<ExperimentVariantSpec> parseVariantsJson(String variantsJson) {
        if (variantsJson == null || variantsJson.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(variantsJson, new TypeReference<List<ExperimentVariantSpec>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid experiment variants_json payload", ex);
        }
    }
}
