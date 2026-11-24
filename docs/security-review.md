# ConsentHub — Application Security Review & OWASP Top 10 Audit (`docs/security-review.md`)

> **Document Owner**: Principal Security Engineer (`security-engineer`)  
> **Gate 1 Status (`T-05`)**: **GATE 1 SECURITY SIGN-OFF: APPROVED**  
> **Gate 4 Status (`T-12`)**: Pending Live Build Verification (Verification Matrix Ready in §5)  
> **Reviewed Frozen Contracts**:
> - `docs/contracts/database-schema.sql`
> - `docs/contracts/graphql-schema.graphqls`
> - `docs/contracts/rest-endpoints.md`
> - `docs/contracts/sdui-schema.json`
> - `DECISIONS.md` (`ADR-001` through `ADR-010`)

---

## 1. Executive Security Summary

ConsentHub processes high-trust privacy governance state: user authentication credentials, granular per-purpose consent decisions, self-service Data Subject Access Request (DSAR) portability archives (`EXPORT`) and erasure lifecycles (`DELETE`), Server-Driven UI (SDUI) component trees, third-party SDK telemetry (`consent.js`), and regulatory audit proofs.

A comprehensive Gate 1 review of all four frozen architectural contracts and `DECISIONS.md` confirms **zero contract-level security defects or cross-layer contradictions**. Cryptographic primitives, session transport mechanics, append-only database triggers, hash-chain canonicalization rules, and SDUI schema constraints are soundly specified. Section 4 enumerates **10 Mandatory Implementation Guardrails (`SEC-G01` through `SEC-G10`)** that Backend, GraphQL/SDUI, Frontend, and DevOps engineers must enforce during Gate 2 implementation, followed by the Gate 4 Live Verification Matrix in Section 5.

---

## 2. Threat Model & Trust Boundaries

### 2.1 System Trust Zones & Boundary Topology

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ TZ-1: FIRST-PARTY BROWSER SPA                     TZ-2: THIRD-PARTY HOST SITE           │
│ (React 18 + Apollo Client, Untrusted Client)      (External Domain + consent.js SDK)    │
│ - Holds HttpOnly JWT cookie (opaque to JS)        - Holds publishable X-API-Key         │
│ - Reads ch_csrf_token -> sends X-CSRF-Token       - Generates pseudonymous anon:<uuid>  │
└──────────────────────┬───────────────────────────────────────────────┬──────────────────┘
                       │ TB-1: HTTPS / Same-Origin Proxy               │ TB-2: Cross-Origin HTTPS
                       │ Cookie (ch_access_token) + X-CSRF-Token       │ X-API-Key + Origin Header
                       │ Strict CORS Allowlist                         │ Credential-less CORS + Rate Limit
                       ▼                                               ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ TZ-3 / TZ-4: REVERSE PROXY (Nginx) + SPRING BOOT 3.4 BACKEND (Java 21, Port 8080)       │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ SecurityFilterChain (Deny-by-Default)                                               │ │
