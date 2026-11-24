package io.consenthub.sdui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.consenthub.sdui.SduiModels.SduiAction;
import io.consenthub.sdui.SduiModels.SduiExperimentContext;
import io.consenthub.sdui.SduiModels.SduiNode;
import io.consenthub.sdui.SduiModels.SduiScreen;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Server-side structural, prop-catalog, depth, and security validator enforcing
 * {@code classpath:sdui/sdui-schema.json} and {@code SEC-G03} on every outgoing
 * {@link SduiScreen} before returning it over GraphQL or REST.
 */
@Component
public class SduiSchemaValidator {

    public static final String EXPECTED_SCHEMA_VERSION = "1.0";
    public static final int MAX_TREE_DEPTH = 5;
    public static final String SCHEMA_CLASSPATH_LOCATION = "/sdui/sdui-schema.json";

    public static final Set<String> ALLOWED_SCREEN_NAMES = Set.of(
            "CONSENT_BANNER",
            "PREFERENCE_CENTER",
            "PRIVACY_OVERVIEW"
    );

    public static final Set<String> ALLOWED_REGION_CODES = Set.of(
            "EU",
            "US_CA",
            "GLOBAL"
    );

    public static final Set<String> ALLOWED_REGIMES = Set.of(
            "GDPR",
            "CCPA",
            "DEFAULT"
    );

    public static final Set<String> CANONICAL_COMPONENT_TYPES = Set.of(
            "Container",
            "Card",
            "Stack",
            "BannerShell",
            "Header",
            "Text",
            "Badge",
            "Divider",
            "PurposeToggleRow",
            "PolicySummaryAccordion",
            "ActionBar",
            "Button",
            "NoticeCallout",
            "StatGrid",
            "StatItem"
    );

    public static final Set<String> ALLOWED_ACTION_TRIGGERS = Set.of(
            "onClick",
            "onToggle"
    );

    public static final Set<String> ALLOWED_ACTION_TYPES = Set.of(
            "SUBMIT_CONSENT",
            "ACCEPT_ALL",
            "REJECT_OPTIONAL",
            "TOGGLE_PURPOSE",
            "NAVIGATE",
            "OPEN_PREFERENCES",
            "DISMISS"
    );

    /**
     * Strict safe URI pattern (SEC-G03): permits only single-slash app-relative paths
     * ({@code /...}, never protocol-relative {@code //...}) or {@code https://} URLs,
     * rejecting {@code javascript:}, {@code data:}, {@code vbscript:}, {@code file:}, and {@code //}.
     */
    private static final Pattern SAFE_URI_PATTERN =
            Pattern.compile("^(\\/(?![\\/\\\\])[^\\s\\\\]*|https:\\/\\/[^\\s\\\\]+)$");

    private static final Set<String> URI_PROP_KEYS = Set.of("href", "to", "url");

    private static final Set<String> CONTAINER_MAX_WIDTHS = Set.of("portal", "admin", "narrow", "full");
    private static final Set<String> CONTAINER_PADDINGS = Set.of("none", "sm", "md", "lg");
    private static final Set<String> CARD_PADDINGS = Set.of("compact", "default", "flush");
    private static final Set<String> CARD_TONES = Set.of("surface", "subtle");
    private static final Set<String> STACK_DIRECTIONS = Set.of("vertical", "horizontal");
    private static final Set<String> STACK_GAPS = Set.of("xs", "sm", "md", "lg", "xl");
    private static final Set<String> STACK_ALIGNS = Set.of("start", "center", "end", "stretch", "between");
    private static final Set<String> BANNER_STYLES = Set.of("MODAL", "BANNER", "COMPACT");
    private static final Set<Integer> HEADER_LEVELS = Set.of(1, 2, 3);
    private static final Set<String> TEXT_VARIANTS = Set.of("body-base", "body-sm", "caption-xs", "mono-xs");
    private static final Set<String> TEXT_TONES = Set.of("primary", "secondary", "muted");
    private static final Set<String> TEXT_WEIGHTS = Set.of("regular", "medium", "semibold");
    private static final Set<String> BADGE_VARIANTS = Set.of(
            "GDPR", "CCPA", "DEFAULT", "ESSENTIAL", "SUCCESS", "WARNING", "DANGER"
    );
    private static final Set<String> DIVIDER_SPACINGS = Set.of("sm", "md", "lg");
    private static final Set<String> LEGAL_BASES = Set.of(
            "STRICTLY_NECESSARY", "CONSENT", "LEGITIMATE_INTEREST_OPT_OUT"
    );
    private static final Set<String> ACTION_BAR_LAYOUTS = Set.of("symmetric-inline", "stacked", "space-between");
    private static final Set<String> BUTTON_VARIANTS = Set.of("primary", "secondary", "ghost", "destructive");
    private static final Set<String> NOTICE_TONES = Set.of("info", "success", "warning", "danger");
    private static final Set<Integer> STAT_GRID_COLUMNS = Set.of(2, 3, 4);
    private static final Set<String> STAT_ITEM_TONES = Set.of("default", "success", "warning", "brand");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchema compiledJsonSchema;

