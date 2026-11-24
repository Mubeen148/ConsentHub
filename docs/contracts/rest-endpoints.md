# ConsentHub — Frozen REST API Specification (`docs/contracts/rest-endpoints.md`)

> **Gate 1 Status**: **FROZEN**  
> **Authoritative Scope**: Governs all Spring MVC REST controllers (`/api/v1/auth/*`, `/api/v1/privacy/*`, `/api/v1/sdk/*`, `/api/v1/health`, `/actuator/health`), HTTP cookie/CSRF mechanics, API key authentication, rate limiting, and error envelopes.

---

## 1. Global Protocol, Security & Error Conventions

### 1.1 Base URLs & Content Negotiation
- **Local Development**:
  - Backend direct: `http://localhost:8080`
  - Frontend Vite / Docker Nginx proxy: `/api/v1/*` and `/graphql` proxied cleanly to `backend:8080` (same-origin cookie transport).
- **Content-Type**: `application/json; charset=utf-8` on all JSON requests and responses.
- **Timestamps**: All timestamps use ISO-8601 UTC format (`2026-11-24T09:15:30.123Z`).
- **Region Parameter Normalization**: Inbound query parameters (`?region=...`) and headers (`X-Consent-Region`) accept `EU`, `US_CA`, `US-CA` (normalized to canonical `US_CA`), or `GLOBAL`. Outbound JSON always emits canonical `EU` | `US_CA` | `GLOBAL`.

### 1.2 Cookie Session & Double-Submit Header CSRF Contract
1. **`ch_access_token` Cookie** (Authentication):
   - Set on `POST /api/v1/auth/signup` and `POST /api/v1/auth/login`.
   - Attributes: `HttpOnly; SameSite=Lax; Path=/; Max-Age=86400` (`Secure` appended automatically when `APP_COOKIE_SECURE=true` in production).
   - Payload: Signed HMAC-SHA256 JWT (`sub=<userUuid>`, `email`, `role`=`USER|ADMIN`, `region`=`EU|US_CA|GLOBAL`, `iat`, `exp`).
2. **`ch_csrf_token` Cookie + `X-CSRF-Token` Header** (CSRF Mitigation):
   - Issued/refreshed on `GET /api/v1/auth/csrf`, `POST /api/v1/auth/signup`, and `POST /api/v1/auth/login`.
   - Attributes: `SameSite=Lax; Path=/; Max-Age=86400` (`HttpOnly` is **omitted** so the SPA can read it if needed, and the token is also returned in JSON from `GET /api/v1/auth/csrf`).
   - **Enforcement Rule**: Every state-changing request (`POST`, `PUT`, `PATCH`, `DELETE`, including `/graphql` mutations) that carries a `ch_access_token` cookie **MUST** include a non-empty `X-CSRF-Token` header matching the `ch_csrf_token` cookie value (`403 Forbidden` with `code: "CSRF_TOKEN_INVALID"` on mismatch/omission).
   - **Exemptions**:
     - `POST /api/v1/auth/login` and `POST /api/v1/auth/signup` (initial credential submission before an authenticated cookie session exists).
     - `/api/v1/sdk/**` (stateless API-key-authenticated endpoints that ignore cookies completely).
     - Unauthenticated `/graphql` queries and anonymous visitor consent mutations when no `ch_access_token` cookie is present (note: the SPA still attaches `X-CSRF-Token` unconditionally after calling `GET /api/v1/auth/csrf` on startup).

### 1.3 Rate Limiting Contract
| Route Pattern | Limit Key | Threshold | Exceeded Response |
|---------------|-----------|-----------|-------------------|
| `POST /api/v1/auth/login`, `POST /api/v1/auth/signup` | Client IP | `10 req / 60s` | `429 Too Many Requests` + `Retry-After: <seconds>` |
| `GET /api/v1/privacy/export` | Authenticated `userId` | `5 req / 60s` | `429 Too Many Requests` + `Retry-After: <seconds>` |
| `/api/v1/sdk/**` | `X-API-Key` prefix + Client IP | `120 req / 60s` | `429 Too Many Requests` + `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After` |

### 1.4 Standard Error Envelope (`4xx` / `5xx`)
All REST error responses return a uniform JSON body (never exposing stack traces or SQL internals):
```json
{
  "timestamp": "2026-11-24T09:30:00.123Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "code": "VALIDATION_ERROR",
  "message": "One or more request fields failed validation.",
  "path": "/api/v1/auth/signup",
  "correlationId": "req_9b3e7f12a4c0",
  "fieldErrors": {
    "email": "Must be a valid email address",
    "password": "Must be at least 8 characters and include uppercase, lowercase, and a number or symbol"
  }
}
```

