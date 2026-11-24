package io.consenthub.security;

import io.consenthub.domain.exception.ConsentHubException;
import io.consenthub.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless API key authentication filter exclusively for /api/v1/sdk/** endpoints.
 * Completely ignores browser session cookies (SEC-G05), validates SHA-256 hashed X-API-Key,
 * enforces allowed_origins against Origin header, and checks sdk:read / sdk:write scopes.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyService apiKeyService;
    private final SecurityErrorWriter errorWriter;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService, SecurityErrorWriter errorWriter) {
        this.apiKeyService = apiKeyService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/v1/sdk/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // Explicitly isolate SDK routes from any ambient browser session context (SEC-G05)
        SecurityContextHolder.clearContext();

        String rawKey = request.getHeader(API_KEY_HEADER);
        String origin = request.getHeader("Origin");
        String requiredScope = HttpMethod.GET.matches(request.getMethod()) ? "sdk:read" : "sdk:write";

        try {
            ApiKeyPrincipal principal = apiKeyService.authenticateSdkKey(rawKey, origin, requiredScope);
            var authorities = List.of(new SimpleGrantedAuthority("SCOPE_SDK"));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ConsentHubException ex) {
            errorWriter.writeError(request, response, ex.getStatus(), ex.getCode(), ex.getMessage());
        }
    }
}
