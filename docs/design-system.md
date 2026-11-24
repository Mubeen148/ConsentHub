# ConsentHub — Design System & Token Specification (`T-02`)

> **Authoritative Source of Truth**: This specification governs all UI primitives, Server-Driven UI (SDUI) rendered components, layouts, and third-party embeddable SDK (`consent.js`) styles across ConsentHub. All values are deterministic (`px`, `rem`, `hex`, `hsl`, WCAG 2.1 contrast ratios) for direct implementation in `frontend/src/styles/tokens.css` and `frontend/tailwind.config.ts`.

---

## 1. Brand Personality & Design Principles

- **Architectural Clarity**: Privacy software earns trust through calm legibility, honest symmetry, and zero visual gimmicks. Static surfaces rely on warm architectural neutrals (`#F7F6F3` canvas, `#FFFFFF` cards) and crisp `1px` structural borders (`#D0CBC0`) rather than heavy drop shadows.
- **High-Trust Privacy Engineering**: Every consent choice displays its regulatory regime (`GDPR`, `CCPA`, `DEFAULT`), legal basis (`Essential`, `Opt-In`, `Opt-Out`), policy version (`v1.0.0`), and cryptographic ledger proof (`SHA-256` hash in monospace).
- **Zero Dark Patterns (Symmetric Choice Architecture)**: "Accept All" and "Reject Optional" actions share identical height (`44px`), identical typography (`14px`, weight `600`), and WCAG AA contrast (`>= 4.5:1`). Users never hunt through nested menus to decline non-essential processing.
- **Creator-Platform Ergonomics**: Inspired by best-in-class SaaS dashboards, dense operational views (DSAR queues, A/B experiment conversions, SHA-chained audit logs) use tabular numerals (`font-variant-numeric: tabular-nums`), `44px` accessible touch targets, and instant keyboard navigation.

---

## 2. Color Token System (WCAG 2.1 AA/AAA Verified)

All text tokens meet **WCAG 2.1 AA (`>= 4.5:1`)**—and predominantly **AAA (`>= 7.0:1`)**—against both pure white surfaces (`#FFFFFF`) and the warm neutral canvas (`#F7F6F3`). All interactive control borders and focus indicators meet **WCAG 1.4.11 Non-Text Contrast (`>= 3.0:1`)**.

### 2.1 Surfaces, Canvas & Borders

| CSS Token | Hex | HSL | Usage | Verified Contrast Ratio |
|-----------|-----|-----|-------|-------------------------|
| `--ch-bg-canvas` | `#F7F6F3` | `hsl(45, 17%, 96%)` | App page canvas (Portal & Admin workspace) | Base background (`L = 0.923`) |
| `--ch-bg-surface` | `#FFFFFF` | `hsl(0, 0%, 100%)` | Cards, tables, sidebar, topbar, modals, inputs | Elevated clean surface (`L = 1.000`) |
| `--ch-bg-subtle` | `#EFECE6` | `hsl(40, 21%, 92%)` | Table `<thead>`, hover fills, code blocks, skeleton base | Subtle grouping (`L = 0.848`) |
| `--ch-bg-muted` | `#E5E1D8` | `hsl(42, 19%, 87%)` | Skeleton shimmer peak, inactive toggle track fill | Muted fill (`L = 0.768`) |
| `--ch-bg-inverse` | `#111827` | `hsl(221, 39%, 11%)` | Dark tooltips, SDK code preview drawer | `17.52:1` with `#FFFFFF` text |
| `--ch-border-subtle` | `#E2DED5` | `hsl(42, 18%, 86%)` | Internal table row hairlines, section dividers | Decorative divider |
| `--ch-border-default` | `#D0CBC0` | `hsl(41, 14%, 78%)` | Card, sidebar, header, and table outer `1px` border | Structural container border |
| `--ch-border-control` | `#76726A` | `hsl(40, 5%, 44%)` | Form inputs, checkboxes, off-state switch border | **`4.73:1`** on `#FFFFFF` / **`4.38:1`** on `#F7F6F3` (`>= 3:1` AA) |
| `--ch-border-strong` | `#141519` | `hsl(228, 11%, 9%)` | Selected card border, active tab indicator | **`18.23:1`** on `#FFFFFF` |
| `--ch-focus-ring` | `#1D4ED8` | `hsl(224, 76%, 48%)` | `2px` `:focus-visible` outline (`2px` offset) | **`7.14:1`** on `#FFFFFF` / **`6.62:1`** on `#F7F6F3` (`>= 3:1` AA) |

### 2.2 Typography Hierarchy Tokens

