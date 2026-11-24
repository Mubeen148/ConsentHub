package io.consenthub.domain.dto;

public record PolicyVersionDto(
        String id,
        int versionNumber,
        String versionLabel,
        String title,
        String summary,
        String contentMarkdown,
        boolean requiresReconsent,
        boolean isActive,
        String publishedByEmail,
        String publishedAt,
        String createdAt
) {
    public boolean getIsActive() {
        return isActive;
    }
}
