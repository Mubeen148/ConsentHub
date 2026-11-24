# ConsentHub — Privacy, Data Rights & Server-Driven Consent Platform

> **Disclaimer**: ConsentHub is an independent, original software engineering portfolio project inspired by the technical challenges of global privacy, server-driven UI, and experimentation platforms. It is **not** affiliated with, endorsed by, or derived from Netflix, Whop, or Gumroad, and uses 100% original code, copy, and visual identity.

ConsentHub is a production-grade **Consent Management, Data Subject Rights (DSAR), and Server-Driven UI (SDUI) Platform** built with **Java 21 + Spring Boot 3.4**, **Spring for GraphQL**, **PostgreSQL 16**, and **React 18 + strict TypeScript**.

---

## Run Everything in 3 Commands

Prerequisites: **Docker** & **Docker Compose** (`v2+`).

```bash
cp .env.example .env
docker compose up --build -d
# Open http://localhost:5173 in your browser (Backend API/GraphQL runs on http://localhost:8080)
```

> On Windows PowerShell, `Copy-Item .env.example .env` works identically (or run `docker compose up --build -d` directly—safe local development defaults are preconfigured).

---

## Seeded Demo Credentials

| Role | Email | Password | Experience |
|------|-------|----------|------------|
| **End-User** (`ROLE_USER`) | `user@consenthub.io` | `UserPass123!` | Privacy Center (`/portal`), Granular Preference Center, SHA-256 Consent Ledger, Self-Service JSON Export & Account Deletion |
| **Administrator** (`ROLE_ADMIN`) | `admin@consenthub.io` | `AdminPass123!` | Governance Console (`/admin`), Analytics, Purposes & Policy Versions, Region Rules, Sticky A/B Experiments, DSAR Queue, SDK Keys & Cryptographic Audit Chain |
| **3rd-Party SDK Demo** | *Public API Key*: `ch_live_demo_public_sdk_key_2026` | — | Open `http://localhost:5173/sdk-demo.html` to test the standalone Shadow-DOM `consent.js` widget |

---

## Core Capabilities

### 1. End-User Privacy Center (`/portal`)
- **Region-Aware First-Visit Consent Banner**: Dynamically adapts between **GDPR (`EU`)** strict opt-in non-dismissible modal, **CCPA (`US_CA`)** 1-click *"Do Not Sell or Share"* opt-out banner, and **Global (`GLOBAL`)** compact notice—including a live **Region Simulator** and **SDUI JSON Tree Inspector**.
- **Granular Preference Center**: Per-purpose toggles (*Essential Security* locked *"Always Active"*, *Analytics*, *Personalization*, *Marketing*, *Cross-Context Ads*) with symmetric 1-click **Accept All** and **Reject Optional** parity.
- **Immutable Consent Receipt Timeline**: Chronological ledger with policy version tags, collection source, A/B variant attribution, and copyable SHA-256 `proofHash` receipts.
- **Self-Service Data Rights (DSAR)**: Instant machine-readable JSON portability download (`GET /api/v1/privacy/export`) and typed-confirmation (`DELETE MY DATA`) account erasure workflow with live status tracking.

### 2. Server-Driven UI Engine (`Query.screen(name, context)`)
- Composes versioned (`schemaVersion: "1.0"`) UI component trees on the backend across `CONSENT_BANNER`, `PREFERENCE_CENTER`, and `PRIVACY_OVERVIEW`.
- Enforces server-side JSON structural & security validation (15 canonical component types, tree depth `<= 5`, URI allowlist blocking `javascript:`/`data:`), paired with a resilient client `UnknownComponentFallback`.

### 3. Admin Governance & Experimentation Console (`/admin`)
- **Consent Analytics**: Time-series opt-in charts, regional comparison (`EU` vs `US_CA` vs `GLOBAL`), and per-purpose opt-in breakdowns (`7d` / `30d` / `90d`).
- **Purposes & Policy Versioning**: Publishing a new active policy version with `requiresReconsent = true` automatically prompts users to re-confirm choices.
- **Sticky A/B Experimentation**: Deterministic `SHA-256(experimentId:subjectKey) mod 100` bucketing, variant preview modal, and live per-variant conversion rate metrics.
- **DSAR Fulfillment Queue**: Review, approve, and complete user requests (completing `DELETE` pseudonymizes PII, scrubs historical exports, revokes optional consents, and invalidates sessions).
- **Tamper-Evident Audit Log**: PostgreSQL trigger-protected append-only ledger with live `SHA-256` hash-chain integrity verification (`verifyAuditChain`).

### 4. Embeddable Third-Party SDK (`consent.js`)
- Zero-dependency Shadow-DOM browser snippet (`http://localhost:5173/consent.js`) + `X-API-Key` protected `/api/v1/sdk/{config,consent}` REST endpoints.

---

## Running Automated Tests & Coverage

