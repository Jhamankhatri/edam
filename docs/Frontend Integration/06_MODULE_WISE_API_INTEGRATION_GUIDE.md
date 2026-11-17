# 06 — Module-wise API Integration Guide

This document describes the frontend integration sequence per module. Detailed request/response contracts are in §02; permission codes in §05; pagination conventions in §14.

## 6.1 Authentication & bootstrap (`/auth/*`)
1. On app load: `GET /actuator/health` → smoke (status=UP means backend reachable); then `GET /api/v1/auth/installation` → choose login vs bootstrap. Once authenticated, `GET /api/v1/system/info` provides application name/version for the shell.
2. If not bootstrapped: render Bootstrap Wizard → `POST /api/v1/auth/csrf` then `POST /api/v1/auth/bootstrap` with `X-EDAM-Bootstrap-Token` header. Success authenticates as the new admin.
3. If bootstrapped: `GET /api/v1/auth/csrf` (to ensure XSRF cookie), then `GET /api/v1/public/configuration` for the branding safe-subset, then show the login form.
4. Login: `POST /api/v1/auth/login`; re-read XSRF cookie; `GET /api/v1/auth/me` to load principal, permissions, data scopes.
5. Change password: `POST /api/v1/auth/change-password`. If `mustChangePassword` is true, force it before entering the app shell.
6. Forgot/reset password: `POST /api/v1/auth/forgot-password` (always neutral message) → later `POST /api/v1/auth/reset-password` with token from email.
7. Logout: `POST /api/v1/auth/logout`, then redirect to `/login` and re-fetch CSRF.

## 6.2 Users, roles, data scopes
- List users: `GET /users?page=&size=&search=&status=` → `PageResponse<UserSummary>`.
- Create user: `POST /users` → 201; on success assign roles (`POST /users/{id}/roles`) and scopes (`POST /users/{id}/scopes`).
- Edit/activation/unlock/password reset on user detail page.
- Role catalogue for role picker: `GET /roles/permissions` returns all 60 permission codes (use this to render permission checkboxes, not constants).
- Roles CRUD: create/update/activate roles; set permissions via `PUT /roles/{id}/permissions` with a permission-code array.
- Effective permissions view: `GET /users/{id}/effective-permissions` (admin debug view).

## 6.3 Organization
- Profile: `GET/PATCH /organization/profile`.
- Units: `GET /organization/unit-types`, `GET /organization/units`, `GET /organization/units/tree`; CRUD via POST/PUT/activation. Units are the generic tree; departments and branches are separate lists.
- Departments: `GET/POST/PUT /organization/departments` + activation.
- Branches: `GET/POST/PUT /organization/branches` + activation.
- When creating a user, department/branch pickers source their options from these endpoints.

## 6.4 Master data
- Currencies: list (active first, ISO-4217 codes), create/update/activate, exchange rates (open/close). The base currency is on the organization profile.
- Vendors: list with department/branch filters, detail, CRUD, activation.
- Tax authorities: CRUD + activation.
- All master-data list endpoints support pagination/search.

## 6.5 Expense configuration
- Categories → subcategories → natures → request types. Use hierarchical pickers (cascading dropdowns) on the request form. Subcategory lists are filtered by category id (query param).
- Activation is soft; deleted concepts must not be hard-deleted because historical requests reference them.

## 6.6 Approval limits / delegation / committees / recommending authorities / exceeding-limit rules
All follow the same CRUD-with-activation pattern:
- List (paginated) with filters;
- GET by id;
- POST create;
- PUT update (only mutable fields);
- POST `/…/{id}/activation` toggles active flag;
- Additional endpoints:
  - Approval limits: `POST /approval-limits/authority-check` — run an authority check from the admin screen (useful for QA).
  - Delegation: `POST /delegation-matrix/preview` — previews routing for a hypothetical request without creating one.
  - Recommending authorities: `GET /recommending-authorities/resolved` — resolved view per (department, category).
  - Exceeding-limit: `GET /exceeding-limit-rules/evaluation` — evaluate a request against the rule set.
  - Committees: member add/end, level add/deactivate, `GET /committees/coverage` shows what (department, category) combinations are covered.

## 6.7 Business rules
- List rule sets → get detail → versions; create a set; create a new draft; upsert rules into a draft; publish (validates and activates) or retire.
- The rule editor must only allow the closed operator/field/action set the backend advertises through rule detail responses; never allow arbitrary expressions.

