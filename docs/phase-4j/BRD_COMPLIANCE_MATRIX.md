# BRD Compliance Matrix (EDM-BRD-001 v2.0)

> Source: `docs/brd/EDM-BRD-001-v2.txt`. Status values: ✅ implemented, 🟡 partial (foundation exists, gated by an open decision), 🔲 not started, ⚠️ open decision, 🧱 requires external infrastructure, 🎨 planned frontend.
>
> Every endpoint, permission and table below is verified against the Phase 4J source (26 controllers, 206 endpoints, 60 permissions, 72 mapped JPA tables, Flyway V1→V27).

## 1. Objectives (OBJ-01 … OBJ-09)

| Ref | BRD requirement | Status | Evidence | Notes |
|---|---|---|---|---|
| OBJ-01 | Configurable OPEX/CAPEX request & approval platform | ✅ | 206 endpoints; `expense_requests` + `opex_request_details` + `capex_request_details`; configurable workflow engine | OPEX/CAPEX share `/api/v1/expense-requests` with type discriminator. |
| OBJ-02 | Institution branding without a code change | ✅ | `configuration` module, `branding_themes` + `theme_tokens`; public `/configuration/theme` | Token set is closed (no arbitrary CSS/JS). |
| OBJ-03 | Configurable workflow (stages, approvers, quorum) | ✅ | `workflow` module; immutable versions on publish | OD-02 (CEO/CFO/HOD routing order) is a per-bank config item, not a code constant. |
| OBJ-04 | Auditability of all important changes | ✅ | `audit_events` (append-only via trigger V10/V25); `AuditController`; request timeline | |
| OBJ-05 | Secure authentication & authorization | ✅ | Spring Security deny-by-default; CSRF; method-level `@EdamPermission`; object-level visibility | Session cookies + CSRF (no JWT). OD-11 (SSO/OIDC) deferred; ADR-0004 defines the seam. |
| OBJ-06 | Multi-institution ready | 🟡 | `EdamPrincipal.institutionRoles` is a Map; data scopes include INSTALLATION | OD-20 (tenant isolation model) is deferred; single-instance-per-institution today. |
| OBJ-07 | Attachments with size/type limits and malware scan | 🟡/🧱 | `documents` module; `MalwareScanner` port; fail-closed download | OD-14 (scanner provider, retention) open; Cloudinary required for production. |
| OBJ-08 | Notifications & email | 🟡/🧱 | `comm_*` tables; SMTP config + templates + outbox | OD-15 (full event matrix) partial; SMTP required for actual delivery. |
| OBJ-09 | Business rules in Java (no stored procedures) | ✅ | All logic in services; declarative JSON-AST rule engine; no procedural SQL | ADR-0001 layering enforced by architecture tests. |

## 2. Functional modules (FM-01 … FM-13)

