# ConsentHub — Phase 0 UX Research Synthesis (`T-01`)

> **Methodology & Originality Notice**: This research synthesizes structural UX architecture, layout geometry, interaction ergonomics, and tokenized scales observed via strictly read-only documentation and design-system inspection of modern creator/commerce SaaS platforms (**Whop Frosted UI** and **Gumroad UI System**). Zero proprietary copy, logos, brand names, color signatures, or code assets are reused; all findings are translated into original, privacy-specific architectural patterns for **ConsentHub**.

---

## 1. Executive Summary of Comparative UX Patterns

Modern creator and digital-commerce platforms solve a dual-audience UX challenge identical in structure to **ConsentHub**:
1. **Self-Service End-User / Buyer Portal**: Needs calm, low-cognitive-load scannability, unmistakable status transparency, self-serve lifecycle actions (manage preferences, download receipts/exports, cancel/delete without dark patterns), and mobile-first ergonomics.
2. **High-Density Admin / Governance Console**: Needs persistent multi-section navigation, filterable queues (disputes/refunds -> DSAR review queues), live time-series/experiment metrics, cryptographic/immutable IDs in monospace, and zero-ambiguity destructive confirmations.

| UX Dimension | Whop (Frosted UI / Radix Architecture) | Gumroad (`$app/components/ui` Architecture) | ConsentHub Synthesis Decision |
|--------------|----------------------------------------|---------------------------------------------|-------------------------------|
| **Visual Containment** | `1px` subtle alpha borders (`gray-a4` / `gray-6`) paired with surface contrast and soft elevation on overlays | Stark border-first containment (`1px`–`2px` explicit borders) on a warm editorial neutral canvas (`#F4F4F0`) with `#FFFFFF` cards | **Warm Architectural Border-First**: Warm neutral canvas (`#F7F6F3`), pure white surfaces (`#FFFFFF`), crisp `1px solid #D0CBC0` structural borders, zero diffuse drop-shadow clutter on static cards |
| **Role Separation** | Distinct `@me/settings/*` self-service customer portal (`1024px` max-width) vs `/dashboard/*` multi-module admin workspace (`240px`–`260px` left rail) | Explicit `buyer` library/settings shell vs `seller` analytics/products/audience workspace (`256px`–`260px` fixed sidebar) | **Strict Dual-Shell Separation**: Focused `1040px` End-User Privacy Center (top nav + calm cards) vs `256px` sidebar + `1280px` fluid Admin Governance Console |
| **Action Queues** | Resolution Center queue with SLA countdowns (`7 days`), status filter tabs, and explicit **Accept / Deny / Request Info** actions | Unified `<Table>` with status pills, inline search/filter bar, and slide-over `<Sheet>` detail drawers | **DSAR Governance Queue**: Status tabs (`ALL`, `PENDING`, `IN_REVIEW`, `COMPLETED`, `REJECTED`), SLA badge, detail drawer/modal, and audited state transitions |
| **Destructive Safety** | Dedicated **Danger Zone** section requiring explicit typed confirmation (`delete`) before permanent account removal | Explicit warning callouts + two-step modal confirmation tied directly to server mutations | **Zero-Dark-Pattern Erasure Flow**: Symmetric opt-in/opt-out effort on banners; DSAR **Delete My Data** uses a confirmation modal requiring typed verification (`DELETE MY DATA`) + clear retention impact |

---

## 2. Layout Anatomy: Admin Dashboard vs. End-User Portal

### 2.1 Creator / Admin Governance Console Anatomy
Observed across high-trust SaaS dashboards (`Whop /dashboard/*` and `Gumroad /dashboard`, `/products`, `/customers`):
- **Desktop Left Navigation Rail**: Fixed width `256px` (`16rem`), full viewport height (`100dvh`), `1px` right border, vertical padding `16px` (`1rem`), grouped navigation sections with `36px`–`44px` row heights (`44px` standardized in ConsentHub for WCAG touch compliance), `16px`–`20px` stroke icons (`1.75px` stroke width), and active-route indicator pill (`8px` radius).
- **Sticky Top Header Bar**: Height `64px` (`4rem`), `position: sticky; top: 0; z-index: 30`, `1px` bottom border, horizontal padding `24px` (`32px` at `>= 1280px`). Houses page title/breadcrumb on the left and environment/region simulator badge, active policy version pill, and quick primary CTA on the right.
- **Workspace Container & Grid**:
  - Max content width: `1280px` (`80rem`) centered inside the main scroll region (`calc(100vw - 256px)` on desktop), horizontal padding `16px` (`< 768px`), `24px` (`768px`–`1279px`), `32px` (`>= 1280px`).
  - KPI Metric Row: `12-column` CSS Grid (`repeat(4, minmax(0, 1fr))` on `>= 1024px`, `repeat(2, 1fr)` on `768px`–`1023px`, `1fr` on `< 768px`) with `16px` (`1rem`) grid gutter on mobile and `24px` (`1.5rem`) gutter on desktop.

