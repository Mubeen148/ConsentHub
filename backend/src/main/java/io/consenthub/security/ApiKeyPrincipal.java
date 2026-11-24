package io.consenthub.security;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable principal representing a verified third-party SDK API key on /api/v1/sdk/**.
 */
public record ApiKeyPrincipal(
        UUID id,
        String name,
        String keyPrefix,
        String allowedOrigins,
        Set<String> scopes
) implements Principal {

    @Override
    public String getName() {
        return keyPrefix;
    }

    public boolean hasScope(String scope) {
        return scopes != null && scopes.contains(scope);
    }
}
