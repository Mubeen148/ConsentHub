package io.consenthub.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Canonical sorted-key compact JSON serializer per ADR-010 and database-schema.sql.
 * Guarantees identical UTF-8 output before and after PostgreSQL JSONB round-tripping.
 */
public final class CanonicalJsonUtil {

    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private CanonicalJsonUtil() {
    }

    public static String toCanonicalJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            JsonNode root = CANONICAL_MAPPER.valueToTree(details);
            JsonNode sortedRoot = sortJsonNodeRecursively(root);
            return CANONICAL_MAPPER.writeValueAsString(sortedRoot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize canonical audit JSON", ex);
        }
    }

    private static JsonNode sortJsonNodeRecursively(JsonNode node) {
        if (node == null || node.isNull()) {
            return CANONICAL_MAPPER.nullNode();
        }
        if (node.isObject()) {
            ObjectNode sortedObj = CANONICAL_MAPPER.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                fieldNames.add(names.next());
            }
            Collections.sort(fieldNames);
            for (String fieldName : fieldNames) {
                sortedObj.set(fieldName, sortJsonNodeRecursively(node.get(fieldName)));
            }
            return sortedObj;
        }
        if (node.isArray()) {
            ArrayNode sortedArr = CANONICAL_MAPPER.createArrayNode();
            for (JsonNode child : node) {
                sortedArr.add(sortJsonNodeRecursively(child));
            }
            return sortedArr;
        }
        return node;
    }
}
