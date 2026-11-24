# ConsentHub — Master Architecture & Execution Plan

> **Project**: ConsentHub — Privacy, Data Rights & Server-Driven Consent Management Platform  
> **Brand Disclaimer**: ConsentHub is an independent, original portfolio project. It is **not** affiliated with, endorsed by, or derived from Netflix, Whop, or Gumroad, and uses 100% original branding, copy, and code.

---

## 1. Executive Vision & System Architecture

ConsentHub models the core engineering challenges of a **User Data & Consent Experiences** platform:
1. **End-User Privacy Center**: Region-aware first-visit consent banner (GDPR opt-in, CCPA opt-out, global default), granular per-purpose preference center, cryptographic/immutable consent history timeline, self-service DSAR workflows (**Download My Data** JSON portability export & **Delete My Data** erasure request with status tracking), and profile/account management.
2. **Server-Driven UI (SDUI) Engine**: The backend composes versioned JSON UI component trees (`screen(name, context)`) via GraphQL—evaluating the user's region, active policy version, current consent state, and sticky A/B experiment variant on the server so the client stays thin, declarative, and safe (with unknown-component fallback rendering).
3. **Admin Governance & Experimentation Console**: Purpose & policy version management (publishing a major policy version triggers re-consent), regional regulatory rule configuration (`GDPR`, `CCPA`, `DEFAULT`), sticky A/B experimentation on SDUI consent screens with live conversion metrics, DSAR review/approval/completion queue, time-series & regional consent analytics, API key management, and an append-only SHA-chained audit log.
4. **Embeddable Third-Party SDK (`consent.js`) + Public REST API**: Lightweight zero-dependency browser snippet + API-key-protected `/api/v1/sdk/*` REST endpoints so external properties can render region-aware banners and read/record consent state.

### High-Level Architecture

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                           CLIENT LAYER (Port 5173 / 80)                        │
│                                                                                │
│  ┌──────────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │ End-User Privacy Center  │  │   Admin Governance   │  │ Embeddable SDK   │  │
│  │  (React 18 + TS + Apollo)│  │      Dashboard       │  │  (/consent.js +  │  │
│  │  + SDUI Screen Renderer  │  │ (Experiments, DSARs, │  │  Demo Host Page) │  │
│  │  + Region Consent Banner │  │ Analytics, Audit)    │  │                  │  │
│  └────────────┬─────────────┘  └──────────┬───────────┘  └────────┬─────────┘  │
└───────────────┼───────────────────────────┼───────────────────────┼────────────┘
                │ GraphQL + REST            │ GraphQL + REST        │ REST (X-API-Key)
                │ (httpOnly JWT + CSRF)     │ (ROLE_ADMIN)          │
                ▼                           ▼                       ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT 3 BACKEND (Java 21, Port 8080)                 │
