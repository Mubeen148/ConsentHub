package io.consenthub.domain.enums;

import java.util.Locale;

public enum ConsentSource {
    BANNER,
    PREFERENCE_CENTER,
    SDK,
    ADMIN_SEED,
    SYSTEM_ERASURE;

    public static ConsentSource fromOrDefault(String raw, ConsentSource fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ConsentSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
