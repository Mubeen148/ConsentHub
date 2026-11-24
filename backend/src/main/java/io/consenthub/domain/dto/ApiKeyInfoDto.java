package io.consenthub.domain.dto;

public record ApiKeyInfoDto(
        String id,
        String name,
        String keyPrefix,
        String allowedOrigins,
        String scopes,
        boolean active,
        String lastUsedAt,
        String createdAt
) {
}
