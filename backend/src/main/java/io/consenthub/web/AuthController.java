package io.consenthub.web;

import io.consenthub.domain.dto.AuthSessionResponse;
import io.consenthub.domain.dto.CsrfTokenResponse;
import io.consenthub.domain.dto.LoginRequest;
import io.consenthub.domain.dto.SignupRequest;
import io.consenthub.domain.dto.UserProfileDto;
import io.consenthub.security.CookieService;
import io.consenthub.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Authentication & Session Controller implementing {@code /api/v1/auth/*}
 * per {@code docs/contracts/rest-endpoints.md §2}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieService cookieService;

    public AuthController(AuthService authService, CookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> issueCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = cookieService.readCsrfTokenCookie(request)
                .orElseGet(cookieService::generateCsrfToken);
        cookieService.writeCsrfCookie(response, token);
        return ResponseEntity.ok(new CsrfTokenResponse(token));
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthSessionResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        AuthSessionResponse session = authService.signup(request, httpRequest, httpResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse httpResponse
    ) {
        AuthSessionResponse session = authService.login(request, httpResponse);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse httpResponse) {
        authService.logout(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> me() {
        return ResponseEntity.ok(authService.requireCurrentUserProfile());
    }
}
