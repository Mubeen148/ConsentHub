package io.consenthub.security;

import io.consenthub.domain.entity.UserEntity;
import io.consenthub.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Verifies the HttpOnly ch_access_token cookie (HMAC-SHA256) and ensures the underlying user
 * account has not been erased (users.deleted_at IS NULL per SEC-G08).
 * Strictly ignores browser cookies on /api/v1/sdk/** routes (SEC-G05).
 */
public class JwtCookieAuthFilter extends OncePerRequestFilter {

    private final CookieService cookieService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public JwtCookieAuthFilter(
            CookieService cookieService,
            JwtTokenProvider jwtTokenProvider,
            UserRepository userRepository
    ) {
        this.cookieService = cookieService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path != null && path.startsWith("/api/v1/sdk/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> rawToken = cookieService.readAccessTokenCookie(request);
        if (rawToken.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<UUID> userIdOpt = jwtTokenProvider.parseUserId(rawToken.get());
            if (userIdOpt.isPresent()) {
                Optional<UserEntity> activeUser = userRepository.findByIdAndDeletedAtIsNull(userIdOpt.get());
                if (activeUser.isPresent()) {
                    UserEntity entity = activeUser.get();
                    AuthenticatedUser principal = new AuthenticatedUser(
                            entity.getId(),
                            entity.getEmail(),
                            entity.getFullName(),
                            entity.getRole(),
                            entity.getRegion()
                    );
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + entity.getRole().name()));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
