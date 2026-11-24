# ConsentHub — Architectural & Product Decisions Log (`DECISIONS.md`)

All ambiguities, technical trade-offs, and sensible defaults chosen during execution are recorded here in chronological ADR (Architecture Decision Record) format.

---

## ADR-001: Keep Full Java 21 + Spring Boot 3 + GraphQL Backend Stack
- **Context**: The prompt specifies Java 21, Spring Boot 3, Spring for GraphQL, Spring Security, Spring Data JPA, Flyway, and PostgreSQL to mirror the target "User Data & Consent Experiences" role, while noting Node.js as an optional fallback only if Java/Spring proves infeasible.
- **Decision**: Build the authentic **Java 21 + Spring Boot 3.4 + Spring for GraphQL + PostgreSQL 16** stack, paired with an extensive `LEARNING.md` guide so a developer coming from TypeScript/Node can explain every annotation, layer, and design choice with confidence in interviews.
- **Consequences**: Maximum alignment with the target engineering role; multi-stage Docker builds (`eclipse-temurin:21-jdk-alpine` / `jre-alpine`) ensure `docker compose up` works out-of-the-box on any machine even without a pre-installed host JDK.

## ADR-002: Brand Identity & Aesthetic Direction ("ConsentHub")
- **Context**: Must achieve creator-grade dashboard polish (inspired by Whop and Gumroad UX clarity) while remaining 100% original and unaffiliated with Netflix, Whop, or Gumroad.
- **Decision**: Establish **ConsentHub** with a high-contrast architectural editorial palette (warm alabaster surfaces `#FAFAFA` / `#FFFFFF`, crisp `1px` slate-ink borders `#E4E4E7`, deep obsidian primary actions `#18181B`, and trust-emerald `#059669` semantic accents) using `Plus Jakarta Sans` / `Inter` + `JetBrains Mono` for policy versions, hashes, and SDK snippets.

## ADR-003: Authentication & Session Security (JWT in `httpOnly` Cookies + Custom Header CSRF Protection)
- **Context**: Storing JWTs in `localStorage` exposes tokens to XSS, while `httpOnly` cookies require CSRF mitigation.
- **Decision**:
  1. Issue short-lived signed HMAC-SHA256 JWTs inside an `httpOnly; SameSite=Lax; Path=/` cookie (`ch_access_token`, `Secure` enabled in production profile).
  2. Enforce double-layer CSRF protection on cookie-authenticated state-changing requests (`POST`, `PUT`, `PATCH`, `DELETE`, and GraphQL mutations): a readable `ch_csrf_token` cookie paired with required `X-CSRF-Token` header validation (plus strict CORS origin allowlisting).
  3. Authenticate third-party SDK REST calls (`/api/v1/sdk/**`) via `X-API-Key` header against SHA-256 hashed keys stored in `api_keys` (raw secret shown only once at creation, plus a pre-seeded demo public/publishable SDK key documented in `.env.example` and `README.md` for local demo verification).

## ADR-004: Region Detection & Override Mechanism
- **Context**: Local development and automated E2E tests run from a single machine IP (`127.0.0.1`), making GeoIP lookup uniform unless overridable.
- **Decision**: Resolve user region in priority order:
  1. Explicit `region` context parameter passed to `screen(name, context)` or `?region=EU|US-CA|GLOBAL` query/header (`X-Consent-Region`) for deterministic testing and region switcher preview in the UI.
  2. Authenticated user's saved profile `region` (`EU`, `US-CA`, `GLOBAL`).
  3. Standard CDN geo headers (`CF-IPCountry`, `CloudFront-Viewer-Country`) mapped to regulatory regimes (`GDPR` for EEA/UK, `CCPA` for `US-CA`, `DEFAULT` otherwise).
  4. Fallback to `GLOBAL` (`DEFAULT` regime).

## ADR-005: Deterministic Sticky A/B Experiment Assignment
- **Context**: Consent screen experiments require traffic splitting and sticky per-user variant assignment across sessions (including anonymous visitors before login).
- **Decision**: Compute deterministic bucket `SHA-256(experimentId + ":" + subjectKey) mod 100`, where `subjectKey` is `userId` (if authenticated) or client-supplied pseudonymous `subjectId` (`ch_anon_id` cookie/UUID), AND persist the assignment in `experiment_assignments` so variant exposure and subsequent consent conversion are accurately attributed even if traffic split weights are later inspected.

## ADR-006: Append-Only Tamper-Evident Audit & Consent Ledger
- **Context**: Privacy regulations (GDPR Art. 7(1) demonstrable consent) require immutable proof of what policy version, purposes, region rule, and UI variant the user saw when consenting.
- **Decision**:
  1. `consent_records` holds current per-purpose effective state for fast lookup, while `consent_history` (ledger) and `audit_logs` are strictly **append-only** (no UPDATE/DELETE repository methods exposed; enforced by database trigger in Flyway migration).
  2. Each `audit_logs` entry computes a ` entry_hash = SHA-256(prev_hash + actor + action + target + timestamp + metadata_json)` hash chain for tamper evidence.

