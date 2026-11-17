# 15 — Frontend Screen-to-API Mapping

Each row maps a React screen action to its backend API. **Status** values:
- ✅ **IMPLEMENTED AND VERIFIED** — endpoint exists, is permission-guarded and tested in Postman.
- 🟡 **PARTIALLY IMPLEMENTED** — endpoint exists but some behaviour depends on configuration or an open decision.
- 🧱 **PRODUCTION CONFIGURATION REQUIRED** — works with external dependency (SMTP, Cloudinary, malware scanner).
- ⚠️ **BLOCKED BY OPEN DECISION** — backend has the data shape but sign-off is needed before the full user story works.
- 🔲 **NOT IMPLEMENTED** — no backend endpoint yet.

## 15.1 Screens

| Screen | User action | Method & path | Permission | Request DTO | Response DTO | Status |
|---|---|---|---|---|---|---|
| **App bootstrap / splash** | Check reachability | `GET /actuator/health` | public | — | `{status:"UP"}` | ✅ |
| **App shell (post-login)** | Application meta | `GET /api/v1/system/info` | authenticated | — | `{application,version,status}` | ✅ |
| **Bootstrap wizard** | Check installation | `GET /api/v1/auth/installation` | public | — | `InstallationStatusResponse` | ✅ |
| **Bootstrap wizard** | Submit first admin | `POST /api/v1/auth/bootstrap` (+ header `X-EDAM-Bootstrap-Token`) | public | `BootstrapRequest` | `BootstrapResponse` | ✅ |
| **Login** | Load branding | `GET /api/v1/public/configuration` | public | — | `PublicConfigurationResponse` | ✅ |
| **Login** | Get CSRF | `GET /api/v1/auth/csrf` | public | — | `CsrfTokenResponse` | ✅ |
| **Login** | Sign in | `POST /api/v1/auth/login` | public | `LoginRequest` | `CurrentUserResponse` | ✅ |
| **Logout** | Sign out | `POST /api/v1/auth/logout` | public (with session) | — | ok | ✅ |
| **Forgot password** | Request reset | `POST /api/v1/auth/forgot-password` | public | `ForgotPasswordRequest` | `NeutralResponse` | ✅ 🧱 (SMTP) |
| **Reset password** | Submit new password | `POST /api/v1/auth/reset-password` | public | `ResetPasswordRequest` | ok | ✅ |
| **Change password** | Self-service | `POST /api/v1/auth/change-password` | authenticated | `ChangePasswordRequest` | ok | ✅ |
| **Shell / current user** | Load session | `GET /api/v1/auth/me` | authenticated | — | `CurrentUserResponse` | ✅ |
| **App shell** | Active theme | `GET /api/v1/themes/active` | `configuration:view` | — | `ThemeResponse` | ✅ |
| **App shell** | Notification badge | `GET /api/v1/notifications/unread-count` | `notification:view` | — | `{count}` | ✅ |
| **Notification centre** | List | `GET /api/v1/notifications` | `notification:view` | query | `PageResponse<NotificationResponse>` | ✅ |
| **Notification centre** | Mark read | `POST /api/v1/notifications/{id}/read` | `notification:view` | — | ok | ✅ |
| **Notification centre** | Mark all read | `POST /api/v1/notifications/read-all` | `notification:view` | — | ok | ✅ |
| **My profile** | Load self | `GET /api/v1/auth/me` | authenticated | — | `CurrentUserResponse` | ✅ |
| **Dashboard** | KPIs | `GET /api/v1/reporting/dashboard` | `report:view` | — | `DashboardResponse` | ✅ |
| **My Requests** | List (mine) | `GET /api/v1/expense-requests?mineOnly=true` | `request:view-own` | query | `PageResponse<RequestSummary>` | ✅ |
| **All Requests** | List (scope) | `GET /api/v1/expense-requests` | `request:view-own` | query | `PageResponse<RequestSummary>` | ✅ |
| **New OPEX** | Load lookups | GET categories/natures/request-types/vendors/currencies/departments/branches | various `*:view` | query | lists | ✅ |
| **New OPEX** | Create draft | `POST /api/v1/expense-requests` | `request:create` | `CreateExpenseRequest` | `RequestDetail` | ✅ |
| **New OPEX** | Upload documents | `POST /api/v1/documents` | `document:upload` | multipart | `DocumentMetadata` | ✅ 🧱 (storage/scanner) |
| **Edit request** | Load | `GET /api/v1/expense-requests/{id}` | `request:view-own` | — | `RequestDetail` | ✅ |
| **Edit request** | Save | `PUT /api/v1/expense-requests/{id}` | `request:edit` | `UpdateExpenseRequest` | `RequestDetail` | ✅ |
| **Edit request** | Submit | `POST /api/v1/expense-requests/{id}/submit` | `request:submit` | — | `RequestDetail` | ✅ |
| **Edit request** | Cancel | `POST /api/v1/expense-requests/{id}/cancel` | `request:cancel` | `{reason}` | ok | ✅ |
| **Request detail** | Header/items | `GET /api/v1/expense-requests/{id}` | `request:view-own` | — | `RequestDetail` | ✅ |
| **Request detail** | Documents | `GET /api/v1/documents/requests/{requestId}` | `document:download` | — | `DocumentMetadata[]` | ✅ |
| **Request detail** | Document download | `GET /api/v1/documents/{id}/download-url` | `document:download` | — | `{url}` | ✅ 🧱 |
| **Request detail** | Timeline | `GET /api/v1/expense-requests/{id}/activity` | `request:view-own` | — | `TimelinePage` | ✅ |
| **New CAPEX** | Create CAPEX draft | same endpoints as OPEX with CAPEX `requestTypeId` | `request:create` | `CreateExpenseRequest` (CAPEX block) | `RequestDetail` | 🟡 (CAPEX finance field owner OD-13, variance OD-08) |
| **Approval inbox** | Badge/count | `GET /api/v1/approvals/count` | `approval:view` | — | `{total, overdue}` | ✅ |
| **Approval inbox** | List | `GET /api/v1/approvals` | `approval:view` | query | `PageResponse<TaskSummary>` | ✅ |
| **Approval case file** | Task | `GET /api/v1/approvals/tasks/{taskId}` | `approval:view` | — | `TaskDetail` | ✅ |
| **Approval case file** | Request | `GET /api/v1/approvals/tasks/{taskId}/request` | `approval:view` | — | `RequestDetail` | ✅ |
| **Approve** | Approve | `POST /api/v1/approvals/tasks/{taskId}/approve` | `approval:act` | `{remarks?}` | `TaskDetail` | ✅ |
| **Reject** | Reject | `POST /api/v1/approvals/tasks/{taskId}/reject` | `approval:act` | `{remarks}` | `TaskDetail` | ✅ |
| **Revert** | Revert | `POST /api/v1/approvals/tasks/{taskId}/revert` | `approval:act` | `{remarks}` | `TaskDetail` | ⚠️ (OD-10 selective-revert eligibility) |
| **Forward** | Forward | `POST /api/v1/approvals/tasks/{taskId}/forward` | `approval:act` | `{recipientUserId, remarks}` | `TaskDetail` | ✅ |
| **Workflow admin** | Definitions list | `GET /api/v1/workflows` | `workflow:view` | — | `PageResponse<WorkflowDefinition>` | ✅ |
| **Workflow admin** | Versions | `GET /api/v1/workflows/{id}/versions` | `workflow:view` | — | list | ✅ |
| **Workflow admin** | Save draft | `PUT /api/v1/workflows/{definitionId}/draft` | `workflow:manage` | `WorkflowDraft` | version detail | ✅ |
| **Workflow admin** | Validate | `GET /api/v1/workflows/versions/{versionId}/validate` | `workflow:view` | — | `{valid, errors, warnings}` | ✅ |
| **Workflow admin** | Publish | `POST /api/v1/workflows/versions/{versionId}/publish` | `workflow:publish` | — | version detail | ✅ |
| **Users admin** | List | `GET /api/v1/users` | `user:view` | query | `PageResponse<UserSummary>` | ✅ |
| **Users admin** | Create | `POST /api/v1/users` | `user:manage` | `CreateUserRequest` | `UserDetail` | ✅ |
| **Users admin** | Detail/Edit/Activate/Unlock/Reset | GET/PUT/POSTs on `/users/{id}` | `user:manage` | various | `UserDetail` | ✅ |
| **Users admin** | Roles | GET/POST/DELETE on `/users/{id}/roles[/…]` | `user-role:assign` | role payloads | lists | ✅ |
| **Users admin** | Data scopes | POST/DELETE on `/users/{id}/scopes[/…]` | `data-scope:manage` | scope payloads | lists | ✅ |
| **Users admin** | Effective perms | `GET /api/v1/users/{id}/effective-permissions` | `user:view` | — | permission-code list | ✅ |
| **Roles admin** | Catalogue | `GET /api/v1/roles/permissions` | `role:view` | — | all 60 permission codes | ✅ |
| **Roles admin** | CRUD/activate/perms-set | `/roles` endpoints | `role:view`/`role:manage` | — | role DTOs | ✅ |
| **Organization** | Profile | GET/PATCH `/organization/profile` | `organization:view`/`configuration:manage` | — | profile DTO | ✅ |
| **Organization** | Units/Departments/Branches CRUD | `/organization/units`, `/departments`, `/branches` | `organization:view`/`organization:manage` | — | lists/details | ✅ |
| **Master data** | Currencies, vendors, tax authorities | `/master-data/*` | `currency/vendor/tax-authority:*` | — | lists/details | ✅ 🟡 (FX arithmetic OD-07) |
| **Expense config** | Categories/subcategories/natures/request types | `/expense-configuration/*` | `expense-configuration:*` | — | lists/details | ✅ |
| **Approval limits** | CRUD/check | `/approval-limits*` | `approval-limit:*` | — | lists/details | ✅ |
| **Delegation** | CRUD/preview | `/delegation-matrix*` | `delegation:*` | — | lists/details | ⚠️ (OD-01/OD-03 routing sign-off needed for full EC/CEO/CFO flows) |
| **Recommending authorities** | CRUD/resolved | `/recommending-authorities*` | `recommending-authority:*` | — | lists/details | ✅ |
| **Exceeding limits** | CRUD/evaluate | `/exceeding-limit-rules*` | `exceeding-limit:*` | — | lists/details | 🟡 (OD-05 enforcement default off / warn-only) |
| **Committees** | CRUD/members/levels/coverage | `/committees*` | `committee:*` | — | lists/details | ⚠️ (OD-17 EC pool reconciliation) |
| **Business rules** | CRUD/draft/publish/retire | `/business-rules*` | `rule:*` | — | lists/details | ✅ |
| **Documents admin** | List / delete | `/documents*` | `document:download`/`delete` | — | metadata | ✅ 🧱 |
| **SMTP admin** | Read/save/test/deactivate | `/communication/smtp*` | `email-configuration:manage` | SMTP config DTO | masked config | ✅ 🧱 (SMTP server) |
| **Email templates** | CRUD/versions/preview/active | `/communication/email-templates*` | `notification-template:manage` | template DTOs | lists/details | ✅ |
| **Notification triggers** | List/update | `/communication/events*` | `notification-template:manage` | event config DTOs | lists/details | ✅ |
| **Branding admin** | Themes CRUD/activate/default/logo | `/themes*`, `/public/configuration` | `configuration:*` | theme DTOs | lists/details | ✅ |
| **Settings** | CRUD/bulk | `/settings*` | `configuration:*` | setting DTOs | lists/details | ✅ |
| **Feature flags** | CRUD/toggle | `/feature-flags*` | `configuration:*` | — | lists/details | ✅ |
| **Reports** | Register/Export | `GET/POST /reporting/expense-requests/register|export` | `report:view`/`report:export` | filter payloads | page / CSV blob | ✅ (CSV only; XLSX/PDF 🔲 OD-22) |
| **Audit** | Search/detail | `/audit-events*` | `audit:view` | query | `PageResponse<AuditEventSummary>`/detail | ✅ |
| **Integrations** | Providers/CRUD/test/execute/history/retry | `/integrations*` | `integration:*` | integration DTOs | lists/details | ✅ (HTTP webhook provider shipped; others 🔲) |
| **Print/PDF export** | — | — | — | — | — | 🔲 (OD-22) |
| **Bank/tenant switcher** | — | — | — | — | — | 🔲 (intentionally — independent deployments, per settled decision) |
| **XLSX export** | — | — | — | — | — | 🔲 (OD-22) |
| **Real-time websocket notifications** | — | — | — | — | — | 🔲 (polling only in Phase 4J) |
