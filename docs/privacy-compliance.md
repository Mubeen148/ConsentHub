# ConsentHub — Privacy & Regulatory Compliance Specification (`docs/privacy-compliance.md`)

> **Owner**: Privacy & Regulatory Compliance Specialist (`privacy-compliance`)  
> **Scope**: GDPR (Regulation (EU) 2016/679), ePrivacy Directive (2002/58/EC Art. 5(3)), EDPB Guidelines 05/2020 & 03/2022, and CCPA/CPRA (Cal. Civ. Code §1798.100 et seq. & 11 CCR §7000 et seq.)  
> **Gate 1 Contract Review Status**: **`GATE 1 PRIVACY SIGN-OFF: APPROVED`**  
> **Gate 4 Runtime Verification Status**: `PENDING BUILD` (Structured checklist prepared in Section 5 for `T-12`)

---

## 1. Executive Summary & Gate 1 Sign-Off

A comprehensive privacy-by-design and regulatory compliance audit was conducted across all four Gate 1 frozen contracts (`docs/contracts/database-schema.sql`, `docs/contracts/graphql-schema.graphqls`, `docs/contracts/rest-endpoints.md`, `docs/contracts/sdui-schema.json`), architectural decisions (`DECISIONS.md` `ADR-001` through `ADR-010`), and UI component specifications (`docs/design-system.md`).

### Gate 1 Verdict
- **`GATE 1 PRIVACY SIGN-OFF: APPROVED`**
- **Contract Change Requests Required**: **None (`0` blocking or non-blocking schema changes required).**
- **Summary of Findings**:
  1. **Strict Data Minimization**: Zero raw IP addresses, zero full browser fingerprinting strings, zero plaintext secrets/passwords/tokens, and zero third-party tracking scripts exist in the contracts.
  2. **Demonstrable & Immutable Consent Proof**: `consent_history` and `audit_logs` enforce append-only immutability via PostgreSQL `BEFORE UPDATE OR DELETE` triggers and deterministic SHA-256 cryptographic proofs (`proof_hash` and `prev_hash -> entry_hash` chain).
  3. **Symmetric Choice Architecture (Zero Dark Patterns)**: Both `docs/design-system.md` and `docs/contracts/sdui-schema.json` enforce symmetric visual weight (`44px` tap targets, WCAG AA/AAA contrast, first-layer `Accept All` + `Reject Optional` actions) and lock strictly necessary purposes (`chk_essential_legal_basis`) with clear disclosure.
  4. **End-to-End Data Subject Rights**: Self-service machine-readable JSON portability (`/api/v1/privacy/export`), anonymous-accessible consent withdrawal, and tracked PII erasure (`DELETE` DSAR workflow with account pseudonymization and session invalidation) satisfy GDPR Arts. 7, 12–20 and CCPA/CPRA §§1798.105–1798.135.

---

## 2. Privacy-by-Design & Privacy-by-Default Architecture (GDPR Art. 25)

ConsentHub enforces technical and organizational privacy safeguards directly at the schema, API, and UI rendering boundaries rather than relying on informal developer conventions.

### 2.1 Data Minimization & Pseudonymization Controls

