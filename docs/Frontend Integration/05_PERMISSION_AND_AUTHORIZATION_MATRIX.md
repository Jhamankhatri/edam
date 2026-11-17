# 05 — Permission & Authorization Matrix

The permission catalogue is defined in `src/main/java/com/edam/common/security/Permissions.java` and seeded into the `permissions` table by migration `V12__permission_catalogue_and_roles.sql`. The frontend reads the user's effective permission codes from `GET /api/v1/auth/me` (field `permissions: string[]`) and uses them for navigation and action visibility only. The backend enforces `@EdamPermission` on every endpoint via `EdamPermissionAuthorizationManager` (plus service-layer object visibility checks).

There are **60 permission codes** in Phase 4J, plus the two special access levels: `public` and `authenticated (no business permission)`.

## 5.1 Important rules

- **A permission is not a role.** Roles are arbitrary named collections of permissions created by the institution. The frontend must never hard-code role names to gate UI; only permission codes are stable.
- **Endpoint permission != object visibility.** For example `request:view-own` on `GET /expense-requests/{id}` still returns 404 if the id belongs to another user's request outside the caller's data scopes. Service methods enforce this (`ExpenseRequestService.isVisible`, etc.).
- **Approval authority is workflow, not permission.** `approval:act` permits acting on tasks assigned to the caller (or groups the caller is a member of); it does not grant blanket approval ability across the bank. Workflow eligibility is checked server-side per task.
- **Bootstrap token** is a one-time installation secret sent as `X-EDAM-Bootstrap-Token`, not a permission.

## 5.2 Permission → endpoint map

