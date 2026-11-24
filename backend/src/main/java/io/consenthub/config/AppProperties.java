package io.consenthub.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly typed application properties under the {@code consenthub.*} prefix.
 */
@ConfigurationProperties(prefix = "consenthub")
public record AppProperties(
        String version,
        Jwt jwt,
        Cookie cookie,
        Cors cors,
        Privacy privacy,
        Seed seed,
        RateLimit rateLimit
) {
    public record Jwt(
            String secret,
            long expirationSeconds
    ) {
        @Override
        public String toString() {
            return "Jwt[secret=[REDACTED], expirationSeconds=" + expirationSeconds + "]";
        }
    }

    public record Cookie(
            boolean secure,
            String accessTokenName,
            String csrfTokenName
    ) {
    }

    public record Cors(
            List<String> allowedOrigins
    ) {
    }

    public record Privacy(
            String ipHashSalt
    ) {
        @Override
        public String toString() {
            return "Privacy[ipHashSalt=[REDACTED]]";
        }
    }

    public record Seed(
            boolean enabled
    ) {
    }

    public record RateLimit(
            int authPerMinute,
            int exportPerMinute,
            int sdkPerMinute
    ) {
    }
}