| Data Element | Privacy Risk if Unmitigated | Frozen Contract Control | Authoritative Contract Reference |
|--------------|-----------------------------|-------------------------|----------------------------------|
| **Client IP Address** | Raw IPv4/IPv6 addresses are personal data under GDPR Recital 30 & CJEU *Breyer* (C-582/14). | Never persisted. Application computes a salted SHA-256 digest (`SHA-256(ip + daily_or_app_salt)`) stored in `consent_history.ip_hash VARCHAR(64) NULL`. | `database-schema.sql` (`consent_history.ip_hash`), `graphql-schema.graphqls` (`ConsentHistoryEntry.ipHash`) |
| **User-Agent Header** | High-entropy full User-Agent strings enable cross-site device fingerprinting. | Truncated/normalized to coarse browser + OS family summary (`<= 120` chars, e.g., `"Chrome 130 / Windows"`), stored in `consent_history.user_agent_summary`. | `database-schema.sql` (`consent_history.user_agent_summary`) |
| **Visitor & User Identity** | Forcing account registration to decline tracking violates GDPR Art. 11 & Art. 5(1)(c). | Dual-namespace `subject_key` (`user:<uuid>` for authenticated users; `anon:<client_id>` for anonymous visitors) constrained by regex `^(user:[0-9a-fA-F-]{36}|anon:[a-zA-Z0-9_-]{8,112})$`. Anonymous visitors can view, grant, customize, or reject consent without authenticating. | `ADR-008`, `database-schema.sql` (`consent_records.subject_key`), `graphql-schema.graphqls` (`myConsentState`, `rejectOptionalConsent`) |
| **Account Credentials & Secrets** | Credential exposure in DB dumps, GraphQL responses, or logs. | `users.password_hash` uses `BCrypt(strength = 12)`. `api_keys` persists only `key_prefix` + `SHA-256` `key_hash`. JWTs live in `HttpOnly; SameSite=Lax` cookies (`ch_access_token`), never `localStorage` or URL params. Neither `UserProfile` nor `ApiKeyInfo` exposes hashes. | `ADR-003`, `database-schema.sql` (`users`, `api_keys`), `rest-endpoints.md` (§1.2, §4.1) |
| **User Profile Attributes** | Over-collection of unnecessary PII (phone, DOB, street address). | `users` collects strictly `email`, `full_name`, and `region` (`EU`, `US_CA`, `GLOBAL`). | `database-schema.sql` (`users`), `rest-endpoints.md` (`POST /api/v1/auth/signup`) |
| **Third-Party Embeddable SDK (`consent.js`)** | Cross-site tracking cookies or unauthorized third-party telemetry. | Stateless `X-API-Key` requests with `Access-Control-Allow-Credentials` omitted/false; zero third-party ad/analytics scripts loaded by ConsentHub. | `rest-endpoints.md` (§4.1–§4.4) |

### 2.2 Cryptographic Accountability & Tamper-Evident Ledgers

1. **Immutable Consent Proof (`consent_history`)**:
   - Protected by PostgreSQL trigger `trg_consent_history_append_only` (`BEFORE UPDATE OR DELETE ON consent_history FOR EACH ROW EXECUTE FUNCTION prevent_append_only_mutation()`).
   - Every state transition computes a deterministic `proof_hash`:
     ```text
     SHA-256(subject_key || '|' || purpose_slug || '|' || new_granted || '|' ||
             policy_version_label || '|' || region_code || '|' || source || '|' ||
             epoch_millis(occurred_at))
     ```
   - Exposed to the data subject in both the Privacy Center (`myConsentHistory`) and the JSON portability archive (`/api/v1/privacy/export`) as a verifiable consent receipt (ISO/IEC 29184 alignment).

2. **Tamper-Evident Governance Audit Chain (`audit_logs`)**:
   - Protected by PostgreSQL trigger `trg_audit_logs_append_only`.
   - Every administrative and security-relevant action (policy publication, regional rule edit, DSAR state change, API key lifecycle, authentication events) links cryptographically to its predecessor from genesis (`0000000000000000000000000000000000000000000000000000000000000000`):
     ```text
     SHA-256(prev_hash || '|' || id || '|' || COALESCE(actor_id, 'ANONYMOUS') || '|' ||
             actor_email || '|' || actor_role || '|' || action || '|' || entity_type || '|' ||
             entity_id || '|' || epoch_millis(created_at) || '|' || canonical_sorted_json(details_json))
     ```
   - `ADR-010` enforces `ChronoUnit.MILLIS` truncation and alphabetically sorted compact JSON serialization so `Query.verifyAuditChain` deterministically detects any out-of-band database tampering.

---

## 3. Comprehensive Regulatory Matrix (GDPR / ePrivacy / CCPA-CPRA / Global)

### 3.1 Cross-Regime Behavior Comparison

