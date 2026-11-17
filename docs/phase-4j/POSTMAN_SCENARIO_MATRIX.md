# Postman Scenario Matrix — 13 End-to-End Flows

Each scenario lists prerequisites, the folder of requests to execute in order, and expected outcomes. Run scenarios sequentially — later scenarios depend on artifacts (users, categories, workflows, request IDs) created earlier.

| # | Scenario | Folders used | Expected outcomes |
|---|---|---|---|
| **S1** | **Initial bootstrap of a fresh EDAM instance** | 99 System/Info → 01 Auth (CSRF, bootstrap) → 01 Auth (login, me) | `/info` returns `status: UP`; `GET /auth/installation` returns `installed:false`; bootstrap succeeds once; subsequent bootstrap returns 409 (already installed); `GET /auth/me` returns principal with full `system:administer` permission. |
| **S2** | **First-administrator password change** | 01 Auth (change-password) → 01 Auth (logout → login with new password) | Change password succeeds; old password rejected; new password accepted. |
| **S3** | **Organization setup** | 05 Organization Management (profile GET/PATCH, unit-types, units, departments, branches, activations) | Institution profile populated; a department tree and a branch list are returned; activation/deactivation does not delete rows; duplicate names are rejected. |
| **S4** | **Currencies & vendors master data** | 06 Master Data (currencies CRUD, vendors CRUD, tax authorities) | PKR is the base currency; ISO-4217 validation rejects invalid codes; vendor/vendor-contacts/departments/branches filter; tax authority CRUD. |
| **S5** | **Categories, subcategories, natures, request types** | 07 Expense Configuration | Categories/natures/subcategories/request types CRUD; OPEX and CAPEX request types exist; published configuration endpoints return the active set; duplicate codes rejected. |
| **S6** | **Users, roles and permissions** | 03 Users, 04 Roles | A test requester, approver and HOD user are created; roles with permission subsets are created; user-role assignments and data-scope grants are made; `/users/{id}/effective-permissions` reflects assigned permissions. |
| **S7** | **Delegation matrix & approval limits** | 10 Department Approval Limits, 11 Delegation/Recommending Authorities, 12 Expense Committees | Department approval limits per (department, category, currency); delegation matrix rows; recommending authorities; committees/members. |
| **S8** | **Workflow configuration & publish** | 13 Workflow Configuration, 15 Business Rules | Workflow definition drafted for OPEX and CAPEX; stages/steps/quorum configured; `validate` returns no errors; `publish` creates a published version; editing draft leaves published version immutable; `retire` retires a published version only when no newer version supersedes it (or after all in-flight cases are drained per product policy). |
| **S9** | **OPEX request submit → approval → complete** | 08 OPEX (create draft, edit, add line items, upload docs, submit, list mine, list all) → 16 Inbox → 17 Approval Actions (approve at each required step) | Draft → SUBMITTED; each required approver sees the task; approvals advance the workflow; final approval completes the request; requester can view status in timeline. |
| **S10** | **CAPEX request submit (shares /expense-requests API with CAPEX requestTypeId)** | 08/09 OPEX/CAPEX | CAPEX request uses the CAPEX requestTypeId; CAPEX-specific finance fields validate; request flows through CAPEX workflow; (CAPEX finance field owner is an open decision — flag any missing field). |
| **S11** | **Rejection, revert, forward and resubmission** | 17 Approval Actions, 18 Rejection and Reverting, 19 Forwarding and Resubmission | Rejection returns request to requester with status/remarks; revert returns the entire case to the initial state (resubmission reuses pinned workflow version); forward replaces the current task with a new task for the recipient; forward to non-existent user returns 404. |
| **S12** | **SMTP, email templates, notification events, outbox** | 21 SMTP, 22 Templates/Triggers, 23 Notifications | SMTP configuration saved with encrypted password; template preview renders variables; event toggle switches triggers; on approval/rejection a notification_outbox row is created; with SMTP configured the outbox is drained; without SMTP the row stays FAILED with a redacted diagnostic. |
| **S13** | **Branding, themes, dashboards, audit, integrations** | 24 Branding/Themes, 25 Dashboard/Reporting, 26 Audit, 27 Integrations | Logo upload (Cloudinary required); theme tokens saved and returned via `/theme`; dashboard stats and CSV export; audit_events captured for critical actions and searchable by request/entity; integration config saved with encrypted secret; outbound HTTP webhook provider (Phase 4I) can be executed and produces claim/deliver/timeout/error diagnostics without leaking secrets. |

## Idempotency (Folder 28)
For mutating endpoints that accept the `Idempotency-Key` header (documented on the controller), repeat the same call with the same key within the configured window: the response is identical and exactly one row is created.

## Negative & authorization tests (Folder 29)
These are placeholders. Add at least the following for each endpoint:
- No session → **401**.
- Session but missing permission → **403** with `errorCode: AUTHORIZATION_FAILURE`.
- Missing required field → **400** with `VALIDATION_ERROR`.
- Optimistic-lock conflict on two concurrent edits → **409** `CONCURRENT_MODIFICATION`.
- Referential integrity violation → **409** `DATA_INTEGRITY_VIOLATION`.
- Idempotency replay with same key and same body → **200/201** with identical response.
- Idempotency replay with same key and different body → **422** `IDEMPOTENCY_KEY_REPLAY`.
