package io.consenthub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces constant-time double-submit CSRF validation (X-CSRF-Token == ch_csrf_token) on all
 * state-changing HTTP methods (POST, PUT, PATCH, DELETE, including /graphql) whenever a
 * ch_access_token session cookie is present (docs/contracts/rest-endpoints.md §1.2, SEC-G05).
 */
public class DoubleSubmitCsrfFilter extends OncePerRequestFilter {

    public static final String CSRF_HEADER_NAME = "X-CSRF-Token";

    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> EXEMPT_EXACT_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup"
    );

    private final CookieService cookieService;
    private final SecurityErrorWriter errorWriter;

    public DoubleSubmitCsrfFilter(CookieService cookieService, SecurityErrorWriter errorWriter) {
        this.cookieService = cookieService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || !STATE_CHANGING_METHODS.contains(method.toUpperCase())) {
            return true;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        return EXEMPT_EXACT_PATHS.contains(path) || path.startsWith("/api/v1/sdk/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> accessTokenCookie = cookieService.readAccessTokenCookie(request);
        if (accessTokenCookie.isPresent()) {
            Optional<String> csrfCookie = cookieService.readCsrfTokenCookie(request);
            String csrfHeader = request.getHeader(CSRF_HEADER_NAME);

            if (csrfCookie.isEmpty() || csrfHeader == null || csrfHeader.isBlank()) {
                errorWriter.writeError(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "CSRF_TOKEN_INVALID",
                        "Missing or invalid CSRF token."
                );
                return;
            }

            byte[] cookieBytes = csrfCookie.get().getBytes(StandardCharsets.UTF_8);
            byte[] headerBytes = csrfHeader.trim().getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(cookieBytes, headerBytes)) {
                errorWriter.writeError(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "CSRF_TOKEN_INVALID",
                        "Missing or invalid CSRF token."
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