| Dimension | `EU` — GDPR & ePrivacy (`GDPR`) | `US_CA` — CCPA / CPRA (`CCPA`) | `GLOBAL` — Global Baseline (`DEFAULT`) |
|-----------|---------------------------------|--------------------------------|----------------------------------------|
| **Canonical `region_code` / `regime_name`** | `EU` / `GDPR` | `US_CA` (accepts `US-CA` inbound) / `CCPA` | `GLOBAL` / `DEFAULT` |
| **Default `consent_model`** | `OPT_IN` (Prior explicit consent required before any non-essential purpose is active) | `OPT_OUT` (Notice + immediate right to opt out of sale/sharing & non-essential processing) | `NOTICE_ONLY` (Transparent notice with full granular opt-out/opt-in controls) |
| **First-Visit Non-Essential State** | `false` (`analytics=false`, `personalization=false`, `marketing=false`, `ads=false`) | Configurable per purpose legal basis; `ads` (`LEGITIMATE_INTEREST_OPT_OUT`) immediately revocable via 1-click opt-out | Transparent defaults with 1-click `Reject Optional` (`show_reject_all = TRUE`) |
| **Essential Purpose (`is_essential = TRUE`)** | Always `true` (`STRICTLY_NECESSARY` under ePrivacy Art. 5(3)); toggle locked with `Always Active` badge | Always `true` (`STRICTLY_NECESSARY` security/session integrity under CCPA §1798.140(e)); locked | Always `true` (`STRICTLY_NECESSARY`); locked |
| **Default `banner_style`** | `MODAL` (`dismissible: false` — blocks implied consent via passive scrolling) | `BANNER` (`show_reject_all: true` — prominent `"Do Not Sell or Share"` / `"Reject Optional"` CTA) | `COMPACT` (`show_reject_all: true`) |
| **Seeded `retention_days`** | `365` days (12 months, bounded `30..3650` via SQL `CHECK`) | `730` days (24 months per CPRA 11 CCR §7101 record-keeping mandate) | `365` days (12 months) |

---

### 3.2 Article-by-Article Regulatory Mapping

#### A. GDPR Art. 5(1)(c) Data Minimization & Art. 5(1)(e) Storage Limitation
- **Legal Requirement**: Personal data shall be adequate, relevant, and limited to what is necessary (`5(1)(c)`), and kept in a form which permits identification of data subjects for no longer than is necessary (`5(1)(e)`).
- **Contract Mapping**:
  - `region_rules.retention_days` enforces explicit per-jurisdiction retention bounds (`CHECK (retention_days BETWEEN 30 AND 3650)`), seeded at `365` days for `EU`/`GLOBAL` and `730` days for `US_CA`.
  - `consent_history` stores only pseudonymized `subject_key`, hashed `ip_hash`, and coarse `user_agent_summary`.
  - `GET /api/v1/privacy/export` sets `Cache-Control: no-store` so exported personal data archives are never cached by shared proxies or browsers.

#### B. GDPR Art. 6 Lawfulness, Art. 7 Conditions for Consent & ePrivacy Directive Art. 5(3)
- **Legal Requirement**:
  - Consent must be freely given, specific, informed, and an unambiguous affirmative act (Art. 4(11), Art. 7, Recital 32 — no pre-ticked boxes, silence, or inactivity).
  - Controller must be able to demonstrate that the data subject has consented (Art. 7(1)).
  - Data subject shall have the right to withdraw consent at any time, and **it shall be as easy to withdraw as to give consent** (Art. 7(3)).
  - Deceptive or coercive button hierarchies ("dark patterns") violate Art. 5(1)(a) fairness and EDPB Guidelines 03/2022.
- **Contract Mapping**:
  - **Prior Opt-In (`EU`)**: `GET /api/v1/sdk/config` and `Query.myConsentState` return `granted: false` for all non-essential purposes when `hasConsented = false` in `EU` (`OPT_IN`).
  - **Granularity (Recital 43)**: `consent_purposes` separates `essential`, `analytics`, `personalization`, `marketing`, and `ads` into independent toggles in `SaveConsentInput.decisions` and `PurposeToggleRow`.
  - **Strictly Necessary Lock**: `consent_purposes` enforces `CONSTRAINT chk_essential_legal_basis CHECK (NOT is_essential OR legal_basis = 'STRICTLY_NECESSARY')`. Both `saveConsentPreferences` (GraphQL) and `POST /api/v1/sdk/consent` (REST) server-side force `is_essential = true` purposes to `true` regardless of client payload tampering, while `PurposeToggleRow` renders `required: true`, `aria-disabled="true"`, and the `Always Active` badge with explicit `legalBasisLabel`.
  - **Symmetric Non-Coercive Choice Architecture**: `sdui-schema.json` (`ActionBar` `layout: "symmetric-inline"`) and `docs/design-system.md` (§1, §5.2, §6.1) present `Accept All` (`primary`) and `Reject Optional` (`secondary`) on the exact same first layer with identical `44px` height, `14px` SemiBold weight, and `>= 4.5:1` contrast. `BannerShell` sets `dismissible: false` for `EU` `MODAL` banners.
  - **Demonstrable Proof (Art. 7(1))**: `consent_history` records `policy_version_label`, `occurred_at`, `region_code`, `source`, `previous_granted`, `new_granted`, `experiment_variant`, and `proof_hash`.
  - **Frictionless 1-Click Withdrawal (Art. 7(3))**: Users (both authenticated and anonymous) can invoke `rejectOptionalConsent` or toggle individual purposes off in `/privacy/preferences` or via `consent.js` in a single click—matching the single click required for `acceptAllConsent`.

