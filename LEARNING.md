# ConsentHub — Engineering Learning Guide & Interview Prep (`LEARNING.md`)

Welcome! If your background is primarily **JavaScript / TypeScript / React / Node.js**, this guide translates every backend, GraphQL, Server-Driven UI, security, and privacy engineering concept in ConsentHub into plain English—mapped directly to the files in this repository so you can walk an interviewer through the architecture with total confidence.

---

## Part 1: Plain-English Concept Guide

### 1. Spring Boot 3 & Java 21 (Mental Model for Node/TS Developers)

Think of **Spring Boot** as a batteries-included, strongly-typed enterprise version of NestJS or Express + Prisma + Passport:

| Express / NestJS Concept | Spring Boot Equivalent in ConsentHub | Example File |
|--------------------------|--------------------------------------|--------------|
| `package.json` | [`backend/pom.xml`](file:///d:/The%20Company/Netflix/backend/pom.xml) (Maven dependencies & plugins) | `backend/pom.xml` |
| `.env` / config module | [`application.yml`](file:///d:/The%20Company/Netflix/backend/src/main/resources/application.yml) + `@ConfigurationProperties` record [`AppProperties.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/config/AppProperties.java) | `config/AppProperties.java` |
| Express Middleware | `OncePerRequestFilter` in [`SecurityConfig.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/config/SecurityConfig.java) | `security/JwtCookieAuthFilter.java` |
| Route Controller | `@RestController` (`@GetMapping`, `@PostMapping`) | [`AuthController.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/web/AuthController.java) |
| Injectable Service | `@Service` + constructor dependency injection + `@Transactional` | [`ConsentService.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/service/ConsentService.java) |
| Prisma / TypeORM Model + Repo | `@Entity` class + `JpaRepository<Entity, UUID>` interface | [`ConsentRecordRepository.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/repository/ConsentRecordRepository.java) |
| Prisma Migrate | **Flyway** numbered SQL files (`V1__init_schema.sql`, `V2__seed_demo_data.sql`) | `resources/db/migration/` |
| TypeScript `readonly` type | Java 21 **`record`** (`public record UserProfileDto(...) {}` — immutable data carrier with auto-generated getters, `equals`, and `hashCode`) | `domain/dto/UserProfileDto.java` |

**Key Annotations to Know**:
- `@Transactional`: Opens a PostgreSQL database transaction before the method starts; commits if it succeeds, and automatically rolls back every SQL write if an exception is thrown. `@Transactional(readOnly = true)` optimizes read queries so Hibernate skips dirty-checking.
- `@PreAuthorize("hasRole('ADMIN')")`: Enforced at method entry before a single line of business logic runs—if the caller is not an authenticated admin, Spring Security rejects the call with `403 FORBIDDEN`.

---

### 2. Why Combine GraphQL AND REST?

Real production platforms rarely use *only* GraphQL or *only* REST—each shines at specific workloads:
- **GraphQL (`/graphql`)** powers our **React SPA & Server-Driven UI**:
  - A single typed schema ([`schema.graphqls`](file:///d:/The%20Company/Netflix/backend/src/main/resources/graphql/schema.graphqls)) lets complex screens fetch exact nested trees (`screen`, `myConsentState`, `adminAnalytics`) in one network round-trip without over-fetching.
  - Spring for GraphQL maps SDL fields to Java methods via `@QueryMapping` and `@MutationMapping` in [`ConsentGraphQlController.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/graphql/ConsentGraphQlController.java).
- **REST (`/api/v1/*`)** handles **protocol-level & third-party flows**:
  - `/api/v1/auth/*`: Setting/clearing `HttpOnly` cookies (`Set-Cookie`) and `204 No Content` logouts fit standard HTTP semantics cleanly.
  - `/api/v1/privacy/export`: Streams a binary/file download (`Content-Disposition: attachment; filename="consenthub-data-export-....json"`), which GraphQL JSON responses aren't designed for.
  - `/api/v1/sdk/*`: Gives third-party websites a tiny, cacheable, curl-friendly API protected by `X-API-Key` without forcing external sites to bundle a GraphQL client.

---

### 3. Server-Driven UI (SDUI) — How It Works & Why Teams Like Netflix Use It

In traditional **Client-Driven UI**, if legal regulations change in Germany, or if the Growth team wants to test new banner copy and button order, engineers must edit React components, redeploy web/iOS/Android/TV apps, and wait for clients to update.

In **Server-Driven UI (SDUI)**:
1. **The Server Composes the Screen**: [`SduiTreeComposer.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/sdui/SduiTreeComposer.java) inspects the user's region (`EU` vs `US_CA` vs `GLOBAL`), active policy version (`v2.0`), current consent state, and sticky A/B experiment variant, then outputs a **JSON tree of UI nodes**:
   ```json
   {
     "id": "btn-accept-all",
     "type": "Button",
     "props": { "label": "Accept All", "variant": "primary" },
     "children": [],
     "actions": [{ "trigger": "onClick", "type": "ACCEPT_ALL", "payload": { "source": "BANNER" } }]
   }
   ```
2. **The Server Validates Before Sending**: [`SduiSchemaValidator.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/sdui/SduiSchemaValidator.java) verifies `schemaVersion == "1.0"`, ensures every component is in our 15-type canonical allowlist, caps tree depth at `<= 5`, and blocks unsafe `javascript:`/`data:` URIs.
3. **The Client Has a Dumb Component Registry**: [`SduiRenderer.tsx`](file:///d:/The%20Company/Netflix/frontend/src/sdui/SduiRenderer.tsx) recursively looks up `node.type` in [`ComponentRegistry.tsx`](file:///d:/The%20Company/Netflix/frontend/src/sdui/ComponentRegistry.tsx) and renders it. If the server sends a brand-new component (`type: "AiVideoConsentCard"`) that an older client doesn't know yet, [`UnknownComponentFallback.tsx`](file:///d:/The%20Company/Netflix/frontend/src/sdui/UnknownComponentFallback.tsx) renders safely instead of crashing the page!

---

### 4. JWT in `HttpOnly` Cookies + Double-Submit CSRF

- **Why NOT `localStorage`?** Any XSS bug or compromised npm package on the page can read `localStorage.getItem('token')` and exfiltrate the user's session.
- **Why `HttpOnly` Cookie?** Setting `HttpOnly; SameSite=Lax; Path=/` on `ch_access_token` ([`CookieService.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/security/CookieService.java)) means browser JavaScript **cannot read** the JWT at all—the browser attaches it automatically.
- **Why CSRF Protection Then?** Because browsers attach cookies automatically, we add **Double-Submit CSRF** ([`DoubleSubmitCsrfFilter.java`](file:///d:/The%20Company/Netflix/backend/src/main/java/io/consenthub/security/DoubleSubmitCsrfFilter.java)):
  1. Server sets a second readable cookie `ch_csrf_token` containing 32 cryptographic random bytes.
  2. Our first-party SPA reads `ch_csrf_token` and copies it into an `X-CSRF-Token` request header on every `POST`/`PUT`/`DELETE` and `/graphql` call.
  3. An attacker's site (`evil.example`) cannot read `ch_csrf_token` across origins (Same-Origin Policy) and cannot send custom headers cross-origin without CORS preflight approval. Our backend compares cookie vs header in constant time (`MessageDigest.isEqual`).

---

### 5. Privacy Consent Models (GDPR vs CCPA) & Tamper-Evident Ledgers

- **GDPR (EU — Strict Prior `OPT_IN`)**: Non-essential purposes (`analytics`, `personalization`, `marketing`, `ads`) MUST default to **`false`** until the user takes an unambiguous affirmative action. Withdrawing consent must be just as easy as giving it (no dark patterns hiding "Reject Optional" three clicks deep!).
- **CCPA/CPRA (California — `OPT_OUT` / "Do Not Sell or Share")**: Businesses may process certain categories with clear notice provided the user has an immediate, symmetric 1-click **"Do Not Sell or Share" / "Reject Optional"** opt-out control.
- **Immutable Proof & Hash Chains**:
  - `consent_records` stores the *current* state (`1 row per subject + purpose`) for fast lookups.
  - `consent_history` stores *every historical change* with a `proof_hash` and is protected by a PostgreSQL trigger (`prevent_append_only_mutation()`) that throws an SQL error if anyone tries `UPDATE` or `DELETE`.
  - `audit_logs` links each row's `prev_hash` to the previous row's `entry_hash` (`SHA-256`), like a lightweight blockchain inside PostgreSQL, verified live by `verifyAuditChain`!

---

## Part 2: 15 Interview Questions & Model Answers

### Q1. Walk me through what happens end-to-end when a first-time user from Germany visits ConsentHub.
**Answer**:
1. On boot, the React app completes `/api/v1/auth/csrf`, generates a pseudonymous visitor ID (`anon:<id>`), and queries GraphQL `screen(name: CONSENT_BANNER, context: { subjectKey, region: EU })`.
2. On the backend, `RegionResolverService` resolves `EU`, and `RegionRuleService` loads the `GDPR` rule (`consentModel = OPT_IN`, `bannerStyle = MODAL`, `showRejectAll = true`).
3. `ConsentService` checks `consent_records` for `anon:<id>`, sees zero prior records (`hasConsented = false`, `needsReconsent = true`), and sets all optional purposes to `false` while keeping `essential` locked `true`.
4. `ExperimentService` computes `SHA-256(experimentId + ":" + subjectKey) mod 100`, deterministically buckets the visitor into `control` or `concise_trust`, and saves an exposure row in `experiment_assignments`.
5. `SduiTreeComposer` builds the modal component tree (`dismissible: false`, symmetric `Accept All` and `Reject Optional` buttons), validates it through `SduiSchemaValidator`, and returns it to React's `SduiRenderer`.

### Q2. Why did you choose Server-Driven UI for consent screens instead of building standard React forms?
**Answer**: Consent flows sit at the intersection of **fast-moving regional regulations** and **high-velocity growth experimentation**. With SDUI, legal copy updates, regional layout switches (`MODAL` for GDPR vs `BANNER` for CCPA), and multi-variant A/B tests live centrally in `SduiTreeComposer` on the backend. Every client (our React portal and third-party `consent.js` consumers) receives consistent, compliant experiences immediately without client redeployments, while keeping client UI logic minimal.

### Q3. What happens in your SDUI architecture if the backend sends a new component type that an older frontend bundle doesn't recognize?
**Answer**: We designed **dual-mode validation** (`ADR-009`). On the backend, `SduiSchemaValidator` enforces a strict canonical allowlist of 15 component types so typos or rogue types never slip out accidentally. On the frontend, `SduiRenderer` treats `node.type` as an open string lookup against `ComponentRegistry`: if an unknown type arrives (for example during a rolling deploy), it renders `UnknownComponentFallback` (`role="status"`) rather than throwing a runtime React error, preserving layout stability and banner usability.

### Q4. How do you prevent Server-Driven UI payloads from becoming an XSS or Open Redirect vector?
**Answer**: Three defense-in-depth layers (`SEC-G03` & `SEC-G04`):
1. Server-side `SduiSchemaValidator` inspects all URL-bearing props and `NAVIGATE` payloads, allowing only app-relative paths (`/portal/...`) or `https://` URLs—explicitly rejecting `javascript:`, `data:`, `vbscript:`, and protocol-relative `//` URIs.
2. React components render all server strings as plain text children (zero `dangerouslySetInnerHTML`), and `consent.js` builds DOM strictly with `document.createElement` and `textContent` (zero `.innerHTML`).
3. Client-side `security.ts` re-validates navigation targets before invoking React Router or `window.location`.

### Q5. How does your A/B experimentation engine guarantee sticky assignment without flickering, including for anonymous visitors?
**Answer**: Instead of `Math.random()`, `ExperimentService.computeDeterministicBucket` hashes `experimentId + ":" + subjectKey` with `SHA-256`, reads the first 4 bytes as an unsigned 32-bit integer `mod 100`, and maps that `0..99` bucket across variant weights. We also persist the assignment in `experiment_assignments` (`UNIQUE(experiment_id, subject_key)`). Even if an admin later adjusts traffic split percentages, already-exposed users read their persisted `variant_key` first so they never flip variants mid-journey.

### Q6. Can an admin accidentally create an A/B experiment variant that violates GDPR (for example, hiding "Reject Optional" or pre-checking marketing)?
**Answer**: No—compliance invariants are enforced structurally in `SduiTreeComposer` and `ConsentService`, below the experimentation layer. Experiment variants can only customize `headline`, `body`, CTA labels, and visual `layoutTone`. Whenever `regionRule.consentModel == OPT_IN` or `regime == GDPR/CCPA`, `SduiTreeComposer` unconditionally renders the symmetric secondary reject button and initializes unconsented optional toggles to `false`.

### Q7. Why store JWTs in `HttpOnly` cookies instead of `Authorization: Bearer` from `localStorage`, and what tradeoff did you have to solve?
**Answer**: `localStorage` is readable by any JavaScript executing on the origin, meaning a single XSS flaw compromises active tokens. Putting the JWT in an `HttpOnly; SameSite=Lax` cookie makes the token invisible to `document.cookie`. The tradeoff is that cookies require **CSRF mitigation** on state-changing requests, which we solved via `DoubleSubmitCsrfFilter` (`ch_csrf_token` cookie matched in constant time against `X-CSRF-Token` header) plus strict CORS origin allowlisting.

### Q8. How did you handle consent continuity when an anonymous visitor accepts or rejects the banner and then signs up or logs in?
**Answer**: We use a dual-namespace `subject_key` (`anon:<id>` for visitors, `user:<uuid>` for authenticated users, `ADR-008`). During `/api/v1/auth/signup` or `/login`, if the client passes its `anonymousSubjectKey` and the user account has no prior `user:<uuid>` consent records, `ConsentService.linkAnonymousConsentIfPresent` copies those choices into `user:<uuid>` and writes initial `consent_history` receipts—while `SecurityUtils.resolveEffectiveSubjectKey` strictly blocks unauthenticated callers from ever spoofing `user:*` keys (`SEC-G01`).

### Q9. Why do you have both `consent_records` and `consent_history` tables instead of querying the latest row from `consent_history`?
**Answer**: Read/write workload separation (CQRS-lite):
- `consent_records` has `UNIQUE(subject_key, purpose_id)`—at most 5 tiny rows per user—making hot-path reads (`myConsentState`, `/api/v1/sdk/config`) fast `O(1)` index lookups.
- `consent_history` is an **append-only immutable ledger** protected by a PostgreSQL trigger blocking `UPDATE` and `DELETE`, storing every historical transition (`previous_granted -> new_granted`, `policy_version_label`, salted `ip_hash`, `proof_hash`) required to demonstrate compliance under GDPR Art. 7(1).

### Q10. How does your policy versioning and re-consent trigger work?
**Answer**: `policy_versions` tracks sequential `version_number`s with a partial unique index `WHERE is_active = TRUE` guaranteeing exactly one active policy globally. When an admin publishes `v3.0` with `requiresReconsent = true`, `PolicyService.requiresReconsentSince(consentedPolicy, activePolicy)` checks whether any policy version newer than the user's `consentedPolicy.versionNumber` up to `activePolicy.versionNumber` has `requires_reconsent = true`. If so, `needsReconsent` and `shouldShowBanner` immediately flip to `true` without mutating or deleting historical `v2.0` consent records.

### Q11. How do you implement "Delete My Data" (GDPR Art. 17 / CCPA Right to Delete) without losing legal proof that you fulfilled the deletion request?
**Answer**: Hard-deleting everything creates a compliance paradox: if audited later, you have no record that a deletion request existed or was honored, and deleting `audit_logs` breaks the cryptographic hash chain (`ADR-007`). Instead, when an Admin marks a `DELETE` DSAR `COMPLETED`, `DsarService.executeAccountErasure`:
1. Scrubs any cached `export_payload_json` on prior DSAR tickets for that user.
2. Revokes all optional consents (`SYSTEM_ERASURE`) and nulls out `user_id` foreign references in `consent_records` and `experiment_assignments`.
3. Overwrites `users.email` (`deleted-<uuid>@redacted.consenthub.invalid`), `full_name` (`Redacted User`), and `password_hash`, and sets `deleted_at = NOW()` (which immediately invalidates any unexpired JWT in `JwtCookieAuthFilter`).
4. Appends a PII-free `DSAR_DELETE_COMPLETED` receipt to `audit_logs`.

### Q12. Explain how the tamper-evident SHA-256 hash chain in `audit_logs` works and why concurrency matters.
**Answer**: Each `audit_logs` row stores `prev_hash` (the `entry_hash` of the immediately preceding `sequence_num`, or 64 zeros for row #1) and `entry_hash = SHA-256(prev_hash | id | actor | action | entity | epochMillis | canonicalSortedJson)`. If two concurrent threads read the same `prev_hash` simultaneously, the chain would fork! To prevent race conditions, `AuditLogService` acquires PostgreSQL transaction-scoped advisory lock `pg_advisory_xact_lock(948271)` before reading the latest hash and inserting the new row, and truncates timestamps to milliseconds (`ADR-010`) so `verifyAuditChain` recomputes identical hashes across platforms.

### Q13. How is the third-party `consent.js` SDK isolated from the host website and from ConsentHub's first-party session cookies?
**Answer**:
1. **UI Isolation**: `consent.js` mounts inside a **Shadow DOM** root (`attachShadow({ mode: 'open' })`), so host site CSS cannot distort our symmetric consent buttons and our styles cannot leak onto the host page.
2. **Auth & Cookie Isolation**: `consent.js` calls `/api/v1/sdk/*` with `credentials: 'omit'` and `X-API-Key`. On the backend, `ApiKeyAuthFilter` hashes the incoming key with `SHA-256` to match `api_keys.key_hash`, checks `allowed_origins` and `sdk:read`/`sdk:write` scopes, and ignores browser cookies (`SEC-G05`).

### Q14. How did you protect login against timing-based account enumeration and brute-force attacks?
**Answer**:
1. **Anti-Enumeration**: Login always returns `401 Invalid email or password`. If the email does not exist (or `deleted_at IS NOT NULL`), `AuthService.login` still runs `passwordEncoder.matches(password, dummyBcryptHash)` against a precomputed `BCrypt(12)` hash so response latency (~250ms) is indistinguishable from a wrong password on a valid account (`SEC-G07`).
2. **Rate Limiting**: `RateLimitFilter` enforces per-client sliding-window/token-bucket limits (`429 Too Many Requests` + `Retry-After` header) using salted-hash client IP keys so raw IPs aren't even stored in memory maps.

### Q15. If ConsentHub scaled to 50 million daily active users like a global streaming service, what would you evolve first?
**Answer**:
1. **Read Caching on Hot Path**: Cache `PolicyVersion` (`is_active = true`), `RegionRule`s (`3` rows), active `ConsentPurpose`s, and `RUNNING` `Experiment` configs in Redis or in-process Caffeine cache invalidated on admin publish events—eliminating 4 DB reads on every `screen()` / `/sdk/config` call.
2. **Async Event Streaming for History & Analytics**: Write hot-path `consent_records` synchronously, and publish `ConsentChanged` events via transactional outbox + Kafka to populate `consent_history` and pre-aggregated daily/regional OLAP rollups (ClickHouse / BigQuery) instead of scanning raw rows in `AnalyticsService`.
3. **Distributed Rate Limiting & Multi-Region Read Replicas**: Move `RateLimitFilter` counters from JVM memory to Redis, and route EU/US read traffic to regional PostgreSQL replicas while keeping `audit_logs` hash-chain sequencing partitioned per tenant/shard.
