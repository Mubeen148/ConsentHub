package io.consenthub.domain.dto;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimestampFormatter {

    public static final DateTimeFormatter ISO_UTC_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private TimestampFormatter() {
    }

    public static String format(Instant instant) {
        return instant == null ? null : ISO_UTC_MILLIS.format(instant);
    }
}
