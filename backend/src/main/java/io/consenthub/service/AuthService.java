package io.consenthub.service;

import io.consenthub.domain.dto.AuthSessionResponse;
import io.consenthub.domain.dto.LoginRequest;
import io.consenthub.domain.dto.SignupRequest;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.dto.UpdateProfileInput;
import io.consenthub.domain.dto.UserProfileDto;
import io.consenthub.domain.entity.UserEntity;
import io.consenthub.domain.enums.RegionCode;
import io.consenthub.domain.enums.UserRole;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ConflictException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.domain.exception.UnauthorizedException;
import io.consenthub.repository.UserRepository;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.CookieService;
import io.consenthub.security.JwtTokenProvider;
import io.consenthub.security.SecurityUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user signup, login, logout, profile updates, and anti-enumeration credential checks (SEC-G07, SEC-G08).
 */
@Service
public class AuthService {

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT_OR_SYMBOL_PATTERN = Pattern.compile(".*[0-9[^a-zA-Z0-9]].*");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieService cookieService;
    private final RegionResolverService regionResolverService;
    private final ConsentService consentService;
    private final AuditLogService auditLogService;

    private String dummyBcryptHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            CookieService cookieService,
            RegionResolverService regionResolverService,
            ConsentService consentService,
            AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.cookieService = cookieService;
        this.regionResolverService = regionResolverService;
        this.consentService = consentService;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    void initDummyHash() {
        // SEC-G07: Precomputed dummy BCrypt(12) hash so unknown/deleted account logins take constant CPU time
        this.dummyBcryptHash = passwordEncoder.encode("ConsentHub-Anti-Timing-Enumeration-Dummy-2026!");
    }

    @Transactional
    public AuthSessionResponse signup(
            SignupRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        if (request == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Signup payload is required.");
        }
        String email = normalizeEmail(request.email());
        validatePasswordStrength(request.password());

        String fullName = request.fullName() != null ? request.fullName().trim() : "";
        if (fullName.length() < 2 || fullName.length() > 120) {
            throw new BadRequestException("VALIDATION_ERROR", "Full name must be between 2 and 120 characters.");
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists.");
        }

        RegionCode resolvedRegion = (request.region() != null && !request.region().isBlank())
                ? regionResolverService.parseOptionalOrDefault(request.region(), RegionCode.GLOBAL)
                : regionResolverService.resolveRegion((RegionCode) null, httpRequest);

        Instant now = Instant.now();
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(fullName);
        user.setRole(UserRole.USER);
        user.setRegion(resolvedRegion);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        UserEntity saved = userRepository.saveAndFlush(user);
        consentService.linkAnonymousConsentIfPresent(request.anonymousSubjectKey(), saved);

        String jwt = jwtTokenProvider.createAccessToken(saved);
        String csrfToken = cookieService.generateCsrfToken();
        cookieService.writeAccessCookie(httpResponse, jwt);
        cookieService.writeCsrfCookie(httpResponse, csrfToken);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("region", saved.getRegion().name());
        auditDetails.put("role", saved.getRole().name());
        auditLogService.append(
                saved.getId(),
                saved.getEmail(),
                saved.getRole().name(),
                "USER_SIGNUP",
                "USER",
                saved.getId().toString(),
                auditDetails
        );

        return new AuthSessionResponse(toProfileDto(saved), csrfToken);
    }

    @Transactional
    public AuthSessionResponse login(LoginRequest request, HttpServletResponse httpResponse) {
        if (request == null || request.email() == null || request.password() == null) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
        }
        String email = normalizeEmail(request.email());
        Optional<UserEntity> candidateOpt = userRepository.findByEmailIgnoreCase(email);

        if (candidateOpt.isEmpty() || candidateOpt.get().getDeletedAt() != null) {
            // SEC-G07: Execute dummy BCrypt(12) verification to equalize timing
            passwordEncoder.matches(request.password(), dummyBcryptHash);
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
        }

