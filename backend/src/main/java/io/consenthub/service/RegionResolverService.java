package io.consenthub.service;

import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.RequestMetadataUtil;
import io.consenthub.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Resolves effective regulatory region in strict priority order per ADR-004:
 * 1. Explicit region argument / query parameter
 * 2. X-Consent-Region HTTP header
 * 3. Authenticated user's saved profile region
 * 4. Standard CDN geo headers (CF-IPCountry, CloudFront-Viewer-Country)
 * 5. Fallback to GLOBAL (DEFAULT regime)
 */
@Service
public class RegionResolverService {

    public static final String CONSENT_REGION_HEADER = "X-Consent-Region";

    private static final Set<String> EEA_UK_CH_COUNTRY_CODES = Set.of(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE",
            "IS", "LI", "NO", "GB", "UK", "CH"
    );

    public RegionCode resolveRegion(RegionCode explicitRegion) {
        return resolveRegion(explicitRegion, RequestMetadataUtil.currentHttpRequestOrNull());
    }

    public RegionCode resolveRegion(RegionCode explicitRegion, HttpServletRequest request) {
        if (explicitRegion != null) {
            return explicitRegion;
        }
        return resolveFromHeadersProfileOrFallback(request);
    }

    public RegionCode resolveRegion(String explicitRegionRaw, HttpServletRequest request) {
        if (explicitRegionRaw != null && !explicitRegionRaw.isBlank()) {
            RegionCode parsed = RegionCode.fromNullableString(explicitRegionRaw);
            if (parsed == null) {
                throw new BadRequestException(
                        "VALIDATION_ERROR",
                        "Unsupported region '" + explicitRegionRaw + "'. Expected EU, US_CA, or GLOBAL."
                );
            }
            return parsed;
        }
        return resolveFromHeadersProfileOrFallback(request);
    }

    public RegionCode parseOptionalOrDefault(String rawRegion, RegionCode fallback) {
        if (rawRegion == null || rawRegion.isBlank()) {
            return fallback;
        }
        RegionCode parsed = RegionCode.fromNullableString(rawRegion);
        if (parsed == null) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Unsupported region '" + rawRegion + "'. Expected EU, US_CA, or GLOBAL."
            );
        }
        return parsed;
    }

    private RegionCode resolveFromHeadersProfileOrFallback(HttpServletRequest request) {
        if (request != null) {
            String headerOverride = request.getHeader(CONSENT_REGION_HEADER);
            RegionCode fromHeader = RegionCode.fromNullableString(headerOverride);
            if (fromHeader != null) {
                return fromHeader;
            }
        }

        Optional<AuthenticatedUser> currentUser = SecurityUtils.getCurrentUser();
        if (currentUser.isPresent() && currentUser.get().region() != null) {
            return currentUser.get().region();
        }

        if (request != null) {
            RegionCode cdnRegion = resolveFromCdnGeoHeaders(request);
            if (cdnRegion != null) {
                return cdnRegion;
            }
        }

        return RegionCode.GLOBAL;
    }

    private static RegionCode resolveFromCdnGeoHeaders(HttpServletRequest request) {
        String country = firstNonBlank(
                request.getHeader("CF-IPCountry"),
                request.getHeader("CloudFront-Viewer-Country"),
                request.getHeader("X-Country-Code")
        );
        if (country == null) {
            return null;
        }
        String upperCountry = country.trim().toUpperCase(Locale.ROOT);
        if (EEA_UK_CH_COUNTRY_CODES.contains(upperCountry)) {
            return RegionCode.EU;
        }
        if ("US-CA".equals(upperCountry) || "US_CA".equals(upperCountry)) {
            return RegionCode.US_CA;
        }
        if ("US".equals(upperCountry)) {
            String subdivision = firstNonBlank(
                    request.getHeader("CF-Region-Code"),
                    request.getHeader("CloudFront-Viewer-Country-Region")
            );
            if (subdivision != null && "CA".equalsIgnoreCase(subdivision.trim())) {
                return RegionCode.US_CA;
            }
        }
        return RegionCode.GLOBAL;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }
}