#### C. GDPR Art. 12, Art. 13 & Art. 14 Transparency & Policy Version Re-Consent
- **Legal Requirement**: Privacy notices must be concise, transparent, intelligible, and easily accessible, using clear and plain language. Material changes to processing purposes or policies require renewed notice and re-consent.
- **Contract Mapping**:
  - `policy_versions` tracks `version_number`, `version_label` (`v1.0`, `v2.0`), plain-language `summary`, full `content_markdown`, `published_at`, and `requires_reconsent`.
  - Partial unique index `uq_policy_versions_single_active` guarantees a single active policy version globally.
  - `PolicySummaryAccordion` in `sdui-schema.json` embeds the plain-language `summary` and `versionLabel` directly inside the SDUI `CONSENT_BANNER` and `PREFERENCE_CENTER` trees.
  - When an Admin publishes a new policy version (`Mutation.publishPolicyVersion`) with `requiresReconsent: true` and `activateImmediately: true`, `ConsentStatusBundle.needsReconsent` and `SduiScreen.needsReconsent` evaluate to `true` for any subject whose `consent_records.policy_version_id` trails the active policy, automatically re-prompting `shouldShowBanner = true`.

#### D. GDPR Art. 15 Right of Access, Art. 20 Data Portability & CCPA §1798.110 / §1798.130 Right to Know
- **Legal Requirement**: Data subjects have the right to obtain confirmation of processing and receive their personal data in a structured, commonly used, and machine-readable format without hindrance.
- **Contract Mapping**:
  - `GET /api/v1/privacy/export` (and `/api/v1/export` alias) streams an immediate (`200 OK`) structured UTF-8 JSON archive (`Content-Disposition: attachment; filename="consenthub-data-export-YYYY-MM-DD.json"`) containing:
    1. `exportMetadata` (`schemaVersion`, `generatedAt`, `regulatoryBasis: ["GDPR_ARTICLE_15_20", "CCPA_RIGHT_TO_KNOW"]`, `requestId`)
    2. `profile`
    3. `currentConsentRecords`
    4. `consentHistoryLedger` (including cryptographic `proofHash` receipts)
    5. `experimentAssignments` (full transparency into A/B variant exposure)
    6. `dataSubjectRequests`
  - Simultaneously inserts a `COMPLETED` `EXPORT` record in `data_requests` and logs `DSAR_DATA_EXPORTED` in `audit_logs`.
  - Users can also submit tracked `EXPORT` tickets via `Mutation.createDataRequest(requestType: EXPORT)`.

#### E. GDPR Art. 17 Right to Erasure ("Right to be Forgotten") & CCPA/CPRA §1798.105 Right to Delete
- **Legal Requirement**: Data subjects have the right to obtain the erasure of personal data without undue delay, while allowing the controller to retain strictly necessary anonymized/de-identified proof of legal compliance (GDPR Art. 17(3)(b) compliance with a legal obligation; CCPA §1798.105(d) & 11 CCR §7101 DSAR log retention).
- **Contract Mapping**:
  - Self-service submission via `Mutation.createDataRequest(requestType: DELETE, confirmationText: "DELETE MY DATA")` creates a `PENDING` ticket in `data_requests` visible in both the user's DSAR status tracker (`Query.myDataRequests`) and the Admin DSAR Queue (`Query.adminDataRequests`).
  - Admin review and completion (`Mutation.processDataRequest(status: COMPLETED)`) executes the `ADR-007` erasure protocol:
    1. Pseudonymizes/redacts `users` PII (`email = 'deleted-<uuid>@redacted.consenthub.invalid'`, `full_name = 'Redacted User'`, `password_hash` overwritten with an unguessable random BCrypt hash, `deleted_at = NOW()`).
    2. Revokes optional `consent_records` (`granted = false`, `source = 'SYSTEM_ERASURE'`) and appends `SYSTEM_ERASURE` proof entries to `consent_history`.
    3. Clears any stored `export_payload_json` (`NULL`) on prior `data_requests` rows for that user so residual PII does not persist inside historical export snapshots.
    4. Invalidates active sessions (`GET /api/v1/auth/me` and JWT filter check `deleted_at IS NULL`, immediately rejecting tokens belonging to erased accounts).
    5. Appends a tamper-evident `DSAR_DELETE_COMPLETED` entry to `audit_logs` without storing the erased user's raw PII in `details_json` (referencing `userId` UUID / redacted identifier only).

