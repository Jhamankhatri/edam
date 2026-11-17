# Frontend Master Integration Guide

This guide is the contract between the React + TypeScript frontend (to be built) and the EDAM backend as shipped in Phase 4J. It lists every screen the application needs, the route, permissions and exact API calls each screen must make.

**Rules (non-negotiable):**
- Authorization is **authoritative on the backend**. Frontend route guards and UI element hiding are user experience aids only; the backend always re-checks permissions.
- Authentication is **session-cookie + CSRF double-submit** (see §Authentication). There is no JWT and no `Authorization: Bearer` header.
- Every request is to `/api/v1/*`; the dev server should proxy `/api` to `http://localhost:8080`.
- Every response follows the `ApiResponse<T>` envelope: `{ success: boolean, correlationId: string, path: string, data: T, message: string, errorCode?: string }`.
- Mutating requests must send `X-XSRF-TOKEN` read from the `XSRF-TOKEN` cookie.
- Never invent fields, permissions or endpoints. If a screen needs something that isn't listed, the backend must be extended in a later phase — do not mock it on the client and call it done.

## Authentication flow

```
┌─────────┐  GET /api/v1/auth/csrf    ┌─────────┐
│ Browser │ ────────────────────────► │  EDAM   │   Set-Cookie: JSESSIONID=...; HttpOnly; SameSite=Lax
│ (React) │ ◄──────────────────────── │ Backend │   Set-Cookie: XSRF-TOKEN=<token>; Path=/
└─────────┘                            └─────────┘
     │                                      ▲
     │ POST /api/v1/auth/login              │ read cookie 'XSRF-TOKEN' → header 'X-XSRF-TOKEN'
     │ Body: {username,password}            │ (also on POST /login because Spring rotates CSRF on auth)
     └──────────────────────────────────────┘
     ◄──── 200 + Set-Cookie: JSESSIONID (new); XSRF-TOKEN (rotated)
     │
     │ GET /api/v1/auth/me  →  EdamPrincipal (id, username, displayName,
     │                          permissions: string[], institutionRoles, dataScopes)
     │
     │ For every POST/PUT/PATCH/DELETE:
     │   headers: { 'X-XSRF-TOKEN': readFromCookie('XSRF-TOKEN'),
     │              'Content-Type': 'application/json' }
     │
     └── POST /api/v1/auth/logout (no body; XSRF header required)
```

CSRF rotation: after login, logout and privilege changes, read `XSRF-TOKEN` again from the cookie jar.

### Key unauthenticated endpoints
| Method | Path | Purpose |
|---|---|---|
| GET | `/actuator/health` | Deployment smoke `{status:"UP"}` (public; no DB/host detail in prod). Authenticated `GET /api/v1/system/info` returns app name/version/status after login. |
| GET | `/api/v1/auth/csrf` | Obtain CSRF token + establish session |
| GET | `/api/v1/auth/installation` | Returns `{installed:boolean}`; if `false`, show the bootstrap wizard |
| POST | `/api/v1/auth/bootstrap` | First-administrator bootstrap (requires `installationToken` = `EDAM_BOOTSTRAP_TOKEN`) |
| GET | `/api/v1/configuration/public/configuration` | Public subset of branding (name, primary color, logo) for the login page |
| POST | `/api/v1/auth/login` | Session login |
| POST | `/api/v1/auth/forgot-password` | Request a reset token (SMTP must be configured in prod) |
| POST | `/api/v1/auth/reset-password` | Reset password with `{token, newPassword}` |

