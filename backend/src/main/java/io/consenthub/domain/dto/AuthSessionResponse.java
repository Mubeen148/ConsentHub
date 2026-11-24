package io.consenthub.domain.dto;

public record AuthSessionResponse(
        UserProfileDto user,
        String csrfToken
) {
    @Override
    public String toString() {
        return "AuthSessionResponse[user=" + user + ", csrfToken=[REDACTED]]";
    }
}
