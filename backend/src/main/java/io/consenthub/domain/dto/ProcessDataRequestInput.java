package io.consenthub.domain.dto;

import io.consenthub.domain.enums.DataRequestStatus;

public record ProcessDataRequestInput(
        String id,
        DataRequestStatus status,
        String adminNotes
) {
}
