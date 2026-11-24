package io.consenthub.domain.dto;

import io.consenthub.domain.enums.DataRequestStatus;
import io.consenthub.domain.enums.DataRequestType;

public record DataRequestDto(
        String id,
        String userId,
        String userEmail,
        String userFullName,
        DataRequestType requestType,
        DataRequestStatus status,
        String reason,
        String adminNotes,
        String processedByEmail,
        String requestedAt,
        String updatedAt,
        String completedAt
) {
}