| Code | Description | Methods + paths (controller) | Module |
|---|---|---|---|
| `system:administer` | Super-administrator; typically granted only to the bootstrap administrator role. Not used as an `@EdamPermission` on endpoints (granular permissions protect all actions); reserved for special diagnostics / break-glass screens. | (Reserved; no direct @EdamPermission usage in Phase 4J. Bootstrap response grants this role to the first admin.) | Core |
| `organization:view` | Read organization profile, unit types, units/tree, departments, branches. | GET `/organization/profile`, `/organization/unit-types`, `/organization/units`, `/organization/units/tree`, `/organization/departments`, `/organization/branches` (OrganizationController) | Organization |
| `organization:manage` | Create/update/activate units, departments, branches. | POST/PUT/activation endpoints on `/organization/units`, `/organization/departments`, `/organization/branches` | Organization |
| `configuration:view` | Read themes (list/active/by-id), application settings, feature flags. | GET `/themes`, `/themes/active`, `/themes/{id}`, `/settings`, `/settings/categories/{cat}`, `/settings/{key}`, `/feature-flags`, `/feature-flags/{key}` (ConfigurationController) | Configuration |
| `configuration:manage` | Write themes (create/update/activate/default), application settings (save/bulk), feature flags (save/toggle). Also used to create/update the organization profile (treated as configuration, not org admin). | POST/PUT themes endpoints; POST/PATCH `/organization/profile`; POST/PATCH settings; POST feature flags | Configuration |
| `user:view` | List users, search users, read a user, read user roles and effective permissions. | GET `/users`, `/users/search`, `/users/{id}`, `/users/{id}/roles`, `/users/{id}/effective-permissions` | Users |
| `user:manage` | Create/update/activate users; trigger password reset; unlock. | POST `/users`, PUT `/users/{id}`, POST `/users/{id}/activation`, POST `/users/{id}/password-reset`, POST `/users/{id}/unlock` | Users |
| `role:view` | List roles, read permission catalogue, read one role. | GET `/roles`, `/roles/permissions`, `/roles/{id}` | Roles |
| `role:manage` | Create/update/activate roles and set their permission grants. | POST `/roles`, PUT `/roles/{id}`, POST `/roles/{id}/activation`, PUT `/roles/{id}/permissions` | Roles |
| `user-role:assign` | Assign / revoke roles on a user. | POST `/users/{id}/roles`, DELETE `/users/{id}/roles/{assignmentId}` | Users |
| `data-scope:manage` | Assign / revoke data scopes (INSTALLATION/BRANCH/DEPARTMENT with ID lists). | POST `/users/{id}/scopes`, DELETE `/users/{id}/scopes/{scopeId}` | Users |
| `currency:view` | List currencies and exchange rates; (also used for tax-authority list on master data controller). | GET `/master-data/currencies`, `/master-data/exchange-rates`, `/master-data/tax-authorities` | Master data |
| `currency:manage` | Create/update/activate currencies; create/close exchange rates. | POST/PUT/activation on `/master-data/currencies`; POST `/master-data/exchange-rates`; POST `/master-data/exchange-rates/{id}/close` | Master data |
| `vendor:view` | List vendors and vendor detail. | GET `/master-data/vendors`, `/master-data/vendors/{id}` | Master data |
| `vendor:manage` | Create/update/activate vendors. | POST/PUT/activation on `/master-data/vendors` | Master data |
| `tax-authority:manage` | Create/update/activate tax authorities. | POST/PUT/activation on `/master-data/tax-authorities` | Master data |
| `expense-configuration:view` | Read categories, natures, request types. | GET `/expense-configuration/categories`, `/expense-configuration/natures`, `/expense-configuration/request-types` | Expense config |
| `expense-configuration:manage` | Create/update/activate categories, subcategories, natures, request types. | POST/PUT/activation on `/expense-configuration/categories/**`, `/subcategories/**`, `/natures/**`; POST `/expense-configuration/request-types` | Expense config |
| `delegation:view` | List delegation rules and preview routing. | GET `/delegation-matrix`, POST `/delegation-matrix/preview` | Delegation |
| `delegation:manage` | Create, close and activate delegation rules. | POST `/delegation-matrix`, POST `/delegation-matrix/{id}/close`, POST `/delegation-matrix/{id}/activation` | Delegation |
| `approval-limit:view` | List/get approval limits and run authority check. | GET `/approval-limits`, `/approval-limits/{id}`, POST `/approval-limits/authority-check` | Approval limits |
| `approval-limit:manage` | Create/update/activate approval limits. | POST/PUT/activation on `/approval-limits` | Approval limits |
| `recommending-authority:view` | List/get recommending authorities and resolve. | GET `/recommending-authorities`, `/recommending-authorities/{id}`, `/recommending-authorities/resolved` | Approval limits |
| `recommending-authority:manage` | Create/update/activate recommending authorities. | POST/PUT/activation on `/recommending-authorities` | Approval limits |
| `exceeding-limit:view` | List/get exceeding-limit rules and evaluate against a request. | GET `/exceeding-limit-rules`, `/exceeding-limit-rules/{id}`, GET `/exceeding-limit-rules/evaluation` | Approval limits |
| `exceeding-limit:manage` | Create/update/activate exceeding-limit rules. | POST/PUT/activation on `/exceeding-limit-rules` | Approval limits |
| `committee:view` | List/get committees and coverage. | GET `/committees`, `/committees/{id}`, `/committees/coverage` | Committees |
| `committee:manage` | Create/rename/deactivate committees, members and levels. | POST/PUT/POST-end/POST-level/deactivate-level on `/committees/**` | Committees |
| `request:create` | Create a new (draft) expense request. | POST `/expense-requests` | Requests |
| `request:view-own` | List requests visible to the caller, get a request by id, view the request timeline. (Visibility is filtered by data scope — requesters see their own; broader scopes include more.) | GET `/expense-requests`, `/expense-requests/{id}`, GET `/expense-requests/{id}/activity` (RequestTimelineController) | Requests |
| `request:view-scope` | *(Reserved)* View requests within granted data scope. Not yet enforced on a distinct endpoint in Phase 4J; `request:view-own` serves all GETs and applies scope at the service layer. | — | Requests |
| `request:view-all` | *(Reserved)* Cross-institution view. Not enforced on a distinct endpoint in Phase 4J. | — | Requests |
| `request:edit` | Update an existing (typically DRAFT/REJECTED/REVERTED) request. | PUT `/expense-requests/{id}` | Requests |
| `request:submit` | Submit a draft request into workflow. | POST `/expense-requests/{id}/submit` | Requests |
| `request:cancel` | Cancel a request. | POST `/expense-requests/{id}/cancel` | Requests |
| `request:reverse` | *(Reserved)* Reverse a completed/approved request. Not wired to a controller endpoint in Phase 4J; reverts during approval are performed with `approval:act` on a task. | — | Requests |
| `request:export` | *(Reserved)* Request-specific export. The current export endpoint is `/reporting/expense-requests/export` and uses `report:export`. | — | Requests |
| `document:upload` | Upload a document for a request. | POST `/documents` | Documents |
| `document:download` | List documents for a request, get metadata, get a signed download URL, list scan events. | GET `/documents/requests/{requestId}`, `/documents/{id}`, `/documents/{id}/download-url`, `/documents/{id}/scans` | Documents |
| `document:delete` | Delete a document (subject to state policy). | DELETE `/documents/{id}` | Documents |
| `approval:view` | Inbox list/count; view a task and its request. | GET `/approvals`, `/approvals/count`, `/approvals/tasks/{taskId}`, `/approvals/tasks/{taskId}/request` | Approvals |
| `approval:act` | Approve, reject, revert or forward a workflow task. | POST `/approvals/tasks/{taskId}/approve`, `/reject`, `/revert`, `/forward` | Approvals |
| `workflow:view` | List workflow definitions/versions; get a version; compare versions; validate a draft. | GET `/workflows`, `/workflows/{definitionId}/versions`, `/workflows/versions/{versionId}`, `/workflows/versions/compare`, `/workflows/versions/{versionId}/validate` | Workflow |
| `workflow:manage` | Create/update a definition; set active; save draft; clone from published or a specific version. | POST/PUT/active/save/clone endpoints on `/workflows` | Workflow |
| `workflow:publish` | Publish or retire a workflow version. | POST `/workflows/versions/{versionId}/publish`, `/workflows/versions/{versionId}/retire` | Workflow |
| `rule:view` | List/get business rule sets. | GET `/business-rules`, `/business-rules/{id}` | Business rules |
| `rule:manage` | Create/update rule sets; create drafts; upsert/delete rules in a draft version. | POST/PUT `/business-rules`, POST `/business-rules/{id}/drafts`, PUT `/business-rules/versions/{versionId}/rules`, DELETE `/business-rules/rules/{ruleId}` | Business rules |
| `rule:publish` | Publish or retire a rule set. | POST `/business-rules/{id}/publish`, `/business-rules/{id}/retire` | Business rules |
| `audit:view` | Search and read audit events. | GET `/audit-events`, `/audit-events/{id}` | Audit |
| `audit:export` | *(Reserved)* Export audit events; not wired to a controller endpoint in Phase 4J. | — | Audit |
| `notification:view` | List notifications, unread count, mark read / mark all read. | GET `/notifications`, GET `/notifications/unread-count`, POST `/notifications/{id}/read`, POST `/notifications/read-all` | Notifications |
| `email-configuration:manage` | Read/save/test/deactivate SMTP configuration. Also covers GET of the SMTP configuration (read-before-edit). | GET/POST/DELETE/test on `/communication/smtp` | SMTP |
| `notification-template:manage` | List/get/create/create-version/set-active/preview email templates; list/update notification event trigger configuration. | EmailTemplateController + NotificationEventConfigController endpoints (GET/POST/PUT under `/communication/email-templates` and `/communication/events`) | Templates / events |
| `report:view` | Dashboard and expense-request register. | GET `/reporting/dashboard`, `/reporting/expense-requests/register` | Reports |
| `report:export` | CSV (or configured) export. | POST `/reporting/expense-requests/export` | Reports |
| `dashboard:manage` | View widget and report catalogues (admin-configurable dashboards). | GET `/reporting/widgets`, `/reporting/reports` | Dashboard admin |
| `integration:view` | List providers/configs; view a config; list/view executions. | GET `/integrations/providers`, `/integrations`, `/integrations/{id}`, `/integrations/{id}/executions`, `/integrations/executions/{execId}` | Integrations |
| `integration:manage` | Create/update/activate/deactivate/archive an integration config. | POST/PUT/activate/deactivate/DELETE on `/integrations` | Integrations |
| `integration:execute` | Execute an integration and retry an execution. | POST `/integrations/{id}/executions`, POST `/integrations/executions/{execId}/retry` | Integrations |
| `integration:test` | Dry-run/test an integration configuration. | POST `/integrations/{id}/test` | Integrations |