| CSS Token | Hex | HSL | Usage | Contrast on `#FFFFFF` | Contrast on `#F7F6F3` |
|-----------|-----|-----|-------|-----------------------|-----------------------|
| `--ch-text-primary` | `#141519` | `hsl(228, 11%, 9%)` | Headings, primary body copy, table primary values | **`18.23:1` (AAA)** | **`16.89:1` (AAA)** |
| `--ch-text-secondary` | `#45474F` | `hsl(228, 7%, 29%)` | Purpose descriptions, subheadings, form labels | **`9.38:1` (AAA)** | **`8.69:1` (AAA)** |
| `--ch-text-muted` | `#5C5F69` | `hsl(226, 7%, 39%)` | Timestamps, helper captions, pagination metadata | **`6.36:1` (AA)** | **`5.90:1` (AA)** |
| `--ch-text-on-primary` | `#FFFFFF` | `hsl(0, 0%, 100%)` | Text/icons on primary ink, cobalt, and danger buttons | — | — |

### 2.3 Interactive Brand & Action Tokens

| CSS Token | Hex | HSL | Foreground Pair | Verified Contrast Ratio |
|-----------|-----|-----|-----------------|-------------------------|
| `--ch-action-primary` | `#141519` | `hsl(228, 11%, 9%)` | `#FFFFFF` | **`18.23:1` (AAA)** — Primary CTA fill |
| `--ch-action-primary-hover` | `#272932` | `hsl(231, 12%, 17%)` | `#FFFFFF` | **`14.35:1` (AAA)** — Primary CTA hover |
| `--ch-action-primary-active` | `#0B0C0E` | `hsl(220, 12%, 5%)` | `#FFFFFF` | **`19.60:1` (AAA)** — Primary CTA active |
| `--ch-accent-brand` | `#1E40AF` | `hsl(226, 71%, 40%)` | `#FFFFFF` (fill) or `#FFFFFF` bg (link) | **`8.82:1` (AAA)** — Active toggle track, links, chart primary |
| `--ch-accent-brand-hover` | `#1E3A8A` | `hsl(224, 64%, 33%)` | `#FFFFFF` | **`10.65:1` (AAA)** — Active toggle/link hover |
| `--ch-accent-brand-subtle` | `#EFF6FF` | `hsl(214, 100%, 97%)` | `#1E3A8A` | **`9.98:1` (AAA)** — Selected nav item / info callout |

### 2.4 Semantic Status Tokens (Emerald, Amber, Crimson)

| Semantic State | Surface (`bg`) | Border (`1px`) | Text / Icon Token | Solid Action Fill | Verified Contrast Ratio |
|----------------|----------------|----------------|-------------------|-------------------|-------------------------|
| **Success / Granted / Active** | `#ECFDF5` `hsl(152, 81%, 96%)` | `#6EE7B7` `hsl(156, 72%, 67%)` | `#065F46` `hsl(163, 88%, 20%)` | `#047857` `hsl(163, 94%, 24%)` | Text on tint: **`7.22:1` (AAA)**; `#FFFFFF` on solid: **`5.50:1` (AA)** |
| **Warning / Pending DSAR** | `#FFFBEB` `hsl(48, 100%, 96%)` | `#FCD34D` `hsl(46, 97%, 65%)` | `#92400E` `hsl(23, 83%, 31%)` | `#B45309` `hsl(26, 90%, 37%)` | Text on tint: **`7.13:1` (AAA)**; `#FFFFFF` on solid: **`5.33:1` (AA)** |
| **Danger / Revoked / Delete** | `#FEF2F2` `hsl(0, 86%, 97%)` | `#FCA5A5` `hsl(0, 93%, 82%)` | `#991B1B` `hsl(0, 70%, 35%)` | `#B91C1C` `hsl(0, 74%, 42%)` (hover `#991B1B`) | Text on tint: **`7.98:1` (AAA)**; `#FFFFFF` on `#B91C1C`: **`6.65:1` (AA)** |

### 2.5 Regulatory Region & Purpose Lock Badges