## 6.8 Workflow configuration
- Definitions list (`GET /workflows`), create, update.
- Versions: `GET /workflows/{id}/versions`; `GET /workflows/versions/{versionId}` (full detail); `clone from published`, `clone from version`, `save draft`, `validate`, `compare`, `publish`, `retire`.
- **Immutable after publish**: edit only by cloning. In-flight requests pin the version they were submitted against.

## 6.9 Expense requests (OPEX & CAPEX share the same endpoint)
1. Create draft: `POST /expense-requests` with the header block + requestTypeId (OPEX vs CAPEX).
2. Edit draft: `PUT /expense-requests/{id}` — sends the full request payload (line items, CAPEX/ OPEX details, allocations, etc.).
3. Upload supporting documents via `POST /documents` (see §10).
4. Submit: `POST /expense-requests/{id}/submit`; include `Idempotency-Key` to avoid double-submit.
5. List my/all requests: `GET /expense-requests?page=&size=&mineOnly=&status=&type=&q=` (pagination §14).
6. Request detail: `GET /expense-requests/{id}` (returns items, allocations, detail block, status, current step, assignees).
7. Cancel: `POST /expense-requests/{id}/cancel`.
8. Timeline: `GET /expense-requests/{id}/activity` (for the activity tab).

## 6.10 Approval inbox & actions
- Inbox badge: `GET /approvals/count`.
- Inbox list: `GET /approvals` (paginated, filter by step/dept/age).
- Case file: `GET /approvals/tasks/{taskId}`; request detail: `GET /approvals/tasks/{taskId}/request` (same shape as request GET but loaded in the approver context).
- Actions: `POST /approvals/tasks/{taskId}/approve|reject|revert|forward`. Reject/revert/forward require remarks (forward requires recipient user id).
- Always send `Idempotency-Key` on actions to prevent double-clicks from double-acting.

## 6.11 Documents
- Upload: `POST /documents` (multipart) → returns document metadata and initial `scanStatus`.
- List for request: `GET /documents/requests/{requestId}`.
- Metadata: `GET /documents/{id}`.
- Download URL: `GET /documents/{id}/download-url` → short-lived signed URL (open in a new tab; do not proxy).
- Scan events: `GET /documents/{id}/scans`.
- Delete: `DELETE /documents/{id}` (subject to state policy).
- UI must block download until `scanStatus=CLEAN`.

## 6.12 SMTP / templates / notifications
- SMTP: `GET/POST/DELETE/test` on `/communication/smtp`. Password field is write-only; never echo it.
- Email templates: list, get, create, new version, history, set-active, preview (returns rendered sample).
- Notification event triggers: list + update (toggle in-app/email per event).
- Notification inbox: `GET /notifications`, unread count, mark read, mark all read.

## 6.13 Branding / themes / settings / feature flags
- Public config: `GET /public/configuration` (pre-login).
- Themes: list/active/get/create/update/activate/default. Active theme drives CSS custom properties.
- Settings: list, by category, by key, save, bulk patch.
- Feature flags: list/get/save/toggle. Use feature flags to gate unfinished UI (e.g. `capex-finance-fields`).

## 6.14 Reports & dashboard
- Dashboard cards: `GET /reporting/dashboard`.
- Register: `GET /reporting/expense-requests/register` (paginated, same filter set as my-requests list plus visibility scopes).
- Export: `POST /reporting/expense-requests/export` → CSV stream (blob download).
- Widget/report catalogues: admin-only dashboard management.

## 6.15 Audit
- Search: `GET /audit-events?actor=&entityType=&entityId=&correlationId=&from=&to=&page=&size=`.
- Detail: `GET /audit-events/{id}`.
- Request timeline is on the request screen (not here).

## 6.16 Integrations
- Providers catalogue: `GET /integrations/providers` (which adapters are installed).
- CRUD/activate/deactivate/archive integrations.
- Test: `POST /integrations/{id}/test`.
- Execute: `POST /integrations/{id}/executions` with `Idempotency-Key` header.
- History: `GET /integrations/{id}/executions`; detail: `GET /integrations/executions/{execId}`.
- Retry: `POST /integrations/executions/{execId}/retry`.
- Secret fields are write-only; on GET they return blank/masked placeholders.