    public SduiSchemaValidator() {
        this.compiledJsonSchema = loadAndVerifySchemaResource();
    }

    /**
     * Validates an {@link SduiScreen} in strict mode (canonical 15-component allowlist enforced)
     * and returns the screen if valid, or throws {@link SduiValidationException}.
     */
    public SduiScreen validate(SduiScreen screen) {
        return validate(screen, true);
    }

    /**
     * Validates an {@link SduiScreen} against {@code sdui-schema.json}.
     *
     * @param screen               composed SDUI screen envelope
     * @param strictComponentTypes when true, enforces {@link #CANONICAL_COMPONENT_TYPES}
     * @return validated screen
     */
    public SduiScreen validate(SduiScreen screen, boolean strictComponentTypes) {
        if (screen == null) {
            throw new SduiValidationException("SDUI screen cannot be null");
        }

        List<String> violations = new ArrayList<>();

        if (!EXPECTED_SCHEMA_VERSION.equals(screen.schemaVersion())) {
            violations.add("schemaVersion must equal '1.0' (got '" + screen.schemaVersion() + "')");
        }

        if (screen.screenName() == null || !ALLOWED_SCREEN_NAMES.contains(screen.screenName())) {
            violations.add("screenName must be one of " + ALLOWED_SCREEN_NAMES + " (got '" + screen.screenName() + "')");
        }

        if (isBlank(screen.policyVersion()) || screen.policyVersion().length() > 32) {
            violations.add("policyVersion must be a non-empty string of length 1..32");
        }

        if (screen.regionCode() == null || !ALLOWED_REGION_CODES.contains(screen.regionCode())) {
            violations.add("regionCode must be one of " + ALLOWED_REGION_CODES + " (got '" + screen.regionCode() + "')");
        }

        if (screen.regime() == null || !ALLOWED_REGIMES.contains(screen.regime())) {
            violations.add("regime must be one of " + ALLOWED_REGIMES + " (got '" + screen.regime() + "')");
        }

        SduiExperimentContext exp = screen.experiment();
        if (exp != null) {
            if (isBlank(exp.experimentId())) {
                violations.add("experiment.experimentId must be non-blank when experiment is present");
            }
            if (isBlank(exp.experimentSlug())) {
                violations.add("experiment.experimentSlug must be non-blank when experiment is present");
            }
            if (isBlank(exp.variantKey())) {
                violations.add("experiment.variantKey must be non-blank when experiment is present");
            }
        }

        if (screen.root() == null) {
            violations.add("root SduiNode cannot be null");
        } else {
            Set<String> seenNodeIds = new HashSet<>();
            validateNode(screen.root(), 1, strictComponentTypes, seenNodeIds, "root", violations);
        }

        if (violations.isEmpty()) {
            JsonNode screenTree = objectMapper.valueToTree(SduiModels.toMap(screen));
            Set<ValidationMessage> schemaMessages = compiledJsonSchema.validate(screenTree);
            for (ValidationMessage msg : schemaMessages) {
                violations.add("json-schema: " + msg.getMessage());
            }
        }

        if (!violations.isEmpty()) {
            throw new SduiValidationException("SDUI schema validation failed", violations);
        }

        return screen;
    }

