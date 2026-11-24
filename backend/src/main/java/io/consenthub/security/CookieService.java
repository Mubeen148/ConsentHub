package io.consenthub.security;

import io.consenthub.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Manages ch_access_token (HttpOnly) and ch_csrf_token (readable double-submit) cookies
 * per docs/contracts/rest-endpoints.md §1.2.
 */
@Component
public class CookieService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppProperties appProperties;

    public CookieService(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public String generateCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public void writeAccessCookie(HttpServletResponse response, String jwt) {
        ResponseCookie cookie = ResponseCookie.from(appProperties.cookie().accessTokenName(), jwt)
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(appProperties.jwt().expirationSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void writeCsrfCookie(HttpServletResponse response, String csrfToken) {
        ResponseCookie cookie = ResponseCookie.from(appProperties.cookie().csrfTokenName(), csrfToken)
                .httpOnly(false)
                .secure(appProperties.cookie().secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(appProperties.jwt().expirationSeconds()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearSessionCookies(HttpServletResponse response) {
        ResponseCookie clearAccess = ResponseCookie.from(appProperties.cookie().accessTokenName(), "")
                .httpOnly(true)
                .secure(appProperties.cookie().secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        ResponseCookie clearCsrf = ResponseCookie.from(appProperties.cookie().csrfTokenName(), "")
                .httpOnly(false)
                .secure(appProperties.cookie().secure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearCsrf.toString());
    }

    public Optional<String> readCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                return Optional.of(cookie.getValue());
            }
        }
        return Optional.empty();
    }

    public Optional<String> readAccessTokenCookie(HttpServletRequest request) {
        return readCookie(request, appProperties.cookie().accessTokenName());
    }

    public Optional<String> readCsrfTokenCookie(HttpServletRequest request) {
        return readCookie(request, appProperties.cookie().csrfTokenName());
    }
}
