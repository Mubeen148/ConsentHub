# ConsentHub — QA Bug Report Template & Defect Log

## 1. Standard Bug Report Template

```markdown
### [BUG-XXX] Short Descriptive Title
- **Severity**: Critical | High | Medium | Low
- **Status**: Open | Fixed | Verified
- **Component**: Backend | GraphQL/SDUI | Frontend | SDK | DevOps | Security | Privacy
- **Environment**: Docker Compose / Local / Browser Viewport
- **Steps to Reproduce**:
  1. ...
  2. ...
- **Expected Behavior**: ...
- **Actual Behavior**: ...
- **Root Cause & Fix**: ...
- **Verification Evidence**: Unit / Newman / Playwright test ID passing
```

---

## 2. Gate 3 & Gate 4 Defect Log

| Bug ID | Severity | Component | Summary | Root Cause & Resolution | Status |
|--------|----------|-----------|---------|-------------------------|--------|
| `BUG-001` | Medium | Security / CSP (`sdk-demo.html`) | Inline `<script>` in `sdk-demo.html` would violate Nginx strict `Content-Security-Policy: script-src 'self'` header and trigger browser CSP console error. | Extracted interactive demo harness logic into external `/sdk-demo-app.js` loaded via `<script src="/sdk-demo-app.js" defer></script>`. | **Verified** |
| `BUG-002` | Medium | GraphQL Schema Mapping (`AuditLogEntry`) | Internal `AuditLogEntryDto` exposed `detailsJson` whereas frozen `graphql-schema.graphqls` specifies `AuditLogEntry.details: JSON!` and `PolicyVersion.isActive: Boolean!`. | Mapped `AuditLogEntryDto` to `AuditLogEntryView` (`details`) and `PolicyVersionDto` to `PolicyVersionView` (`isActive` + `getIsActive()`) in `ConsentGraphQlController`. | **Verified** |
| `BUG-003` | Medium | Security / Open-Redirect Hardening (`GHSA-wrjc-x8rr-h8h6`) | `npm audit` flagged React Router v6 moderate advisory where backslash-prefixed paths (`/\evil.example`) can trigger browser open-redirect normalization. | Hardened `SAFE_NAVIGATION_PATTERN` in `frontend/src/lib/security.ts` and `SAFE_URI_PATTERN` in `backend/.../SduiSchemaValidator.java` to reject all backslash characters (`\`), verified via JUnit 5 and Vitest unit tests. | **Verified** |
| `BUG-004` | Low | DevOps (`backend/Dockerfile`) | `mvn dependency:go-offline` downloaded hundreds of unused reporting plugin artifacts during cold Docker image builds. | Replaced with BuildKit `--mount=type=cache,target=/root/.m2` + `mvn clean package -DskipTests -B` for fast deterministic multi-stage builds. | **Verified** |
| `BUG-005` | High | Backend JPA (`AuditLogEntity`) | Hibernate 6.6.8 threw `UnsupportedLockAttemptException` when combining `@Immutable` with `@Generated(event = EventType.INSERT)` (`BIGSERIAL sequence_num`) during `DataSeeder` `audit_logs` insertion. | Removed `@Immutable` class annotation while retaining `updatable = false` on all columns and PostgreSQL trigger `trg_audit_logs_append_only`. | **Verified** |
| `BUG-006` | Medium | Frontend Boot (`PrivacySessionContext.tsx`) | Initial anonymous page load called `GET /api/v1/auth/me`, emitting a browser `401 Unauthorized` console error when no session cookie existed. | Switched session bootstrap & refresh in `PrivacySessionContext.tsx` to GraphQL `Query.me` (`200 OK` `{ me: null }` when unauthenticated) after `ensureCsrfToken()`, eliminating unauthenticated `401` console noise. | **Verified** |
| `BUG-007` | High | Backend SDK Config (`ConsentService.getSdkConfig`) | `GET /api/v1/sdk/config` was annotated `@Transactional(readOnly = true)`, causing PostgreSQL `read-only transaction` errors when sticky A/B bucketing persisted a new `experiment_assignments` exposure row for first-time `anon:sdk_*` visitors. | Changed `ConsentService.getSdkConfig` to `@Transactional` and added regression assertion `#12` in Newman API collection. | **Verified** |
