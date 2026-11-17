# Frontend Architecture Blueprint (React + TypeScript, strict mode)

This is a recommended project structure and set of patterns for the React frontend. It is deliberately conservative: EDAM is a financial workflow app, so predictability, type safety and auditability outweigh novelty.

## Stack

- **React 18** with strict mode; **TypeScript 5** with `strict: true`, `noUncheckedIndexedAccess: true`, `exactOptionalPropertyTypes: false`, `noImplicitOverride: true`.
- **Vite** for dev/build; configure `server.proxy` to forward `/api` → `http://localhost:8080` so CSRF cookies flow.
- **TanStack Query (React Query)** for server state — caching, retries, invalidation, background refresh. Do NOT put server data in Redux/Zustand.
- **React Router v6+** (data router with loaders/actions is fine but keep loaders thin; business data should flow through React Query).
- **React Hook Form + Zod** for forms and validation. Mirror Bean Validation rules on the client for responsiveness but never trust them — the backend is authoritative.
- **Tailwind CSS** for styling, themed via CSS custom properties loaded from `GET /api/v1/configuration/public/configuration` and (when authed) `GET /api/v1/themes/active`.
- **Headless UI** or **Radix UI** primitives for dialogs, menus, popovers, comboboxes, tabs (accessibility-first).
- **zustand** (optional, small) for purely client-side ephemeral state: sidebar collapse, toasts, theme override for preview.
- **date-fns** for dates; **@tanstack/react-table** for tables with server-side pagination/sort/filter.
- **i18next** if multi-language is required; for v1 English-only is acceptable but structure strings for translation.
- **Playwright** for end-to-end tests against a running backend (parallel to Postman).

## Project layout

```
src/
  main.tsx                       # StrictMode, QueryClientProvider, RouterProvider, ThemeProvider, ToastProvider
  app/
    routes.tsx                   # Route tree with permission guards
    auth/
      AuthProvider.tsx           # Session boot (GET /auth/me), login(), logout(), refreshCsrf()
      csrf.ts                    # read XSRF-TOKEN from document.cookie
      RequireAuth.tsx            # <RequireAuth permissions={["request:approve"]}>...</RequireAuth>
    theme/
      ThemeProvider.tsx          # applies CSS vars from branding endpoint; supports preview tokens
    layout/
      AppShell.tsx               # sidebar, topbar (search, notifications, user menu)
  api/
    http.ts                      # fetch wrapper: sets Content-Type, X-XSRF-TOKEN, ApiResponse<T> parsing, correlation id, error normalization
    client.ts                    # TanStack Query client defaults (retries, staleTime)
    endpoints/                   # One file per controller (auth.ts, users.ts, requests.ts, …).
      auth.ts
      users.ts
      organization.ts
      masterData.ts
      expenseConfiguration.ts
      requests.ts
      approvals.ts
      workflow.ts
      documents.ts
      smtp.ts
      emailTemplates.ts
      notifications.ts
      configuration.ts
      reporting.ts
      audit.ts
      integrations.ts
  dto/                           # Generated/adapted types mirroring ApiResponse<T> + DTOs
    api.ts                       # ApiResponse<T>, PageResponse<T>, ErrorEnvelope
    auth.ts
    users.ts
    requests.ts
    ...
  features/                      # Feature-sliced modules; each folder contains pages, hooks, components
    auth/                        # Login, forgot-password, reset-password, bootstrap, change-password
    dashboard/
    users/
    roles/
    organization/
    master-data/
    expense-configuration/
    approval-limits/
    delegation/
    committees/
    workflow/
    requests/
      components/
      hooks/
      schemas/                   # Zod schemas per form
      pages/
        RequestListPage.tsx
        RequestDetailPage.tsx
        NewRequestPage.tsx
    approvals/
    documents/
    communication/
    branding/
    audit/
    integrations/
    notifications/
  components/                    # Shared UI primitives (Button, TextField, Select, DataTable, Dialog, Toast, Form, FileUploader, ConfirmDialog, PermissionGate)
  hooks/                         # usePermissions, useDebounce, usePaginationQueryParams, useBreakpoint
  utils/
    dates.ts, currency.ts, errors.ts, correlation.ts
  styles/
    index.css                    # Tailwind layers + CSS variables for theme tokens
test/
  unit/
  e2e/                            # Playwright
```

## Auth & CSRF

- On app boot: call `GET /api/v1/auth/me` via React Query with `retry: false`. If 401 → render public route.
- Before any mutation, ensure `XSRF-TOKEN` cookie exists; if not, call `GET /api/v1/auth/csrf` once. Refresh the cookie value from `document.cookie` immediately before every mutating request (login/logout rotate it).
- `http.ts` wrapper:
  ```ts
  async function api<T>(path: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    if (init.method && init.method !== "GET") {
      headers.set("X-XSRF-TOKEN", getCsrfTokenFromCookie());
      if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
      }
    }
    const res = await fetch(`/api/v1${path}`, { ...init, headers, credentials: "include" });
    const data = await res.json().catch(() => null) as ApiResponse<T> | null;
    if (!res.ok || !data?.success) throw normalizeError(res, data);
    // After login/logout, re-read XSRF cookie (Spring rotates it)
    return data.data as T;
  }
  ```
