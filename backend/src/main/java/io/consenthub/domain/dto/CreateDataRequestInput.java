package io.consenthub.domain.dto;

import io.consenthub.domain.enums.DataRequestType;

public record CreateDataRequestInput(
        DataRequestType requestType,
        String reason,
        String confirmationText
) {
}