```bash
# 1. Backend JUnit 5 + Mockito Unit & Slice Tests + JaCoCo Coverage Report
docker run --rm -v "$(pwd)/backend:/workspace" -w /workspace maven:3.9.9-eclipse-temurin-21-alpine mvn -B test jacoco:report

# 2. Frontend Vitest + React Testing Library + V8 Coverage Report
cd frontend && npm run test:coverage

# 3. Live REST + GraphQL API Contract Suite (Newman)
npx newman run tests/api/consenthub.postman_collection.json

# 4. Playwright End-to-End Browser Suite (Desktop + Mobile Viewports)
npx playwright test
```

---

## Documentation & Architecture Index

- [PLAN.md](file:///d:/The%20Company/Netflix/PLAN.md) — Master execution plan, ownership boundaries, and gate sign-offs
- [DECISIONS.md](file:///d:/The%20Company/Netflix/DECISIONS.md) — Architectural Decision Records (`ADR-001` – `ADR-011`)
- [ARCHITECTURE.md](file:///d:/The%20Company/Netflix/ARCHITECTURE.md) — System topology, SDUI sequence flows, A/B bucketing & security diagrams
- [LEARNING.md](file:///d:/The%20Company/Netflix/LEARNING.md) — Plain-English guide to every technology used + **15 interview Q&As**
- [docs/API.md](file:///d:/The%20Company/Netflix/docs/API.md) — GraphQL, REST, and `consent.js` SDK integration examples
- [docs/ux-research.md](file:///d:/The%20Company/Netflix/docs/ux-research.md) & [docs/design-system.md](file:///d:/The%20Company/Netflix/docs/design-system.md) — Quantified UX research & WCAG AA/AAA design tokens
- [docs/privacy-compliance.md](file:///d:/The%20Company/Netflix/docs/privacy-compliance.md) — GDPR & CCPA/CPRA legal mapping & verification matrix
- [docs/security-review.md](file:///d:/The%20Company/Netflix/docs/security-review.md) — Threat model, OWASP Top 10 controls & audit log
- [docs/screenshots/](file:///d:/The%20Company/Netflix/docs/screenshots/) — Captured Desktop & Mobile verification screenshots


---

## Live Product Showcase (Visual Walkthrough)

### 1. Region-Aware Server-Driven GDPR Modal & Live SDUI JSON Inspector
Dynamically composed on the backend via `Query.screen(name: CONSENT_BANNER, context)` with symmetric `Accept All` / `Reject Optional` choice parity (`GDPR` strict opt-in).

![GDPR Server-Driven Consent Banner](./docs/screenshots/showcase/01-gdpr-sdui-banner.svg)

### 2. Granular Per-Purpose Preference Center (Interactive + SDUI Modes)
Locked *Essential Security* (`Always Active`) paired with 1-click optional consent withdrawal and dual Interactive / Server-Driven UI rendering modes.

![Granular Preference Center](./docs/screenshots/showcase/02-granular-preference-center.svg)

### 3. Self-Service Data Rights (GDPR Art. 15/20 JSON Export & Art. 17 / CCPA Erasure)
Instant `Cache-Control: no-store` machine-readable JSON portability archive download and typed-confirmation (`DELETE MY DATA`) account erasure tracking.

![DSAR Portability & Erasure](./docs/screenshots/showcase/03-dsar-portability-erasure.svg)

### 4. Multi-Region Consent Telemetry & Conversion Analytics (`ROLE_ADMIN`)
Real-time opt-in conversion tracking across `EU · GDPR`, `US_CA · CCPA`, and `GLOBAL` regimes alongside per-purpose opt-in breakdowns.

![Admin Multi-Region Analytics](./docs/screenshots/showcase/04-admin-multi-region-analytics.svg)

### 5. Sticky SHA-256 Server-Driven UI A/B Experimentation
Deterministic `SHA-256(experimentId:subjectKey) mod 100` sticky traffic bucketing, per-variant conversion lift comparison, and isolated variant preview.

![Sticky A/B Experiments](./docs/screenshots/showcase/05-sticky-ab-experiments.svg)

### 6. Tamper-Evident SHA-256 Cryptographic Audit Hash Chain
PostgreSQL trigger-protected append-only ledger linking `prev_hash -> entry_hash` with one-click live cryptographic integrity verification (`verifyAuditChain`).

![SHA-256 Audit Chain Verified](./docs/screenshots/showcase/06-sha256-audit-chain-verified.svg)

### 7. Zero-Dependency Shadow-DOM Third-Party SDK (`consent.js`) & Mobile View (`390px`)
External publisher storefront embedding `/consent.js` via `X-API-Key` without CSS bleed or session cookie leakage, plus mobile bottom-sheet ergonomics.

![Embeddable Third-Party SDK Demo](./docs/screenshots/showcase/07-embeddable-sdk-demo.svg)

<p align="center">
  <img src="./docs/screenshots/showcase/08-mobile-gdpr-sheet.svg" alt="Mobile Responsive GDPR Consent Sheet (390px)" width="360" />
</p>
