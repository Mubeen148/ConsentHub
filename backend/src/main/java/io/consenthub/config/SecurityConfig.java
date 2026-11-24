package io.consenthub.config;

import io.consenthub.repository.UserRepository;
import io.consenthub.security.ApiKeyAuthFilter;
import io.consenthub.security.CookieService;
import io.consenthub.security.CorrelationIdFilter;
import io.consenthub.security.DoubleSubmitCsrfFilter;
import io.consenthub.security.JwtCookieAuthFilter;
import io.consenthub.security.JwtTokenProvider;
import io.consenthub.security.RateLimitFilter;
import io.consenthub.security.SecurityErrorWriter;
import io.consenthub.service.ApiKeyService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Deny-by-default Spring Security 6 filter chain enforcing:
 * - Segregated CORS (strict explicit allowlist with credentials on first-party routes vs stateless SDK CORS)
 * - Security response headers (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy)
 * - CorrelationIdFilter -> RateLimitFilter (SDK/Auth) -> JwtCookieAuthFilter -> ApiKeyAuthFilter -> DoubleSubmitCsrfFilter
 * - Method-level @PreAuthorize support for GraphQL resolvers (SEC-G02)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String CSP_POLICY =
            "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
            + "font-src 'self' https://fonts.gstatic.com data:; "
            + "img-src 'self' data: https:; "
            + "connect-src 'self'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AppProperties appProperties,
            CookieService cookieService,
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository,
            ApiKeyService apiKeyService,
            SecurityErrorWriter errorWriter
    ) throws Exception {
        CorrelationIdFilter correlationIdFilter = new CorrelationIdFilter();
        JwtCookieAuthFilter jwtCookieAuthFilter =
                new JwtCookieAuthFilter(cookieService, jwtTokenProvider, userRepository);
        ApiKeyAuthFilter apiKeyAuthFilter = new ApiKeyAuthFilter(apiKeyService, errorWriter);
        DoubleSubmitCsrfFilter doubleSubmitCsrfFilter = new DoubleSubmitCsrfFilter(cookieService, errorWriter);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(appProperties, errorWriter);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource(appProperties)))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_POLICY))
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> { })
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                        ))
                        .permissionsPolicyHeader(permissions ->
                                permissions.policy("camera=(), microphone=(), geolocation=()"))
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                errorWriter.writeError(
                                        request,
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED",
                                        "Authentication is required to access this resource."
                                ))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorWriter.writeError(
                                        request,
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "FORBIDDEN",
                                        "You do not have permission to perform this action."
                                ))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/health", "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout"
                        ).permitAll()
                        .requestMatchers("/graphql").permitAll()
                        .requestMatchers("/api/v1/sdk/**").hasAuthority("SCOPE_SDK")
                        .requestMatchers("/api/v1/auth/me", "/api/v1/privacy/export", "/api/v1/export").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtCookieAuthFilter, CorrelationIdFilter.class)
                .addFilterAfter(rateLimitFilter, JwtCookieAuthFilter.class)
                .addFilterAfter(apiKeyAuthFilter, RateLimitFilter.class)
                .addFilterAfter(doubleSubmitCsrfFilter, ApiKeyAuthFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties appProperties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Stateless credential-less CORS for third-party embeddable consent.js SDK
        CorsConfiguration sdkConfig = new CorsConfiguration();
        sdkConfig.setAllowedOriginPatterns(List.of("*"));
        sdkConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        sdkConfig.setAllowedHeaders(List.of("Content-Type", "X-API-Key", "X-Consent-Region", "X-Request-Id"));
        sdkConfig.setExposedHeaders(List.of("X-Request-Id", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
        sdkConfig.setAllowCredentials(false);
        sdkConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/v1/sdk/**", sdkConfig);

        // Strict credentialed CORS allowlist for first-party React SPA
        CorsConfiguration appConfig = new CorsConfiguration();
        appConfig.setAllowedOrigins(appProperties.cors().allowedOrigins());
        appConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        appConfig.setAllowedHeaders(List.of("Content-Type", "X-CSRF-Token", "X-Consent-Region", "X-Request-Id"));
        appConfig.setExposedHeaders(List.of("X-Request-Id", "Content-Disposition", "Retry-After"));
        appConfig.setAllowCredentials(true);
        appConfig.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", appConfig);

        return source;
    }
}