| Ref | Module | Status | Endpoints (sample) | Permissions | Tables | Tests | Gaps / open decisions |
|---|---|---|---|---|---|---|---|
| FM-01 | User / identity management | ✅ | `/users` CRUD, activate, password-reset, unlock, roles, scopes, effective-permissions | USER_VIEW/MANAGE, USER_ROLE_ASSIGN, DATA_SCOPE_MANAGE | `users`, `user_roles`, `user_data_scopes` | ✅ | OD-16 (HR/AD sync) not built; local maintenance only. |
| FM-02 | Roles & permissions | ✅ | `/roles` CRUD + permission grants | ROLE_VIEW/MANAGE | `roles`, `permissions` | ✅ | OD-12 (customer role matrix) to be signed off. |
| FM-03 | Organization structure (units, departments, branches) | ✅ | `/organization/units`, `/departments`, `/branches`, activation | ORGANIZATION_VIEW/MANAGE | `organization_units`, `departments`, `branches`, `organization_unit_types`, `organization_profile` | ✅ | |
| FM-04 | Institution branding / themes | ✅ | `/configuration/branding`, `/configuration/theme` (public), theme tokens, logo upload | BRANDING_VIEW/MANAGE, CONFIGURATION_VIEW/MANAGE | `branding_themes`, `theme_tokens`, `application_settings`, `feature_flags`, `media_assets` | ✅ | No arbitrary CSS/JS execution (tokens are whitelisted). |
| FM-05 | Currencies & FX | 🟡 | `/master-data/currencies`, `/master-data/exchange-rates` | CURRENCY_VIEW/MANAGE | `currencies`, `exchange_rates` | ✅ | OD-07 (FX arithmetic/rounding) not implemented. |
| FM-06 | Vendors & tax authorities | ✅ | `/master-data/vendors`, `/master-data/tax-authorities` | VENDOR_VIEW/MANAGE, TAX_AUTHORITY_MANAGE | `vendors`, `tax_authorities` | ✅ | |
| FM-07 | Expense categories / sub-categories / natures / request types / delegation matrix | ✅ (categories, natures, request types); 🟡 (delegation resolution) | `/expense-configuration/*`, `/delegation/*` | EXPENSE_CONFIGURATION_VIEW/MANAGE, DELEGATION_VIEW/MANAGE | `expense_categories`, `expense_subcategories`, `expense_natures`, `expense_request_types`, `expense_delegation_rules`, `delegation_approver_steps` | ✅ | OD-01 (EC level mapping) and OD-03 (standalone routes) must be configured per bank; OD-06 (HOD threshold) resolved to single nullable threshold. |
| FM-08 | EC member pool / committees / approval levels | ✅ (committees); 🟡 (EC pool reconciliation) | `/committees/*` | COMMITTEE_VIEW/MANAGE | `expense_committees`, `committee_levels`, `committee_members`, `committee_level_members` | ✅ | OD-17 (legacy EC pool vs committee tables reconciliation) open. |
| FM-09 | Currency (display/default) | ✅ | base-currency on institution profile; ISO-4217 validation | CURRENCY_VIEW/MANAGE, CONFIGURATION_MANAGE | `currencies` | ✅ | |
| FM-10 | Department Head (HOD) limit | ✅ | `/approval-limits/*` | APPROVAL_LIMIT_VIEW/MANAGE | `department_approval_limits` | ✅ | Single nullable threshold; documented fallback per OD-06. |
| FM-11 | Recommending authorities | ✅ | `/recommending-authorities/*` | RECOMMENDING_AUTHORITY_VIEW/MANAGE | `recommending_authorities` | ✅ | |
| FM-12 | Workflow engine & approval routing | ✅ | `/workflows/*`, `/approvals/*` | WORKFLOW_CONFIGURATION_VIEW/MANAGE, REQUEST_APPROVE | `workflow_definitions`, `workflow_versions`, `workflow_states`, `workflow_transitions`, `workflow_steps`, `workflow_step_approvers`, `workflow_instances`, `workflow_tasks`, `workflow_actions` | ✅ | OD-02 routing table, OD-04 CFO actions, OD-09 state machine, OD-10 selective revert must be finalized; current engine supports the generic model but ships no pre-seeded CEO/CFO route. |
| FM-13 | Exceeding-limit rule | ✅ (stored/configurable) | `/exceeding-limit-rules/*` | EXCEEDING_LIMIT_VIEW/MANAGE | `exceeding_limit_rules` | ✅ | OD-05: currently warn-only (`exceeding-limit-enforced-in-routing: false`). |

## 3. Acceptance criteria (AC-01 … AC-14)

| Ref | BRD acceptance criterion | Status | Evidence | Notes |
|---|---|---|---|---|
| AC-01 | All endpoints authenticated except explicit public list | ✅ | Deny-by-default Spring Security chain; public endpoints enumerated in `SecurityConfiguration`; architecture test enforces `@EdamPermission/@PublicEndpoint/@AuthenticatedEndpoint` on every handler | |
| AC-02 | CSRF protection on mutating endpoints | ✅ | `CookieCsrfTokenRepository` with `XSRF-TOKEN`; `X-XSRF-TOKEN` required for POST/PUT/PATCH/DELETE | Session cookie + double-submit. |
| AC-03 | Server-side authorization for every operation | ✅ | `@EnableMethodSecurity` with `@EdamPermission`; service-layer object visibility | Frontend must never be authoritative. |
| AC-04 | Secure password storage & policy | ✅ | BCrypt password hashing; password policy validator; login-attempt lockout | |
| AC-05 | Validation of inputs (Bean Validation + business rules) | ✅ | `@Valid` on DTOs; service-layer invariants; `VALIDATION_ERROR` envelope | |
| AC-06 | No stack traces / internal details in error responses | ✅ | `GlobalExceptionHandler`; `ApiResponse` envelope; redaction utilities | |
| AC-07 | Correlation id on every request/response | ✅ | `CorrelationIdFilter`; MDC logging; `ApiResponse.correlationId` | |
| AC-08 | Consistent response envelope (success/data/error) | ✅ | `ApiResponse<T>` | ADR-0005. |
| AC-09 | Transactional integrity for multi-step operations | ✅ | Service `@Transactional`; REQUIRES_NEW split for external calls; optimistic locking (`@Version`) on request/task/outbox/version/integration entities | |
| AC-10 | Audit logging of important changes | ✅ | `audit_events` + trigger; JPA auditing base class; `AuditController`; request timeline | |
| AC-11 | Optimistic locking prevents concurrent edits | ✅ | `@Version` on critical entities; `CONCURRENT_MODIFICATION` → 409 | |
| AC-12 | Multi-tenant isolation seam (not full tenancy) | 🟡 | `EdamPrincipal.institutionRoles`; data scopes; ADR-0002 | OD-20: actual multi-tenant schema not implemented. |
| AC-13 | Institution branding (name/logo/colors) configurable without code change | ✅ | `branding_themes` + `theme_tokens`; public `/configuration/theme`; logo via asset upload | No arbitrary CSS/JS allowed. |
| AC-14 | Health check endpoint | ✅ | `/actuator/health` (prod: status-only; public) and `/api/v1/system/info` (authenticated; returns app name/version/status) | No DB/host details leaked in prod. |

