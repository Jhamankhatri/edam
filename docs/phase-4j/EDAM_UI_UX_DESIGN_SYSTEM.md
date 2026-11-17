# EDAM UI / UX Design System

EDAM is an **enterprise expense-approval platform for banks**. The visual language is **premium banking SaaS**: restrained, high-contrast, information-dense, trustworthy. It supports **light** and **dark** modes and a **brand theming layer** that maps directly to the backend theme-token APIs (`/api/v1/configuration/public/configuration`, `/api/v1/themes`, `/api/v1/themes/active`).

This document describes the system in terms of tokens, components, layout, motion and responsive rules. Where motion or a visual feature would require a backend capability that does not exist, it is flagged **[proposed]** so the team can decide whether to deliver it as frontend-only or open a backend ticket.

## 1. Brand pillars

- **Trust** — predictable navigation, consistent controls, obvious primary actions, zero "surprise" animations.
- **Clarity** — data-dense tables, legible typography, strong hierarchy (approval steps, amounts, statuses, dates).
- **Composure** — restrained motion; no bouncy/playful easing; everything has an audit-grade feel.
- **Institutional flexibility** — colours, logo and font family are driven by the institution's theme so Al Baraka, a microfinance bank or a government finance department can each feel like their own product on the same codebase.

## 2. Typography

- **Body / UI font** — `Inter` (default, open-license); institutions can override via the `font.sans` theme token.
- **Numeric font** — `JetBrains Mono` or `Tabular Lining` figures for amounts, IDs, dates (tabular numerals are critical for tables and approval cards).
- **Scale (lightweight 1.2 ratio):**
  - `text-xs` 12/16 — captions, IDs, correlation ids
  - `text-sm` 13/20 — table cells, form hints
  - `text-base` 14/22 — body
  - `text-lg` 16/24 — card titles, section headings
  - `text-xl` 18/28 — page titles
  - `text-2xl` 22/32 — dashboard KPI numbers
  - `text-3xl` 28/36 — login hero
- **Weights:** Regular 400 (body), Medium 500 (labels, buttons), Semibold 600 (headings, amounts). No heavy 800 weights — banking UIs stay calm.

## 3. Colour (tokens)

Tokens are stored as CSS custom properties and mapped from backend `theme_tokens`. All component styles read tokens, never hard-coded hex. Default palette below is the "EDAM Navy" reference (light mode first, dark mode values generated with luminosity-shift logic in the frontend).

```css
:root {
  /* Brand (institution-overridable) */
  --color-brand-50:  #eef4ff;
  --color-brand-100: #d9e6ff;
  --color-brand-500: #1e4fd1;   /* primary actions, links, active step */
  --color-brand-600: #1a43b3;
  --color-brand-700: #14338a;
  --color-accent-500: #0e8f7b;  /* positive amounts / approvals */
  --color-danger-500: #c0392b;  /* rejections, errors */
  --color-warning-500:#d99a1b;  /* pending / exceeded limit */

  /* Neutrals (light) */
  --color-neutral-0:   #ffffff;
  --color-neutral-50:  #f6f8fb;
  --color-neutral-100: #eef1f6;
  --color-neutral-200: #dfe4ec;
  --color-neutral-300: #c3cad6;
  --color-neutral-500: #6b7588;
  --color-neutral-700: #2f3645;
  --color-neutral-900: #101521;

  /* Semantic */
  --color-surface:   var(--color-neutral-0);
  --color-surface-2: var(--color-neutral-50);
  --color-border:    var(--color-neutral-200);
  --color-text:      var(--color-neutral-900);
  --color-text-muted:var(--color-neutral-500);
  --color-success:   var(--color-accent-500);
  --color-danger:    var(--color-danger-500);
  --color-warning:   var(--color-warning-500);
  --color-focus-ring:var(--color-brand-500);

  /* Radii, spacing, shadows */
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --shadow-sm: 0 1px 2px rgba(16,21,33,.06);
  --shadow-md: 0 4px 12px rgba(16,21,33,.08);
  --shadow-lg: 0 12px 32px rgba(16,21,33,.12);
  --font-sans: 'Inter', system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, monospace;
}

[data-theme="dark"] {
  --color-surface:   #0e1320;
  --color-surface-2: #151b2c;
  --color-border:    #222a3e;
  --color-text:      #e7ebf4;
  --color-text-muted:#9aa4b8;
  /* brand/accent/danger remain similar luminosity to preserve brand identity */
}
```

