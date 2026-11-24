package io.consenthub.security;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Privacy-by-design request metadata helper (PRIV-01):
 * - Computes salted SHA-256 hash of client IP without ever persisting or logging raw IP addresses.
 * - Summarizes User-Agent to coarse Browser / OS families (<= 120 chars) to prevent fingerprinting.
 */
public final class RequestMetadataUtil {

    private RequestMetadataUtil() {
    }

    public static HttpServletRequest currentHttpRequestOrNull() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    public static String hashClientIp(HttpServletRequest request, String salt) {
        if (request == null) {
            return null;
        }
        String rawIp = extractClientIp(request);
        if (rawIp == null || rawIp.isBlank()) {
            return null;
        }
        String effectiveSalt = (salt == null || salt.isBlank()) ? "consenthub-salt" : salt;
        return sha256Hex(effectiveSalt + ":" + rawIp.trim());
    }

    public static String extractRateLimitClientKey(HttpServletRequest request, String salt) {
        String ipHash = hashClientIp(request, salt);
        return ipHash != null ? ipHash.substring(0, 24) : "unknown";
    }

    public static String summarizeUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return "Unknown / Unknown";
        }
        String browser = detectBrowserFamily(ua);
        String os = detectOsFamily(ua);
        String summary = browser + " / " + os;
        return summary.length() <= 120 ? summary : summary.substring(0, 120);
    }

    private static String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int commaIdx = xff.indexOf(',');
            return (commaIdx > 0 ? xff.substring(0, commaIdx) : xff).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String detectBrowserFamily(String ua) {
        if (ua.contains("Edg/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("Firefox/")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") && !ua.contains("Chromium/")) {
            return "Chrome";
        }
        if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            return "Safari";
        }
        if (ua.contains("PostmanRuntime") || ua.contains("Newman")) {
            return "Newman-API";
        }
        if (ua.contains("Playwright") || ua.contains("HeadlessChrome")) {
            return "Playwright";
        }
        if (ua.contains("curl/")) {
            return "curl";
        }
        return "Browser";
    }

    private static String detectOsFamily(String ua) {
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) {
            return "iOS";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }
        return "Other";
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
