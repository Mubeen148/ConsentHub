package io.consenthub.domain.dto;

public record CreateApiKeyInput(
        String name,
        String allowedOrigins,
        String scopes
) {
}