Backend token map (from `theme_tokens`): tokens expose these categories — `color.brand[50..700]`, `color.accent`, `color.danger`, `color.warning`, `radius.[sm/md/lg/xl]`, `font.sans`, `font.mono`, `logo.assetId`, `favicon.assetId`, `application.name`, `application.tagline`. Any other key sent by the backend is ignored (defence in depth against accidental CSS/JS injection — ADR-level rule).

## 4. Components

### Buttons
- **Primary** — solid brand-600, white text; used once per screen (main CTA: Submit, Approve, Save).
- **Secondary** — outlined neutral-300, neutral-900 text; used for Cancel, Back, secondary actions.
- **Tertiary / ghost** — text-only, brand-600; used for table row actions ("View", "Download").
- **Danger** — solid danger-500 for Reject, Delete, Revoke; must always be inside a `ConfirmDialog`.
- **Sizes:** sm (32), md (40), lg (48). Min touch target 40px.
- States: hover darkens one shade; focus ring 2px `--color-focus-ring`; disabled 40% opacity, no pointer events. Loading state replaces label with spinner and disables.

### Inputs
- 40px height, radius-md, 1px neutral-200 border, focus ring brand-500 2px; error state replaces border and adds inline message.
- Labels 13/medium, placeholders 14/neutral-500; helper text 12/neutral-500 below.
- Required fields use a red dot suffix on the label (not a "*" asterisk).

### Tables
- Sticky header, zebra striping off (cleaner for banking), horizontal row dividers only; hover row bg `neutral-50`.
- Numeric columns right-aligned with tabular figures; currency values formatted with the institution's base currency and include ISO code (`PKR 1,250,000.00`).
- Row actions in a meatball menu on mobile, inline icons on desktop.
- Pagination: page size 20 (10/20/50 selector), total count, page navigation; server-side sorting on column headers.

### Cards
- White surface (neutral-0 in light, surface in dark), radius-lg, shadow-sm, padding 20-24px. Dashboard KPIs use cards with a large numeric value, delta indicator and sparkline [proposed — sparkline is frontend-only until a timeseries endpoint ships].

### Status badges
Filled pills 22px high, text-xs/medium, rounded-full:

| Status | Colour |
|---|---|
| DRAFT | neutral |
| SUBMITTED / PENDING | warning |
| APPROVED / COMPLETED / CLEAN | success |
| REJECTED / INFECTED / FAILED | danger |
| CANCELLED / REVERTED | muted |
| IN REVIEW / PENDING_SCAN | brand |

### Workflow stepper
- Horizontal on desktop, vertical on mobile; completed steps filled with success, current step highlighted brand with a 2px ring, future steps neutral outline.
- Approver avatar stack + step status label ("Awaiting HOD approval (2 of 3)").

### Dialogs
- Centered modal with backdrop; max-width 560 for confirmations, 720 for complex forms, 960 for large editors (workflow builder).
- Confirm dialogs for destructive actions include the entity name/amount and a "Type the name to confirm" field for irreversible deletes (e.g. deleting an approval limit in use).

### Navigation
- **Sidebar** (240px collapsible to 64px icon rail): top-level sections grouped (Dashboard, Requests, Approvals, Notifications, Administration). Active item bg brand-50 with a 3px left brand-500 bar.
- **Topbar:** institution logo (from branding), global search, notification bell with unread count badge, user menu (profile, change password, logout).
- **Mobile (<768px):** sidebar hidden; bottom tab bar with 4–5 top-level items (My requests, Approvals, Notifications, Profile/Mine).

### Toasts
- Top-right (top-center on mobile), 4 types: success, error, warning, info. Auto-dismiss 4s for success/info; errors persist until dismissed. Show correlation id in a collapsible "Technical details" panel.