| Badge Key | Background | Border (`1px`) | Text Color | Contrast | Usage |
|-----------|------------|----------------|------------|----------|-------|
| **`GDPR`** | `#EFF6FF` `hsl(214, 100%, 97%)` | `#93C5FD` `hsl(213, 94%, 78%)` | `#1E3A8A` `hsl(224, 64%, 33%)` | **`9.98:1` (AAA)** | EU/EEA Strict Opt-In banner, rule cards, ledger rows |
| **`CCPA`** | `#ECFEFF` `hsl(183, 100%, 96%)` | `#67E8F9` `hsl(187, 92%, 69%)` | `#155E75` `hsl(194, 70%, 27%)` | **`7.03:1` (AAA)** | California Opt-Out ("Do Not Sell/Share") regime |
| **`DEFAULT`** | `#F4F3EF` `hsl(45, 16%, 95%)` | `#CBC6BA` `hsl(42, 14%, 76%)` | `#3F424A` `hsl(225, 8%, 27%)` | **`9.64:1` (AAA)** | Global baseline privacy regime |
| **`ESSENTIAL`** | `#F1EFEA` `hsl(40, 19%, 93%)` | `#C5C0B4` `hsl(42, 12%, 74%)` | `#363840` `hsl(228, 8%, 23%)` | **`10.85:1` (AAA)** | Locked "Always Active" strictly-necessary purpose pill |

---

## 3. Typography System

### 3.1 Font Families
- **Display & Headings (`--ch-font-display`)**: `'Plus Jakarta Sans', 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`
- **UI & Body (`--ch-font-sans`)**: `'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif`
- **Audit, Ledger & Code (`--ch-font-mono`)**: `'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace`
- **Numeric Alignment**: Apply `font-variant-numeric: tabular-nums` to all KPI counters, conversion percentages, timestamps, and table columns.

### 3.2 Concrete Type Scale (Base `16px` = `1rem`)

| Token | Size (`px` / `rem`) | Weight | Line Height | Letter Spacing | Semantic Role |
|-------|---------------------|--------|-------------|----------------|---------------|
| `display-lg` | `32px` / `2.00rem` | `700` (Bold) | `1.20` (`38.4px`) | `-0.025em` | Page hero title, KPI headline metric |
| `heading-lg` | `24px` / `1.50rem` | `700` (Bold) | `1.25` (`30.0px`) | `-0.020em` | Workspace page title (`h1`) |
| `heading-md` | `20px` / `1.25rem` | `600` (SemiBold) | `1.25` (`25.0px`) | `-0.015em` | Section heading (`h2`), Modal title, Banner title |
| `body-base` | `16px` / `1.00rem` | `400` / `500` / `600` | `1.50` (`24.0px`) | `0em` | Portal body copy, card titles (`600`), form inputs (`400`) |
| `body-sm` | `14px` / `0.875rem` | `400` / `500` / `600` | `1.50` (`21.0px`) | `0em` | Purpose descriptions (`400`), labels (`500`), buttons (`600`), table cells |
| `caption-xs` | `12px` / `0.75rem` | `500` / `600` | `1.40` (`16.8px`) | `+0.01em` (`+0.04em` uppercase) | Status badges, helper text, `<thead>` column headers |
| `mono-xs` | `12px` / `0.75rem` | `500` (Medium) | `1.50` (`18.0px`) | `0em` | SHA-256 hashes, API key prefixes, policy versions, SDUI keys |

---

## 4. Spacing, Sizing, Radii & Elevation

### 4.1 `8px` Spatial Grid (with `4px` / `12px` Component Half-Steps)

| Token | px | rem | Standard Usage |
|-------|----|-----|----------------|
| `--ch-space-1` | `4px` | `0.25rem` | Half-step: badge vertical padding, icon-to-label tight inline gap |
| `--ch-space-2` | `8px` | `0.50rem` | `1x` grid: button icon gap, compact stack gap, toolbar item gap |
| `--ch-space-3` | `12px` | `0.75rem` | Half-step: input horizontal padding, table cell vertical padding |
| `--ch-space-4` | `16px` | `1.00rem` | `2x` grid: mobile page gutter, table cell horizontal padding, card compact inset |
| `--ch-space-5` | `20px` | `1.25rem` | Preference row vertical padding, modal header vertical padding |
| `--ch-space-6` | `24px` | `1.50rem` | `3x` grid: standard card padding, desktop grid gutter, modal body padding |
| `--ch-space-8` | `32px` | `2.00rem` | `4x` grid: major section separation, desktop (`>=1280px`) horizontal gutter |
| `--ch-space-10` | `40px` | `2.50rem` | `5x` grid: empty state vertical padding |
| `--ch-space-12` | `48px` | `3.00rem` | `6x` grid: page bottom breathing room |
| `--ch-space-16` | `64px` | `4.00rem` | `8x` grid: sticky topbar height |

### 4.2 Border Radii, Shadows & Touch Targets
- **Radii**:
  - `--ch-radius-control`: `8px` (`0.5rem`) — Buttons, inputs, selects, textareas, nav items, code blocks.
  - `--ch-radius-card`: `12px` (`0.75rem`) — Cards, data table wrappers, callouts, toasts, sticky consent banner.
  - `--ch-radius-modal`: `16px` (`1rem`) — Dialog modals (`16px 16px 0 0` on mobile bottom sheets).
  - `--ch-radius-pill`: `9999px` — Status badges, region pills, toggle switch tracks.
