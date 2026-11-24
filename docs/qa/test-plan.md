# ConsentHub — Master QA & Verification Test Plan

## 1. Test Strategy & Quality Pyramid

ConsentHub enforces a 4-tier automated verification pyramid with zero mocked backend layers during integration, API, and end-to-end browser verification:

| Tier | Tooling | Target Scope | Coverage / Gate Target |
|------|---------|--------------|------------------------|
| **1. Backend Unit & Slice Tests** | JUnit 5, Mockito, Spring Security/GraphQL Test, JaCoCo | `ConsentService`, `SduiSchemaValidator`, `SduiTreeComposer`, `ExperimentBucketer`, `ExperimentService`, `AuditLogService` (SHA-256 chain & tamper detection), `DsarService` (erasure PII scrubbing), `AuthService` & `JwtTokenProvider` | Core domain & security services verified; JaCoCo HTML + XML coverage report generated at `backend/target/site/jacoco/index.html` |
| **2. Frontend Component & Unit Tests** | Vitest, React Testing Library, `@vitest/coverage-v8`, jsdom | `SduiRenderer` & all 15 canonical SDUI components, `UnknownComponentFallback` resilience, `ConsentToggle` locked Essential behavior, URL scheme XSS sanitizer (`SEC-G04`), UI primitives & feedback states | All Vitest suites green; V8 coverage report generated at `frontend/coverage/index.html` |
| **3. Live API Contract Tests** | Postman Collection v2.1 + Newman CLI | REST (`/api/v1/health`, `/api/v1/auth/*`, `/api/v1/privacy/export`, `/api/v1/sdk/*`), CSRF double-submit enforcement, `HttpOnly` cookie flags, `ROLE_ADMIN` RBAC, GraphQL queries/mutations, SDUI tree contract | 100% Newman assertions passing against live Docker Compose stack |
| **4. End-to-End Browser Tests** | Playwright (`Desktop Chrome 1280x800` + `Mobile Pixel/iPhone 375x812`) | First-visit regional banner (`EU` modal vs `US_CA` opt-out), signup/login, granular Preference Center, Consent Ledger timeline, DSAR JSON download & typed `DELETE MY DATA` request, Admin Analytics/Policies/Experiments/DSAR Queue/Audit Chain verification, and `sdk-demo.html` third-party `consent.js` flow + zero console errors | 100% Playwright E2E specs passing on desktop & mobile viewports |

---

## 2. Environments Under Test

- **Backend**: Java 21 + Spring Boot 3.4 + PostgreSQL 16 (`http://localhost:8080`)
- **Frontend + Nginx Reverse Proxy**: React 18 + Vite production build served by Nginx (`http://localhost:5173`)
- **Third-Party SDK Host Harness**: `http://localhost:5173/sdk-demo.html` loading `/consent.js` with `X-API-Key: ch_live_demo_public_sdk_key_2026`
- **Seeded Accounts**:
  - End-User: `user@consenthub.io` / `UserPass123!` (`ROLE_USER`, `EU`)
  - Admin: `admin@consenthub.io` / `AdminPass123!` (`ROLE_ADMIN`, `GLOBAL`)

---

## 3. Accessibility, UX & Security Acceptance Criteria

1. **Zero Console Errors**: Playwright attaches `page.on('pageerror')` and `page.on('console')` listeners asserting zero uncaught exceptions or runtime console errors across user and admin journeys.
2. **UI State Completeness**: Skeleton loading states, designed empty states, and retryable error states verified in component and E2E tests.
3. **Accessibility Fundamentals**: Semantic landmarks (`<main>`, `<nav>`, `role="dialog"`), explicit `<label>`/`aria-label` on form controls and switches (`role="switch"`, `aria-checked`), keyboard `:focus-visible` outlines, `>= 44px` interactive tap targets, and WCAG AA contrast tokens.
4. **Security & Privacy Controls**: `HttpOnly` JWT cookie, `403 CSRF_TOKEN_INVALID` when `X-CSRF-Token` is omitted/tampered on cookie-authenticated mutations, `403 FORBIDDEN` when `ROLE_USER` queries `admin*` fields, `javascript:` URI rejection in SDUI validator and client renderer, and `verifyAuditChain.chainValid == true`.