### 2.2 Buyer / End-User Portal Anatomy
Observed across self-service account & billing portals (`Whop /@me/settings/*` and `Gumroad /settings`, `/library`):
- **Top Navigation Header**: Clean single-bar header, height `64px` (`4rem`), `1px` bottom border, containing brand mark, 4 focused top-level tabs (`Preferences`, `Consent History`, `Data Rights (DSAR)`, `Profile`), active region indicator (`GDPR` / `CCPA` / `DEFAULT`), and user menu.
- **Focused Reading & Action Container**: Max width `1040px` (`65rem`) for multi-card overview pages and `768px` (`48rem`) for linear preference/DSAR forms so eye-tracking across purpose descriptions and toggle switches remains effortless (line length capped at `60ch`–`72ch`).
- **Actionable Status Banners**: Pattern adapted from Whop's `required-actions` dashboard surface—renders prominently at the top of the viewport *only* when action is required (e.g., a major Policy Version upgrade requiring re-consent, or an active DSAR request in progress) and collapses cleanly (`0px`) when resolved.

---

## 3. Component & Workflow Pattern Findings

### 3.1 Account Settings & Granular Preference Toggles
- **Row Layout**: Two-column flex/grid row (`1fr auto`, gap `24px`, padding `20px 24px`) separated by `1px` subtle horizontal dividers inside a grouped surface card.
- **Copy Hierarchy**: Purpose title (`16px`, weight `600`, line-height `1.4`) + regulatory legal basis badge (`Essential` / `Opt-In` / `Opt-Out`) on line 1; plain-language data usage description (`14px`, weight `400`, line-height `1.5`, muted contrast `>= 5.9:1`) on line 2.
- **Toggle Ergonomics**: Visual switch track `44px × 24px` (`2px` internal thumb inset, `20px × 20px` circular thumb) wrapped in a `>= 44px × 44px` hit area (`role="switch"`, `aria-checked`). Essential/strictly-necessary purposes render in a locked **"Always Active"** pill + disabled switch state with lock icon so users never confuse mandatory security cookies with optional tracking.
- **Anti-Dark-Pattern Parity**: Primary banner actions (**Accept All**, **Reject Optional**, **Customize Choices**) use identical height (`44px`), identical font size (`14px`), and >= `4.5:1` readable contrast—never hiding opt-out behind low-contrast text links.

### 3.2 Data Tables, Filter Bars, Status Pills & Pagination
- **Filter Toolbar**: Positioned directly above the table card (`padding: 16px`, `gap: 12px`, flex-wrap). Combines a `280px`–`320px` search input (`44px` height), segmented status pills or `<select>` filter (`44px` height), and result count summary (`Showing 1–10 of 48 records`).
- **Desktop Table Geometry**:
  - Header row (`<thead>`): Height `40px`, background subtle neutral (`#EFECE6`), `12px` (`0.75rem`) font size, weight `600`, uppercase tracking `+0.04em`, `1px` bottom structural border, cell padding `10px 16px`.
  - Data rows (`<tbody>`): Minimum row height `52px` (`56px` when containing two-line user email + ID), cell padding `12px 16px`, `1px` bottom hairline border, subtle hover highlight (`#F7F6F3`), right-aligned numeric metrics (`font-variant-numeric: tabular-nums`) and action menus.
  - Monospace Identifiers: Audit hashes (`SHA-256` truncated to `12 chars` with copy button), API key prefixes (`ch_live_8f92...`), policy versions (`v2.0.0`), and timestamps render in `JetBrains Mono` (`12px`–`13px`).
