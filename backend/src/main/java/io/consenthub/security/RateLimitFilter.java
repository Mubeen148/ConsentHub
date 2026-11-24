package io.consenthub.security;

import io.consenthub.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window rate limiter enforcing docs/contracts/rest-endpoints.md §1.3 & SEC-G07:
 * - POST /api/v1/auth/login & POST /api/v1/auth/signup: 10 req / 60s per client IP
 * - GET /api/v1/privacy/export & GET /api/v1/export: 5 req / 60s per authenticated userId
 * - /api/v1/sdk/**: 120 req / 60s per (X-API-Key prefix + client IP) with X-RateLimit-* headers
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final AppProperties appProperties;
    private final SecurityErrorWriter errorWriter;
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger cleanupCounter = new AtomicInteger();

    public RateLimitFilter(AppProperties appProperties, SecurityErrorWriter errorWriter) {
        this.appProperties = appProperties;
        this.errorWriter = errorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (path == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(method)
                && ("/api/v1/auth/login".equals(path) || "/api/v1/auth/signup".equals(path))) {
            String clientKey = RequestMetadataUtil.extractRateLimitClientKey(request, appProperties.privacy().ipHashSalt());
            int limit = appProperties.rateLimit().authPerMinute();
            RateDecision decision = tryAcquire("auth:" + clientKey, limit);
            if (!decision.allowed()) {
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                errorWriter.writeError(
                        request,
                        response,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "Too many authentication attempts. Please retry later."
                );
                return;
            }
        } else if ("GET".equalsIgnoreCase(method)
                && ("/api/v1/privacy/export".equals(path) || "/api/v1/export".equals(path))) {
            Optional<AuthenticatedUser> user = SecurityUtils.getCurrentUser();
            if (user.isPresent()) {
                int limit = appProperties.rateLimit().exportPerMinute();
                RateDecision decision = tryAcquire("export:" + user.get().id(), limit);
                if (!decision.allowed()) {
                    response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                    errorWriter.writeError(
                            request,
                            response,
                            HttpStatus.TOO_MANY_REQUESTS,
                            "RATE_LIMIT_EXCEEDED",
                            "Export rate limit exceeded. Please wait before generating another archive."
                    );
                    return;
                }
            }
        } else if (path.startsWith("/api/v1/sdk/")) {
            String rawApiKey = request.getHeader(ApiKeyAuthFilter.API_KEY_HEADER);
            String keyPrefix = (rawApiKey != null && !rawApiKey.isBlank())
                    ? rawApiKey.substring(0, Math.min(14, rawApiKey.length()))
                    : "anon";
            String clientKey = RequestMetadataUtil.extractRateLimitClientKey(request, appProperties.privacy().ipHashSalt());
            int limit = appProperties.rateLimit().sdkPerMinute();

            RateDecision decision = tryAcquire("sdk:" + keyPrefix + ":" + clientKey, limit);
            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

            if (!decision.allowed()) {
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                errorWriter.writeError(
                        request,
                        response,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "SDK rate limit exceeded. Please back off and retry."
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private RateDecision tryAcquire(String key, int maxRequests) {
        long now = System.currentTimeMillis();
        long cutoff = now - WINDOW_MILLIS;

        if ((cleanupCounter.incrementAndGet() & 0xFF) == 0) {
            evictStaleBuckets(cutoff);
        }

        Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : now;
                long retryAfterMillis = Math.max(1000L, (oldest + WINDOW_MILLIS) - now);
                long retryAfterSeconds = (retryAfterMillis + 999L) / 1000L;
                return new RateDecision(false, 0, retryAfterSeconds);
            }
            timestamps.addLast(now);
            int remaining = Math.max(0, maxRequests - timestamps.size());
            return new RateDecision(true, remaining, 0L);
        }
    }

    private void evictStaleBuckets(long cutoff) {
        Iterator<Map.Entry<String, Deque<Long>>> iterator = buckets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Deque<Long>> entry = iterator.next();
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                    deque.pollFirst();
                }
                if (deque.isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    private record RateDecision(boolean allowed, int remaining, long retryAfterSeconds) {
    }
}