- **Shadows (Overlay-Only Discipline)**:
  - `--ch-shadow-none`: `none` (Mandatory on static cards and tables—use `1px solid var(--ch-border-default)`).
  - `--ch-shadow-sm`: `0 1px 2px 0 rgba(20, 21, 25, 0.05)` (Toggle thumb & subtle dropdown trigger).
  - `--ch-shadow-md`: `0 8px 24px -4px rgba(20, 21, 25, 0.12), 0 2px 6px -1px rgba(20, 21, 25, 0.06)` (Dropdowns, popovers, toasts).
  - `--ch-shadow-lg`: `0 20px 40px -8px rgba(20, 21, 25, 0.20), 0 4px 12px -2px rgba(20, 21, 25, 0.08)` (Modals, slide-over drawers).
  - `--ch-shadow-banner`: `0 -4px 24px rgba(20, 21, 25, 0.10)` (Bottom-docked region Consent Banner).
- **Minimum Interactive Tap Target**: `--ch-tap-target: 44px` (`2.75rem`). Every interactive control (`Button`, `IconButton`, `Switch` wrapper, `Select`, `Tab`, `Pagination` item, `Modal` close button) MUST enforce `min-height: 44px` and `min-width: 44px`.

---

## 5. Navigation & Layout Specifications

### 5.1 Responsive Breakpoints
- `xs` (Mobile baseline): `375px` (`0px`–`767px`)
- `md` (Tablet): `768px` (`768px`–`1023px`)
- `lg` (Laptop / Desktop sidebar threshold): `1024px` (`1024px`–`1279px`)
- `xl` (Wide Desktop workspace): `1280px` (`>= 1280px`)

### 5.2 End-User Privacy Center Shell (`/privacy/*`)
- **Top Navigation Bar**: Height `64px`, `background: var(--ch-bg-surface)`, `border-bottom: 1px solid var(--ch-border-default)`, `position: sticky; top: 0; z-index: 30`.
  - Left: ConsentHub shield wordmark + product title (`16px`, weight `700`).
  - Center/Left Tabs (`>= 768px`): `Preferences`, `Consent Ledger`, `Data Rights (DSAR)`, `Account` (`height: 64px` tab rail with `2px` bottom `#141519` active bar; collapses into horizontally scrollable `44px` pill bar below header on `< 768px`).
  - Right: Simulated/Detected Region Selector (`GDPR` | `CCPA` | `DEFAULT` pill, `44px` height) + User Avatar/Sign Out.
- **Main Container**: `max-width: 1040px`, centered (`margin: 0 auto`), padding `24px 16px 64px` on `< 768px` and `32px 24px 64px` on `>= 768px`.
- **Region-Aware Consent Banner (SDUI + `consent.js` SDK)**:
  - Desktop (`>= 768px`): Floating bottom card (`position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); width: min(960px, calc(100vw - 48px)); z-index: 50`), `background: #FFFFFF`, `border: 1px solid #141519`, `border-radius: 12px`, `padding: 24px`, `box-shadow: var(--ch-shadow-banner)`.
  - Mobile (`< 768px`): Full-width bottom sheet (`bottom: 0; left: 0; right: 0; border-radius: 16px 16px 0 0; padding: 20px 16px`), action buttons stacked (`width: 100%`, `height: 44px`, `gap: 8px`).
  - Region Behavior:
    - `GDPR`: Displays `GDPR · EU Opt-In` badge; non-essential toggles default `OFF`; symmetric `Accept All` (`Primary`) + `Reject Optional` (`Secondary`) + `Manage Preferences` (`Ghost`).
    - `CCPA`: Displays `CCPA · California Opt-Out` badge; includes explicit **"Do Not Sell or Share My Personal Information"** one-click action (`44px` height).
    - `DEFAULT`: Displays `Global Privacy Standard` badge with clear preference customization.

### 5.3 Admin Governance & Experimentation Console (`/admin/*`)
- **Desktop Fixed Sidebar (`>= 1024px`)**: `width: 256px`, `height: 100dvh`, `position: fixed; left: 0; top: 0`, `background: var(--ch-bg-surface)`, `border-right: 1px solid var(--ch-border-default)`, `padding: 16px 12px`.
  - Navigation Items (`height: 44px`, `padding: 0 12px`, `border-radius: 8px`, `gap: 10px`, `font-size: 14px`, `font-weight: 500`):
    1. `Overview & Analytics`
    2. `Purposes & Policies`
    3. `Regional Rules`
    4. `SDUI & Experiments`
    5. `DSAR Queue` (with live pending count pill)
    6. `API Keys & SDK`
    7. `Audit Ledger`
  - Active State: `background: var(--ch-accent-brand-subtle)` (`#EFF6FF`), `color: var(--ch-accent-brand-hover)` (`#1E3A8A`), `font-weight: 600`.
