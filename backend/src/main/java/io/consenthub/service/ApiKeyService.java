package io.consenthub.service;

import io.consenthub.domain.dto.ApiKeyInfoDto;
import io.consenthub.domain.dto.CreateApiKeyInput;
import io.consenthub.domain.dto.CreatedApiKeyResultDto;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.entity.ApiKeyEntity;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ForbiddenException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.domain.exception.UnauthorizedException;
import io.consenthub.repository.ApiKeyRepository;
import io.consenthub.security.ApiKeyPrincipal;
import io.consenthub.security.AuthenticatedUser;
import io.consenthub.security.SecurityUtils;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages third-party SDK & public REST API keys (SHA-256 hashed at rest, plaintext returned once).
 */
@Service
public class ApiKeyService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern SAFE_ORIGIN_PATTERN =
            Pattern.compile("^https?://[a-zA-Z0-9.-]+(:[0-9]{1,5})?$");
    private static final Set<String> VALID_SCOPES = Set.of("sdk:read", "sdk:write");

    private final ApiKeyRepository apiKeyRepository;
    private final AuditLogService auditLogService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository, AuditLogService auditLogService) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyInfoDto> listApiKeys() {
        SecurityUtils.requireAdminUser();
        return apiKeyRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ApiKeyService::toDto)
                .toList();
    }

    @Transactional
    public CreatedApiKeyResultDto createApiKey(CreateApiKeyInput input) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        if (input == null || input.name() == null || input.name().isBlank()) {
            throw new BadRequestException("VALIDATION_ERROR", "API key name is required.");
        }
        String name = input.name().trim();
        if (name.length() > 120) {
            throw new BadRequestException("VALIDATION_ERROR", "API key name must be 120 characters or fewer.");
        }

        String allowedOrigins = normalizeAllowedOrigins(input.allowedOrigins());
        String scopes = normalizeScopes(input.scopes());

        byte[] randomBytes = new byte[20];
        SECURE_RANDOM.nextBytes(randomBytes);
        String plainTextKey = "ch_live_" + HexFormat.of().formatHex(randomBytes);
        String keyPrefix = plainTextKey.substring(0, 14);
        String keyHash = HashUtil.sha256Hex(plainTextKey);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(UUID.randomUUID());
        entity.setName(name);
        entity.setKeyPrefix(keyPrefix);
        entity.setKeyHash(keyHash);
        entity.setAllowedOrigins(allowedOrigins);
        entity.setScopes(scopes);
        entity.setActive(true);
        entity.setCreatedBy(admin.id());
        entity.setCreatedAt(Instant.now());

        ApiKeyEntity saved = apiKeyRepository.save(entity);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("name", saved.getName());
        auditDetails.put("keyPrefix", saved.getKeyPrefix());
        auditDetails.put("allowedOrigins", saved.getAllowedOrigins());
        auditDetails.put("scopes", saved.getScopes());
        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "API_KEY_CREATED",
                "API_KEY",
                saved.getId().toString(),
                auditDetails
        );

        return new CreatedApiKeyResultDto(plainTextKey, toDto(saved));
    }

    @Transactional
    public boolean revokeApiKey(String idRaw) {
        AuthenticatedUser admin = SecurityUtils.requireAdminUser();
        UUID id = parseUuid(idRaw, "Invalid API key ID.");
        ApiKeyEntity entity = apiKeyRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("API key not found: " + id));

        entity.setActive(false);
        apiKeyRepository.save(entity);

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("keyPrefix", entity.getKeyPrefix());
        auditDetails.put("name", entity.getName());
        auditLogService.append(
                admin.id(),
                admin.email(),
                admin.role().name(),
                "API_KEY_REVOKED",
                "API_KEY",
                entity.getId().toString(),
                auditDetails
        );
        return true;
    }

    @Transactional
    public ApiKeyPrincipal authenticateSdkKey(String rawKey, String originHeader, String requiredScope) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new UnauthorizedException("INVALID_API_KEY", "Missing or invalid X-API-Key header.");
        }
        String keyHash = HashUtil.sha256Hex(rawKey.trim());
        ApiKeyEntity entity = apiKeyRepository.findByKeyHashAndActiveTrue(keyHash)
                .orElseThrow(() -> new UnauthorizedException("INVALID_API_KEY", "Invalid or revoked API key."));

        validateOriginAllowed(entity.getAllowedOrigins(), originHeader);

        Set<String> scopeSet = Arrays.stream(entity.getScopes().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        if (requiredScope != null && !scopeSet.contains(requiredScope)) {
            throw new ForbiddenException("FORBIDDEN", "API key lacks required scope: " + requiredScope);
        }

        apiKeyRepository.updateLastUsedAt(entity.getId(), Instant.now());

        return new ApiKeyPrincipal(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getAllowedOrigins(),
                scopeSet
        );
    }

    private static void validateOriginAllowed(String allowedOriginsConfig, String originHeader) {
        if (originHeader == null || originHeader.isBlank()) {
            return;
        }
        String trimmedConfig = allowedOriginsConfig != null ? allowedOriginsConfig.trim() : "*";
        if ("*".equals(trimmedConfig)) {
            return;
        }
        String normalizedOrigin = originHeader.trim().toLowerCase(Locale.ROOT);
        boolean matched = Arrays.stream(trimmedConfig.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> "*".equals(allowed) || allowed.equals(normalizedOrigin));

        if (!matched) {
            throw new ForbiddenException("ORIGIN_NOT_ALLOWED", "Request Origin is not authorized for this API key.");
        }
    }

    private static String normalizeAllowedOrigins(String raw) {
        if (raw == null || raw.isBlank() || "*".equals(raw.trim())) {
            return "*";
        }
        List<String> origins = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            return "*";
        }
        for (String origin : origins) {
            if (!"*".equals(origin) && !SAFE_ORIGIN_PATTERN.matcher(origin).matches()) {
                throw new BadRequestException(
                        "VALIDATION_ERROR",
                        "Invalid origin in allowedOrigins: '" + origin + "'. Expected '*' or 'https://hostname[:port]'."
                );
            }
        }
        return String.join(",", origins);
    }

    private static String normalizeScopes(String raw) {
        if (raw == null || raw.isBlank()) {
            return "sdk:read,sdk:write";
        }
        List<String> parsed = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (parsed.isEmpty()) {
            return "sdk:read,sdk:write";
        }
        for (String scope : parsed) {
            if (!VALID_SCOPES.contains(scope)) {
                throw new BadRequestException(
                        "VALIDATION_ERROR",
                        "Unsupported API key scope '" + scope + "'. Allowed: sdk:read, sdk:write."
                );
            }
        }
        return String.join(",", parsed);
    }

    private static UUID parseUuid(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            throw new BadRequestException("VALIDATION_ERROR", message);
        }
    }

    public static ApiKeyInfoDto toDto(ApiKeyEntity entity) {
        return new ApiKeyInfoDto(
                entity.getId().toString(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getAllowedOrigins(),
                entity.getScopes(),
                entity.isActive(),
                TimestampFormatter.format(entity.getLastUsedAt()),
                TimestampFormatter.format(entity.getCreatedAt())
        );
    }
}