---

## 2. Authentication & Session Endpoints (`/api/v1/auth/*`)

### 2.1 `GET /api/v1/auth/csrf`
Issues (or refreshes) the `ch_csrf_token` cookie and returns the token value so the React SPA can initialize Apollo/fetch headers on boot.
- **Auth Required**: None.
- **Response (`200 OK`)**:
  - `Set-Cookie: ch_csrf_token=<64-hex-random>; Path=/; Max-Age=86400; SameSite=Lax`
  ```json
  {
    "csrfToken": "f4a81d29e7c34b09a12d5e88c90f33b7190a4c8d6e2f1a3b5c7d9e0f1a2b3c4d"
  }
  ```

### 2.2 `POST /api/v1/auth/signup`
Registers a new end-user (`role = "USER"`), hashes the password with `BCrypt(strength = 12)`, optionally links prior anonymous visitor consent (`anonymousSubjectKey`) to the newly created `user:<uuid>` subject, logs an `AUTH_SIGNUP` audit event, and establishes an authenticated cookie session.
- **Auth Required**: None (rate-limited).
- **Request Body**:
  ```json
  {
    "email": "claire.dubois@example.com",
    "password": "StrongUser123!",
    "fullName": "Claire Dubois",
    "region": "EU",
    "anonymousSubjectKey": "anon:550e8400-e29b-41d4-a716-446655440000"
  }
  ```
  - Validation rules:
    - `email`: Required, trimmed, lowercased, RFC-compliant email, `<= 255` chars, unique (`409 Conflict` if already registered).
    - `password`: Required, `8..128` chars, at least 1 letter and 1 digit/symbol.
    - `fullName`: Required, trimmed, `2..120` chars.
    - `region`: Optional, defaults to `"GLOBAL"`; must be `"EU" | "US_CA" | "GLOBAL"`.
    - `anonymousSubjectKey`: Optional (`^anon:[a-zA-Z0-9_-]{8,112}$`). When present, existing `consent_records` for that anonymous key are copied to `user:<newUserId>` if not already set.
- **Response (`201 Created`)**:
  - `Set-Cookie: ch_access_token=<jwt>; Path=/; Max-Age=86400; HttpOnly; SameSite=Lax`
  - `Set-Cookie: ch_csrf_token=<token>; Path=/; Max-Age=86400; SameSite=Lax`
  ```json
  {
    "user": {
      "id": "a3f189d0-6e2b-4c91-8a15-2b4d90c7f312",
      "email": "claire.dubois@example.com",
      "fullName": "Claire Dubois",
      "role": "USER",
      "region": "EU",
      "subjectKey": "user:a3f189d0-6e2b-4c91-8a15-2b4d90c7f312",
      "createdAt": "2026-11-24T09:32:10.045Z",
      "updatedAt": "2026-11-24T09:32:10.045Z"
    },
    "csrfToken": "f4a81d29e7c34b09a12d5e88c90f33b7190a4c8d6e2f1a3b5c7d9e0f1a2b3c4d"
  }
  ```
- **Error Status Codes**:
  - `400 Bad Request` (`VALIDATION_ERROR`)
  - `409 Conflict` (`EMAIL_ALREADY_EXISTS`)
  - `429 Too Many Requests` (`RATE_LIMIT_EXCEEDED`)

### 2.3 `POST /api/v1/auth/login`
Authenticates an active (`deleted_at IS NULL`) user via email and BCrypt password verification, rotates `ch_access_token` and `ch_csrf_token` cookies, and writes an `AUTH_LOGIN_SUCCESS` (or `AUTH_LOGIN_FAILED`) audit log entry.
- **Auth Required**: None (rate-limited).
- **Request Body**:
  ```json
  {
    "email": "user@consenthub.io",
    "password": "UserPass123!",
    "anonymousSubjectKey": "anon:550e8400-e29b-41d4-a716-446655440000"
  }
  ```
