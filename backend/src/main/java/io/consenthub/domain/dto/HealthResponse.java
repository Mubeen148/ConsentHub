package io.consenthub.domain.dto;

public record HealthResponse(
        String status,
        String database,
        String version,
        String timestamp
) {
}
