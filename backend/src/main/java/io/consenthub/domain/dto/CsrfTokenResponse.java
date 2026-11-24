package io.consenthub.domain.dto;

public record CsrfTokenResponse(String csrfToken) {
    @Override
    public String toString() {
        return "CsrfTokenResponse[csrfToken=[REDACTED]]";
    }
}