- **Response (`200 OK`)**:
  - Sets `ch_access_token` (`HttpOnly`) and `ch_csrf_token` cookies.
  ```json
  {
    "user": {
      "id": "00000000-0000-4000-8000-000000000002",
      "email": "user@consenthub.io",
      "fullName": "Marcus Lindqvist",
      "role": "USER",
      "region": "EU",
      "subjectKey": "user:00000000-0000-4000-8000-000000000002",
      "createdAt": "2026-11-01T08:00:00.000Z",
      "updatedAt": "2026-11-24T09:35:00.000Z"
    },
    "csrfToken": "8c12a0f49b6d4e31a7f2c901d83e54b2019f8e7d6c5b4a392817263544536271"
  }
  ```
- **Error Status Codes**:
  - `401 Unauthorized` (`INVALID_CREDENTIALS` — identical generic message `"Invalid email or password."` for non-existent email, wrong password, or erased account to prevent account enumeration).
  - `429 Too Many Requests` (`RATE_LIMIT_EXCEEDED`)

### 2.4 `POST /api/v1/auth/logout`
Clears session cookies (`Max-Age=0`) and records an `AUTH_LOGOUT` audit event.
- **Auth Required**: Cookie session + valid `X-CSRF-Token` header (if unauthenticated, still clears cookies idempotently).
- **Response (`204 No Content`)**:
  - `Set-Cookie: ch_access_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax`
  - `Set-Cookie: ch_csrf_token=; Path=/; Max-Age=0; SameSite=Lax`
  - Empty body.

### 2.5 `GET /api/v1/auth/me`
Returns the authenticated user's profile from the verified JWT + active database record (`deleted_at IS NULL`).
- **Auth Required**: Valid `ch_access_token` cookie.
- **Response (`200 OK`)**:
  ```json
  {
    "id": "00000000-0000-4000-8000-000000000002",
    "email": "user@consenthub.io",
    "fullName": "Marcus Lindqvist",
    "role": "USER",
    "region": "EU",
    "subjectKey": "user:00000000-0000-4000-8000-000000000002",
    "createdAt": "2026-11-01T08:00:00.000Z",
    "updatedAt": "2026-11-24T09:35:00.000Z"
  }
  ```
- **Error Status Codes**: `401 Unauthorized` (`UNAUTHENTICATED`)

---

## 3. Self-Service Data Portability Export (`/api/v1/privacy/*`)

### 3.1 `GET /api/v1/privacy/export` *(Alias: `GET /api/v1/export`)*
Generates and streams a complete, machine-readable **GDPR Art. 20 / CCPA Right-to-Know** JSON data portability archive for the authenticated user. Automatically records a `COMPLETED` `EXPORT` entry in `data_requests` (including snapshot metadata) and appends a `DSAR_DATA_EXPORTED` event to `audit_logs`.
- **Auth Required**: Valid `ch_access_token` cookie (`ROLE_USER` or `ROLE_ADMIN`).
- **Response (`200 OK`)**:
  - `Content-Type: application/json; charset=utf-8`
  - `Content-Disposition: attachment; filename="consenthub-data-export-2026-11-24.json"`
  - `Cache-Control: no-store`
  ```json
  {
    "exportMetadata": {
      "schemaVersion": "1.0",
      "generatedAt": "2026-11-24T09:40:12.500Z",
      "platform": "ConsentHub",
      "regulatoryBasis": ["GDPR_ARTICLE_15_20", "CCPA_RIGHT_TO_KNOW"],
      "requestId": "7d92f8a1-3b4e-4c10-9a22-6e1f0b8c3d45"
    },
    "profile": {
      "id": "00000000-0000-4000-8000-000000000002",
      "email": "user@consenthub.io",
      "fullName": "Marcus Lindqvist",
      "role": "USER",
      "region": "EU",
      "subjectKey": "user:00000000-0000-4000-8000-000000000002",
      "createdAt": "2026-11-01T08:00:00.000Z",
      "updatedAt": "2026-11-24T09:35:00.000Z"
    },
    "currentConsentRecords": [
      {
        "purposeSlug": "essential",
        "purposeName": "Essential Security & Session",
        "legalBasis": "STRICTLY_NECESSARY",
        "granted": true,
        "policyVersionLabel": "v2.0",
        "regionCode": "EU",
        "source": "PREFERENCE_CENTER",
        "updatedAt": "2026-11-20T14:10:00.000Z"
      },
      {
        "purposeSlug": "analytics",
        "purposeName": "Product Analytics & Telemetry",
        "legalBasis": "CONSENT",
        "granted": true,
        "policyVersionLabel": "v2.0",
        "regionCode": "EU",
        "source": "PREFERENCE_CENTER",
        "updatedAt": "2026-11-20T14:10:00.000Z"
      }
    ],
    "consentHistoryLedger": [
      {
        "id": "f1c2d3e4-5a6b-4789-8012-3456789abcde",
        "purposeSlug": "analytics",
        "purposeName": "Product Analytics & Telemetry",
        "previousGranted": false,
        "newGranted": true,
        "policyVersionLabel": "v2.0",
        "regionCode": "EU",
        "source": "PREFERENCE_CENTER",
        "experimentVariant": "control",
        "proofHash": "9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d9c8b7a6f5e4d3c2b1a098765",
        "occurredAt": "2026-11-20T14:10:00.000Z"
      }
    ],
    "experimentAssignments": [
      {
        "experimentSlug": "eu-banner-clarity-q4",
        "variantKey": "control",
        "converted": true,
        "assignedAt": "2026-11-18T10:05:00.000Z",
        "convertedAt": "2026-11-20T14:10:00.000Z"
      }
    ],
    "dataSubjectRequests": [
      {
        "id": "7d92f8a1-3b4e-4c10-9a22-6e1f0b8c3d45",
        "requestType": "EXPORT",
        "status": "COMPLETED",
        "requestedAt": "2026-11-24T09:40:12.500Z",
        "completedAt": "2026-11-24T09:40:12.500Z"
      }
    ]
  }
  ```
