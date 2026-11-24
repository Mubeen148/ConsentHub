package io.consenthub.domain.dto;

public record AuditChainVerificationDto(
        boolean chainValid,
        int totalEntriesChecked,
        Integer brokenAtSequenceNum,
        String message,
        String verifiedAt
) {
}