    /**
     * Checks whether a URI string is safe for client navigation ({@code ^(\/(?!\/)|https:\/\/)}).
     */
    public boolean isSafeUri(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        for (int i = 0; i < uri.length(); i++) {
            char ch = uri.charAt(i);
            if (Character.isISOControl(ch)) {
                return false;
            }
        }
        return SAFE_URI_PATTERN.matcher(uri).matches();
    }

    private void validateNode(
            SduiNode node,
            int depth,
            boolean strictComponentTypes,
            Set<String> seenNodeIds,
            String path,
            List<String> violations
    ) {
        if (node == null) {
            violations.add(path + ": SduiNode cannot be null");
            return;
        }

        if (depth > MAX_TREE_DEPTH) {
            violations.add(path + ": tree depth " + depth + " exceeds maximum allowed depth of " + MAX_TREE_DEPTH);
            return;
        }

        if (isBlank(node.id()) || node.id().length() > 120) {
            violations.add(path + ".id must be a non-blank string of length 1..120");
        } else if (!seenNodeIds.add(node.id())) {
            violations.add(path + ".id '" + node.id() + "' is duplicated within the screen tree");
        }

        if (isBlank(node.type()) || node.type().length() > 64) {
            violations.add(path + ".type must be a non-blank string of length 1..64");
        } else if (strictComponentTypes && !CANONICAL_COMPONENT_TYPES.contains(node.type())) {
            violations.add(path + ".type '" + node.type() + "' is not in CanonicalComponentType allowlist");
        }

        Map<String, Object> props = node.props();
        if (props == null) {
            violations.add(path + ".props cannot be null");
        } else {
            validateUriEntries(props, path + ".props", violations);
            if (node.type() != null && CANONICAL_COMPONENT_TYPES.contains(node.type())) {
                validateComponentProps(node.type(), props, path + ".props", violations);
            }
        }

        List<SduiAction> actions = node.actions();
        if (actions == null) {
            violations.add(path + ".actions cannot be null");
        } else {
            for (int i = 0; i < actions.size(); i++) {
                validateAction(actions.get(i), path + ".actions[" + i + "]", violations);
            }
        }

        List<SduiNode> children = node.children();
        if (children == null) {
            violations.add(path + ".children cannot be null");
        } else {
            for (int i = 0; i < children.size(); i++) {
                validateNode(
                        children.get(i),
                        depth + 1,
                        strictComponentTypes,
                        seenNodeIds,
                        path + ".children[" + i + "]",
                        violations
                );
            }
        }
    }

    private void validateAction(SduiAction action, String path, List<String> violations) {
        if (action == null) {
            violations.add(path + " cannot be null");
            return;
        }
        if (action.trigger() == null || !ALLOWED_ACTION_TRIGGERS.contains(action.trigger())) {
            violations.add(path + ".trigger must be one of " + ALLOWED_ACTION_TRIGGERS + " (got '" + action.trigger() + "')");
        }
        if (action.type() == null || !ALLOWED_ACTION_TYPES.contains(action.type())) {
            violations.add(path + ".type must be one of " + ALLOWED_ACTION_TYPES + " (got '" + action.type() + "')");
        }

        Map<String, Object> payload = action.payload();
        if (payload != null) {
            validateUriEntries(payload, path + ".payload", violations);
        }

        if ("NAVIGATE".equals(action.type())) {
            Object toVal = payload != null ? (payload.containsKey("to") ? payload.get("to") : payload.get("href")) : null;
            if (!(toVal instanceof String target) || !isSafeUri(target)) {
                violations.add(path + ".payload must contain a safe 'to' or 'href' URI for NAVIGATE action");
            }
        }
    }

