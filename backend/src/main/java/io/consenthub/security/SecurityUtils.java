package io.consenthub.security;

import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ForbiddenException;
import io.consenthub.domain.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Centralized security & subject-key namespace isolation helpers (SEC-G01).
 */
public final class SecurityUtils {

    public static final Pattern ANON_SUBJECT_KEY_PATTERN =
            Pattern.compile("^anon:[a-zA-Z0-9_-]{8,112}$");

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public static AuthenticatedUser requireAuthenticatedUser() {
        return getCurrentUser().orElseThrow(() ->
                new UnauthorizedException("UNAUTHENTICATED", "Authentication is required."));
    }

    public static AuthenticatedUser requireAdminUser() {
        AuthenticatedUser user = requireAuthenticatedUser();
        if (!user.isAdmin()) {
            throw new ForbiddenException("FORBIDDEN", "Administrator privileges are required.");
        }
        return user;
    }

    public static boolean isCurrentUserAdmin() {
        return getCurrentUser().map(AuthenticatedUser::isAdmin).orElse(false);
    }

    /**
     * Resolves the effective consent subject_key per ADR-008 and SEC-G01:
     * <ul>
     *   <li>When authenticated: ALWAYS binds strictly to {@code user:<principal.id>} so authenticated
     *       callers can only inspect or mutate their own subject namespace.</li>
     *   <li>When unauthenticated: rejects any {@code user:*} key with 403 FORBIDDEN, validates
     *       {@code ^anon:[a-zA-Z0-9_-]{8,112}$}, or generates an ephemeral anon key on read-only
     *       fallbacks when {@code allowEphemeralFallback} is true.</li>
     * </ul>
     */
    public static String resolveEffectiveSubjectKey(String requestedSubjectKey, boolean allowEphemeralFallback) {
        Optional<AuthenticatedUser> currentUser = getCurrentUser();
        if (currentUser.isPresent()) {
            return currentUser.get().subjectKey();
        }

        if (requestedSubjectKey == null || requestedSubjectKey.isBlank()) {
            if (allowEphemeralFallback) {
                return "anon:ephemeral-" + UUID.randomUUID();
            }
            throw new BadRequestException("VALIDATION_ERROR", "An anonymous subjectKey ('anon:<id>') is required when unauthenticated.");
        }

        String trimmed = requestedSubjectKey.trim();
        if (trimmed.startsWith("user:")) {
            throw new ForbiddenException(
                    "FORBIDDEN",
                    "Unauthenticated callers cannot access or modify 'user:*' consent namespaces."
            );
        }
        if (!ANON_SUBJECT_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "subjectKey must match 'anon:<8..112 alphanumeric/dash/underscore chars>'."
            );
        }
        return trimmed;
    }

    /**
     * Validates an SDK subjectKey strictly against the anonymous namespace (SEC-G01).
     */
    public static String validateSdkAnonSubjectKey(String subjectKey) {
        if (subjectKey == null || subjectKey.isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Query/body parameter 'subjectKey' is required.");
        }
        String trimmed = subjectKey.trim();
        if (trimmed.startsWith("user:")) {
            throw new ForbiddenException(
                    "FORBIDDEN",
                    "Public SDK endpoints only accept pseudonymous 'anon:*' subject keys."
            );
        }
        if (!ANON_SUBJECT_KEY_PATTERN.matcher(trimmed).matches()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "subjectKey must match 'anon:<8..112 alphanumeric/dash/underscore chars>'."
            );
        }
        return trimmed;
    }
}
