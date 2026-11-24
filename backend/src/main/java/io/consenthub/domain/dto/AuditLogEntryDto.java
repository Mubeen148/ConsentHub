package io.consenthub.domain.dto;

import java.util.Map;

public record AuditLogEntryDto(
        String id,
        int sequenceNum,
        String actorId,
        String actorEmail,
        String actorRole,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> details,
        String prevHash,
        String entryHash,
        String createdAt
) {
}
