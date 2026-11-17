# EDAM Product Capability Register (Phase 4J baseline)

Status legend:
- ✅ **Implemented** — backend + persistence + API + tests complete; frontend will consume the API.
- 🟡 **Partial** — backend foundation exists but behaviour is gated by an open decision or missing configuration.
- 🔲 **Not implemented** — not started.
- 🧱 **Requires infrastructure** — implemented at the application layer but requires an external service to be useful (SMTP, malware scanner, Cloudinary).
- 🎨 **Planned frontend** — the backend exposes the API; React + TypeScript delivery is a separate project (out of scope for Phase 4J).

## Foundation & identity

| Capability | Status | Endpoints (count) | Permissions | DB objects | Tests | Notes / Open decisions |
|---|---|---|---|---|---|---|
| First-administrator bootstrap (one-time) | ✅ | 2 (`/auth/installation`, `/auth/bootstrap`) | public | `installation_state` | ✅ | Requires `EDAM_BOOTSTRAP_TOKEN`; bootstrap after install returns 409. |
| Session-cookie login/logout + CSRF double-submit | ✅ | 5 (`/auth/csrf`, `/auth/login`, `/auth/logout`, `/auth/me`, `/auth/change-password`) | public + authenticated | `login_attempts`, `password_reset_tokens` | ✅ | No JWT. |
| Forgot/reset password flow | 🟡 | 2 public | public | `password_reset_tokens` | 🟡 | Requires SMTP to deliver the token out-of-band; without SMTP the token is only surfaced through dev diagnostics. |
| Password policy enforcement | ✅ | (part of change-password / set-password) | — | — | ✅ | Minimum length, common-password block, complexity rules. |
| System info (public) | ✅ | 1 (`/info`) | public | — | ✅ | App name, version, status; no business data. |
| User CRUD + activation + unlock + password reset | ✅ | 12 | USER_VIEW, USER_MANAGE, USER_ROLE_ASSIGN, DATA_SCOPE_MANAGE | `users`, `user_roles`, `user_data_scopes` | ✅ | |
| Role CRUD + permission grants | ✅ | Role endpoints | ROLE_VIEW, ROLE_MANAGE | `roles`, `permissions` | ✅ | Role → permission mapping is defined by the catalogue; OD-12 still requires business-owner sign-off on the actual role matrix. |
| Data-scope grants per user | ✅ | 2 endpoints | DATA_SCOPE_MANAGE | `user_data_scopes` | ✅ | Scopes enforce INSTALLATION/BRANCH/DEPARTMENT visibility. |

## Organization & master data

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Institution profile | ✅ | 3 | ORGANIZATION_VIEW, CONFIGURATION_MANAGE | `organization_profile` | ✅ | |
| Unit types, organization units (tree), departments, branches | ✅ | 12 | ORGANIZATION_VIEW, ORGANIZATION_MANAGE | `organization_unit_types`, `organization_units`, `departments`, `branches` | ✅ | Activation only (no hard delete). |
| Currencies | ✅ | Master Data set | CURRENCY_VIEW, CURRENCY_MANAGE | `currencies`, `exchange_rates` | ✅ | ISO-4217 validation. OD-07 (FX rounding/arithmetic) open. |
| Vendors + contacts | ✅ | | VENDOR_VIEW, VENDOR_MANAGE | `vendors` | ✅ | |
| Tax authorities | ✅ | | TAX_AUTHORITY_MANAGE | `tax_authorities` | ✅ | |

## Expense configuration

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Expense natures / categories / subcategories | ✅ | Expense Configuration | EXPENSE_CONFIGURATION_VIEW/MANAGE | `expense_natures`, `expense_categories`, `expense_subcategories` | ✅ | |
| Request types (OPEX/CAPEX) | ✅ | | same | `expense_request_types` | ✅ | OPEX/CAPEX share `/expense-requests`; type discriminates behaviour. |

## Approval limits, delegation, committees

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Department approval limits (per dept × category × currency) | ✅ | 10 | APPROVAL_LIMIT_VIEW/MANAGE | `department_approval_limits` | ✅ | Effective date ranges; OD-06 (global vs per-category HOD threshold) resolved to "single nullable threshold with documented fallback". |
| Exceeding-limit rules | ✅ | | EXCEEDING_LIMIT_VIEW/MANAGE | `exceeding_limit_rules` | ✅ | OD-05 routing enforcement is `false` (warn only) until signed off. |
| Recommending authorities | ✅ | | RECOMMENDING_AUTHORITY_VIEW/MANAGE | `recommending_authorities` | ✅ | |
| Delegation matrix + approver steps | ✅ | Delegation | DELEGATION_VIEW/MANAGE | `expense_delegation_rules`, `delegation_approver_steps` | ✅ | OD-01/03 (EC level composition, CEO/CFO standalone routes) still open; matrix stores configured values; engine resolution only honours signed-off rules. |
| Expense committees + levels + members | ✅ | Committees | COMMITTEE_VIEW/MANAGE | `expense_committees`, `committee_levels`, `committee_level_members`, `committee_members` | ✅ | OD-17 (EC pool vs committees reconciliation) open. |