        UserEntity user = candidateOpt.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password.");
        }

        consentService.linkAnonymousConsentIfPresent(request.anonymousSubjectKey(), user);

        String jwt = jwtTokenProvider.createAccessToken(user);
        String csrfToken = cookieService.generateCsrfToken();
        cookieService.writeAccessCookie(httpResponse, jwt);
        cookieService.writeCsrfCookie(httpResponse, csrfToken);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("role", user.getRole().name());
        auditDetails.put("region", user.getRegion().name());
        auditLogService.append(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                "USER_LOGIN",
                "USER",
                user.getId().toString(),
                auditDetails
        );

        return new AuthSessionResponse(toProfileDto(user), csrfToken);
    }

    @Transactional
    public void logout(HttpServletResponse httpResponse) {
        Optional<AuthenticatedUser> currentUser = SecurityUtils.getCurrentUser();
        cookieService.clearSessionCookies(httpResponse);
        currentUser.ifPresent(actor -> auditLogService.append(
                actor.id(),
                actor.email(),
                actor.role().name(),
                "USER_LOGOUT",
                "USER",
                actor.id().toString(),
                Map.of("sessionCleared", true)
        ));
    }

    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUserProfileOrNull() {
        return SecurityUtils.getCurrentUser()
                .flatMap(principal -> userRepository.findByIdAndDeletedAtIsNull(principal.id()))
                .map(AuthService::toProfileDto)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UserProfileDto requireCurrentUserProfile() {
        AuthenticatedUser principal = SecurityUtils.requireAuthenticatedUser();
        UserEntity entity = userRepository.findByIdAndDeletedAtIsNull(principal.id())
                .orElseThrow(() -> new UnauthorizedException("UNAUTHENTICATED", "Active user account not found."));
        return toProfileDto(entity);
    }

    @Transactional
    public UserProfileDto updateMyProfile(UpdateProfileInput input) {
        AuthenticatedUser principal = SecurityUtils.requireAuthenticatedUser();
        if (input == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Profile input is required.");
        }
        String fullName = input.fullName() != null ? input.fullName().trim() : "";
        if (fullName.length() < 2 || fullName.length() > 120) {
            throw new BadRequestException("VALIDATION_ERROR", "Full name must be between 2 and 120 characters.");
        }
        if (input.region() == null) {
            throw new BadRequestException("VALIDATION_ERROR", "Region is required.");
        }

        UserEntity entity = userRepository.findByIdAndDeletedAtIsNull(principal.id())
                .orElseThrow(() -> new NotFoundException("User account not found."));

        RegionCode previousRegion = entity.getRegion();
        entity.setFullName(fullName);
        entity.setRegion(input.region());
        entity.setUpdatedAt(Instant.now());
        UserEntity saved = userRepository.save(entity);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousRegion", previousRegion.name());
        auditDetails.put("newRegion", saved.getRegion().name());
        auditLogService.append(
                saved.getId(),
                saved.getEmail(),
                saved.getRole().name(),
                "PROFILE_UPDATED",
                "USER",
                saved.getId().toString(),
                auditDetails
        );

        return toProfileDto(saved);
    }

    public static void validatePasswordStrength(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Password must be between 8 and 128 characters."
            );
        }
        if (!UPPERCASE_PATTERN.matcher(password).matches()
                || !LOWERCASE_PATTERN.matcher(password).matches()
                || !DIGIT_OR_SYMBOL_PATTERN.matcher(password).matches()) {
            throw new BadRequestException(
                    "VALIDATION_ERROR",
                    "Password must include at least one uppercase letter, one lowercase letter, and one digit or symbol."
            );
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "Email is required.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public static UserProfileDto toProfileDto(UserEntity entity) {
        return new UserProfileDto(
                entity.getId().toString(),
                entity.getEmail(),
                entity.getFullName(),
                entity.getRole(),
                entity.getRegion(),
                entity.getSubjectKey(),
                TimestampFormatter.format(entity.getCreatedAt()),
                TimestampFormatter.format(entity.getUpdatedAt())
        );
    }
}
