package io.consenthub.domain.dto;

public record CreatedApiKeyResultDto(
        String plainTextKey,
        ApiKeyInfoDto apiKey
) {
    @Override
    public String toString() {
        return "CreatedApiKeyResultDto[plainTextKey=[REDACTED], apiKey=" + apiKey + "]";
    }
}
