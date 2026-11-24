package io.consenthub.web;

import io.consenthub.domain.dto.SdkConfigResponse;
import io.consenthub.domain.dto.SdkConsentRequest;
import io.consenthub.domain.dto.SdkConsentResponse;
import io.consenthub.domain.exception.ForbiddenException;
import io.consenthub.security.ApiKeyPrincipal;
import io.consenthub.service.ConsentService;
import io.consenthub.service.SduiScreenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public / Third-Party Embeddable SDK REST Controller ({@code /api/v1/sdk/*})
 * protected by {@code X-API-Key} SHA-256 authentication and origin scoping.
 */
@RestController
@RequestMapping("/api/v1/sdk")
public class SdkController {

    private final ConsentService consentService;
    private final SduiScreenProvider sduiScreenProvider;

    public SdkController(ConsentService consentService, SduiScreenProvider sduiScreenProvider) {
        this.consentService = consentService;
        this.sduiScreenProvider = sduiScreenProvider;
    }

    @GetMapping("/config")
    public ResponseEntity<SdkConfigResponse> getConfig(
            @RequestParam("subjectKey") String subjectKey,
            @RequestParam(value = "region", required = false) String region,
            HttpServletRequest request
    ) {
        requireSdkScope("sdk:read");
        return ResponseEntity.ok(consentService.getSdkConfig(subjectKey, region, request, sduiScreenProvider));
    }

    @GetMapping("/consent")
    public ResponseEntity<SdkConsentResponse> getConsent(
            @RequestParam("subjectKey") String subjectKey,
            @RequestParam(value = "region", required = false) String region,
            HttpServletRequest request
    ) {
        requireSdkScope("sdk:read");
        return ResponseEntity.ok(consentService.getSdkConsent(subjectKey, region, request));
    }

    @PostMapping("/consent")
    public ResponseEntity<SdkConsentResponse> recordConsent(
            @Valid @RequestBody SdkConsentRequest requestBody,
            HttpServletRequest request
    ) {
        requireSdkScope("sdk:write");
        return ResponseEntity.ok(consentService.recordSdkConsent(requestBody, request));
    }

    private static void requireSdkScope(String scope) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof ApiKeyPrincipal principal)) {
            throw new ForbiddenException("FORBIDDEN", "Valid SDK API key authentication is required.");
        }
        if (!principal.hasScope(scope)) {
            throw new ForbiddenException("FORBIDDEN", "API key lacks required scope: " + scope);
        }
    }
}