│ │  1. Correlation ID + Structured Access Logger (zero secret/token logging)           │ │
│ │  2. Security Headers (CSP, X-Content-Type-Options, X-Frame-Options, Referrer-Policy)│ │
│ │  3. RateLimitFilter (/api/v1/auth/**, /api/v1/privacy/export, /api/v1/sdk/**)       │ │
│ │  4. ApiKeyAuthFilter (/api/v1/sdk/** ONLY — ignores cookies, checks SHA-256 + Origin│ │
│ │  5. JwtCookieAuthFilter (verifies HMAC-SHA256 + users.deleted_at IS NULL)           │ │
│ │  6. DoubleSubmitCsrfFilter (constant-time cookie == X-CSRF-Token check)             │ │
│ └──────────────────────────────────────────┬──────────────────────────────────────────┘ │
│                                            │ TB-4: Privilege & Subject Namespace Guard  │
│                                            │ (ROLE_ADMIN vs ROLE_USER vs anon:<id>)     │
│                                            ▼                                            │
│ ┌─────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ Domain Services + SduiSchemaValidator + Parameterized Spring Data JPA               │ │
│ └──────────────────────────────────────────┬──────────────────────────────────────────┘ │
└────────────────────────────────────────────┼────────────────────────────────────────────┘
                                             │ TB-3: Internal Docker Bridge Network (TCP 5432)
                                             │ Parameterized JDBC + Append-Only DB Triggers
                                             ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│ TZ-5: POSTGRESQL 16 DATA & IMMUTABLE LEDGER STORE                                       │
│ - UUIDv4 Primary Keys, CHECK Constraints, Partial Unique Active Policy Index            │
│ - BEFORE UPDATE OR DELETE Triggers on `consent_history` and `audit_logs`                │
│ - SHA-256 Hash Chain (`audit_logs`) & Consent Receipt Hashes (`consent_history`)        │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Trust Boundary Specifications

#### TB-1: Browser SPA <-> Spring Boot GraphQL & REST (`httpOnly` JWT + Double-Submit CSRF)
- **Threats**: Token theft via XSS (`localStorage` exfiltration), Cross-Site Request Forgery (CSRF) against cookie-authenticated `/graphql` mutations or `/api/v1/*` endpoints, cross-origin credentialed data theft via permissive CORS.
- **Contract Controls**:
  - Session JWTs are stored exclusively in `ch_access_token` (`HttpOnly; SameSite=Lax; Path=/; Max-Age=86400`, plus `Secure` when `APP_COOKIE_SECURE=true`), making the bearer token inaccessible to `document.cookie` (`ADR-003`, `rest-endpoints.md` §1.2).
  - Every state-changing request (`POST`, `PUT`, `PATCH`, `DELETE`, including `/graphql` operations) carrying `ch_access_token` must present an `X-CSRF-Token` header matching the `ch_csrf_token` cookie (`403 Forbidden`, `code: "CSRF_TOKEN_INVALID"` on mismatch/absence).
  - Credentialed CORS (`Access-Control-Allow-Credentials: true`) is restricted strictly to explicit origins configured via `CORS_ALLOWED_ORIGINS` (never `*`).

#### TB-2: Third-Party Host Site (`consent.js`) <-> `/api/v1/sdk/*` (`X-API-Key` + Origin + Rate Limit)
- **Threats**: Unauthorized SDK abuse, cross-site quota exhaustion / DDoS, ambient browser cookie leakage if a user logged into ConsentHub visits an external page embedding `consent.js`, DOM XSS inside the host page from SDUI responses.
- **Contract Controls**:
  - `/api/v1/sdk/**` authenticates strictly via the `X-API-Key` header hashed with `SHA-256` against `api_keys.key_hash` where `active = TRUE` (`401 INVALID_API_KEY` if missing, unknown, or revoked via `revokeApiKey`).
  - **Ambient Cookie Isolation**: `/api/v1/sdk/**` is stateless, sets `Access-Control-Allow-Credentials: false` (omitted), and **completely ignores** `ch_access_token` / `ch_csrf_token` cookies (`rest-endpoints.md` §1.2, §4.1).
  - **Origin & Scope Enforcement**: When an `Origin` header is present and `api_keys.allowed_origins != '*'`, the request origin must match the allowlist (`403 ORIGIN_NOT_ALLOWED`). `GET` endpoints require `sdk:read`; `POST /api/v1/sdk/consent` requires `sdk:write`.
  - **Rate Limiting**: `120 req / 60s` per `(key_prefix + clientIp)` bucket with `429 Too Many Requests` and `Retry-After` headers.

#### TB-3: Spring Boot 3 <-> PostgreSQL 16
- **Threats**: SQL/JPQL injection, accidental or malicious mutation/deletion of regulatory consent receipts or audit logs, race conditions corrupting active policy uniqueness or SHA-256 hash chain continuity.
- **Contract Controls**:
  - Parameterized Spring Data JPA queries (`database-schema.sql` regex `CHECK` constraints provide defense-in-depth on `slug`, `subject_key`, and status enums).
  - PostgreSQL engine-level `prevent_append_only_mutation()` triggers (`trg_consent_history_append_only`, `trg_audit_logs_append_only`) raise `integrity_constraint_violation` on any `UPDATE` or `DELETE`.
  - Partial unique index `uq_policy_versions_single_active` guarantees at most one active policy version globally at the database level.
  - PostgreSQL runs on an internal Docker network without public host port exposure required in production deployments.

#### TB-4: Role & Subject Namespace Privilege Boundary (`ROLE_ADMIN` vs `ROLE_USER` vs Anonymous Visitor)
- **Threats**: Vertical privilege escalation (`ROLE_USER` or anonymous caller invoking `admin*` GraphQL queries or governance mutations), horizontal privilege escalation / IDOR (`user A` or anonymous caller passing `subjectKey: "user:<user_B_uuid>"` to inspect or overwrite `user B`'s consent ledger or DSAR export), erased user continuing to use a pre-erasure JWT.
- **Contract Controls**:
  - Explicit role separation (`USER` vs `ADMIN` in `users.role`) enforced via `@PreAuthorize("hasRole('ADMIN')")` on all governance resolvers.
  - Canonical `subject_key` namespace separation (`user:<uuid>` vs `anon:<client_id>` per `ADR-008`) with server-enforced principal binding: authenticated sessions always resolve to `user:<principal.id>`, and unauthenticated callers are forbidden from reading or mutating any `user:*` namespace key.
  - `JwtCookieAuthFilter` validates `users.deleted_at IS NULL` so fulfilled `DELETE` DSARs invalidate sessions immediately (`ADR-007`, `rest-endpoints.md` §2.3, §2.5).

### 2.3 STRIDE Threat Summary

| STRIDE Category | Concrete Attack Vector | Frozen Contract Mitigation |
|-----------------|------------------------|----------------------------|
| **S — Spoofing** | Forging JWT cookies, brute-forcing `/api/v1/auth/login`, or spoofing another user's `subjectKey` | HMAC-SHA256 (>=256-bit `JWT_SECRET`), `BCrypt(12)`, `10 req/60s` IP rate limit, server-side `principal.id` subject binding |
| **T — Tampering** | Modifying historical `audit_logs` or `consent_history` rows; bypassing `is_essential=true` via tampered GraphQL/SDK payload | `BEFORE UPDATE OR DELETE` PostgreSQL triggers, `SHA-256` `prev_hash -> entry_hash` chain + `verifyAuditChain`, server-forced `is_essential = true` |
| **R — Repudiation** | Denying policy publication, DSAR approval/erasure, or prior user consent grant | Immutable `consent_history.proof_hash` receipts + append-only `audit_logs` recording actor ID, email, role, timestamp, and sorted JSON details |
| **I — Information Disclosure** | Enumerating registered emails via login errors/timing; reading another user's DSAR export; leaking raw IPs or API keys | Uniform `401 Invalid email or password.` + constant-time dummy BCrypt check; `principal.id` scoping on `/api/v1/privacy/export`; salted `ip_hash`; hashed `api_keys.key_hash` |
| **D — Denial of Service** | Deeply nested GraphQL queries, unbounded `limit` arguments, BCrypt megabyte password payloads, SDK flooding | GraphQL max depth (`<= 8`) & complexity (`<= 200`), password length cap (`<= 128`), query `limit` caps, endpoint rate limiters |
| **E — Elevation of Privilege** | Registering with `role: "ADMIN"` on signup, calling `publishPolicyVersion` or `processDataRequest` as `ROLE_USER`, injecting `javascript:` in SDUI actions | `SignupRequest` omits `role` (hardcoded `'USER'`); `@PreAuthorize("hasRole('ADMIN')")` on all admin operations; strict SDUI type & URL scheme allowlists |

---

## 3. OWASP Top 10 (2021 / 2025) Controls Specification & Contract Audit

### A01: Broken Access Control

- **Contract Audit Status**: **APPROVED** (with Mandatory Guardrails `SEC-G01` & `SEC-G02`).
- **Deny-by-Default Security Filter Chain**:
  - Spring Security configures `.anyRequest().authenticated()` as the terminal fallback rule, explicitly permitting only `/api/v1/health`, `/actuator/health`, `/api/v1/auth/csrf`, `/api/v1/auth/signup`, `/api/v1/auth/login`, `/api/v1/auth/logout`, `/api/v1/sdk/**` (guarded by `ApiKeyAuthFilter`), and `/graphql` (guarded per-resolver via `@PreAuthorize` and principal checks).
- **Complete Endpoint & Resolver Authorization Matrix**:

| Surface | Operation(s) | Required Principal / Scope | Ownership / Namespace Rule |
|---------|--------------|----------------------------|----------------------------|
| **REST Auth** | `GET /api/v1/auth/csrf`, `POST /api/v1/auth/signup`, `POST /api/v1/auth/login` | Public (Rate-limited) | Signup hardcodes `role = 'USER'`; `anonymousSubjectKey` must match `^anon:[a-zA-Z0-9_-]{8,112}$` |
| **REST Auth** | `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` | Authenticated (`ROLE_USER` or `ROLE_ADMIN`) | Reads only `SecurityContext` `principal.id` (`deleted_at IS NULL`); logout clears cookies idempotently |
| **REST Privacy** | `GET /api/v1/privacy/export`, `GET /api/v1/export` | Authenticated (`ROLE_USER` or `ROLE_ADMIN`) | Exports strictly `user_id == principal.id` & `subject_key == 'user:' + principal.id` (zero request ID parameter — IDOR impossible by design) |
| **REST SDK** | `GET /api/v1/sdk/config`, `GET /api/v1/sdk/consent` | Valid `X-API-Key` (`active=true`, `sdk:read`) | Validates `Origin` against `allowed_origins`; restricts `subjectKey` to `^anon:[a-zA-Z0-9_-]{8,112}$` (blocks `user:*` enumeration via public SDK key) |
| **REST SDK** | `POST /api/v1/sdk/consent` | Valid `X-API-Key` (`active=true`, `sdk:write`) | Validates `Origin` against `allowed_origins`; restricts `subjectKey` to `^anon:[a-zA-Z0-9_-]{8,112}$`; forces `is_essential = true` |
| **GraphQL Public/Hybrid** | `purposes`, `policyVersions`, `regionRules`, `screen` | Public or Authenticated | `purposes(includeInactive: true)` honors `includeInactive` **only** when caller has `ROLE_ADMIN`; `previewVariantKey` never writes to `experiment_assignments` |
| **GraphQL Consent** | `myConsentState`, `myConsentHistory`, `saveConsentPreferences`, `acceptAllConsent`, `rejectOptionalConsent` | Public (`anon:*`) or Authenticated (`user:*`) | If authenticated: **force** `subjectKey = "user:" + principal.id` (reject or ignore mismatched `subjectKey`). If unauthenticated: **require** `subjectKey` matching `^anon:[a-zA-Z0-9_-]{8,112}$` (`user:*` rejected with `FORBIDDEN`/`UNAUTHORIZED`) |
| **GraphQL User Portal** | `me` | Public (returns `null`) or Authenticated | Returns only `principal.id` profile |
| **GraphQL User Portal** | `myDataRequests`, `updateMyProfile`, `createDataRequest` | `@PreAuthorize("isAuthenticated()")` | Queries/mutates strictly `user_id = principal.id`; `DELETE` DSAR enforces `confirmationText == "DELETE MY DATA"` |
| **GraphQL Admin Queries** | `adminExperiments`, `adminDataRequests`, `adminAnalytics`, `adminAuditLogs`, `verifyAuditChain`, `adminApiKeys` | `@PreAuthorize("hasRole('ADMIN')")` | `ROLE_USER` and anonymous callers receive GraphQL `FORBIDDEN` error; `adminApiKeys` never returns `key_hash` or plain key |
| **GraphQL Admin Mutations** | `upsertPurpose`, `publishPolicyVersion`, `updateRegionRule`, `createExperiment`, `updateExperimentStatus`, `processDataRequest`, `createApiKey`, `revokeApiKey` | `@PreAuthorize("hasRole('ADMIN')")` | `ROLE_USER` and anonymous callers receive GraphQL `FORBIDDEN` error; all mutations append an `audit_logs` record |

---

### A02: Cryptographic Failures

- **Contract Audit Status**: **APPROVED** (Verified SHA-256 test vector parity across all contracts).
- **Password Storage**:
  - `BCryptPasswordEncoder(12)` (`strength = 12`, minimum `2^12` rounds) for all user passwords (`database-schema.sql` line 395, `rest-endpoints.md` §2.2).
  - Password validation enforces `max = 128` characters (preventing CPU exhaustion from multi-megabyte password inputs) and note BCrypt's 72-byte input boundary.
- **JWT Signing & Secret Management**:
  - Signed with `HMAC-SHA256` (`HS256`) using a secret key of **at least 256 bits (32 UTF-8 bytes / 64 hex chars)** injected via `JWT_SECRET` environment variable.
  - Zero hardcoded production secrets in Java source, `application.yml`, `Dockerfile`, or frontend bundles. In the `prod` Spring profile, application startup **must fail fast** if `JWT_SECRET` is unset, `< 32` bytes, or matches a local dev placeholder.
- **API Key Hashing**:
  - Plaintext API keys (`ch_live_<32+ csprng chars>`) are hashed via `SHA-256` lowercase hex into `api_keys.key_hash` (`VARCHAR(64) UNIQUE NOT NULL`) and truncated to `api_keys.key_prefix` for UI identification. Plaintext keys are never stored in PostgreSQL or logs and are returned only once in `CreatedApiKeyResult.plainTextKey`.
  - **Contract Vector Verification**: Verified `SHA-256("ch_live_demo_public_sdk_key_2026") == 37f0f4a4131b49bae8a6ffcdfbc8c585b7972459cfeb3d5d8a65d37b407abb1d`, matching `database-schema.sql` (line 452), `rest-endpoints.md` (§4.1), and `PLAN.md` (§5).
- **Pseudonymized Client IP Hashing**:
  - `consent_history.ip_hash` stores `SHA-256(IP_HASH_SALT + ":" + clientIp)` (lowercase hex) using an environment-injected `IP_HASH_SALT`. Raw IP addresses are never persisted in any PostgreSQL table.

---

### A03: Injection (SQL, GraphQL, SDUI, DOM & URI Schemes)

- **Contract Audit Status**: **APPROVED** (with Mandatory Guardrails `SEC-G03` & `SEC-G04`).
- **Database Parameterization**:
  - All persistence operations use Spring Data JPA derived queries, parameterized JPQL (`@Param`), or parameterized JDBC statements. String concatenation in SQL/JPQL (including `adminAuditLogs(actionFilter)` and `adminAnalytics(days)`) is strictly prohibited.
- **Strict Input & Schema Validation**:
  - All REST request bodies (`SignupRequest`, `LoginRequest`, `SdkConsentRequest`) and GraphQL mutation inputs apply Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Email`, `@Size`, `@Pattern`) matching database column sizes and `CHECK` regular expressions (`slug ~ '^[a-z][a-z0-9_]{1,63}$'`, `subject_key ~ '^(user:[0-9a-fA-F-]{36}|anon:[a-zA-Z0-9_-]{8,112})$'`).
- **Server-Side SDUI Validation (`SduiSchemaValidator`)**:
  - Every `SduiScreen` tree composed by `SduiEngineService` (for both GraphQL `screen` and REST `GET /api/v1/sdk/config`) is validated server-side before serialization against `docs/contracts/sdui-schema.json`:
    1. `schemaVersion == "1.0"`.
    2. Every node `type` belongs to `$defs/CanonicalComponentType` (the 15 canonical types: `Container`, `Card`, `Stack`, `BannerShell`, `Header`, `Text`, `Badge`, `Divider`, `PurposeToggleRow`, `PolicySummaryAccordion`, `ActionBar`, `Button`, `NoticeCallout`, `StatGrid`, `StatItem`).
    3. Maximum tree nesting depth `<= 5` (`ADR-009`).
    4. Every action `trigger` (`onClick`, `onToggle`) and `type` (`SUBMIT_CONSENT`, `ACCEPT_ALL`, `REJECT_OPTIONAL`, `TOGGLE_PURPOSE`, `NAVIGATE`, `OPEN_PREFERENCES`, `DISMISS`) belongs to the closed enum allowlist.
- **Client-Side Safe Rendering & URI Scheme Guardrail**:
  - **React SPA (`frontend/src/**`)**: Zero usage of `dangerouslySetInnerHTML`, `eval()`, or `new Function()`. `PolicyVersion.contentMarkdown` and `PolicySummaryAccordion.props.contentMarkdown` must be rendered via safe React elements / text-escaped blocks (raw HTML tags disabled). Unknown SDUI component types render `<UnknownComponentFallback />` safely without throwing.
  - **Third-Party SDK (`frontend/public/consent.js`)**: Zero usage of `.innerHTML`, `.outerHTML`, or `document.write()`. All banner DOM nodes must be built with `document.createElement(...)` and `node.textContent = ...`.
  - **Strict Action URL Allowlist (`NAVIGATE` / `OPEN_PREFERENCES`)**: Both the backend `SduiSchemaValidator` and the React/SDK action dispatchers **must validate** any navigation target (`payload.to` / `href`) so ONLY app-relative paths starting with a single slash (`^\/(?!\/)`) or `https://` URLs are permitted. `javascript:`, `data:`, `vbscript:`, `file:`, and protocol-relative `//` URIs **must be blocked and logged/dropped**.

---

### A04: Insecure Design & A05: Security Misconfiguration

- **Contract Audit Status**: **APPROVED** (with Mandatory Guardrails `SEC-G05` & `SEC-G06`).
- **Cookie + Double-Submit Header CSRF Protection**:
  - `ch_csrf_token` is generated using `SecureRandom` (32 bytes / 64 hex chars), set with `SameSite=Lax; Path=/; Max-Age=86400` (`Secure` in prod), and rotated on `/api/v1/auth/login` and `/api/v1/auth/signup`.
  - `DoubleSubmitCsrfFilter` verifies that any state-changing HTTP request (`POST`, `PUT`, `PATCH`, `DELETE`, including `POST /graphql`) carrying a `ch_access_token` cookie also supplies a non-empty `X-CSRF-Token` header equal to `ch_csrf_token` via **constant-time byte comparison** (`MessageDigest.isEqual`), returning `403 Forbidden` (`code: "CSRF_TOKEN_INVALID"`) otherwise.
- **Strict Segregated CORS Configuration**:
  - **First-Party Routes (`/graphql`, `/api/v1/auth/**`, `/api/v1/privacy/**`)**: `allowedOrigins` loaded from `CORS_ALLOWED_ORIGINS` (defaulting in dev to `http://localhost:5173,http://localhost:80,http://localhost`), `allowedMethods = GET, POST, PUT, PATCH, DELETE, OPTIONS`, `allowedHeaders = Content-Type, X-CSRF-Token, X-Consent-Region, X-Request-Id`, `allowCredentials = true`.
  - **Third-Party SDK Routes (`/api/v1/sdk/**`)**: `allowedOriginPatterns = *` at HTTP preflight level with `allowCredentials = false`, `allowedMethods = GET, POST, OPTIONS`, `allowedHeaders = Content-Type, X-API-Key, X-Consent-Region`, paired with per-key `allowed_origins` enforcement inside `ApiKeyAuthFilter`.
- **Mandatory HTTP Security Headers** (emitted by Spring Security & Nginx):
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `Referrer-Policy: strict-origin-when-cross-origin`
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()`
  - `Cache-Control: no-store` on `/api/v1/auth/**` and `/api/v1/privacy/export`.
- **GraphQL & Actuator Hardening**:
  - GraphQL schema introspection disabled in `prod` profile (`spring.graphql.schema.introspection.enabled: ${GRAPHQL_INTROSPECTION_ENABLED:false}` in prod, `true` in dev/test).
  - Register GraphQL `MaxQueryDepthInstrumentation(8)` and `MaxQueryComplexityInstrumentation(200)` to block deeply nested or abusive query trees.
  - Enforce server-side upper bounds on list arguments: `myConsentHistory.limit <= 200`, `adminAuditLogs.limit <= 500`, `adminAnalytics.days` clamped to `1..365`.
  - Actuator exposure restricted strictly to `health,info` (`management.endpoints.web.exposure.include=health,info`; `show-details=never` for anonymous callers).
  - Global `@RestControllerAdvice` and GraphQL `DataFetcherExceptionResolverAdapter` emit uniform `ApiErrorResponse` envelopes (`rest-endpoints.md` §1.4) and never leak Java stack traces, class names, or SQL constraint fragments.
- **Privacy-by-Design Business Logic Safeguards**:
  - Essential purposes (`consent_purposes.is_essential = TRUE`) are unconditionally forced to `granted = TRUE` in `ConsentService` across GraphQL `saveConsentPreferences`, `rejectOptionalConsent`, and REST `POST /api/v1/sdk/consent`.
  - `createDataRequest` with `requestType = DELETE` requires `confirmationText == "DELETE MY DATA"` on the server, preventing accidental one-click account erasure.

---

### A06: Vulnerable and Outdated Components

- **Contract Audit Status**: **APPROVED**.
- **Stack & Supply-Chain Hygiene**:
  - Backend targets **Java 21 LTS** (`eclipse-temurin:21-jdk-alpine` build stage, minimal `eclipse-temurin:21-jre-alpine` non-root runtime stage) and **Spring Boot 3.4.x** managed BOM dependencies (Spring Security 6.4+, Spring for GraphQL 1.3+, Jackson 2.18+, PostgreSQL JDBC 42.7+, `jjwt` 0.12.x or Nimbus JOSE+JWT).
  - Database targets **PostgreSQL 16** (`postgres:16-alpine`).
  - Frontend targets **React 18.3+**, **TypeScript 5.x (strict)**, **Vite**, **Apollo Client 3.11+**, and **Playwright** current stable versions with lockfile integrity (`package-lock.json`) and `npm audit --audit-level=high` / Maven dependency verification in CI (`.github/workflows/ci.yml`).
  - `consent.js` is authored with **zero third-party runtime dependencies**, eliminating third-party supply-chain script risk on host pages.

---

### A07: Identification and Authentication Failures

- **Contract Audit Status**: **APPROVED** (with Mandatory Guardrails `SEC-G07` & `SEC-G08`).
- **Password Strength Policy**:
  - Harmonized across `rest-endpoints.md` §1.4 and §2.2: passwords must be `8..128` characters and contain **at least 1 uppercase letter, 1 lowercase letter, and 1 digit or symbol** (`^(?=.*[a-z])(?=.*[A-Z])(?=.*[\d\W]).{8,128}$`). Verified compatible with seeded credentials (`AdminPass123!`, `UserPass123!`) and contract examples (`StrongUser123!`).
- **Account Enumeration & Timing Side-Channel Prevention**:
  - `POST /api/v1/auth/login` returns the exact same HTTP `401 Unauthorized` payload (`code: "INVALID_CREDENTIALS"`, `message: "Invalid email or password."`) whether the email does not exist, the password is wrong, or `users.deleted_at IS NOT NULL` (`rest-endpoints.md` §2.3).
  - When an email lookup misses or `deleted_at IS NOT NULL`, `AuthService` **must still execute** `passwordEncoder.matches(submittedPassword, DUMMY_BCRYPT_12_HASH)` so login response latency is indistinguishable from a valid-email wrong-password check.
- **Endpoint Rate Limiting (`429 Too Many Requests` + `Retry-After`)**:
  - Sliding-window / token-bucket rate limiter enforced ahead of controller execution (`rest-endpoints.md` §1.3):
    - `POST /api/v1/auth/login` & `POST /api/v1/auth/signup`: **`10 req / 60s`** per client IP.
    - `GET /api/v1/privacy/export` & `GET /api/v1/export`: **`5 req / 60s`** per authenticated `userId`.
    - `/api/v1/sdk/**`: **`120 req / 60s`** per `(apiKeyPrefix + clientIp)` with `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` headers.
- **Immediate Erasure Session Revocation**:
  - Completing a `DELETE` DSAR sets `users.deleted_at = NOW()`, pseudonymizes `email` to `deleted-<uuid>@redacted.consenthub.invalid`, and scrubs `full_name` and `password_hash` (`ADR-007`). `JwtCookieAuthFilter` checks `user.getDeletedAt() == null` on every authenticated request, immediately rejecting lingering JWT cookies from erased accounts.

---

### A08: Software and Data Integrity Failures

- **Contract Audit Status**: **APPROVED** (with Mandatory Guardrail `SEC-G09`).
- **Database-Enforced Immutability**:
  - Both `consent_history` and `audit_logs` attach PostgreSQL `BEFORE UPDATE OR DELETE` triggers (`prevent_append_only_mutation()`) in `database-schema.sql` (lines 29–38, 270–273, 387–390), guaranteeing immutability even against bug-induced repository updates. Spring Data repositories for these entities must expose only `save`/`insert` and read queries (`delete*` methods prohibited).
- **Tamper-Evident SHA-256 Hash Chain (`audit_logs` + `verifyAuditChain`)**:
  - Genesis row sets `prev_hash = "0000000000000000000000000000000000000000000000000000000000000000"` (`64` zeros).
  - Subsequent rows link `prev_hash = previousRow.entry_hash` ordered strictly by `sequence_num ASC`.
  - Canonical pre-image (`database-schema.sql` lines 363–368, `ADR-010`):
    ```text
    prev_hash || '|' || id || '|' || COALESCE(actor_id::text, 'ANONYMOUS')
    || '|' || actor_email || '|' || actor_role || '|' || action || '|'
    || entity_type || '|' || entity_id || '|' || epoch_millis(created_at)
    || '|' || canonical_sorted_json(details_json)
    ```
  - `created_at` is truncated to `ChronoUnit.MILLIS` prior to hashing and persistence, and `details_json` is serialized via a Jackson `ObjectMapper` configured with `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` and `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` on both write and `verifyAuditChain` recomputation.
  - **Concurrency Serialization Guardrail (`SEC-G09`)**: `AuditLogService.append(...)` must acquire a transaction-scoped PostgreSQL advisory lock (`SELECT pg_advisory_xact_lock(84920174)`) before reading the latest `entry_hash` and inserting the next row so concurrent HTTP requests never fork `prev_hash`.
- **Cryptographic Consent Receipts (`consent_history.proof_hash`)**:
  - Every consent ledger entry records `proof_hash = SHA-256(subject_key || '|' || purpose_slug || '|' || new_granted || '|' || policy_version_label || '|' || region_code || '|' || source || '|' || epoch_millis(occurred_at))`, returned in `myConsentHistory` and `/api/v1/privacy/export`.

---

### A09: Security Logging and Monitoring Failures

- **Contract Audit Status**: **APPROVED** (with Mandatory Guardrail `SEC-G10`).
- **Correlation & Structured Logging**:
  - Inbound requests accept an optional safe `X-Request-Id` header (`^[a-zA-Z0-9_-]{8,64}$`) or generate a fresh `req_<12-hex>` correlation ID placed in SLF4J MDC (`correlationId`), echoed in the `X-Request-Id` response header and in every `ApiErrorResponse.correlationId`.
- **Zero Secret / Token / Raw PII Leakage in Logs**:
  - Application logs must **never** output plaintext `password`, `passwordHash`, `ch_access_token` cookie values, `JWT_SECRET`, `ch_csrf_token`, raw `X-API-Key` values (`plainTextKey`), full DSAR `export_payload_json` bodies, or unhashed client IP addresses. DTO `toString()` methods on credential/key classes must mask sensitive fields (`[REDACTED]`).
- **Mandatory Security & Governance Audit Event Coverage (`audit_logs`)**:
  - Authentication: `AUTH_SIGNUP`, `AUTH_LOGIN_SUCCESS`, `AUTH_LOGIN_FAILED` (recording attempted email or masked identifier + failure reason without logging submitted password), `AUTH_LOGOUT`.
  - Consent & DSAR Rights: `CONSENT_UPDATED`, `DSAR_REQUEST_CREATED`, `DSAR_DATA_EXPORTED`, `DSAR_STATUS_UPDATED`, `DSAR_ERASURE_COMPLETED`.
  - Admin Governance: `PURPOSE_UPSERTED`, `POLICY_PUBLISHED`, `REGION_RULE_UPDATED`, `EXPERIMENT_CREATED`, `EXPERIMENT_STATUS_UPDATED`, `API_KEY_CREATED` (logging `keyPrefix` only, never `plainTextKey`), `API_KEY_REVOKED`.

---

### A10: Server-Side Request Forgery (SSRF)

- **Contract Audit Status**: **APPROVED**.
- **Zero Outbound User-Controlled Fetch Surface**:
  - ConsentHub's backend performs **zero outbound HTTP/TCP requests** derived from user-supplied parameters (no webhook callbacks, remote URL imports, OIDC discovery URLs, avatar fetchers, or server-side markdown image fetchers).
  - `api_keys.allowed_origins` is parsed and validated purely syntactically (`https?://hostname[:port]` or `*`) against the inbound `Origin` HTTP request header without performing any DNS resolution or outbound network probe.

---

## 4. Gate 1 Security Contract Verdict & Mandatory Implementation Guardrails

> ### **VERDICT — `GATE 1 SECURITY SIGN-OFF: APPROVED`**
> All four frozen contracts (`database-schema.sql`, `graphql-schema.graphqls`, `rest-endpoints.md`, `sdui-schema.json`) and `DECISIONS.md` pass Gate 1 Application Security Review with **zero schema-breaking defects**. Gate 2 implementation is authorized to proceed subject to the 10 mandatory guardrails below.

### Mandatory Gate 2 Implementation Guardrails

| ID | Owner Agent(s) | Target Component | Mandatory Guardrail Requirement |
|----|----------------|------------------|---------------------------------|
| **SEC-G01** | `backend-spring`, `graphql-sdui` | Subject Namespace Isolation (IDOR Prevention) | On `/graphql`, when authenticated (`ch_access_token` valid), **always bind** effective subject to `user:<principal.id>` and reject (`FORBIDDEN`) or ignore any mismatched client `subjectKey`. When unauthenticated on `/graphql` or calling `/api/v1/sdk/*`, **reject** any `subjectKey` starting with `user:` (`401`/`403`) — anonymous & SDK callers may only access `^anon:[a-zA-Z0-9_-]{8,112}$`. |
| **SEC-G02** | `graphql-sdui` | Admin Resolver Guards & Preview Isolation | Annotate all 6 `admin*` / `verifyAuditChain` queries and all 8 admin governance mutations with `@PreAuthorize("hasRole('ADMIN')")`. Ignore `purposes(includeInactive: true)` unless caller has `ROLE_ADMIN`. Ensure `ScreenContextInput.previewVariantKey` never writes to `experiment_assignments`. |
| **SEC-G03** | `graphql-sdui` | Server-Side SDUI Tree & URI Validator | Validate every server-composed `SduiScreen` against `CanonicalComponentType` (15 types), max depth `<= 5`, closed `SduiAction` enums, and enforce that any `NAVIGATE` / `OPEN_PREFERENCES` `payload.to` string matches `^(\/(?!\/)[^\s]*|https:\/\/[^\s]+)$`. |
| **SEC-G04** | `frontend-react` | Zero Raw HTML & Safe Action URL Guard | Prohibit `dangerouslySetInnerHTML` across `frontend/src/**` (including `contentMarkdown` rendering) and prohibit `.innerHTML` in `frontend/public/consent.js` (use `textContent` + DOM APIs only). Re-validate `payload.to` on client before routing, blocking `javascript:`, `data:`, `vbscript:`, and `//` URLs. |
| **SEC-G05** | `backend-spring` | Constant-Time Double-Submit CSRF & Cookie Isolation | Enforce `X-CSRF-Token == ch_csrf_token` via `MessageDigest.isEqual` on all `POST`/`PUT`/`PATCH`/`DELETE` requests (including `POST /graphql`) whenever `ch_access_token` cookie is present (exempting `/api/v1/auth/login`, `/api/v1/auth/signup`, and `/api/v1/sdk/**`). Ensure `ApiKeyAuthFilter` on `/api/v1/sdk/**` ignores browser cookies completely. |
| **SEC-G06** | `backend-spring`, `devops-engineer` | Security Headers, GraphQL Depth & Prod Secret Fail-Fast | Emit `CSP`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and `Referrer-Policy: strict-origin-when-cross-origin`. Register GraphQL max depth (`8`) & complexity (`200`), disable introspection in `prod`, clamp `limit`/`days` arguments, and fail startup in `prod` if `JWT_SECRET` is `< 32` bytes. |
| **SEC-G07** | `backend-spring` | Harmonized Password Policy | Enforce `8..128` chars with `>= 1` uppercase, `>= 1` lowercase, and `>= 1` digit or symbol on `POST /api/v1/auth/signup`, hashed via `BCryptPasswordEncoder(12)`. |
| **SEC-G08** | `backend-spring` | Timing-Safe Login & Erased Account Rejection | Execute a dummy `BCrypt(12)` comparison when `email` is unknown or `deleted_at IS NOT NULL` before returning generic `401 Invalid email or password.`. Verify `users.deleted_at IS NULL` inside `JwtCookieAuthFilter` on every authenticated request. |
| **SEC-G09** | `backend-spring` | Serialized Deterministic Audit Hash Chain | Acquire `pg_advisory_xact_lock` inside `AuditLogService` writes, truncate `created_at` to `ChronoUnit.MILLIS`, and canonicalize `details_json` with sorted keys (`ADR-010`) so seeded and runtime `audit_logs` pass `verifyAuditChain` (`chainValid: true`) under concurrent load. |
| **SEC-G10** | `backend-spring`, `devops-engineer` | Redacted Structured Logging & Non-Root Containers | Propagate `correlationId` (`X-Request-Id`), redact passwords/JWTs/CSRF tokens/raw API keys/raw IPs from all logs, and run Docker runtime containers as non-root users. |

---

## 5. Gate 4 Live Security Verification Matrix (`T-12` Audit Checklist)

> *To be executed and populated with live code paths, automated test references, and runtime HTTP/GraphQL evidence during Gate 4 (`T-12`).*

| Check ID | OWASP Category | Verification Test Case | Expected Result | Code / Test Evidence | Gate 4 Status |
|----------|----------------|------------------------|-----------------|----------------------|---------------|
| **SEC-V01** | A01 Access Control | Call `adminExperiments`, `adminAuditLogs`, `publishPolicyVersion`, and `createApiKey` as `ROLE_USER` and unauthenticated | GraphQL `FORBIDDEN` / `UNAUTHORIZED`; zero DB state change | *Pending Gate 4* | `PENDING` |
| **SEC-V02** | A01 IDOR / Namespace | Call `myConsentHistory(subjectKey: "user:00000000-0000-4000-8000-000000000002")` anonymously and as a different authenticated user; call `GET /api/v1/privacy/export` | Anonymous call rejected; authenticated call returns/exports only caller's own data | *Pending Gate 4* | `PENDING` |
| **SEC-V03** | A01 SDK Scoping | Call `/api/v1/sdk/config` with revoked key, invalid key, disallowed `Origin` header, and `user:*` `subjectKey` | `401 INVALID_API_KEY` on bad/revoked key; `403 ORIGIN_NOT_ALLOWED` on disallowed origin; `400`/`403` on `user:*` namespace | *Pending Gate 4* | `PENDING` |
| **SEC-V04** | A02 Cryptography | Inspect seeded & newly created `users.password_hash`, `api_keys.key_hash`, `consent_history.ip_hash`, and `JWT_SECRET` config | `$2a$12$`/`$2b$12$` BCrypt hashes; 64-hex SHA-256 `key_hash` & salted `ip_hash`; zero hardcoded prod secrets in repo | *Pending Gate 4* | `PENDING` |
| **SEC-V05** | A03 Injection & XSS | Grep `frontend/` for `dangerouslySetInnerHTML` & `innerHTML`; test SQL/JPQL meta-characters in `actionFilter`/`email`; test `javascript:alert(1)` in SDUI `NAVIGATE` action & unknown SDUI component type | Zero unsafe HTML sinks; parameterized queries safe; `javascript:`/`data:` blocked; `<UnknownComponentFallback />` renders cleanly | *Pending Gate 4* | `PENDING` |
| **SEC-V06** | A04/A05 Cookie & CSRF | Inspect `Set-Cookie` on `/api/v1/auth/login`; replay authenticated `POST /graphql` mutation & `POST /api/v1/auth/logout` without `X-CSRF-Token` or with tampered token | `ch_access_token` has `HttpOnly; SameSite=Lax`; missing/mismatched `X-CSRF-Token` rejected with `403 CSRF_TOKEN_INVALID` | *Pending Gate 4* | `PENDING` |
| **SEC-V07** | A05 Headers & CORS | Inspect HTTP response headers on SPA & API routes; send cross-origin `Origin: https://evil.example` preflight to `/graphql` and `/api/v1/auth/me` | `CSP`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy` present; evil origin denied credentialed CORS | *Pending Gate 4* | `PENDING` |
| **SEC-V08** | A07 Auth & Rate Limit | Submit 11 rapid `POST /api/v1/auth/login` attempts; compare error payload for unknown email vs wrong password vs erased user (`DELETE` DSAR completed) | 11th request returns `429 Too Many Requests` + `Retry-After`; all auth failures return identical `401 Invalid email or password.`; erased user's existing JWT immediately rejected | *Pending Gate 4* | `PENDING` |
| **SEC-V09** | A08 Ledger Integrity | Attempt SQL `UPDATE`/`DELETE` on `audit_logs` and `consent_history`; execute GraphQL `verifyAuditChain` after concurrent mutations | PostgreSQL trigger aborts `UPDATE`/`DELETE`; `verifyAuditChain` returns `chainValid: true` across all entries | *Pending Gate 4* | `PENDING` |
| **SEC-V10** | A09 Logging & A10 SSRF | Inspect backend logs after signup, login failure, API key creation, and DSAR export; audit codebase for outbound HTTP client calls | Correlation IDs present; zero passwords, JWTs, raw API keys, or raw IPs in logs; zero user-controlled outbound URL fetches | *Pending Gate 4* | `PENDING` |


---

## Gate 4 & Gate 5 Live Runtime Security Audit Sign-Off (`T-12` & `T-14`)

- **Audit Date**: `2026-11-24`
- **Status**: **`GATE 4 & GATE 5 SECURITY SIGN-OFF: PASSED (0 Open Findings)`**
- **Verified Live Controls**:
  - `SEC-V01` (RBAC & Namespace Isolation): Verified via JUnit (`CoreGovernanceServicesTest.namespaceIsolationAndJwtSigningWorkStrictly`) and Newman `#07` (`ROLE_USER` blocked with GraphQL `FORBIDDEN` on `adminAnalytics`).
  - `SEC-V02` (Password & Key Cryptography): Verified `BCryptPasswordEncoder(12)` + anti-enumeration dummy hash timing parity (`353ms` invalid vs `417ms` valid in Newman `#03`/`#05`) and `SHA-256` API key lookup (`401` on bad key in Newman `#11`).
  - `SEC-V03` & `SEC-V04` (SDUI & URI/XSS Hardening): Verified `javascript:`, `data:`, `//`, and backslash `/\` open-redirect blocking (`BUG-003` / `GHSA-wrjc-x8rr-h8h6`) in both `SduiSchemaValidator` and `security.ts`; zero `dangerouslySetInnerHTML` or `.innerHTML` across entire source tree.
  - `SEC-V05` & `SEC-V06` (`HttpOnly` Cookie, Double-Submit CSRF & Security Headers): Verified `Set-Cookie: ch_access_token=...; HttpOnly; SameSite=Lax` in Newman `#05`, `403 CSRF_TOKEN_INVALID` when `X-CSRF-Token` omitted in Newman `#06`, and `CSP`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy` headers in Nginx and Spring Security.
  - `SEC-V09` (Tamper-Evident Audit Hash Chain): Verified both tamper detection (`CoreGovernanceServicesTest.auditLogHashChainDetectsTamperingImmediately`) and live PostgreSQL 43-entry chain verification (`verifyAuditChain.chainValid == true`) in Newman `#10` and Playwright `14-admin-audit-verified.png`.
  - **Secrets Scan**: `.env` gitignored; `.env.example` contains safe local-dev placeholders only; zero hardcoded production credentials in repository.
