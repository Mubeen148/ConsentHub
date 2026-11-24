package io.consenthub.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Enforces SEC-G06: fails startup immediately in the {@code prod} profile if {@code JWT_SECRET}
 * is missing, shorter than 32 UTF-8 bytes (256 bits), or matches the local dev fallback.
 */
@Component
public class ProdSecretValidator {

    private static final String DEV_PLACEHOLDER_PREFIX = "local-dev-only-consenthub";
    private static final int MIN_SECRET_BYTES = 32;

    private final Environment environment;
    private final AppProperties appProperties;

    public ProdSecretValidator(Environment environment, AppProperties appProperties) {
        this.environment = environment;
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void validate() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        String secret = appProperties.jwt() != null ? appProperties.jwt().secret() : null;

        if (isProd) {
            if (secret == null || secret.isBlank()) {
                throw new IllegalStateException(
                        "SECURITY FAIL-FAST (SEC-G06): JWT_SECRET must be explicitly configured in the 'prod' profile."
                );
            }
            if (secret.startsWith(DEV_PLACEHOLDER_PREFIX)) {
                throw new IllegalStateException(
                        "SECURITY FAIL-FAST (SEC-G06): Local development JWT_SECRET placeholder is prohibited in 'prod'."
                );
            }
            if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "SECURITY FAIL-FAST (SEC-G06): JWT_SECRET must be at least 32 UTF-8 bytes (256 bits) in 'prod'."
                );
            }
        } else if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT signing secret must be at least 32 UTF-8 bytes (256 bits) for HMAC-SHA256."
            );
        }
    }
}