## ADR-007: Self-Service Data Erasure ("Delete My Data") Lifecycle
- **Context**: Immediate hard-deletion upon clicking "Delete My Data" would prevent demonstrating status tracking (`PENDING` → `IN_REVIEW` → `APPROVED` → `COMPLETED`) and regulatory retention of anonymized DSAR fulfillment logs.
- **Decision**: Submitting a deletion request creates a `data_requests` ticket (`type = DELETE`, `status = PENDING`) visible in the user's Privacy Center status tracker and the Admin DSAR Queue. When an Admin completes a `DELETE` request, user PII (`name`, `email`, password hash, preferences) is pseudonymized/redacted (`deleted-<uuid>@redacted.consenthub.invalid`) and active sessions are invalidated, while preserving anonymized regulatory proof that the erasure request was fulfilled.

## ADR-008: Unified `subject_key` Namespace for Anonymous Visitors & Authenticated Users
- **Context**: Visitors interact with the region-aware Consent Banner and third-party `consent.js` SDK *before* creating an account or logging in, yet authenticated users expect continuity across devices and sessions.
- **Decision**:
  1. Standardize `subject_key` (`VARCHAR(128)`) across `consent_records`, `consent_history`, and `experiment_assignments` with two canonical namespaces: `user:<user_uuid>` for authenticated sessions and `anon:<client_id>` for unauthenticated visitors / external SDK hosts.
  2. When authenticated via `ch_access_token`, backend services resolve the effective subject to `user:<currentUser.id>`. During `POST /api/v1/auth/signup` or `POST /api/v1/auth/login`, an optional `anonymousSubjectKey` migrates prior first-visit consent choices if the user has not yet recorded authenticated preferences.
  3. Normalize inbound region identifiers (`US-CA` → `US_CA`) so SQL `CHECK` constraints, GraphQL `RegionCode` enums (`EU`, `US_CA`, `GLOBAL`), REST payloads, and SDUI payloads share 100% identical identifiers.

## ADR-009: GraphQL `JSON` Scalar + Dual-Mode SDUI Validation Strategy
- **Context**: Server-Driven UI (`screen(name, context)`) requires recursive component `props` and `actions.payload` flexibility while preserving strict contract safety between Java resolvers and React TypeScript components.
- **Decision**:
  1. Register `graphql.scalars.ExtendedScalars.Json` (`scalar JSON`) in Spring for GraphQL for `SduiNode.props`, `SduiAction.payload`, and `AuditLogEntry.details`, while keeping all domain envelopes (`SduiScreen`, `SduiNode`, `SduiAction`, `ConsentStatusBundle`, `ConsentAnalytics`, `ExperimentSummary`) strongly typed in SDL.
  2. Enforce **strict canonical type checking** (`15` registered component types, max depth `<= 5`) in the backend's `SduiSchemaValidator`, while typing `SduiNode.type` as `String!` on the wire so the React SDUI renderer gracefully renders `<UnknownComponentFallback />` if an unrecognized future server component type is encountered.

## ADR-010: Deterministic Millisecond & Sorted-JSON Canonicalization for SHA-256 Hash Chains
- **Context**: PostgreSQL `TIMESTAMPTZ` stores microsecond precision (truncating Java `Instant` nanoseconds) and `JSONB` normalizes key ordering, which can cause false-positive hash mismatches when `verifyAuditChain` recomputes `entry_hash` from persisted rows.
- **Decision**: Truncate `created_at` to `ChronoUnit.MILLIS` (`createdAt.toEpochMilli()`) before persistence and serialize `details_json` through a key-sorted compact Jackson mapper on both write and verification, guaranteeing bit-for-bit deterministic SHA-256 chain verification across JVM and PostgreSQL 16 restarts.


## ADR-011: Orchestrator Role-by-Role Continuation on Subagent Quota Exhaustion
- **Context**: During Gate 2 parallel execution, three concurrent specialist subagents encountered per-subagent model quota limits (`RESOURCE_EXHAUSTED` HTTP 429) after generating the majority of `backend/` and `frontend/`.
- **Decision**: Per the prompt's explicit operational guidance (*"If Antigravity has the Agent Manager, run each role as a separate agent. Otherwise, a single agent will work through the roles one by one"*), the Lead Orchestrator seamlessly assumed direct execution of the remaining specialist roles sequentially (`backend-spring` -> `graphql-sdui` -> `frontend-react` -> `reviewer-integrator` -> `qa-automation` -> `security-engineer` / `privacy-compliance` Gate 4 -> `tech-writer` -> Gate 5 verification) while strictly honoring all Gate 1 frozen contracts and review gates.
- **Consequences**: Zero interruption to continuous delivery, zero loss of completed subagent artifacts, and full adherence to every execution gate and quality bar.
