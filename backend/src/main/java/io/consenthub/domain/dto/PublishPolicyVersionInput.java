package io.consenthub.domain.dto;

public record PublishPolicyVersionInput(
        String versionLabel,
        String title,
        String summary,
        String contentMarkdown,
        Boolean requiresReconsent,
        Boolean activateImmediately
) {
}