- **Status Pills**: Height `24px`, padding `2px 10px`, border-radius `9999px`, `1px` semantic border + tinted background + high-contrast text (`>= 7:1`) + `6px` leading status dot (ensuring color-blind safety via text + icon/dot).
- **Pagination Bar**: Footer bar inside table card (`height: 52px`, `padding: 8px 16px`), showing `Page X of Y`, rows-per-page selector (`10 / 25 / 50`), and `Previous` / `Next` buttons (`min-height: 36px` visual with `44px` touch target on mobile).
- **Mobile Responsive Table (`< 768px`)**: Avoids cramped horizontal scroll traps for primary queues by transforming each `<tr>` into a **stacked mobile card** (`padding: 16px`, `gap: 10px`, header = Subject + Status Pill, body = 2-column key-value grid, footer = full-width `44px` action button).

### 3.3 Forms, Modals, Drawers & Destructive Confirmations
- **Form Controls**: Explicit `<label>` above every input (`14px`, weight `500`, `margin-bottom: 6px`), never placeholder-only labels. Input height `44px` (`2.75rem`), horizontal padding `12px`, `1px` high-visibility control border (`>= 3:1` against surface), `8px` border radius, `2px` offset focus ring on `:focus-visible`. Helper/validation text (`12px`, `margin-top: 6px`) linked via `aria-describedby`.
- **Modals (`Dialog`)**:
  - Backdrop: `rgba(20, 21, 25, 0.52)` with subtle `backdrop-filter: blur(4px)`.
  - Widths: Small confirmation `440px`, Standard form/preference modal `560px`, Wide SDUI preview / payload inspector `720px`. On `< 640px` mobile, modals anchor to the bottom sheet (`width: 100%`, `border-radius: 16px 16px 0 0`, max-height `90dvh`).
  - Anatomy: Header (`20px 24px`, `1px` bottom border, `18px`/`20px` title + `44px × 44px` close button), scrollable body (`24px`, `overflow-y: auto`), sticky action footer (`16px 24px`, right-aligned `Cancel` + `Primary/Destructive CTA`, stacked full-width on `< 480px`).
  - Accessibility: Focus trapped inside dialog on open, `Escape` closes (unless an active mutation is in-flight), focus restored to trigger element on close.

### 3.4 Toast Notifications, Skeleton Loaders, Empty States & Error States
- **Toast Notifications**: Viewport bottom-right (`24px` inset; top-center `16px` inset on `< 768px`), width `360px` (`calc(100vw - 32px)` on mobile), `12px` radius, `1px` semantic border + `0 8px 24px rgba(20, 21, 25, 0.12)` elevation, auto-dismiss `5000ms` for success/info (`aria-live="polite"`), persistent until dismissed for error/actionable warnings (`role="alert"`).
- **Skeleton Loaders**: Match exact final component geometry to guarantee `CLS < 0.05` (e.g., `88px` height preference row skeleton, `112px` KPI metric card skeleton, `52px × 5` table row skeletons). Subtle `1.6s` linear shimmer (`#EFECE6` -> `#E5E1D8` -> `#EFECE6`), automatically disabled under `prefers-reduced-motion: reduce`.
- **Designed Empty States** (Gumroad `<Placeholder>` 3-part pattern):
  1. `48px × 48px` rounded icon badge (`12px` radius, subtle neutral fill, `24px` line icon).
  2. Concise title (`16px`, weight `600`, `#141519`) + 1-sentence explanation (`14px`, `#5C5F69`, max-width `380px`).
  3. Constructive primary or secondary CTA (`44px` height, e.g., *"Create First Experiment"* or *"Clear Active Filters"*).
- **Error & Unknown-SDUI Fallback States**:
  - **API / Network Error**: Structured callout card (`1px` crimson border `#FCA5A5`, `#FEF2F2` background, error code in monospace, human-readable recovery message, and `Retry` button).
  - **SDUI Unknown Component Fallback**: In server-driven UI (`screen(name, context)`), unrecognized schema node types never crash the React tree; in production they log a telemetry warning and render `null` (or a non-blocking `1px dashed` diagnostic placeholder in Admin Preview mode).

---

