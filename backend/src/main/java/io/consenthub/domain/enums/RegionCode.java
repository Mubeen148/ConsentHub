package io.consenthub.domain.enums;

import java.util.Locale;

/**
 * Canonical regional jurisdiction codes across PostgreSQL, GraphQL, REST, and SDUI.
 */
public enum RegionCode {
    EU,
    US_CA,
    GLOBAL;

    /**
     * Normalizes inbound region strings such as "US-CA" or "us_ca" into canonical enum values.
     * Returns null if input is blank or unrecognized.
     */
    public static RegionCode fromNullableString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "EU", "EEA", "GDPR" -> EU;
            case "US_CA", "CA_US", "CCPA" -> US_CA;
            case "GLOBAL", "DEFAULT" -> GLOBAL;
            default -> null;
        };
    }
}