- **Mobile / Tablet Slide-Over Drawer (`< 1024px`)**: Sidebar hides off-canvas (`transform: translateX(-100%)`); `64px` sticky top header displays a `44px × 44px` hamburger `IconButton` on the left that opens a `280px` modal drawer (`z-index: 50`) over a `rgba(20, 21, 25, 0.52)` backdrop with `Escape` and outside-tap dismissal.
- **Sticky Workspace Header**: `height: 64px`, `border-bottom: 1px solid var(--ch-border-default)`, `background: rgba(255, 255, 255, 0.92)`, `backdrop-filter: blur(8px)`, `padding: 0 24px` (`0 32px` on `>= 1280px`).
- **Fluid Content Workspace**: `margin-left: 256px` on `>= 1024px` (`0` on `< 1024px`), inner container `max-width: 1280px`, `padding: 24px 16px` (`< 768px`), `24px` (`768px`–`1279px`), `32px` (`>= 1280px`).

---

## 6. Component Specifications

### 6.1 Buttons (`<Button />`)
- **Shared Geometry**: `min-height: 44px` (`2.75rem`), `padding: 10px 18px`, `border-radius: 8px`, `font-size: 14px` (`0.875rem`), `font-weight: 600`, `line-height: 1.25`, `gap: 8px`, `transition: background-color 150ms ease, border-color 150ms ease`.
- **Variants**:
  - `primary`: `background: #141519`, `color: #FFFFFF`, `border: 1px solid #141519`; hover `#272932`; active `#0B0C0E`.
  - `secondary`: `background: #FFFFFF`, `color: #141519`, `border: 1px solid #76726A` (hover `background: #F7F6F3`, `border-color: #141519`). Used for symmetric **"Reject Optional"** and **"Cancel"** actions.
  - `ghost`: `background: transparent`, `color: #141519`, `border: 1px solid transparent` (hover `background: #EFECE6`).
  - `destructive`: `background: #B91C1C`, `color: #FFFFFF`, `border: 1px solid #B91C1C` (hover `#991B1B`). Used for **"Confirm Data Erasure"**, **"Revoke API Key"**, and **"Reject DSAR"**.
- **States**: `:focus-visible` renders `outline: 2px solid #1D4ED8; outline-offset: 2px`. `:disabled` / `loading` sets `opacity: 0.55; cursor: not-allowed` and renders a `16px` inline SVG spinner without shifting button width.

### 6.2 Toggle Switches (`<ConsentToggle />`)
- **Hit Target**: Outer `<button role="switch">` enforces `min-width: 44px; min-height: 44px; display: inline-flex; align-items: center; justify-content: center`.
- **Visual Track**: `width: 44px; height: 24px; border-radius: 9999px; position: relative; transition: background-color 150ms ease`.
  - **Unchecked (`aria-checked="false"`)**: `background: #E5E1D8`, `border: 1px solid #76726A` (`>= 3.4:1` contrast). Thumb (`18px × 18px`, `#FFFFFF`, `box-shadow: var(--ch-shadow-sm)`) at `transform: translateX(2px)`.
  - **Checked (`aria-checked="true"`)**: `background: #1E40AF` (hover `#1E3A8A`), `border: 1px solid #1E40AF`. Thumb (`18px × 18px`, `#FFFFFF`) at `transform: translateX(22px)`.
  - **Locked Essential State (`required={true}`)**: Renders adjacent **`Always Active`** pill (`background: #F1EFEA`, `border: 1px solid #C5C0B4`, `color: #363840`, `12px` weight `600`, `12px` lock icon) + switch set to `aria-checked="true" aria-disabled="true"` with `background: #45474F` and lock glyph inside/beside track so screen readers announce `"Essential Security & Authentication, Always Active, switch checked, disabled"`.

### 6.3 Cards (`<Card />`)
- **Geometry**: `background: #FFFFFF`, `border: 1px solid #D0CBC0`, `border-radius: 12px`, `box-shadow: none`.
- **Padding Variants**: `compact` (`16px`), `default` (`24px`), `flush` (`0px` — used for tables and divided preference lists).
- **Header Slot**: Optional `padding: 20px 24px`, `border-bottom: 1px solid #E2DED5`, flex `space-between` for title + badge/action.