## 5.3 Public endpoints (no session, no permission)
- `GET /api/v1/info`
- `GET /api/v1/auth/csrf`
- `POST /api/v1/auth/login` (CSRF still required as a header)
- `POST /api/v1/auth/logout` (CSRF required; uses an existing session if present)
- `POST /api/v1/auth/forgot-password`
- `POST /api/v1/auth/reset-password`
- `GET /api/v1/auth/installation`
- `POST /api/v1/auth/bootstrap` (requires `X-EDAM-Bootstrap-Token` header)
- `GET /api/v1/public/configuration` (branding safe-subset)

## 5.4 Authenticated-only (no business permission)
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/change-password`

## 5.5 Object-level and state-level restrictions
These are enforced in the service layer after the permission check passes:

| Resource | Visibility / state rule | Where |
|---|---|---|
| Expense request | Caller must be requester, or within data scope (department/branch/installation) to see it. | `ExpenseRequestService.isVisible(...)` |
| Document | Visibility follows the attached request. Download only when `scanStatus=CLEAN`. | `DocumentService` |
| Workflow task | Only the assigned/eligible approver (or group member meeting quorum prerequisites) can act; forward/reject/revert remarks required where configured; `@Version` on `WorkflowTask` prevents concurrent action. | `ApprovalService` |
| User management | Only users within the caller's institution(s) are visible; system-role protection (`SYSTEM_ROLE_PROTECTED`) prevents edits that would break the product. | `UserService`, `RoleService` |
| Workflow versions | Published versions are immutable; retire only succeeds when safe per rules. | `WorkflowService` |
| Integrations/SMTP | Secret fields never returned; passwords appear blank in responses; saving overwrites ciphertext. | `IntegrationSecretCrypto`, `MailCredentialCrypto` |

## 5.6 Frontend helper
```ts
function can(permissions: string[], code: string): boolean {
  return permissions.includes(code);
}
// Example:
{can(user.permissions, "approval:act") && <Button>Approve</Button>}
```
Do not cache permission answers in localStorage; always read from the live `GET /auth/me` response after login or role/scope change.