## Workflow engine

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Workflow definitions + drafts | ✅ | Workflow Configuration | WORKFLOW_CONFIGURATION_VIEW/MANAGE | `workflow_definitions`, `workflow_versions` | ✅ | |
| Version cloning (from published / from version) | ✅ | | | | ✅ | |
| Version validation & publishing | ✅ | POST validate/publish | WORKFLOW_CONFIGURATION_MANAGE | | ✅ | Published versions are immutable. |
| Workflow state/transition/step/approver model | ✅ | | | `workflow_states`, `workflow_transitions`, `workflow_steps`, `workflow_step_approvers` | ✅ | OD-02 (CEO/CFO/HOD routing order) must be configured per-bank before the engine can approve end-to-end. |
| Pin workflow version at submission | ✅ | (internal) | — | `workflow_instances` | ✅ | |
| Approval inbox (task list + count + case file) | ✅ | Approvals inbox | REQUEST_APPROVE / REQUEST_VIEW | `workflow_tasks`, `workflow_actions` | ✅ | |
| Approve / reject / revert / forward actions | ✅ | POST /approvals/tasks/{id}/… | REQUEST_APPROVE | `workflow_tasks`, `workflow_actions` | ✅ | Revert returns to initial state (reuses same version on resubmit); forward replaces the current task; optimistic locking prevents double-approve. OD-04 (CFO action set) and OD-10 (selective revert eligibility) still open. |

## OPEX / CAPEX requests

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Draft / edit / submit / cancel | ✅ | Expense requests | REQUEST_CREATE/EDIT/SUBMIT/CANCEL/VIEW_OWN | `expense_requests` | ✅ | |
| OPEX detail + items + allocations + invoices + quotations | ✅ | | same | `opex_request_details`, `expense_request_items`, `expense_allocations`, `expense_invoices`, `expense_quotations`, `expense_previous_summaries` | ✅ | |
| CAPEX detail + STA + budget summaries | ✅ | | same | `capex_request_details`, `capex_sta_details`, `capex_budget_summaries` | ✅ | OD-08 (CAPEX variance formulas) and OD-13 (CAPEX finance-field owner) open — values are stored; arithmetic is not performed. |
| Exchange-rate resolution | 🟡 | (service) | — | `exchange_rates` | 🟡 | Rates can be stored; OD-07 (conversion arithmetic) is not implemented, so no amounts are auto-converted yet. |
| Request numbering | ✅ | generated on submit | — | | ✅ | Per-institution sequence, zero-padded, gap-safe. |

## Business rules

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Rule sets / versions / rules (JSON-AST) | ✅ | Business Rules | BUSINESS_RULE_VIEW/MANAGE | `business_rule_sets`, `business_rule_versions`, `business_rules` | ✅ | Closed operator set; no MVEL/SpEL/reflection; depth/leaf-count caps; publish-time + eval-time validation. |

## Documents

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Upload, metadata, visibility | ✅ | Documents | DOCUMENT_UPLOAD/VIEW/MANAGE | `request_documents`, `media_assets`, `doc_scan_events` | ✅ | Object-level visibility (requester / department / branch / installation) enforced at service layer. |
| Download via signed URL | ✅ | | | | ✅ | 🧱 Cloudinary (or compatible) required for production; `FakeStorageProvider` is test-only. |
| Malware scanning port | ✅ | (service) | — | `doc_scan_events` | ✅ | 🧱 Must plug in a real scanner. Download is fail-closed (only CLEAN documents served). OD-14 open (provider, retention). |

## Communications

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| SMTP configuration (encrypted) + test mail | ✅ | SMTP Configuration | COMMUNICATION_MANAGE/VIEW | `comm_smtp_configuration`, `comm_smtp_test_history` | ✅ | AES-256-GCM; key from env; redacted diagnostics; fail-closed without master key. 🧱 SMTP server required. |
| Email templates + versions | ✅ | Email Templates | EMAIL_TEMPLATE_VIEW/MANAGE | `comm_email_templates`, `comm_email_template_versions` | ✅ | Variable placeholders + preview. |
| Notification event triggers (toggle per event) | ✅ | Event Configs | NOTIFICATION_CONFIG_VIEW/MANAGE | `comm_notification_event_configs` | ✅ | |
| User notification preferences | ✅ | | | `comm_notification_preferences`, `comm_notification_preference_events` | ✅ | |
| Notification inbox (in-app) | ✅ | Notifications | NOTIFICATION_VIEW | `comm_notifications` | ✅ | Mark read/unread; badge count. |
| Transactional outbox + mail sending worker | ✅ | (background) | — | `comm_notification_outbox` | ✅ | REQUIRES_NEW per-row claim/deliver; at-least-once; failure leaves a redacted diagnostic; no TX rollback from SMTP errors. 🧱 SMTP required for actual delivery. OD-15 (full event matrix) partially complete. |