## 4. Concrete Typography, Spacing, Elevation & Breakpoint Benchmarks

### 4.1 Observed Typography Benchmarks
- **Whop Frosted UI 10-Step Scale**: `10px` (0), `12px` (1 - labels/badges), `14px` (2 - compact dashboard body/tables), `16px` (3 - default portal body), `18px` (4 - emphasized), `20px` (5 - card/modal subheadings), `24px` (6 - section/page headings), `28px` (7), `32px` (8 - display/KPI metrics), `40px` (9 - hero).
- **Gumroad Type Pairing**: Expressive geometric heading font paired with ultra-legible grotesque (`Inter`) at `16px` (`1rem`) base, `14px` (`0.875rem`) dense table cells, `12px` (`0.75rem`) metadata, and tight negative letter-spacing (`-0.02em` to `-0.03em`) on headings `>= 24px`.
- **ConsentHub Standardized Scale**:
  - Display / Heading Font: `'Plus Jakarta Sans'` (geometric authority, crisp numerals) with `'Inter'` fallback.
  - UI / Body Font: `'Inter'` (high x-height, tabular numerals `tnum` for analytics and ledger timestamps).
  - Audit / Hash / SDK Font: `'JetBrains Mono'` (`12px`–`13px`, `line-height: 1.5`) for SHA-256 ledger hashes, policy semver tags, API keys, and SDUI JSON payloads.

### 4.2 Spacing, Radii, Borders & Touch Targets
- **Spacing Grid**: Strict `8px` base spatial grid (`8, 16, 24, 32, 40, 48, 64px`) with `4px` (`0.25rem`) and `12px` (`0.75rem`) half-steps reserved for internal component padding (badge/button/cell insets).
- **Border Radii**:
  - `8px` (`0.5rem`) on interactive controls (buttons, inputs, selects, dropdown items).
  - `12px` (`0.75rem`) on structural containers (cards, data tables, banners, callouts).
  - `16px` (`1rem`) on floating modal dialogs and mobile bottom sheets.
  - `9999px` on status pills, regulatory region badges (`GDPR`, `CCPA`, `DEFAULT`), and toggle switch tracks.
- **Border-First vs. Shadow Discipline**:
  - Static layout elements (sidebar, topbar, KPI cards, preference cards, tables) use `1px solid` structural borders (`shadow: none`), eliminating visual muddiness.
  - Shadows are strictly reserved for **z-axis overlays**: sticky Consent Banner (`--ch-shadow-banner`), dropdowns/popovers (`--ch-shadow-md`), and modals/drawers (`--ch-shadow-lg`).
- **Touch Target Guarantee**: Every interactive button, toggle switch hit-box, select trigger, tab, pagination button, and modal close button enforces `min-height: 44px` and `min-width: 44px` (WCAG 2.5.5 / Apple HIG / Android Material touch compliance).

### 4.3 Responsive Breakpoint Matrix

| Token | Viewport Width | Target Device Class | ConsentHub Structural Adaptation |
|-------|----------------|---------------------|----------------------------------|
| `xs` (Base) | `375px` (`0px`–`767px`) | Mobile phones (`iPhone SE / 14 / Pixel`) | Single-column grids (`16px` padding); Admin `256px` sidebar collapses into off-canvas slide-over drawer + `64px` mobile topbar (`44px × 44px` menu trigger); Data tables render as stacked cards; Consent Banner docks full-width at bottom with stacked `44px` CTAs |
| `md` | `768px` (`768px`–`1023px`) | Tablets (`iPad Mini / Portrait`) | `24px` container padding; 2-column KPI metric grid; desktop `<table>` layout enabled with horizontal scroll safeguard; inline Consent Banner buttons; Admin sidebar remains slide-over drawer to preserve `720px` table viewport |
| `lg` | `1024px` (`1024px`–`1279px`) | Laptops / Landscape Tablets | Admin `256px` fixed left sidebar docks permanently; 4-column KPI grid activates; End-User Privacy Center centers at `1040px` max-width |
| `xl` | `1280px` (`>= 1280px`) | Desktop Monitors | Full `1280px` fluid Admin workspace with `32px` horizontal gutters; side-by-side SDUI JSON Editor + Live Screen Preview split pane enabled |
