package io.consenthub.domain.dto;

import io.consenthub.domain.enums.RegionCode;

public record UpdateProfileInput(
        String fullName,
        RegionCode region
) {
}