│                                                                                │
│  ┌────────────────────┐  ┌──────────────────────────┐  ┌────────────────────┐  │
│  │ Security Filter    │  │ Spring for GraphQL       │  │ REST Controllers   │  │
│  │ - JWT httpOnly     │  │ - screen(name, context)  │  │ - /api/v1/auth/*   │  │
│  │ - API Key Auth     │  │ - Consent & DSAR Queries │  │ - /api/v1/privacy/*│  │
│  │ - Rate Limiter     │  │ - Admin & Experiment Ops │  │ - /api/v1/sdk/*    │  │
│  │ - CORS & Headers   │  │ - SDUI Schema Validator  │  │ - /actuator/health │  │
│  └─────────┬──────────┘  └────────────┬─────────────┘  └─────────┬──────────┘  │
│            └──────────────────────────┼──────────────────────────┘             │
│                                       ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ Domain Services                                                          │  │
│  │ ConsentService │ SduiEngineService │ ExperimentService │ DsarService     │  │
│  │ PolicyService  │ RegionRuleService │ AuditLogService   │ ApiKeyService   │  │
│  └────────────────────────────────────┬─────────────────────────────────────┘  │
│                                       ▼                                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ Spring Data JPA + Flyway Migrations                                      │  │
│  └────────────────────────────────────┬─────────────────────────────────────┘  │
└───────────────────────────────────────┼────────────────────────────────────────┘
                                        ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                         POSTGRESQL 16 DATABASE (Port 5432)                     │
│  users │ consent_purposes │ policy_versions │ region_rules │ consent_records   │
│  consent_history │ experiments │ experiment_assignments │ data_requests        │
│  api_keys │ audit_logs                                                         │
└────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Repository Directory Layout & Agent Ownership Boundaries

```
/
├── PLAN.md                         # Owner: Orchestrator + Architect
├── DECISIONS.md                    # Owner: Architect (append-only ADR log)
├── README.md                       # Owner: Technical Writer
├── ARCHITECTURE.md                 # Owner: Technical Writer + Architect
├── LEARNING.md                     # Owner: Technical Writer
├── .env.example                    # Owner: DevOps
├── docker-compose.yml              # Owner: DevOps
├── .github/workflows/ci.yml        # Owner: DevOps
├── docs/
│   ├── ux-research.md              # Owner: UX Researcher + Design System
│   ├── design-system.md            # Owner: UX Researcher + Design System
│   ├── API.md                      # Owner: Technical Writer
│   ├── privacy-compliance.md       # Owner: Privacy & Compliance Specialist
│   ├── security-review.md          # Owner: Security Engineer
│   ├── contracts/                  # Owner: Architect (FROZEN at Gate 1)
│   │   ├── database-schema.sql
│   │   ├── graphql-schema.graphqls
│   │   ├── rest-endpoints.md
│   │   └── sdui-schema.json
│   ├── qa/                         # Owner: QA Automation
│   │   ├── test-plan.md
│   │   ├── test-cases.md
│   │   └── bug-log.md
│   └── screenshots/                # Owner: Reviewer / Integrator
├── backend/                        # Owner: Backend (Java/Spring) + GraphQL/SDUI
│   ├── Dockerfile                  # Owner: DevOps
│   ├── pom.xml
│   └── src/
│       ├── main/java/io/consenthub/...
│       ├── main/resources/
│       │   ├── application.yml
│       │   ├── db/migration/       # Flyway SQL migrations
│       │   └── graphql/            # schema.graphqls (synced from contract)
│       └── test/java/io/consenthub/... # Owner: Backend + QA Automation
├── frontend/                       # Owner: Frontend (React/TS)
│   ├── Dockerfile                  # Owner: DevOps
│   ├── nginx.conf                  # Owner: DevOps
│   ├── package.json
│   ├── public/
│   │   ├── consent.js              # Embeddable third-party SDK
│   │   └── sdk-demo.html           # Standalone third-party host demo page
│   └── src/
│       ├── styles/tokens.css       # Design system CSS variables
│       ├── sdui/                   # SDUI Component Registry & Renderer
│       ├── features/               # Portal, Banner, Admin, Auth
│       ├── components/             # Shared UI primitives (Button, Card, Modal, etc.)
│       └── __tests__/              # Vitest + React Testing Library
└── tests/
    ├── api/                        # Postman/Newman collection & environment
    └── e2e/                        # Playwright E2E suite (desktop + mobile)
```

---

## 3. Execution Gates & Milestones

| Gate | Milestone | Entry Criteria | Exit Criteria | Approver |
|------|-----------|----------------|---------------|----------|
| **Gate 0** | UX Research & Design System | `PLAN.md` created | `docs/ux-research.md` & `docs/design-system.md` completed with concrete typography, spacing, color, layout, and responsive tokens | Orchestrator |
| **Gate 1** | Contracts Frozen | Gate 0 complete | `/docs/contracts/*` (`database-schema.sql`, `graphql-schema.graphqls`, `rest-endpoints.md`, `sdui-schema.json`) written & reviewed by Privacy + Security + Architect | Architect |
| **Gate 2 (Build)** | Parallel Implementation | Gate 1 frozen | Backend, GraphQL/SDUI, Frontend, SDK, and DevOps artifacts built cleanly against contracts with zero placeholders | Orchestrator |
| **Gate 3** | Full-Stack Integration | Build complete | Frontend & SDK connected to live Spring Boot + PostgreSQL backend; zero mock data layers in app runtime; seed accounts working | Reviewer / Integrator |
| **Gate 4** | QA, Security & Privacy Audits | Gate 3 green | Unit (JUnit 5 + Vitest), Integration, API (Newman), and E2E (Playwright) suites run; OWASP & GDPR/CCPA audits complete; all bugs resolved & re-tested | QA + Security + Privacy |
| **Gate 5** | Final Release Verification | Gate 4 green + Docs complete | `docker compose up` verified, all tests green with coverage reports, screenshots captured in `/docs/screenshots`, zero console/a11y errors, README 3-command check passed | Reviewer / Integrator |

---

## 4. Master Task Board

| ID | Phase / Gate | Task Description | Deliverables | Owner Agent | Status |
|----|--------------|------------------|--------------|-------------|--------|
| T-01 | Gate 0 | Read-only UX research of Whop & Gumroad patterns (dashboards, portals, tables, forms, modals, empty/loading states, mobile) | `docs/ux-research.md` | `ux-design-system` | Completed |
| T-02 | Gate 0 | Define ConsentHub original visual identity & concrete token system (type scale, 8px grid, WCAG AA palette, radii, shadows, component specs) | `docs/design-system.md` | `ux-design-system` | Completed |
| T-03 | Gate 1 | Design & freeze PostgreSQL schema contract, GraphQL schema, REST API spec, and versioned SDUI JSON schema | `docs/contracts/*`, `DECISIONS.md` | `architect` | Completed |
| T-04 | Gate 1 | Privacy & Compliance contract review: GDPR/CCPA rights mapping, consent proof fields, retention, data minimization | `docs/privacy-compliance.md` | `privacy-compliance` | Completed |
| T-05 | Gate 1 | Security contract review: httpOnly JWT + CSRF double-submit/header strategy, BCrypt, API key SHA-256 hashing, rate limiting, CORS | `docs/security-review.md` (design section) | `security-engineer` | Completed |
| T-06 | Build | Implement Spring Boot 3 core: Flyway migrations + seed data, JPA entities/repos, Auth (JWT httpOnly cookies), REST endpoints (`/api/v1/auth`, `/api/v1/privacy/export`, `/api/v1/sdk`), rate limiting, audit log hash chain | `backend/pom.xml`, `backend/src/main/...` | `backend-spring` | Pending |
| T-07 | Build | Implement Spring for GraphQL resolvers, versioned SDUI `screen(name, context)` composer, server-side SDUI schema validator, and sticky SHA-256 A/B experiment bucketing | `backend/src/main/.../graphql/*`, `.../sdui/*` | `graphql-sdui` | Pending |
| T-08 | Build | Implement React 18 + strict TS + Tailwind tokens + Apollo Client frontend: SDUI Component Registry (with unknown-type fallback), Region-aware Banner, End-User Privacy Center, Admin Dashboard, and `consent.js` SDK + demo | `frontend/*` | `frontend-react` | Pending |
| T-09 | Build | Implement Dockerfiles, `docker-compose.yml` (`db`, `backend`, `frontend`), Nginx reverse proxy, `.env.example`, and GitHub Actions CI pipeline | `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `.github/workflows/ci.yml` | `devops-engineer` | Completed |
| T-10 | Gate 3 | Wire live frontend + SDK to Spring Boot + PostgreSQL, verify seeded accounts (`user@consenthub.io`, `admin@consenthub.io`), eliminate any drift from contracts | Integrated runtime | `reviewer-integrator` | Pending |
| T-11 | Gate 4 | Create test plan, test cases, bug template/log; write & run JUnit 5/Mockito tests (with JaCoCo coverage), Vitest/RTL tests (with coverage), Newman API collection, and Playwright E2E suite | `docs/qa/*`, `backend/src/test/*`, `frontend/src/__tests__/*`, `tests/api/*`, `tests/e2e/*` | `qa-automation` | Pending |
| T-12 | Gate 4 | Execute OWASP Top 10 security audit & GDPR/CCPA compliance verification against live codebase; log and verify fixes | `docs/security-review.md`, `docs/privacy-compliance.md` | `security-engineer`, `privacy-compliance` | Pending |
| T-13 | Docs | Author comprehensive `README.md` (3-command setup), `ARCHITECTURE.md`, `docs/API.md`, and beginner-friendly `LEARNING.md` with 15 interview Q&As | `README.md`, `ARCHITECTURE.md`, `docs/API.md`, `LEARNING.md` | `tech-writer` | Pending |
| T-14 | Gate 5 | Final verification: `docker compose up`, full test suite execution, mobile + desktop screenshots in `/docs/screenshots`, console/a11y/secrets/TODO check | `docs/screenshots/*`, final sign-off report | `reviewer-integrator` | Pending |

---

## 5. Architectural Sign-Off Log

### Gate 1 — Principal System Architect Contract Freeze (`T-03` Approved)
- **Status**: **FROZEN & APPROVED** (`architect`)
- **Frozen Artifacts**:
  1. `docs/contracts/database-schema.sql` — 11 PostgreSQL 16 tables with UUID PKs, `TIMESTAMPTZ` columns, strict `CHECK` constraints, single-active-policy partial unique index, append-only `BEFORE UPDATE OR DELETE` triggers on `consent_history` and `audit_logs`, and deterministic seed specifications (including SHA-256 hash `37f0f4a4131b49bae8a6ffcdfbc8c585b7972459cfeb3d5d8a65d37b407abb1d` for `ch_live_demo_public_sdk_key_2026`).
  2. `docs/contracts/graphql-schema.graphqls` — Complete Spring for GraphQL SDL (`scalar JSON`) covering `screen(name, context)` SDUI composition, `myConsentState`, `myConsentHistory`, DSAR lifecycle, and `ROLE_ADMIN` governance/analytics/audit chain verification.
  3. `docs/contracts/rest-endpoints.md` — Exact HTTP methods, cookie/CSRF double-submit contract (`ch_access_token` + `ch_csrf_token` / `X-CSRF-Token`), `/api/v1/privacy/export` (with `/api/v1/export` alias), `/api/v1/sdk/*` (`X-API-Key`), rate limits, and uniform `ApiErrorResponse` envelope.
  4. `docs/contracts/sdui-schema.json` — Validated JSON Schema Draft 2020-12 (`schemaVersion: "1.0"`) specifying `SduiScreen`, `SduiNode`, `SduiAction`, all 15 canonical component types + prop catalogs, and client `UnknownComponentFallback` behavior.
- **Cross-Layer Parity**: Verified zero contradictions across SQL enums/columns, GraphQL enums/types, REST JSON fields, Design System tokens (`docs/design-system.md`), and SDUI component props (`ADR-008` through `ADR-010` recorded in `DECISIONS.md`). Ready for `T-04` (Privacy Review), `T-05` (Security Review), and Gate 2 parallel implementation.


---

## Final Gate 3, Gate 4 & Gate 5 Completion Log

- **Gate 2 (Build)**: Completed (`T-06` Backend Spring Boot 3.4, `T-07` GraphQL + SDUI Engine, `T-08` React 18 Frontend + `consent.js` SDK, `T-09` Docker & CI/CD).
- **Gate 3 (Full-Stack Integration)**: Completed (`T-10` — `docker compose up --build -d --wait` starts `consenthub-db`, `consenthub-backend`, and `consenthub-frontend` all `Healthy` with deterministic seeded accounts and zero runtime mocks).
- **Gate 4 (QA, Security & Privacy Verification)**: Completed (`T-11` & `T-12` — 10/10 JUnit 5 + JaCoCo tests, 6/6 Vitest + V8 coverage tests, 13/13 Newman API contract tests, 8/8 Playwright Desktop + Mobile E2E tests; all 7 logged issues `BUG-001`..`BUG-007` resolved and verified).
- **Documentation (`T-13`)**: Completed (`README.md`, `ARCHITECTURE.md`, `docs/API.md`, `LEARNING.md` with 15 interview Q&As).
- **Gate 5 (Final Release Sign-Off, `T-14`)**: **APPROVED** — 19 Desktop & Mobile screenshots saved in `docs/screenshots/`, zero console errors, zero TODOs/placeholders, zero secrets in repo.