- Never read or set `JSESSIONID`; the browser handles it.
- On 401, redirect to `/login?reason=expired` and clear the cached `me` query.
- Do NOT put JWT/bearer-token logic anywhere — it does not exist in this system.

## Permission guards

- `<PermissionGate anyOf={["user:manage"]}>...</PermissionGate>` hides content the user cannot invoke. It reads permissions from `useAuth()`.
- Routes use a `requireAuth` loader/wrapper that checks `anyOf`/`allOf` and redirects to a 403 page if missing; this is UX only — the backend still enforces.
- Never grant access purely because a route was typed in.

## Forms strategy

- Use React Hook Form with Zod resolvers. Each submit maps the form model to a DTO, calls the API service, and on success invalidates the relevant React Query keys.
- Display field-level errors from `fieldErrors` using `setError(field, { message })`.
- Optimistic updates are only appropriate for ephemeral UI state (e.g. marking a notification read locally); never optimistically update financial data.
- Disable submit while in flight and preserve the idempotency key per submission (see below).

## Tables strategy

- Wrap `@tanstack/react-table` into a `<DataTable>` component accepting: columns, `useQuery` key, query function, default sort/page size, filter bar slot, row actions slot, empty state.
- Pagination, sorting and filters live in the URL (React Router search params) so links/bookmarks restore views.
- Export actions (CSV) call POST export endpoints and trigger download via blob URL.

## File upload / download

- `<FileUploader>` uses `multipart/form-data` and renders per-file progress, virus-scan status badges (`PENDING_SCAN`, `CLEAN`, `INFECTED`). Block download button unless `CLEAN`.
- For downloads, call the signed-URL endpoint and open in a new tab (don't proxy through the frontend — the URL is short-lived and signed).

## Error handling & toasts

- Centralised error normalization maps HTTP status + `errorCode` to user-friendly messages; toast via a global `<ToastProvider>`.
- Always log `correlationId` to a dev console panel; in production report unhandled exceptions to an operator console (not user-visible).
- Never render raw stack traces, SQL, or exception messages.

## Idempotency

- For create-style mutations (create request, approve, execute integration), generate a client-side UUID v4 per submit attempt and send it as the `Idempotency-Key` header. If the user retries (network blip), reuse the same key. The backend returns the original response and prevents duplicates.
- Reuse the same key when React Query retries, so a timeout doesn't create two rows.

## Branding & theming

- Load public branding (`/configuration/public/configuration`) on the public shell; load the full theme after auth (`/themes/active`).
- Map token values to CSS custom properties (e.g. `--color-primary-500`, `--radius-md`, `--font-sans`) under `:root`. All components read tokens, never hard-coded hex.
- Preview mode (in the branding admin) applies a temporary CSS-variable override without saving, so an admin can see the result before activating.
- Never `eval()` or inject admin-provided CSS/JS; theme tokens are an enumerated whitelist (color scales, radii, font stack, logo asset id) enforced server-side.

## Accessibility & responsiveness

- All interactive controls must be keyboard operable; use the headless primitives; test with axe-core in e2e.
- Layout: desktop (≥1280) three-column shell; tablet (768–1279) collapsible sidebar; mobile (<768) bottom tab bar for top-level routes (My requests, Approvals, Notifications, Profile).
- Tables horizontally scroll at tablet widths; action menus collapse into a meatball menu on small screens.
- Touch targets ≥40px; colour contrast AA minimum enforced via the design system tokens.

## Testing

- Unit: Vitest + React Testing Library for hooks, util functions, small components.
- Integration: MSW to mock the API at the fetch level for component tests; fixtures generated from OpenAPI/JSON shapes.
- End-to-end: Playwright against a real backend in CI; reuses the Postman scenarios (login → bootstrap → seeded data → flows).
- Do not bypass auth in tests; use the bootstrap + seeded users pattern.

## Security checklist for the frontend

1. Never log request/response bodies to console in production builds.
2. Never concatenate user input into HTML; always use React escaping.
3. When opening signed download URLs, add `rel="noopener noreferrer"`.
4. Never send cookies to third-party origins; only `/api` is called cross-origin through the dev proxy in production the frontend is served from the same site.
5. All uploads must declare an expected MIME type and enforce client-side size limits before sending (server enforces the real limits).
6. Never store secrets in localStorage/sessionStorage. The only thing you may persist is a lightweight UI preference (collapsed sidebar, theme preview choice).
7. No eval, no `new Function`, no injecting admin CSS/JS into the DOM.