    private void validateUriEntries(Map<String, Object> map, String path, List<String> violations) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (URI_PROP_KEYS.contains(key)) {
                if (!(value instanceof String strValue) || !isSafeUri(strValue)) {
                    violations.add(path + "." + key + " contains unsafe or invalid URI '" + value
                            + "' (only single-slash relative paths or https:// URLs are permitted)");
                }
            } else if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                validateUriEntries(nestedMap, path + "." + key, violations);
            }
        }
    }

    private void validateComponentProps(
            String type,
            Map<String, Object> props,
            String path,
            List<String> violations
    ) {
        switch (type) {
            case "Container" -> {
                checkOptionalEnum(props, "maxWidth", CONTAINER_MAX_WIDTHS, path, violations);
                checkOptionalEnum(props, "padding", CONTAINER_PADDINGS, path, violations);
            }
            case "Card" -> {
                checkOptionalEnum(props, "padding", CARD_PADDINGS, path, violations);
                checkOptionalEnum(props, "tone", CARD_TONES, path, violations);
            }
            case "Stack" -> {
                checkOptionalEnum(props, "direction", STACK_DIRECTIONS, path, violations);
                checkOptionalEnum(props, "gap", STACK_GAPS, path, violations);
                checkOptionalEnum(props, "align", STACK_ALIGNS, path, violations);
                checkOptionalBoolean(props, "wrap", path, violations);
            }
            case "BannerShell" -> {
                checkRequiredEnum(props, "bannerStyle", BANNER_STYLES, path, violations);
                checkRequiredString(props, "ariaLabel", path, violations);
                checkOptionalBoolean(props, "dismissible", path, violations);
            }
            case "Header" -> {
                checkRequiredString(props, "title", path, violations);
                checkOptionalString(props, "subtitle", path, violations);
                checkOptionalString(props, "eyebrow", path, violations);
                checkOptionalIntEnum(props, "level", HEADER_LEVELS, path, violations);
            }
            case "Text" -> {
                checkRequiredString(props, "content", path, violations);
                checkOptionalEnum(props, "variant", TEXT_VARIANTS, path, violations);
                checkOptionalEnum(props, "tone", TEXT_TONES, path, violations);
                checkOptionalEnum(props, "weight", TEXT_WEIGHTS, path, violations);
            }
            case "Badge" -> {
                checkRequiredString(props, "label", path, violations);
                checkRequiredEnum(props, "variant", BADGE_VARIANTS, path, violations);
            }
            case "Divider" -> checkOptionalEnum(props, "spacing", DIVIDER_SPACINGS, path, violations);
            case "PurposeToggleRow" -> {
                checkRequiredString(props, "purposeId", path, violations);
                checkRequiredString(props, "purposeSlug", path, violations);
                checkRequiredString(props, "title", path, violations);
                checkRequiredString(props, "description", path, violations);
                checkRequiredEnum(props, "legalBasis", LEGAL_BASES, path, violations);
                checkRequiredString(props, "legalBasisLabel", path, violations);
                checkRequiredBoolean(props, "checked", path, violations);
                checkRequiredBoolean(props, "required", path, violations);
                checkOptionalString(props, "badgeLabel", path, violations);
            }
            case "PolicySummaryAccordion" -> {
                checkRequiredString(props, "versionLabel", path, violations);
                checkRequiredString(props, "title", path, violations);
                checkRequiredString(props, "summary", path, violations);
                checkRequiredString(props, "publishedAt", path, violations);
                checkOptionalString(props, "contentMarkdown", path, violations);
                checkOptionalBoolean(props, "defaultExpanded", path, violations);
            }
            case "ActionBar" -> {
                checkOptionalEnum(props, "layout", ACTION_BAR_LAYOUTS, path, violations);
                checkOptionalBoolean(props, "sticky", path, violations);
            }
            case "Button" -> {
                checkRequiredString(props, "label", path, violations);
                checkRequiredEnum(props, "variant", BUTTON_VARIANTS, path, violations);
                checkOptionalBoolean(props, "fullWidth", path, violations);
                checkOptionalBoolean(props, "disabled", path, violations);
                checkOptionalString(props, "testId", path, violations);
            }
            case "NoticeCallout" -> {
                checkRequiredString(props, "title", path, violations);
                checkRequiredString(props, "message", path, violations);
                checkRequiredEnum(props, "tone", NOTICE_TONES, path, violations);
            }
            case "StatGrid" -> checkOptionalIntEnum(props, "columns", STAT_GRID_COLUMNS, path, violations);
            case "StatItem" -> {
                checkRequiredString(props, "label", path, violations);
                checkRequiredString(props, "value", path, violations);
                checkOptionalString(props, "sublabel", path, violations);
                checkOptionalEnum(props, "tone", STAT_ITEM_TONES, path, violations);
            }
            default -> {
                // Non-canonical component handled above when strictComponentTypes is enabled
            }
        }
    }

    private static void checkRequiredString(
            Map<String, Object> props,
            String key,
            String path,
            List<String> violations
    ) {
        Object val = props.get(key);
        if (!(val instanceof String str) || str.isBlank()) {
            violations.add(path + "." + key + " is required and must be a non-blank string");
        }
    }

    private static void checkOptionalString(
            Map<String, Object> props,
            String key,
            String path,
            List<String> violations
    ) {
        if (props.containsKey(key) && props.get(key) != null && !(props.get(key) instanceof String)) {
            violations.add(path + "." + key + " must be a string");
        }
    }

    private static void checkRequiredBoolean(
            Map<String, Object> props,
            String key,
            String path,
            List<String> violations
    ) {
        if (!(props.get(key) instanceof Boolean)) {
            violations.add(path + "." + key + " is required and must be a boolean");
        }
    }

    private static void checkOptionalBoolean(
            Map<String, Object> props,
            String key,
            String path,
            List<String> violations
    ) {
        if (props.containsKey(key) && props.get(key) != null && !(props.get(key) instanceof Boolean)) {
            violations.add(path + "." + key + " must be a boolean");
        }
    }

    private static void checkRequiredEnum(
            Map<String, Object> props,
            String key,
            Set<String> allowed,
            String path,
            List<String> violations
    ) {
        Object val = props.get(key);
        if (!(val instanceof String str) || !allowed.contains(str)) {
            violations.add(path + "." + key + " must be one of " + allowed + " (got '" + val + "')");
        }
    }

    private static void checkOptionalEnum(
            Map<String, Object> props,
            String key,
            Set<String> allowed,
            String path,
            List<String> violations
    ) {
        if (!props.containsKey(key) || props.get(key) == null) {
            return;
        }
        Object val = props.get(key);
        if (!(val instanceof String str) || !allowed.contains(str)) {
            violations.add(path + "." + key + " must be one of " + allowed + " (got '" + val + "')");
        }
    }

    private static void checkOptionalIntEnum(
            Map<String, Object> props,
            String key,
            Set<Integer> allowed,
            String path,
            List<String> violations
    ) {
        if (!props.containsKey(key) || props.get(key) == null) {
            return;
        }
        Object val = props.get(key);
        if (!(val instanceof Number num) || num.doubleValue() != num.intValue() || !allowed.contains(num.intValue())) {
            violations.add(path + "." + key + " must be one of " + allowed + " (got '" + val + "')");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private JsonSchema loadAndVerifySchemaResource() {
        try (InputStream stream = SduiSchemaValidator.class.getResourceAsStream(SCHEMA_CLASSPATH_LOCATION)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing frozen SDUI schema resource at classpath:" + SCHEMA_CLASSPATH_LOCATION
                );
            }
            JsonNode root = objectMapper.readTree(stream);
            String schemaConst = root.path("properties").path("schemaVersion").path("const").asText("");
            if (!EXPECTED_SCHEMA_VERSION.equals(schemaConst)) {
                throw new IllegalStateException(
                        "Unexpected sdui-schema.json schemaVersion const: " + schemaConst
                );
            }
            JsonNode canonicalEnum = root.path("$defs").path("CanonicalComponentType").path("enum");
            Set<String> loadedTypes = new HashSet<>();
            for (JsonNode item : canonicalEnum) {
                loadedTypes.add(item.asText());
            }
            if (!CANONICAL_COMPONENT_TYPES.equals(loadedTypes)) {
                throw new IllegalStateException(
                        "SduiSchemaValidator canonical types drifted from sdui-schema.json: " + loadedTypes
                );
            }
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            return factory.getSchema(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read bundled sdui-schema.json contract", ex);
        }
    }
}