### 6.4 Data Tables (`<DataTable />`)
- **Desktop (`>= 768px`)**:
  - Outer wrapper: `Card` (`flush`, `overflow: hidden`).
  - `<thead>`: `background: #EFECE6`, `border-bottom: 1px solid #D0CBC0`, `th` height `40px`, `padding: 10px 16px`, `font-size: 12px`, `font-weight: 600`, `letter-spacing: 0.04em`, `text-transform: uppercase`, `color: #45474F`.
  - `<tbody>`: `tr` min-height `52px`, `border-bottom: 1px solid #E2DED5` (`last-child` `none`), hover `background: #F7F6F3`, `td` `padding: 12px 16px`, `font-size: 14px`, `color: #141519`.
- **Mobile (`< 768px` Responsive Stacked Card List)**:
  - Hides `<thead>` visually and renders each record as an independent stacked card (`padding: 16px`, `border-bottom: 1px solid #D0CBC0`, `display: grid`, `gap: 10px`):
    - Top row: Primary identifier + `StatusBadge`.
    - Middle rows: Label (`12px`, `#5C5F69`) + Value (`14px`, `#141519` or `mono-xs`) pairs.
    - Bottom row: Full-width or flex-row `44px` action buttons.

### 6.5 Modals (`<Modal />`)
- **Overlay**: `position: fixed; inset: 0; z-index: 50; background: rgba(20, 21, 25, 0.52); backdrop-filter: blur(4px)`.
- **Dialog Surface**: `background: #FFFFFF`, `border: 1px solid #D0CBC0`, `border-radius: 16px`, `box-shadow: var(--ch-shadow-lg)`, `width: min(560px, calc(100vw - 32px))` (`440px` for confirmations, `720px` for SDUI JSON inspector), `max-height: 88dvh`, flex column.
- **Mobile `< 640px`**: Docks to bottom (`width: 100vw; border-radius: 16px 16px 0 0; max-height: 92dvh`).
- **A11y Contract**: `role="dialog"`, `aria-modal="true"`, `aria-labelledby`, focus trap, `Escape` key handler, scroll lock on `<body>`.

### 6.6 Skeletons, Empty States, Error States & Toasts
- **Skeleton (`<Skeleton />`)**: `border-radius: 8px`, linear gradient `90deg` `#EFECE6 25%, #E5E1D8 50%, #EFECE6 75%`, `background-size: 200% 100%`, `1.6s ease-in-out infinite` (`animation: none` when `prefers-reduced-motion: reduce`). Must match exact loaded row/card heights (`44px` controls, `88px` preference rows, `112px` KPI cards).
- **Empty State (`<EmptyState />`)**: Centered flex column, `padding: 40px 24px`, `border: 1px dashed #D0CBC0`, `border-radius: 12px`, `background: #FFFFFF`. Contains `48px × 48px` `#EFECE6` icon container (`12px` radius), `16px` SemiBold title, `14px` `#5C5F69` description (`max-width: 380px`), and optional `44px` CTA button.
- **Error State (`<ErrorState />`)**: `background: #FEF2F2`, `border: 1px solid #FCA5A5`, `border-radius: 12px`, `padding: 20px 24px`, `role="alert"`. Displays crimson alert icon (`#991B1B`), clear title (`16px` SemiBold `#991B1B`), actionable message (`14px` `#141519`), optional request/correlation ID in `JetBrains Mono`, and `44px` `Retry` button.
- **Toast (`<Toast />`)**: Fixed bottom-right (`bottom: 24px; right: 24px; z-index: 60`; top-center `top: 16px` on `< 768px`), `width: min(380px, calc(100vw - 32px))`, `min-height: 56px`, `padding: 12px 16px`, `background: #FFFFFF`, `border: 1px solid #D0CBC0`, `border-left: 4px solid <semantic-solid-color>`, `border-radius: 12px`, `box-shadow: var(--ch-shadow-md)`, `44px × 44px` dismiss button.

---

## 7. Deterministic Implementation Artifacts

### 7.1 CSS Custom Properties (`frontend/src/styles/tokens.css`)