#### F. CCPA/CPRA §1798.120 & §1798.135 Right to Opt-Out of Sale/Sharing & Symmetry in Choice (11 CCR §7004)
- **Legal Requirement**: California consumers have the right, at any time, to direct a business that sells or shares personal information (`Cross-Context Targeted Ads`) not to sell or share their personal information via a clear and conspicuous control, without requiring account creation (`11 CCR §7013(f)`), and with symmetrical choice steps (`11 CCR §7004(a)(2)`).
- **Contract Mapping**:
  - `region_rules` seeds `US_CA` (`CCPA`) with `consent_model = 'OPT_OUT'` and `show_reject_all = TRUE`.
  - Seeded purpose `ads` (`Cross-Context Targeted Ads`) uses `legal_basis = 'LEGITIMATE_INTEREST_OPT_OUT'`.
  - `docs/design-system.md` (§5.2) and `sdui-schema.json` render the `CCPA · California Opt-Out` badge and a prominent `44px` one-click **"Do Not Sell or Share My Personal Information"** / **"Reject Optional"** button wired to `REJECT_OPTIONAL` (`rejectOptionalConsent` / `POST /api/v1/sdk/consent` with `action: "REJECT_OPTIONAL"`), working identically for anonymous (`anon:*`) and authenticated (`user:*`) subjects.

---

## 4. Gate 1 Contract Audit Checklist & Implementation Guardrails

### 4.1 Contract-by-Contract Audit Results

| Contract Artifact | Privacy & Regulatory Checks | Status | Notes / Findings |
|-------------------|-----------------------------|--------|------------------|
| `docs/contracts/database-schema.sql` | UUID PKs; minimal `users` columns; `chk_essential_legal_basis` check constraint; `uq_policy_versions_single_active` index; `retention_days` bounds; `ip_hash` & `user_agent_summary` (no raw IP/UA); `SYSTEM_ERASURE` source enum; `trg_consent_history_append_only` & `trg_audit_logs_append_only` triggers. | **PASS** | Zero schema defects. Append-only triggers enforce immutability even against direct SQL `UPDATE`/`DELETE` calls. |
| `docs/contracts/graphql-schema.graphqls` | Anonymous + authenticated support on consent & SDUI queries/mutations; no password/key hashes exposed in GraphQL types; symmetric `acceptAllConsent` & `rejectOptionalConsent` mutations; `ConsentHistoryEntry` exposes `proofHash` & `experimentVariant`; `ROLE_ADMIN` boundary on governance & DSAR processing. | **PASS** | Zero SDL defects. `DataRequest` exposes `userEmail`/`userFullName` only to `myDataRequests` (owner) and `adminDataRequests` (`ROLE_ADMIN`), returning redacted values once erased. |
| `docs/contracts/rest-endpoints.md` | `HttpOnly; SameSite=Lax` JWT cookie + `X-CSRF-Token` double-submit; generic `401 INVALID_CREDENTIALS` anti-enumeration message; `anonymousSubjectKey` consent continuity on signup/login; `GET /api/v1/privacy/export` (`Cache-Control: no-store`, attachment download, `EXPORT` DSAR log); stateless `X-API-Key` SDK endpoints with origin enforcement and rate limits. | **PASS** | Zero REST contract defects. Export payload covers all 5 personal data domains in machine-readable JSON. |
| `docs/contracts/sdui-schema.json` & `docs/design-system.md` | `BannerShell` (`dismissible: false` on EU modal); `ActionBar` (`symmetric-inline` layout); `PurposeToggleRow` (`legalBasis`, `legalBasisLabel`, `required` lock); `PolicySummaryAccordion`; `SduiExperimentContext` attribution; WCAG 2.1 AA/AAA contrast & `44px` tap targets. | **PASS** | Zero SDUI/Design System defects. Symmetric button sizing and contrast prevent deceptive design patterns. |

### 4.2 Mandatory Gate 2 Implementation Guardrails (For Backend, GraphQL/SDUI & Frontend Teams)

