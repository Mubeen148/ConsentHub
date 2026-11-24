package io.consenthub.security;

import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.UserRole;
import java.security.Principal;
import java.util.UUID;

/**
 * Immutable authenticated user principal stored in Spring SecurityContext.
 */
public record AuthenticatedUser(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        RegionCode region
) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }

    public String subjectKey() {
        return "user:" + id;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