## 5. Layout & responsive breakpoints

| Breakpoint | Width | Behaviour |
|---|---|---|
| Mobile | <768 | Single column; bottom tabs; tables scroll horizontally; form labels above inputs; stepper vertical. |
| Tablet | 768–1279 | Collapsible sidebar (default icons); two-column forms; approval inbox list + detail via drill-down. |
| Desktop | 1280–1599 | Full sidebar; three-column dashboard cards; master-detail for approvals (list + case file side-by-side). |
| Wide | ≥1600 | Centered content at 1440 max width; additional side panels (request timeline alongside detail). |

Page chrome uses a 12-column grid (24px gutters on desktop, 16px on tablet, 12px on mobile).

## 6. Motion [proposed where noted]

Motion is deliberately subtle — banking UIs should feel solid, not springy.

- **Durations:** 120ms (micro-interactions: button press, focus); 200ms (hover, tooltip appear, sidebar collapse); 280ms (page transitions, dialog in/out).
- **Easing:** `cubic-bezier(0.2,0,0,1)` (Material standard ease-out); no bounces or back-easing.
- **Preferences:** Respect `prefers-reduced-motion: reduce` — disable all non-essential animation.
- **Page transition:** simple 8px upward fade; no 3D or parallax.
- **Micro-interactions:** button press scales to 0.985; checkmark for approval fades in on success (120ms).
- **Number counters** in dashboard KPIs tween from previous value on load [proposed: frontend-only, purely decorative — never animate amounts in forms].
- **Realtime notification dot pulse** (opacity only, no transform) when unread count increases [proposed: the backend does not currently push — use 30s polling until WebSockets/SSE ship; the badge is still reliable].

## 7. Iconography

- Use **Lucide** (or **Tabler**) at 1.5px stroke for consistency; avoid filled styles except for status indicators.
- Icons are 20px in buttons, 16px inline, 24px in navigation.
- No cartoon/emoji icons; everything outline and geometric.

## 8. Voice & content

- **Form labels** are title-case sentence fragments ("Budget code", "Invoice date").
- **Buttons** use verbs (Approve, Reject, Submit, Save changes) rather than OK/Cancel where possible.
- **Error messages** state what went wrong and how to fix it ("Invoice date cannot be in the future"), not "Invalid input".
- **Empty states** include a short description and a primary action CTA (e.g. "No requests yet" + "New expense request").
- **Dangerous actions** are double-stated in red text: "Rejecting this request returns it to the requester with your remarks."
- Monetary values always include the ISO currency code (PKR, USD, EUR) and format with 2 decimals; whole numbers only when the backend reports scale=0 for the specific currency.

## 9. Dark mode

- Automatically honours `prefers-color-scheme` on first visit; after that, user preference is stored in a non-identifying cookie/local flag.
- All surfaces are desaturated navy (not pure black) to preserve depth and avoid halation.
- Brand colour stays perceptually aligned with the light-mode brand (do not just shift lightness — maintain saturation so the institution logo and primary action remain recognisable).

## 10. Backend-delivered theme behaviour

| Backend capability | Backend endpoint | UI behaviour |
|---|---|---|
| Public branding (name, primary, logo) | `GET /configuration/public/configuration` | Applied on login page / bootstrap / forgot-password screens before auth. |
| Active theme (full token set) | `GET /themes/active` (auth) | Applied on the app shell; replaces defaults via CSS variables. |
| Theme preview (admin) | `POST /themes/{id}` returns preview tokens | Apply ephemeral CSS variables only within the Branding admin preview frame; do not persist until activation. |
| Theme activation | `POST /themes/{id}/activation` | Refresh CSS variables live; all users see the new theme on next page load (poll every 5 minutes, or [proposed] SSE push). |
| Logo asset | Branding `logo.assetId` | Render via `GET /documents/{id}/download-url` signed URL. |

> **Hard rule:** never inject arbitrary CSS, font URLs or JS from theme data. Tokens are an enumerated whitelist (colors, radii, font family from a Google-Fonts-vetted subset, asset IDs); anything outside the whitelist is dropped by the frontend.