- **Error Status Codes**: `401 Unauthorized`, `429 Too Many Requests`

---

## 4. Public / Third-Party Embeddable SDK API (`/api/v1/sdk/*`)

### 4.1 SDK Authentication & CORS Rules
- **Header Required**: `X-API-Key: <raw_api_key>` (e.g., seeded demo key `ch_live_demo_public_sdk_key_2026`, whose SHA-256 hex digest in `api_keys.key_hash` is `37f0f4a4131b49bae8a6ffcdfbc8c585b7972459cfeb3d5d8a65d37b407abb1d`).
- **Validation**:
  1. Compute lowercase hex `SHA-256(UTF-8(X-API-Key))` and match an `api_keys` row with `active = TRUE` (`401 Unauthorized` with `code: "INVALID_API_KEY"` if missing, unknown, or revoked).
  2. If request includes an `Origin` header and `allowed_origins != '*'`, verify `Origin` matches comma-separated `allowed_origins` (`403 Forbidden` with `code: "ORIGIN_NOT_ALLOWED"` otherwise).
  3. Check scope (`sdk:read` for `GET`, `sdk:write` for `POST`) and update `last_used_at` asynchronously/non-blockingly.
- **CORS**: Allows `GET, POST, OPTIONS`, headers `Content-Type, X-API-Key, X-Consent-Region`, without credentials (`Access-Control-Allow-Credentials` omitted/false so wildcard/multi-origin third-party embedding works safely).

### 4.2 `GET /api/v1/sdk/config`
Returns everything `consent.js` needs on page load: resolved regional rule, active policy version, re-consent status, active purposes, current consent map, and the server-composed SDUI `CONSENT_BANNER` screen (including sticky A/B experiment assignment).
- **Query Parameters**:
  - `subjectKey` (required, e.g., `anon:550e8400-e29b-41d4-a716-446655440000` or `user:<uuid>`)
  - `region` (optional: `EU`, `US_CA`, `GLOBAL`; falls back to `X-Consent-Region` header -> CDN Geo header -> `GLOBAL`)