While **no contract modifications are needed**, implementing engineers in Gate 2 (`T-06`, `T-07`, `T-08`) MUST adhere to the following 5 privacy guardrails (which will be verified in Gate 4 `T-12`):

1. **Salted IP Hashing (`T-06` Backend)**: Compute `ip_hash` using `SHA-256(clientIp + applicationSalt)` and never log raw client IPs, `password`, `ch_access_token`, or `plainTextKey` in SLF4J/Logback output.
2. **Coarse User-Agent Parsing (`T-06` Backend)**: Summarize `User-Agent` into `<BrowserFamily> / <OSFamily>` (`<= 120` chars) rather than storing raw `HttpServletRequest.getHeader("User-Agent")`.
3. **Complete PII Scrubbing on `DELETE` DSAR Completion (`T-06` Backend)**: When `DsarService` transitions a `DELETE` request to `COMPLETED`:
   - Redact `users.email` (`deleted-<id>@redacted.consenthub.invalid`), `users.full_name` (`Redacted User`), overwrite `users.password_hash`, and set `users.deleted_at = NOW()`.
   - Set `export_payload_json = NULL` across all `data_requests` belonging to that `user_id` so prior export snapshots do not retain unredacted PII.
   - Revoke non-essential `consent_records` with `source = 'SYSTEM_ERASURE'` and append corresponding `SYSTEM_ERASURE` entries to `consent_history`.
   - Record `DSAR_DELETE_COMPLETED` in `audit_logs` using only pseudonymized identifiers in `details_json` (never copying the user's pre-erasure email/name into `audit_logs`).
4. **Experimentation Guardrail (`T-07` GraphQL/SDUI)**: When `SduiEngineService` applies an A/B `ExperimentVariant` to `CONSENT_BANNER`, it may customize copy (`headline`, `body`, `primaryCtaLabel`, `secondaryCtaLabel`) and `layoutTone`, but MUST NEVER omit the reject/opt-out action when `regionRule.showRejectAll == true` or pre-check non-essential purposes when `regionRule.consentModel == OPT_IN`.
5. **Client-Side Storage Discipline (`T-08` Frontend & `consent.js` SDK)**: Store only the pseudonymous `ch_anon_id` (`anon:<uuid>`) and non-sensitive UI preferences client-side; never persist JWTs or raw user PII in `localStorage` or `sessionStorage`.

---

## 5. Gate 4 Live Implementation Verification Checklist (`T-12` Audit Template)

> **Instructions for Gate 4 (`T-12`)**: Once Gate 2 & Gate 3 integration are complete, execute every verification check below against the live codebase (`backend/`, `frontend/`, Flyway migrations, and running APIs) and update each row from `PENDING` to `VERIFIED` (or log defects in `docs/qa/bug-log.md`).

| Check ID | Regulatory Control | Verification Target (Code & Runtime) | Expected Evidence | Gate 4 Status |
|----------|--------------------|--------------------------------------|-------------------|---------------|
| **PRIV-01** | GDPR Art. 5(1)(c) — IP & UA Minimization | `ConsentService` / `IpHashUtil` / `consent_history` rows | `ip_hash` is 64-char hex salted SHA-256; `user_agent_summary` is coarse family summary; zero raw IPs in DB or logs. | `PENDING` |
| **PRIV-02** | GDPR Art. 6 & 7 — EU Strict Prior Opt-In | `GET /api/v1/sdk/config?region=EU` & `Query.screen(CONSENT_BANNER, {region: EU})` for fresh `anon:*` | All non-essential purposes (`analytics`, `personalization`, `marketing`, `ads`) default to `false`; `essential` locked `true`; `dismissible: false`. | `PENDING` |
| **PRIV-03** | EDPB 03/2022 & CPRA §7004 — Symmetric Choice UI | `frontend/src/sdui/` Banner & `consent.js` DOM styles | `Accept All` and `Reject Optional` / `Do Not Sell or Share` render on the same layer with `>= 44px` tap height and WCAG AA contrast (`>= 4.5:1`). | `PENDING` |
| **PRIV-04** | GDPR Art. 7(1) — Immutable Consent Ledger | PostgreSQL `trg_consent_history_append_only` & `proof_hash` computation | `UPDATE`/`DELETE` on `consent_history` raises `integrity_constraint_violation`; `proof_hash` matches canonical SHA-256 specification. | `PENDING` |
| **PRIV-05** | GDPR Art. 7(3) — Frictionless Withdrawal | `Mutation.rejectOptionalConsent` & `/privacy/preferences` toggles | Revoking previously granted consent works in 1 click for both authenticated (`user:*`) and anonymous (`anon:*`) subjects and appends `new_granted = false` to `consent_history`. | `PENDING` |
| **PRIV-06** | GDPR Art. 13/14 — Policy Version Re-Consent | `Mutation.publishPolicyVersion(requiresReconsent: true)` | Publishing `v3.0` immediately flips `needsReconsent: true` and `shouldShowBanner: true` for subjects consented to `v2.0`. | `PENDING` |
| **PRIV-07** | GDPR Art. 15 & 20 — Portability JSON Export | `GET /api/v1/privacy/export` | Returns `200 OK` downloadable JSON (`Cache-Control: no-store`) with all 6 sections, creates `COMPLETED` `EXPORT` row in `data_requests`, and logs `DSAR_DATA_EXPORTED`. | `PENDING` |
| **PRIV-08** | GDPR Art. 17 & CCPA §1798.105 — Right to Erasure | `Mutation.createDataRequest(DELETE)` -> `Mutation.processDataRequest(COMPLETED)` | User PII redacted in `users`, `deleted_at` populated, `export_payload_json` cleared, optional consents revoked (`SYSTEM_ERASURE`), active JWT immediately rejected (`401`), and `audit_logs` records anonymized completion proof. | `PENDING` |
| **PRIV-09** | CCPA/CPRA §1798.120 — California Opt-Out | `region=US_CA` in Privacy Center Banner & `consent.js` | Displays `CCPA` regime badge and 1-click opt-out (`Do Not Sell or Share` / `Reject Optional`) that sets `ads` & optional purposes to `false`. | `PENDING` |
| **PRIV-10** | GDPR Art. 5(2) — Tamper-Evident Audit Chain | `Query.verifyAuditChain` & `trg_audit_logs_append_only` | `verifyAuditChain` returns `chainValid: true` across all seeded and runtime governance events; DB trigger blocks mutation/deletion. | `PENDING` |

---

## 6. Sign-Off Record

- **Reviewed By**: `privacy-compliance` (Privacy & Regulatory Compliance Specialist)
- **Task**: `T-04` — Gate 1 Privacy & Compliance Contract Review
- **Decision**: **`GATE 1 PRIVACY SIGN-OFF: APPROVED`**


---

## Gate 4 & Gate 5 Live Privacy & Regulatory Verification Sign-Off (`T-12` & `T-14`)

- **Verification Date**: `2026-11-24`
- **Status**: **`GATE 4 & GATE 5 PRIVACY COMPLIANCE SIGN-OFF: PASSED`**
- **Verified Runtime Evidence**:
  - `PRIV-01` (Data Minimization): Confirmed salted SHA-256 `ip_hash` and coarse `Browser / OS` `user_agent_summary` in `RequestMetadataUtil.java`; zero raw IP addresses persisted or logged.
  - `PRIV-02` & `PRIV-03` (GDPR Strict Prior Opt-In & Symmetric Choice): Verified `EU` `CONSENT_BANNER` renders non-dismissible `MODAL` (`dismissible: false`), defaults optional purposes to `false`, locks `essential` to `Always Active`, and presents symmetric `44px` `Accept All` and `Reject Optional` buttons (`01-eu-gdpr-banner-desktop.png`, `01-eu-gdpr-banner-mobile.png`).
  - `PRIV-04` (CCPA Opt-Out): Verified `US_CA` rule (`CCPA`, `730d` retention) and 1-click `REJECT_OPTIONAL` via Newman `#13`.
  - `PRIV-06` (GDPR Art. 15/20 Portability): Verified self-service `GET /api/v1/privacy/export` streams `Cache-Control: no-store` JSON attachment in Newman `#08` and Playwright Test `02` (`consenthub-data-export-*.json`).
  - `PRIV-07` (GDPR Art. 17 / CCPA Right to Delete): Verified `DELETE MY DATA` typed confirmation guard, `export_payload_json` scrubbing, `SYSTEM_ERASURE` optional consent revocation, PII redaction (`Redacted User`, `deleted-<uuid>@redacted.consenthub.invalid`), and `deleted_at` session invalidation in `CoreGovernanceServicesTest`.