```css
:root {
  /* Font Families */
  --ch-font-display: "Plus Jakarta Sans", "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --ch-font-sans: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  --ch-font-mono: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;

  /* Typography Scale */
  --ch-text-xs: 0.75rem;    /* 12px */
  --ch-text-sm: 0.875rem;   /* 14px */
  --ch-text-base: 1rem;     /* 16px */
  --ch-text-lg: 1.25rem;    /* 20px */
  --ch-text-xl: 1.5rem;     /* 24px */
  --ch-text-2xl: 2rem;      /* 32px */

  --ch-leading-tight: 1.25;
  --ch-leading-normal: 1.5;

  /* Surfaces & Canvas */
  --ch-bg-canvas: #F7F6F3;
  --ch-bg-surface: #FFFFFF;
  --ch-bg-subtle: #EFECE6;
  --ch-bg-muted: #E5E1D8;
  --ch-bg-inverse: #111827;

  /* Borders & Focus (WCAG AA >= 3:1 on controls) */
  --ch-border-subtle: #E2DED5;
  --ch-border-default: #D0CBC0;
  --ch-border-control: #76726A;
  --ch-border-strong: #141519;
  --ch-focus-ring: #1D4ED8;

  /* Text Hierarchy (WCAG AA >= 4.5:1 / AAA >= 7:1) */
  --ch-text-primary: #141519;
  --ch-text-secondary: #45474F;
  --ch-text-muted: #5C5F69;
  --ch-text-on-primary: #FFFFFF;

  /* Primary & Brand Actions */
  --ch-action-primary: #141519;
  --ch-action-primary-hover: #272932;
  --ch-action-primary-active: #0B0C0E;
  --ch-accent-brand: #1E40AF;
  --ch-accent-brand-hover: #1E3A8A;
  --ch-accent-brand-subtle: #EFF6FF;

  /* Semantic Status: Success (Emerald) */
  --ch-status-success-bg: #ECFDF5;
  --ch-status-success-border: #6EE7B7;
  --ch-status-success-text: #065F46;
  --ch-status-success-solid: #047857;

  /* Semantic Status: Warning (Amber) */
  --ch-status-warning-bg: #FFFBEB;
  --ch-status-warning-border: #FCD34D;
  --ch-status-warning-text: #92400E;
  --ch-status-warning-solid: #B45309;

  /* Semantic Status: Danger (Crimson) */
  --ch-status-danger-bg: #FEF2F2;
  --ch-status-danger-border: #FCA5A5;
  --ch-status-danger-text: #991B1B;
  --ch-status-danger-solid: #B91C1C;
  --ch-status-danger-hover: #991B1B;

  /* Region & Purpose Badges */
  --ch-badge-gdpr-bg: #EFF6FF;
  --ch-badge-gdpr-border: #93C5FD;
  --ch-badge-gdpr-text: #1E3A8A;

  --ch-badge-ccpa-bg: #ECFEFF;
  --ch-badge-ccpa-border: #67E8F9;
  --ch-badge-ccpa-text: #155E75;

  --ch-badge-default-bg: #F4F3EF;
  --ch-badge-default-border: #CBC6BA;
  --ch-badge-default-text: #3F424A;

  --ch-badge-essential-bg: #F1EFEA;
  --ch-badge-essential-border: #C5C0B4;
  --ch-badge-essential-text: #363840;

  /* 8px Spatial Scale (+ 4px/12px half-steps) */
  --ch-space-1: 0.25rem;  /* 4px */
  --ch-space-2: 0.5rem;   /* 8px */
  --ch-space-3: 0.75rem;  /* 12px */
  --ch-space-4: 1rem;     /* 16px */
  --ch-space-5: 1.25rem;  /* 20px */
  --ch-space-6: 1.5rem;   /* 24px */
  --ch-space-8: 2rem;     /* 32px */
  --ch-space-10: 2.5rem;  /* 40px */
  --ch-space-12: 3rem;    /* 48px */
  --ch-space-16: 4rem;    /* 64px */

  /* Radii & Layout Constants */
  --ch-radius-control: 0.5rem;   /* 8px */
  --ch-radius-card: 0.75rem;     /* 12px */
  --ch-radius-modal: 1rem;       /* 16px */
  --ch-radius-pill: 9999px;

  --ch-tap-target: 2.75rem;      /* 44px minimum touch target */
  --ch-sidebar-width: 16rem;     /* 256px */
  --ch-topbar-height: 4rem;      /* 64px */
  --ch-portal-max-w: 65rem;      /* 1040px */
  --ch-admin-max-w: 80rem;       /* 1280px */

  /* Overlay Shadows */
  --ch-shadow-sm: 0 1px 2px 0 rgba(20, 21, 25, 0.05);
  --ch-shadow-md: 0 8px 24px -4px rgba(20, 21, 25, 0.12), 0 2px 6px -1px rgba(20, 21, 25, 0.06);
  --ch-shadow-lg: 0 20px 40px -8px rgba(20, 21, 25, 0.20), 0 4px 12px -2px rgba(20, 21, 25, 0.08);
  --ch-shadow-banner: 0 -4px 24px rgba(20, 21, 25, 0.10);
}
```