### Authenticated-only (no business permission required)
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/auth/me` | Current principal |
| POST | `/api/v1/auth/change-password` | Self-service password change `{currentPassword, newPassword}` |
| POST | `/api/v1/auth/logout` | Invalidate session |

## EdamPrincipal shape (GET /auth/me)

```ts
interface EdamPrincipal {
  id: string;                // UUID
  username: string;
  displayName: string;
  email?: string;
  employeeCode?: string;
  permissions: string[];     // permission codes (e.g. "user:view"), NOT constants
  institutionRoles: Array<{ institutionId: string; roleIds: string[] }>;
  dataScopes: Array<{ scope: "INSTALLATION"|"BRANCH"|"DEPARTMENT"; ids: string[] }>;
  lastLoginAt?: string;      // ISO-8601
}
```

The `permissions` array is what the UI guards must use. Codes match the string values of `Permissions.*` constants in Java (e.g. `request:create`, `user:manage`).

## Screen inventory

The screens below are the complete set implied by the existing backend. Route paths are recommendations; the path column in each table refers to the API path (not the React route). React routes are shown under "Route".

### Public screens (unauthenticated)

| Screen | Route | API calls | Notes |
|---|---|---|---|
| Splash / deployment health | `/` | `GET /actuator/health` then (post-login) `GET /api/v1/system/info` | If `/actuator/health` is not reachable, show "EDAM is starting…"; surface correlation id on error. |
| Login | `/login` | `GET /auth/csrf` → `GET /configuration/public/configuration` → `POST /auth/login` → `GET /auth/me` | Apply public theme tokens. Show lock-out message after too many failures (HTTP 429 / errorCode `LOGIN_ATTEMPTS_EXCEEDED`). |
| Bootstrap wizard (first-run only) | `/bootstrap` | `GET /auth/installation` → `POST /auth/bootstrap` | Only reachable when `installed === false`. Ask for installation token, admin username, display name, password. On success, auto-login (same cookies) and go to app shell. |
| Forgot password | `/forgot-password` | `POST /auth/forgot-password` | Generic success message even when username unknown (user enumeration defense). |
| Reset password | `/reset-password?token=…` | `POST /auth/reset-password` | Token is a single-use, expiring token delivered out-of-band. |

### Application shell (authenticated)

| Screen | Route | Permissions (any/one of) | API calls |
|---|---|---|---|
| App chrome / sidebar | `/app/*` | any authenticated user | `GET /auth/me` → build nav from `permissions`; `GET /notifications/unread-count` for badge; apply active theme via `GET /themes/active` (or cached) |
| My profile / change password | `/app/profile` | authenticated | `GET /auth/me`; `POST /auth/change-password` |
| Notification centre | `/app/notifications` | `notifications:view` (`NOTIFICATION_VIEW`) | `GET /notifications` (paginated), `POST /notifications/{id}/read`, `POST /notifications/read-all`; click navigates to the related entity (e.g. request detail → `/app/requests/:id`). |
| Global search | `/app/search` | intersection of what the caller can see | Currently the backend exposes `GET /users/search` for user pickers; global-entity search is deferred — scope the box to users/vendors/vouchers only for v1. |

### Admin module (only shown to `system:administer` / config roles)

| Screen | Route | Permission | API calls (key) |
|---|---|---|---|
| Dashboard | `/app/admin/dashboard` | `report:view` (`REPORT_VIEW`) | `GET /reporting/dashboard` → KPI cards; `GET /reporting/expense-requests/register` for recent register |
| Users list | `/app/admin/users` | `user:view` | `GET /users` (paginated, search, status filter); `GET /users/search` for typeahead |
| User create | `/app/admin/users/new` | `user:manage` | `POST /users` (username, displayName, email, employeeCode, initialPassword?) |
| User edit | `/app/admin/users/:id` | `user:manage` | `GET /users/{id}`; `PUT /users/{id}`; `POST /users/{id}/activation` (toggle active/unlock); `POST /users/{id}/password-reset` |
| User roles & data scopes | `/app/admin/users/:id/access` | `user-role:assign`, `data-scope:manage` | `GET /users/{id}/roles`; `POST /users/{id}/roles`; `DELETE /users/{id}/roles/{assignmentId}`; `POST /users/{id}/scopes`; `DELETE /users/{id}/scopes/{scopeId}`; `GET /users/{id}/effective-permissions` |
| Roles list / create / edit | `/app/admin/roles` | `role:view` / `role:manage` | `GET /roles`; `POST /roles`; `PUT /roles/{id}` (name, description, permission-code set). Permission checklist is populated from the 60 known permission codes (see `Permissions.java` or `_generated/permissions.json`). |
| Institution profile | `/app/admin/organization/profile` | `organization:view` / `configuration:manage` | `GET /organization/profile`; `POST/PATCH /organization/profile` |
| Units / departments / branches | `/app/admin/organization/units`, `…/departments`, `…/branches` | `organization:view` / `organization:manage` | `GET /organization/unit-types`; `GET /organization/units` (supports `/tree`); `POST /organization/units`; `PUT`; activation; same pattern for departments and branches |
| Currencies | `/app/admin/master-data/currencies` | `currency:view`/`manage` | `GET/POST/PUT` currencies; `GET/POST/PUT` exchange rates |
| Vendors | `/app/admin/master-data/vendors` | `vendor:view`/`manage` | CRUD vendors, contacts, linked departments/branches |
| Tax authorities | `/app/admin/master-data/tax-authorities` | `tax-authority:manage` | CRUD |
| Expense configuration (natures, categories, subcategories, request types) | `/app/admin/expense-configuration` | `expense-configuration:view`/`manage` | CRUD for each sub-resource under `/expense-configuration/*` (exact paths in `_generated/controllers.json`) |
| Approval limits | `/app/admin/approval-limits` | `approval-limit:view`/`manage` | CRUD `/approval-limits` by (department, category, currency, effective window) |
| Recommending authorities | `/app/admin/recommending-authorities` | `recommending-authority:view`/`manage` | CRUD `/recommending-authorities` |
| Exceeding-limit rules | `/app/admin/exceeding-limits` | `exceeding-limit:view`/`manage` | CRUD `/exceeding-limit-rules`; show a banner that routing enforcement is `warn-only` per OD-05 until business sign-off. |
| Delegation matrix | `/app/admin/delegation` | `delegation:view`/`manage` | CRUD `/delegation` with step-by-step approver configuration; surface OD-01/OD-03 as a banner (CEO/CFO routing requires business sign-off). |
| Committees | `/app/admin/committees` | `committee:view`/`manage` | CRUD `/committees`; levels; members. |
| Business rules | `/app/admin/business-rules` | `business-rule:view`/`manage` | CRUD `/business-rules/sets`, versions, rules; validate; publish. Rule editor must use the closed AST (operator whitelist) — do not let users type arbitrary expressions. |
| Workflow definitions | `/app/admin/workflows` | `workflow-configuration:view`/`manage` | `GET /workflows`; `POST /workflows`; `PUT /workflows/{definitionId}`; `GET /workflows/{definitionId}/versions`; clone-from-published / clone-from-version; `GET /workflows/versions/compare`; `GET /workflows/versions/{versionId}/validate`; `POST /workflows/versions/{versionId}/publish`; `POST /workflows/versions/{versionId}/retire`. The editor must model states, transitions, steps and step-approvers with quorum settings. |
| Documents administration | `/app/admin/documents` | `document:view`/`manage` (scoped by data scopes) | Read-only register for QA; document deletion via `DELETE /documents/{id}` (with confirmation). |
| SMTP configuration | `/app/admin/communication/smtp` | `communication:view`/`manage` | `GET/POST/PUT` SMTP config (host, port, auth, from address); test-mail button; password field is write-only — never echo the plaintext; UI must label "Password is stored encrypted at rest". |
| Email templates | `/app/admin/communication/templates` | `email-template:view`/`manage` | CRUD `/email-templates`; preview endpoint renders with sample variables. |
| Notification event triggers | `/app/admin/communication/triggers` | `notification-config:view`/`manage` | Toggle each notification event (in-app + email) from `/notification-event-configs`. |
| Branding / themes | `/app/admin/branding` | `configuration:view`/`manage` + `branding:manage` | Logo upload via `POST /documents` (or dedicated asset endpoint); theme-token editor (colors, radius, font family) restricted to known tokens; set default / activate theme. |
| Application settings / feature flags | `/app/admin/settings` | `configuration:view`/`manage` | `GET /settings`, `GET /settings/categories/{category}`, `POST /settings`, `PATCH /settings/bulk`; feature flags activation. |
| Audit log | `/app/admin/audit` | `audit:view` | `GET /audit-events` with filters (actor, entityType, entityId, correlationId, date range); detail view. |
| Integrations | `/app/admin/integrations` | `integration:view`/`manage`/`execute` | CRUD `/integrations`; secret field write-only; SSRF-safe URL validation happens server-side (surface URL errors inline); execution history per config; idempotency key can be sent as `Idempotency-Key` header for manual retries. |

### Requester module

| Screen | Route | Permission | API calls |
|---|---|---|---|
| My requests | `/app/requests/mine` | `request:view-own` | `GET /expense-requests?mineOnly=true` (paginated, filter by status/type/date) |
| All requests (if user has broader scope) | `/app/requests` | `request:view` | `GET /expense-requests` (paginated; respects caller's data scopes) |
| New OPEX request | `/app/requests/new?type=OPEX` | `request:create` | Lookups: categories, subcategories, natures, vendors, currencies, departments/branches (via master data & org endpoints); `POST /expense-requests` (draft); add items/allocations via `PUT /expense-requests/{id}`; upload docs via `POST /documents` linked to requestId; `POST /expense-requests/{id}/submit`. |
| New CAPEX request | `/app/requests/new?type=CAPEX` | `request:create` | Same endpoints; CAPEX detail block (STA fields, budget summary) is sent as part of the request payload. OD-08 variance formulas and OD-13 finance-field owner are flagged in the UI as pending finance sign-off. |
| Request detail | `/app/requests/:id` | `request:view-own` (or broader) | `GET /expense-requests/{id}`; `GET /documents/requests/{requestId}`; `GET /expense-requests/{id}/activity` (timeline); actions based on state: edit / cancel / submit. |

### Approver module

| Screen | Route | Permission | API calls |
|---|---|---|---|
| Approval inbox | `/app/approvals` | `request:approve` (plus scope) | `GET /approvals/count` for the badge; `GET /approvals` (paginated; filter by step/age/amount/department); list tasks with request summary. |
| Task / case file | `/app/approvals/tasks/:taskId` | `request:approve` | `GET /approvals/tasks/{taskId}`; `GET /approvals/tasks/{taskId}/request` (full request with documents); `GET /documents/{id}/download-url` for attachments; actions via POST approve/reject/revert/forward. Reject/forward require remarks/recipient; forward recipient must be a valid user (user-search typeahead). |

### Reporting

| Screen | Route | Permission | API calls |
|---|---|---|---|
| Dashboard | `/app/dashboard` | `report:view` | `GET /reporting/dashboard` (cards), `/reporting/widgets` if `dashboard:manage` |
| Request register export | `/app/reporting/register` | `report:view`, `report:export` | `GET /reporting/expense-requests/register` (paginated); `POST /reporting/expense-requests/export` returns CSV (stream). |

## Common UI patterns

### Tables & pagination
All list endpoints return `PageResponse<T>`:
```ts
interface PageResponse<T> {
  content: T[];
  page: number;          // 0-based
  size: number;
  totalElements: number;
  totalPages: number;
  sort?: { property: string; direction: "ASC"|"DESC" }[];
}
```
Use `?page=&size=&sort=property,dir&search=&status=…` as supported by each endpoint.

### Dropdowns / typeaheads
- Users: `GET /users/search?q=…` (permission `user:view`).
- Departments/Branches/Units: the respective `GET` list endpoints (tree where supported).
- Currencies, vendors, categories, natures, subcategories, request types: master data / expense-configuration list endpoints.
- Request types for OPEX/CAPEX: two known entries, but fetch from the API (never hard-code) so CAPEX feature-flag gating works.

### File upload
`POST /api/v1/documents` is a `multipart/form-data` request with parts: `file`, `requestId`, `documentType` (e.g. INVOICE, QUOTATION, SUPPORTING). Response returns document metadata including `id` and `scanStatus`. The UI must poll/refresh `GET /documents/{id}/scans` and only expose the download button when status = `CLEAN`. If a real scanner isn't configured, documents stay `PENDING_SCAN` and download is blocked — surface that clearly.

### File download
Never embed the storage URL directly. Call `GET /api/v1/documents/{id}/download-url` which returns a short-lived signed URL; open it in a new tab.

### Error handling
- Show `errorCode` in a developer-friendly debug tray (corner icon) alongside `correlationId` so support can trace logs.
- HTTP 401 → session expired → redirect to `/login` with `?reason=expired`.
- HTTP 403 with `AUTHORIZATION_FAILURE` → "You don't have permission" page.
- HTTP 409 with `CONCURRENT_MODIFICATION` → offer to reload.
- HTTP 409 with `DATA_INTEGRITY_VIOLATION` → friendly message (duplicate name, FK in use, etc.) — never show the SQL state.
- HTTP 422 with `IDEMPOTENCY_KEY_REPLAY` → inform the user that a duplicate was detected and the original response will be reused.
- HTTP 4xx/5xx generally → toast with `message`; keep `correlationId` visible.

### Validation errors
Bean Validation errors return a `fieldErrors` map in the error payload; highlight fields inline. Example shape:
```json
{ "success": false, "errorCode": "VALIDATION_ERROR", "fieldErrors": { "username": "must not be blank" }, "correlationId": "…" }
```

## Permissions checklist (60 constants)
The frontend must gate features against these codes (from `com.edam.common.security.Permissions`). Always read the user's effective set from `GET /auth/me` — never infer from role names. Codes include:
`system:administer`, `configuration:view`, `configuration:manage`, `organization:view`, `organization:manage`, `user:view`, `user:manage`, `role:view`, `role:manage`, `user-role:assign`, `data-scope:manage`, `currency:view`, `currency:manage`, `vendor:view`, `vendor:manage`, `tax-authority:manage`, `expense-configuration:view`, `expense-configuration:manage`, `delegation:view`, `delegation:manage`, `approval-limit:view`, `approval-limit:manage`, `recommending-authority:view`, `recommending-authority:manage`, `exceeding-limit:view`, `exceeding-limit:manage`, `committee:view`, `committee:manage`, `request:create`, `request:edit`, `request:submit`, `request:cancel`, `request:view-own`, `request:view`, `request:approve`, `business-rule:view`, `business-rule:manage`, `document:upload`, `document:download`, `document:delete`, `communication:view`, `communication:manage`, `email-template:view`, `email-template:manage`, `notification:view`, `notification-config:view`, `notification-config:manage`, `branding:view`, `branding:manage`, `report:view`, `report:export`, `dashboard:manage`, `audit:view`, `integration:view`, `integration:manage`, `integration:execute`.