- **Response (`200 OK`)**:
  ```json
  {
    "subjectKey": "anon:550e8400-e29b-41d4-a716-446655440000",
    "regionRule": {
      "regionCode": "EU",
      "regimeName": "GDPR",
      "consentModel": "OPT_IN",
      "bannerStyle": "MODAL",
      "showRejectAll": true,
      "retentionDays": 365
    },
    "activePolicyVersion": {
      "versionNumber": 2,
      "versionLabel": "v2.0",
      "title": "2026 Global Privacy & AI Telemetry Policy",
      "summary": "Updated transparency around product telemetry and regional data residency.",
      "requiresReconsent": true,
      "publishedAt": "2026-11-10T00:00:00.000Z"
    },
    "hasConsented": false,
    "needsReconsent": true,
    "shouldShowBanner": true,
    "purposes": [
      {
        "slug": "essential",
        "name": "Essential Security & Session",
        "description": "Strictly required for authentication, CSRF protection, and consent storage.",
        "legalBasis": "STRICTLY_NECESSARY",
        "isEssential": true,
        "granted": true
      },
      {
        "slug": "analytics",
        "name": "Product Analytics & Telemetry",
        "description": "Helps us measure feature reliability and page performance.",
        "legalBasis": "CONSENT",
        "isEssential": false,
        "granted": false
      }
    ],
    "consentMap": {
      "essential": true,
      "analytics": false,
      "personalization": false,
      "marketing": false,
      "ads": false
    },
    "sduiBanner": {
      "schemaVersion": "1.0",
      "screenName": "CONSENT_BANNER",
      "policyVersion": "v2.0",
      "needsReconsent": true,
      "regionCode": "EU",
      "regime": "GDPR",
      "experiment": {
        "experimentId": "44444444-4444-4444-8444-444444444444",
        "experimentSlug": "eu-banner-clarity-q4",
        "variantKey": "control"
      },
      "root": {
        "id": "banner-root",
        "type": "BannerShell",
        "props": {
          "bannerStyle": "MODAL",
          "ariaLabel": "Privacy & Consent Preferences"
        },
        "children": [],
        "actions": []
      }
    }
  }
  ```

### 4.3 `GET /api/v1/sdk/consent`
Lightweight fast-path lookup for third-party host scripts checking whether analytics/marketing tags may fire.
- **Query Parameters**:
  - `subjectKey` (required)
  - `region` (optional: `EU`, `US_CA`, `GLOBAL`)
- **Response (`200 OK`)**:
  ```json
  {
    "subjectKey": "anon:550e8400-e29b-41d4-a716-446655440000",
    "regionCode": "EU",
    "policyVersion": "v2.0",
    "hasConsented": true,
    "needsReconsent": false,
    "purposes": {
      "essential": true,
      "analytics": true,
      "personalization": false,
      "marketing": false,
      "ads": false
    },
    "updatedAt": "2026-11-24T09:42:00.000Z"
  }
  ```

### 4.4 `POST /api/v1/sdk/consent`
Records visitor consent choices from `consent.js` (`ACCEPT_ALL`, `REJECT_OPTIONAL`, or `CUSTOM`), updates `consent_records`, appends immutable `consent_history` rows (`source = "SDK"`), attributes conversion on `experiment_assignments` if applicable, and returns the updated state.
- **Request Body**:
  ```json
  {
    "subjectKey": "anon:550e8400-e29b-41d4-a716-446655440000",
    "region": "EU",
    "action": "CUSTOM",
    "decisions": {
      "analytics": true,
      "personalization": false,
      "marketing": false,
      "ads": false
    },
    "experimentId": "44444444-4444-4444-8444-444444444444",
    "variantKey": "control"
  }
  ```
  - Rules:
    - `action`: Required, `"ACCEPT_ALL" | "REJECT_OPTIONAL" | "CUSTOM"`.
    - When `action == "ACCEPT_ALL"`, all active purposes are set to `true` (`decisions` optional/ignored).
    - When `action == "REJECT_OPTIONAL"`, `essential` purposes are set to `true` and all optional purposes are set to `false` (`decisions` optional/ignored).
    - When `action == "CUSTOM"`, `decisions` map is applied for optional purposes while `is_essential = true` purposes are strictly forced to `true`.
- **Response (`200 OK`)**: Same shape as `GET /api/v1/sdk/consent` with `hasConsented: true`, `needsReconsent: false`, updated `purposes` map, and `updatedAt`.
- **Error Status Codes**: `400 Bad Request`, `401 Unauthorized` (`INVALID_API_KEY`), `403 Forbidden` (`ORIGIN_NOT_ALLOWED`), `429 Too Many Requests`.

---

## 5. Health & Observability Endpoints

### 5.1 `GET /api/v1/health` & `GET /actuator/health`
Public liveness/readiness probes used by Docker Compose healthchecks, Nginx upstream checks, and CI smoke tests.
- **Auth Required**: None.
- **`GET /api/v1/health` Response (`200 OK` when DB reachable, `503 Service Unavailable` otherwise)**:
  ```json
  {
    "status": "UP",
    "database": "UP",
    "version": "1.0.0",
    "timestamp": "2026-11-24T09:45:00.000Z"
  }
  ```
- **`GET /actuator/health` Response (`200 OK`)**: Standard Spring Boot Actuator JSON (`{ "status": "UP", ... }`).