### 7.2 Tailwind Theme Extension Mapping (`frontend/tailwind.config.ts`)

```ts
import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    screens: {
      xs: "375px",
      md: "768px",
      lg: "1024px",
      xl: "1280px",
    },
    extend: {
      fontFamily: {
        display: ["var(--ch-font-display)"],
        sans: ["var(--ch-font-sans)"],
        mono: ["var(--ch-font-mono)"],
      },
      fontSize: {
        xs: ["var(--ch-text-xs)", { lineHeight: "1.4", letterSpacing: "0.01em" }],
        sm: ["var(--ch-text-sm)", { lineHeight: "1.5" }],
        base: ["var(--ch-text-base)", { lineHeight: "1.5" }],
        lg: ["var(--ch-text-lg)", { lineHeight: "1.25", letterSpacing: "-0.015em" }],
        xl: ["var(--ch-text-xl)", { lineHeight: "1.25", letterSpacing: "-0.02em" }],
        "2xl": ["var(--ch-text-2xl)", { lineHeight: "1.2", letterSpacing: "-0.025em" }],
      },
      colors: {
        canvas: "var(--ch-bg-canvas)",
        surface: "var(--ch-bg-surface)",
        subtle: "var(--ch-bg-subtle)",
        muted: "var(--ch-bg-muted)",
        inverse: "var(--ch-bg-inverse)",
        border: {
          subtle: "var(--ch-border-subtle)",
          DEFAULT: "var(--ch-border-default)",
          control: "var(--ch-border-control)",
          strong: "var(--ch-border-strong)",
        },
        ink: {
          DEFAULT: "var(--ch-text-primary)",
          secondary: "var(--ch-text-secondary)",
          muted: "var(--ch-text-muted)",
          inverse: "var(--ch-text-on-primary)",
        },
        action: {
          DEFAULT: "var(--ch-action-primary)",
          hover: "var(--ch-action-primary-hover)",
          active: "var(--ch-action-primary-active)",
        },
        brand: {
          DEFAULT: "var(--ch-accent-brand)",
          hover: "var(--ch-accent-brand-hover)",
          subtle: "var(--ch-accent-brand-subtle)",
        },
        status: {
          "success-bg": "var(--ch-status-success-bg)",
          "success-border": "var(--ch-status-success-border)",
          "success-text": "var(--ch-status-success-text)",
          "success-solid": "var(--ch-status-success-solid)",
          "warning-bg": "var(--ch-status-warning-bg)",
          "warning-border": "var(--ch-status-warning-border)",
          "warning-text": "var(--ch-status-warning-text)",
          "warning-solid": "var(--ch-status-warning-solid)",
          "danger-bg": "var(--ch-status-danger-bg)",
          "danger-border": "var(--ch-status-danger-border)",
          "danger-text": "var(--ch-status-danger-text)",
          "danger-solid": "var(--ch-status-danger-solid)",
          "danger-hover": "var(--ch-status-danger-hover)",
        },
        badge: {
          "gdpr-bg": "var(--ch-badge-gdpr-bg)",
          "gdpr-border": "var(--ch-badge-gdpr-border)",
          "gdpr-text": "var(--ch-badge-gdpr-text)",
          "ccpa-bg": "var(--ch-badge-ccpa-bg)",
          "ccpa-border": "var(--ch-badge-ccpa-border)",
          "ccpa-text": "var(--ch-badge-ccpa-text)",
          "default-bg": "var(--ch-badge-default-bg)",
          "default-border": "var(--ch-badge-default-border)",
          "default-text": "var(--ch-badge-default-text)",
          "essential-bg": "var(--ch-badge-essential-bg)",
          "essential-border": "var(--ch-badge-essential-border)",
          "essential-text": "var(--ch-badge-essential-text)",
        },
      },
      borderRadius: {
        control: "var(--ch-radius-control)",
        card: "var(--ch-radius-card)",
        modal: "var(--ch-radius-modal)",
        pill: "var(--ch-radius-pill)",
      },
      boxShadow: {
        sm: "var(--ch-shadow-sm)",
        md: "var(--ch-shadow-md)",
        lg: "var(--ch-shadow-lg)",
        banner: "var(--ch-shadow-banner)",
      },
      minHeight: {
        tap: "var(--ch-tap-target)",
      },
      minWidth: {
        tap: "var(--ch-tap-target)",
      },
      maxWidth: {
        portal: "var(--ch-portal-max-w)",
        admin: "var(--ch-admin-max-w)",
      },
      spacing: {
        sidebar: "var(--ch-sidebar-width)",
        topbar: "var(--ch-topbar-height)",
      },
    },
  },
  plugins: [],
} satisfies Config;
```