## 4. Technical & security requirements

| BRD topic | Status | Evidence |
|---|---|---|
| Java 21 + Spring Boot + PostgreSQL | ✅ | `pom.xml`, CI runs against Postgres 18-alpine; `ddl-auto=validate`. |
| Flyway forward-only migrations | ✅ | V1→V27; no backward migrations in source; Hibernate `ddl-auto=validate`. |
| No hard-coded institution constants | ✅ | Institution name/logo/base-currency from `organization_profile` / branding. |
| No sensitive data in logs | ✅ | `AuditValueRedactor`, `DiagnosticRedactor`; access log without query strings/bodies; redacted SMTP/integration diagnostics. |
| Encryption of secrets at rest | ✅ | AES-256-GCM for SMTP and integration credentials; env-sourced keys; fail-closed. |
| SSRF protection | ✅ | `SafeDestination.requireSafeHttps` blocks loopback/private/link-local/IMDS; applied to integration endpoint config. |
| Malware-scan fail-closed | ✅ | Documents downloadable only when `SCAN_STATUS=CLEAN`; `FakeMalwareScanner` in tests only. |
| CORS exact-origin | ✅ | `edam.cors.allowed-origins`; credentials only with exact match. |
| Error dispatch not authorized | ✅ | ADR-0007; `/error` whitelisted unauthenticated. |

## 5. Items not implemented (tracked as open decisions)

| Capability | Reason / owning decision |
|---|---|
| SSO / AD / OIDC / SAML authentication | OD-11 |
| Tenant-per-schema or per-database isolation | OD-20 |
| PKR conversion arithmetic and rounding | OD-07 |
| CAPEX variance formulas | OD-08 |
| CAPEX finance-field owner (requester vs finance approver) | OD-13 |
| Full CEO/CFO/HOD routing pre-seed | OD-02, OD-03 |
| CFO action set (approve/reject/revert/forward/remarks) | OD-04 |
| Selective-revert eligibility | OD-10 |
| Print/PDF and XLSX export beyond CSV | OD-22 |
| Production-grade malware scanner provider | OD-14 |
| Legacy data migration | OD-18 |
| Full email event matrix / SMS / push channels | OD-15 |
| HR/master-data sync adapter | OD-16 |
| Legacy EC pool table reconciliation | OD-17 |
| Numeric NFRs (performance, DR targets) | OD-19 |

## 6. Tests & verification at the Phase 4J baseline

- Backend test suite: **431 tests, 0 failures, 0 errors** on commit `9cab218` (CI run for Phase 4I PR #21).
- CI jobs green: `build-and-test`, `war-package-smoke-test` (executable WAR boots; `/actuator/health` UP; no DB/host leak), `prod-profile-smoke-test` (prod profile with env credentials).
- Architecture tests enforce layering, annotation coverage and permission-catalogue membership.
- Postman collection (`postman/EDAM.postman_collection.json`) validates as JSON v2.1.0 and covers every endpoint.

## 7. External dependencies that must be provisioned before go-live

- PostgreSQL 15+.
- SMTP server (forgot password, approval notifications).
- Object storage (Cloudinary-compatible) for document upload.
- Malware scanner adapter (ClamAV or SaaS).
- HTTPS reverse proxy with proper `X-Forwarded-Proto` and exact-origin CORS.
- Secure provisioning of `EDAM_BOOTSTRAP_TOKEN`, `EDAM_MAIL_MASTER_KEY`, `EDAM_INTEGRATION_MASTER_KEY`.

See `production-readiness.md` for the complete checklist.
