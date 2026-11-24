package io.consenthub.domain.dto;

import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.UserRole;

public record UserProfileDto(
        String id,
        String email,
        String fullName,
        UserRole role,
        RegionCode region,
        String subjectKey,
        String createdAt,
        String updatedAt
) {
}