## Branding, themes, settings

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Application settings | ✅ | Configuration | CONFIGURATION_VIEW/MANAGE | `application_settings`, `feature_flags` | ✅ | |
| Branding theme + tokens (per institution) | ✅ | Branding & Themes | BRANDING_MANAGE/VIEW | `branding_themes`, `theme_tokens`, `media_assets` | ✅ | Public `/theme` endpoint returns the active theme for unauthenticated landing/setup. No arbitrary CSS/JS allowed; tokens are strictly enumerated (color, radius, font, logo asset id). |
| Public theme (unauthenticated) | ✅ | GET `/api/v1/configuration/theme` | public | | ✅ | Returns the active theme for the landing page. |

## Reporting & dashboard

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Dashboard aggregates | ✅ | Reporting | REPORTING_VIEW | — | ✅ | Counts per status, per department, per period; request throughput. |
| Register/export (CSV) | ✅ | | same | — | ✅ | Paginated register; CSV export follows visibility scopes. OD-22 (PDF/XLSX/print) open. |

## Audit

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Append-only audit events | ✅ | (service interceptor) | — | `audit_events` | ✅ | DB trigger prevents UPDATE/DELETE outside application path. |
| Audit search & detail | ✅ | Audit | AUDIT_VIEW | | ✅ | Filter by actor, entity type, entity id, correlation id, date range. |
| Request timeline | ✅ | Timeline | REQUEST_VIEW (own) / AUDIT_VIEW | | ✅ | Aggregates workflow actions, document events and audit events for a single request. |

## Integrations (Phase 4H/I)

| Capability | Status | Endpoints | Permissions | DB | Tests | Notes |
|---|---|---|---|---|---|---|
| Integration config CRUD (secret encrypted) | ✅ | Integrations | INTEGRATION_MANAGE/VIEW | `integration_configs` | ✅ | AES-256-GCM; SSRF-safe URL validation (blocks loopback/private/link-local/IMDS); fail-closed without master key. |
| Provider SPI + HTTP webhook adapter | ✅ | (service) | — | — | ✅ | SPI is `IntegrationProvider`; HTTP provider is the first shipping adapter. OD-16 (HR/AD sync adapter) is not built. |
| Execution engine (claim/deliver/timeout/error) | ✅ | POST executions + GET status | INTEGRATION_MANAGE/EXECUTE | `integration_executions` | ✅ | REQUIRES_NEW per-row worker, `SELECT FOR UPDATE SKIP LOCKED`, timeout + dead-letter behaviour; redacted diagnostics; concurrency tests. |
| Idempotency on execution | ✅ | (via header) | — | `idempotency_records` | ✅ | Unique constraint `(idempotency_key, actor_id, request_path)`. A known concurrency edge case exists in tests (see open-decisions). |

## Frontend (React + TypeScript)

| Capability | Status | Notes |
|---|---|---|
| React application project | 🔲 / 🎨 | Explicitly out of scope for Phase 4J. This phase delivers the integration blueprint (FRONTEND_MASTER_INTEGRATION_GUIDE.md, FRONTEND_ARCHITECTURE_BLUEPRINT.md, EDAM_UI_UX_DESIGN_SYSTEM.md) that the frontend team will implement against. |
| Landing page & bootstrap wizard | 🎨 | Public `/info`, `/auth/installation`, `/auth/bootstrap`, public `/configuration/theme` are all available. |
| Auth screens | 🎨 | `/auth/csrf`, `/auth/login`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/me` cover login, forgot/reset, session check. |
| All admin and transactional screens | 🎨 | Backend APIs exist for every screen documented in FRONTEND_MASTER_INTEGRATION_GUIDE.md. |

## External / operational dependencies

| Dependency | Purpose | Status in code | Required for go-live? |
|---|---|---|---|
| PostgreSQL 15+ | Primary datastore | Schema validated by Hibernate; Flyway migrations | **Yes** |
| SMTP server | Outbound transactional email | `FakeJavaMailSender` in tests; SMTP config CRUD + outbox in prod | **Yes** for forgot-password, approvals, notifications |
| Document object storage (e.g. Cloudinary) | Document upload/download | Cloudinary adapter wired via `CLOUDINARY_URL`; `FakeStorageProvider` in tests | **Yes** for document upload |
| Malware scanner | Attachment scan before download | `MalwareScanner` port with `FakeMalwareScanner` test double; fail-closed download | **Yes** before enabling user uploads |
| HTTPS reverse proxy | TLS termination | Prod profile sets `secure` cookies; CORS exact-origin | **Yes** |
| Idempotency record cleanup (scheduled job) | Expire old idempotency records | TTL enforced at read; scheduled cleanup job in V24 placeholder | Recommended |
