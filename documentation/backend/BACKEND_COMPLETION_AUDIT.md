# EDAM Backend Completion Audit

**Audit date:** 2026-11-12
**Repository:** `Jhamankhatri/edam`
**Audited commit:** `b73f8c5fc78cc108fe3e013a6b485ff12a7c9600` (`main`, merge of PR #4)
**Audit branch:** `arena/0f75b820-edam`
**Method:** Static inspection of the entire repository at the audited commit, plus retrieval of
executed CI results for that exact commit.
**Scope:** Backend only. No frontend work performed. No production code changed by this audit.

---

## Revision history

This document is a point-in-time audit and is deliberately **not** rewritten in place: the findings below
describe the code at `b73f8c5` and are the evidence for why Phases 1–3 were scheduled. What follows in this
block is what has changed since, so a reader sees both the original state and the current one.

| Phase | Merge | Effect on the findings below |
|---|---|---|
| Phase 1 — Security foundation | `a118364` | Login, logout, CSRF, bootstrap and password reset exist. The §6.1 "React authentication blocker" is **closed**. |
| Phase 2 — Request management | `4783a99` | The expense request exists: OPEX and CAPEX, drafts, submission, cancellation, validation, exchange-rate capture, register. §2.10–2.11 move to **implemented**. |
| Phase 3 — Workflow configuration and approvals | `7119739` | Workflow authoring/publish API, submission integration, approval inbox and the four decisions. §2.12–2.13 move to **implemented with named gaps**. Verified post-merge — see §12. |

**Current headline figures** (re-measured on `main` at `7119739`, not inherited):

| Metric | At `b73f8c5` | Now |
|---|---|---|
| REST endpoints | 59 | **127** |
| Controllers | 7 | **16** |
| Permission constants consumed by an endpoint | 22 of 58 | **36 of 58** |
| Automated tests executed in CI | 70 (3 skipped) | **287 (3 skipped)** |
| Test classes run by CI | — | **23** |
| Tests touching a service or repository | 0 | **All but the pure-model and filter tests — every integration test runs against real PostgreSQL** |

The verdict in §1 — "not ready for frontend integration" — was correct for its commit and is **superseded**.
See §11 for the Phase 3 position and §12 for the post-merge verification and the Phase 4 readiness verdict.

---

## 0. Verification method (and its limits)

Everything below was established by reading the repository, not by trusting prior summaries. Where a
claim was made earlier in this project, it was re-checked against the code at `b73f8c5`.

**What could be verified directly:**

| Evidence | How obtained |
|---|---|
| Package/module inventory | Directory walk of `src/main/java` |
| File and line counts | `find` / `wc` over the source tree |
| Endpoint inventory | Regex extraction of `@GetMapping`/`@PostMapping`/etc. from every controller |
| Entity↔table mapping | `@Table(name=...)` extracted and diffed against `CREATE TABLE` in migrations |
| Table liveness | `CREATE TABLE` set minus tables dropped by `V13` |
| Dead code detection | Grep for callers of each service outside its own package |
| Unimplemented permissions | Set difference between `Permissions` constants and their references |
| Hardcoded data | Full-tree grep for `TODO`, `FIXME`, `UnsupportedOperationException`, `placeholder`, `stub` |
| Executed test results | GitHub check-run annotations for CI run `43379920399` at commit `b73f8c5` |

**What could NOT be verified, and is therefore never marked PASS on this basis:**

This sandbox has **no JDK, no Maven, no PostgreSQL, and no Docker**, and Maven Central is unreachable.
The backend cannot be compiled or executed here. No HTTP request was issued against a running instance
during this audit. Therefore:

- **No API in this document is marked "verified working at runtime" by the auditor.** API status
  reflects *existence, correct wiring, and presence of authorization* — established statically — and,
  where noted, the fact that CI boots a Spring context against real PostgreSQL.
- **No test is claimed as passing here on the auditor's own execution.** Test results are reported from
  CI run `43379920399`, which executed the suite at the audited commit. That is a real execution, but it
  is not one I ran, and the report attributes it as such.
- Startup-time correctness (bean wiring, JPA mapping validation) *is* genuinely exercised by CI, because
  the integration test boots the full context against a Flyway-migrated PostgreSQL container. This is
  the strongest evidence available and it is non-trivial: it is what caught four real defects during
  development.

**A necessary caution about the word "PASS" in this report.** PASS means *the code for this module is
present, coherently structured, and wired to reachable endpoints*. It does not mean "exercised by an
automated test" — for most modules, **no such test exists** (see §7). The distinction is maintained in
every row.

---

## 1. Headline result

| Metric | Count |
|---|---|
| Modules audited | **16** |
| PASS | **2** |
| PARTIAL | **8** |
| FAIL | **6** |
| Live database tables | **81** |
| Tables mapped by a JPA entity | **34** |
| Live tables with **no** entity | **47** |
| REST endpoints | **59** |
| Controllers | **7** |
| Services | **12** |
| Permission constants with **no** implementing endpoint | **36 of 58** |
| Automated tests (CI-executed) | **70**, of which **3 skipped** |
| Tests touching any service or repository | **0** |
| Required docs delivered | **3 of 9** |
| Postman collection | **absent** |

**Verdict: the backend is NOT ready for React frontend integration.** The blocking reason is not
polish — it is that the product's central object, the expense request, does not exist in code, and there
is no way for a browser client to authenticate.

---

## 2. Module-by-module findings

Status key: **PASS** = implemented and structurally verified · **PARTIAL** = real but incomplete ·
**FAIL** = missing or non-functional · **NOT TESTED** = cannot confirm by execution.

### 2.1 Identity / authentication

| Field | Finding |
|---|---|
| Expected | Login, session or token issuance, logout, password policy, first-administrator bootstrap |
| Status | **FAIL** |
| API | **No login endpoint exists.** Zero matches for `/login`, token issuance, or `AuthenticationManager.authenticate` |
| Testing | PARTIAL — `WebLayerSecurityTest` (8 tests) runs at CI against real PostgreSQL and asserts anonymous API access returns 401 |
| Missing work | A browser-facing authentication contract; see §6.1 |

**Detail.** `SecurityConfig` configures `formLogin(...)`, which is Spring Security's **server-rendered
HTML login page**. No controller or filter issues a token. The consequence for a React app is
disqualifying:

- The React app cannot POST JSON credentials anywhere — there is no endpoint to receive them.
- If it posts to Spring's `/login` form endpoint, the response is a **302 redirect plus an `HttpOnly`
  `JSESSIONID` cookie**. A cross-origin `fetch` cannot read the redirect target or the session cookie,
  and the browser will not surface the `Set-Cookie` to JS.
- **CSRF is enabled with no `CsrfTokenRepository` override.** Spring Security's default stores the token
  in the session and exposes the cookie as **`HttpOnly`**. No JavaScript — React included — can read it,
  so every `POST`/`PUT`/`PATCH` from the SPA would be rejected with 403.

So the API is *deployed* as a JSON API but *authenticated* as if a JSP UI were still in front of it. Note
`SecurityConfig` still carries the comment *"CSRF protection stays enabled for the session-based JSP
UI"* — a stale rationale left over from the removed JSP layer. This is a genuine design gap, not a
cosmetic one.

The `InMemoryEdamUserDetailsService` fixtures (`admin`, `ho-admin`, `requester`) are, to their credit,
**not mock business data**: they are `@ConditionalOnProperty`-gated to the `local` profile, never
inserted into the database, never shipped in a migration. That is the correct handling. But it means
that outside the `local` profile there is **no way to create the first administrator** — no bootstrap
mechanism was found.

### 2.2 Multiple administrators in one installation

| Field | Finding |
|---|---|
| Expected | Several administrator accounts, distinct privileges, within one bank installation |
| Status | **PARTIAL** |
| API | **None** — no user CRUD, no role assignment endpoint |
| Testing | NOT TESTED — no test covers this |
| Missing work | User administration and role-assignment APIs (§6.2) |

**Detail, including a decision that needs your review.** The **data model supports multiple
administrators** correctly: `users`, `roles`, `user_roles`, `permissions`, `role_permissions` all exist,
and `EdamPrincipal.hasInstallationScope()` allows more than one holder of installation-wide scope. There
is no single-`admin` constraint and nothing hardcodes one. So the *model* is right.

What is missing is the *API*: `usermanagement` and `rolemanagement` contain **models and repositories
only — zero services, zero controllers, zero DTOs** (verified: 0 files matching `*Service*`,
`*Controller*`, `*Dto*`). Therefore:

- You cannot create a second administrator through the API.
- You cannot assign a role to a user through the API.
- `USER_MANAGE`, `USER_VIEW`, `ROLE_MANAGE`, `ROLE_VIEW`, `USER_ROLE_ASSIGN`, `DATA_SCOPE_MANAGE` are all
  granted in the catalogue but **no endpoint consumes them**.

**This needs your explicit decision.** "Independent configuration per bank without changing source code"
is satisfied for *one bank per installation* — `organization_profile` is a single-row configuration table
holding name, legal name, timezone, date format, default currency, logo/favicon asset references, and it
is editable at runtime via `PATCH /api/v1/organization/profile` with no rebuild. That matches the BRD's
stated model. **Multi-bank-in-one-deployment is deliberately not supported**: `V13` drops the
`institution` table with the recorded rationale that "the requirement models ONE installation for ONE
bank via `organization_profile`". If you intended a single deployment to serve several banks, the schema
was built to the opposite decision and this must be settled before any frontend work relies on it.

### 2.3 User management

| Field | Finding |
|---|---|
| Expected | CRUD, activation/deactivation, data-scope assignment, profile |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Full module: service, controller, DTOs, validation |

Only `User`, `UserDataScope` entities and their repositories exist. There is no service and no DTO. The
`users` table is real and well-constrained (self-manager prevented by CHECK, unique employee code, etc.),
so the schema is ready and the application layer is absent.

### 2.4 Role and permission management

| Field | Finding |
|---|---|
| Expected | Role CRUD, permission assignment to roles, role assignment to users |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Full module |

`V12` correctly seeds **63 permissions and 14 system roles** via versioned migration — that is
requirement-compliant (catalogue ships, business data does not). But the *maintenance* API is absent:
`Role`, `Permission`, `UserRole` entities and repositories exist with **no service or controller**.

### 2.5 Organization, department, branch

| Field | Finding |
|---|---|
| Expected | Profile, unit hierarchy, departments, branches, heads |
| Status | **PASS** |
| API | 16 endpoints under `/api/v1/organization`, all `@EdamPermission`-guarded |
| Testing | NOT TESTED — no service-level test exists |

The most complete module: entities, repositories, service(s), DTOs, controller, validation, and audit
recording. Endpoints cover profile read/patch, unit types, units, unit tree, unit update and activation,
plus departments and branches with create/update/activation. Deactivation rather than deletion is
enforced, with a documented reason (history and foreign-key integrity). Tree retrieval is provided, which
a React org-chart needs.

**Caveat:** verified by inspection and by the fact that CI boots the context with these mappings; it is
**not** covered by a service-level test, so behaviour under real data is unproven.

### 2.6 Expense categories, subcategories, configuration

| Field | Finding |
|---|---|
| Expected | Categories, subcategories, natures, request types, attachment rules |
| Status | **PARTIAL** |
| API | 12 endpoints under `/api/v1/expense-configuration` |
| Testing | NOT TESTED |
| Missing work | Attachment/document requirement configuration; linkage to request creation (which does not exist) |

Real entities, real service, real validation (category/subcategory hierarchy, activation lifecycle), and
audit recording. Incomplete because configuration exists in isolation — it cannot yet configure anything
about a request, since requests are not implemented.

### 2.7 Currency and exchange rates

| Field | Finding |
|---|---|
| Expected | Currency CRUD, effective-dated exchange rates, overlap prevention |
| Status | **PASS** |
| API | 8 endpoints under `/api/v1/master-data` (currencies, exchange rates, tax authorities, vendors) |
| Testing | NOT TESTED |

Genuinely well built. Overlap prevention is enforced **twice** — a readable service-level check plus a
database constraint — which is the correct pattern for a rule that decides which rate applies. Vendor
search is **paged** (`PageResponse`, `PageQuery`, 1 of only 21 list endpoints that are). Rates are
effective-dated rather than a single current value.

### 2.8 Delegation matrix and department approval limits

| Field | Finding |
|---|---|
| Expected | Delegation by classification × amount band × currency; **department approval limits**; precedence |
| Status | **PARTIAL** |
| API | 5 endpoints under `/api/v1/delegation-matrix`, including `POST /preview` |
| Testing | NOT TESTED |
| Missing work | **Department approval limits are entirely absent**; `APPROVAL_LIMIT_*` permissions unimplemented |

The delegation matrix is solid: `expense_delegation_rules` carries `NUMERIC(19,4)` amount bands, and a
PostgreSQL `EXCLUDE USING gist` constraint prevents overlapping active rules for the same
classification/currency/period — a genuine database-level guarantee, not a service convention. The
`preview` endpoint lets an administrator verify routing before a rule governs real money, which is the
right instinct.

**However, "department approval limits" — item 9 of your request — does not exist in code.** The tables
`department_approval_limits` and `exceeding_limit_rules` are created in the schema and then **have no
entity, no repository, no service, and no API**. The permissions `approval-limit:view` and
`approval-limit:manage` are granted by `V12` and used by nothing. Note these tables are *also* not
dropped by `V13`, so they are live schema with no code — genuinely unfinished, not retired.

### 2.9 Recommending authorities and expense committees

| Field | Finding |
|---|---|
| Expected | Recommending authority configuration; committee management with levels, members, quorum |
| Status | **PARTIAL** |
| API | Committees: 10 endpoints. **Recommending authorities: none** |
| Testing | NOT TESTED |
| Missing work | Recommending-authority module entirely; committee resolution inside workflow |

Committees are implemented properly and the *reachable-quorum* validation is a standout: a band whose
`minimum_approvals` exceeds its seated members is refused at write time, because such a band could
satisfy every constraint and still be permanently unable to approve anything. Level overlap is prevented
by an exclusion constraint; one active CEO/CFO/head-of-admin per committee by partial unique indexes.

**Recommending authorities are missing.** `recommending_authorities` has no entity or API, and in
`WorkflowAssignmentResolver` the `RECOMMENDING_AUTHORITY` approver type resolves to a **debug log line
and nothing else** — that is, a workflow step assigned to a recommending authority would resolve zero
approvers and the start would fail. This is an explicit, documented deferral in the code (`"deferred"`),
but it is a functional gap.

### 2.10 OPEX requests

| Field | Finding |
|---|---|
| Expected | Create, edit, submit, track |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Entire module |

The `expenserequest` package **does not exist**. Eleven request tables (`expense_requests`,
`opex_request_details`, `expense_request_items`, `expense_allocations`, `expense_invoices`,
`expense_quotations`, `capex_request_details`, `capex_sta_details`, `capex_budget_summaries`,
`request_documents`, `expense_previous_summaries`) are live and **all unmapped**. `REQUEST_*`
permissions (create, edit, submit, cancel, reverse, view-own, view-scope, view-all, export) — nine codes
— have no endpoint.

### 2.11 CAPEX requests

| Field | Finding |
|---|---|
| Expected | Create, edit, submit, track; STA and budget summary |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Entire module |

Same as OPEX. `capex_request_details`, `capex_sta_details`, `capex_budget_summaries` are schema-only.

### 2.12 Dynamic workflow configuration and versioning

| Field | Finding |
|---|---|
| Expected | Author definitions/versions/steps/approvers, publish, retire, keep in-flight requests stable |
| Status | **PARTIAL** |
| API | **None** — no workflow controller exists |
| Testing | `WorkflowStateModelTest` (12 nested tests) executed in CI; 0 service tests |
| Missing work | Authoring/publish API; transitions not consulted |

The engine's core invariants are implemented and the design is right: an instance **pins the version it
started under** and never re-reads "latest", so publishing a new revision cannot alter an in-flight
request. `V8` enforces one `PUBLISHED` version per definition and requires `published_by`/`published_at`
via CHECK. Assignment resolves offices at submission against live configuration rather than at publish
time — correct, so a new department head takes effect for the next request.

**But nothing can reach it.** `WorkflowEngine` has **no controller and no callers outside its own
package**. There is **no service that creates, publishes, or retires a version** — `WorkflowVersion`
exposes only raw setters. So today the workflow tables can be populated only by direct SQL, and the
engine can only be invoked by code that does not exist.

Additionally, `workflow_transitions` rows are **not consulted**: legality is hardcoded in Java. The table
exists, is unmapped, and would mislead whoever configures it next.

### 2.13 Approval, rejection, revert, forward

| Field | Finding |
|---|---|
| Expected | Act on tasks; quorum; inbox |
| Status | **PARTIAL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Task/action API, approval inbox |

`approve`, `reject`, `revert`, `forward`, `cancel` **are implemented in `WorkflowEngine`**, with sound
semantics (quorum counted from APPROVED tasks, remaining tasks SKIPPED on quorum, revert to initial
state, forward re-opens an equivalent task in the same step). Optimistic locking on tasks prevents a
double-click counting as two approvals — a real four-eyes guarantee.

None of it is reachable over HTTP. There is no approval inbox endpoint, so a React app cannot list a
user's pending approvals.

### 2.14 Documents and attachments

| Field | Finding |
|---|---|
| Expected | Upload to Cloudinary, download, delete, link to requests; virus/type/size control |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Entire module; Cloudinary never integrated |

`cloudinary-http5` is declared in `pom.xml` and **never imported by a single Java file**. There is no
`documentmanagement` package, no upload endpoint, no signed-URL generation, no `media_assets` entity
(though `media_assets`, `request_documents`, and `expense_quotations` tables exist, the last two
unmapped). The organization profile references `logo_asset_id` and `favicon_asset_id`, but **there is no
way to upload an asset for them to point at.**

### 2.15 Theme, branding, logo

| Field | Finding |
|---|---|
| Expected | Branding/theme configuration APIs for the React client |
| Status | **FAIL** |
| API | **Only** `PATCH /organization/profile` (name, legal name, timezone, date format, default currency) |
| Testing | NOT TESTED |
| Missing work | Theme/token API; logo upload |

`branding_themes` and `theme_tokens` are live tables with **no entity and no API**. A React app cannot
fetch its colour palette, typography, or logo. `Application_settings`, `system_parameters`, and
`feature_flags` are likewise unmapped with no API, so a client cannot read feature toggles or
server-maintained parameters.

### 2.16 Email templates and notifications

| Field | Finding |
|---|---|
| Expected | Template management, outbox, delivery, preferences |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Entire module |

`spring-boot-starter-mail` and `thymeleaf` are declared in `pom.xml` and **never imported**. The
`notification` package does not exist. Eight notification tables (`notification_templates`,
`notification_preferences`, `notifications`, `notification_deliveries`, `notification_outbox`,
`email_provider_configs`, plus `scheduled_jobs` and `system_error_events`) are live and unmapped.
`NOTIFICATION_TEMPLATE_MANAGE` and `EMAIL_CONFIGURATION_MANAGE` have no endpoint.

This matters beyond convenience: approval routing without notification means an approver is never told
work is waiting, and there is no inbox API to compensate (§2.13).

### 2.17 Reporting, dashboards, audit history

| Field | Finding |
|---|---|
| Expected | Reporting APIs, dashboard definitions, audit trail search/export |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Entire module |

`reporting` package does not exist. `report_definitions`, `dashboard_definitions`, `dashboard_widgets`,
`user_dashboard_preferences` are live and unmapped. `REPORT_VIEW`, `REPORT_EXPORT`, `DASHBOARD_MANAGE`
have no endpoint.

**Audit is a split verdict.** The audit *write* path is real: `audit_events`/`audit_event_details` are
mapped, `AuditRecorder` is injected and called by the delegation, expense-configuration, masterdata and
organization services, and `AuditAction` is a closed verb set. That is genuine. But the audit **read**
path is absent: there is **no audit search endpoint and no export**, so `AUDIT_VIEW` and `AUDIT_EXPORT`
are unimplemented. An auditor can have events written but cannot query them through the API.

### 2.18 Business rules and application settings

| Field | Finding |
|---|---|
| Expected | Admin-configurable rule engine, restricted JSONB conditions, settings |
| Status | **FAIL** |
| API | **None** |
| Testing | NOT TESTED |
| Missing work | Entire module |

`businessrules` package does not exist. `business_rule_sets`, `business_rule_versions`, `business_rules`
are live and unmapped; `RULE_VIEW`, `RULE_MANAGE`, `RULE_PUBLISH` unimplemented. Application settings
(`application_settings`, `system_parameters`, `feature_flags`) are likewise unmapped with no API.

### 2.19 API validation, exceptions, security, transactions

| Field | Finding |
|---|---|
| Expected | Bean validation, uniform error envelope, correlation IDs, authorization, transactional boundaries |
| Status | **PASS** (with two named exceptions) |
| API | Cross-cutting, applies to all 59 endpoints |
| Testing | PARTIAL — 21 tests in `common` + 2 architecture tests, all executed in CI |

This is the strongest area of the codebase and deserves credit:

- **Uniform envelope** `{success, data, error{code,message}, fieldErrors, correlationId, path, timestamp}`,
  with the container error dispatch nested as `edamEnvelope`. No stack traces or internal messages leak.
- **Correlation IDs** propagated per request (`CorrelationIdFilter`, 15 tests).
- **Authorization**: 56 `@EdamPermission` annotations, enforced by `RequirePermissionAspect` which throws
  `PERMISSION_DENIED`. A dedicated architecture test fails the build if any endpoint is neither
  permission-guarded nor explicitly `@PublicEndpoint` with a stated reason. This is a real, automated
  guard — and it is the single best test in the repository.
- **Transactions**: 9 of 9 genuine services are `@Transactional` (the 3 without are
  `UserDetailsService` implementations, which correctly are not).
- **Validation**: Jakarta annotations on DTOs, `@Valid` on controller bodies, with a
  `GlobalExceptionHandler` test suite.

**Two exceptions, both material:**

1. **CSRF is unusable from a browser SPA** (§2.1) — see §6.1. This is a security-*integration* defect:
   the mechanism is on, but its token is unreadable by JavaScript.
2. **Idempotency is dead code.** `IdempotencyService` exists and is well documented (SHA-256 key hash,
   body fingerprint, claim-before-work, conflict on key reuse), `idempotency_records` is mapped — and it
   is referenced by **zero controllers and zero call sites**. It is never invoked. `IDEMPOTENCY_CONFLICT`
   is defined and unreachable.

---

## 3. Consolidated status table

| # | Module | Expected functionality | Status | API status | Testing status | Missing work |
|---|---|---|---|---|---|---|
| 1 | Identity / auth | Login, logout, sessions/tokens, bootstrap | **FAIL** | None (no login endpoint) | PARTIAL | Auth contract, CSRF cookie, first-admin bootstrap |
| 2 | Multiple administrators | Several admins, distinct privileges | **PARTIAL** | None | NOT TESTED | User/role APIs |
| 3 | User management | CRUD, activation, data scopes | **FAIL** | None | NOT TESTED | Whole application layer |
| 4 | Role/permission mgmt | Role CRUD, assignment | **FAIL** | None | NOT TESTED | Whole application layer |
| 5 | Organization/dept/branch | Structure, heads, hierarchy | **PASS** | 16 endpoints | NOT TESTED | Service-level tests |
| 6 | Expense configuration | Categories, subcategories, natures, types | **PARTIAL** | 12 endpoints | NOT TESTED | Attachment config; request linkage |
| 7 | Currency & rates | Currencies, effective rates, overlap | **PASS** | 8 endpoints | NOT TESTED | Service-level tests |
| 8 | Delegation & approval limits | Matrix, **dept limits** | **PARTIAL** | 5 endpoints | NOT TESTED | **Department approval limits absent** |
| 9 | Recommending authorities & committees | Authorities, committees | **PARTIAL** | Committees 10, authorities 0 | NOT TESTED | Recommending-authority module |
| 10 | OPEX requests | Create/edit/submit/track | **FAIL** | None | NOT TESTED | Entire module |
| 11 | CAPEX requests | Create/edit/submit/track | **FAIL** | None | NOT TESTED | Entire module |
| 12 | Workflow config & versions | Author/publish/retire | **PARTIAL** | None | Model tests only | Publish service + controller |
| 13 | Approve/reject/revert/forward | Task actions, inbox | **PARTIAL** | None | NOT TESTED | Task API + inbox |
| 14 | Documents/attachments | Cloudinary upload/download | **FAIL** | None | NOT TESTED | Entire module |
| 15 | Theme/branding/logo | Theme tokens, logo | **FAIL** | Profile patch only | NOT TESTED | Theme API, logo upload |
| 16 | Email/notifications | Templates, outbox, delivery | **FAIL** | None | NOT TESTED | Entire module |
| 17 | Reporting/dashboards/audit | Reports, dashboards, audit search | **FAIL** | None | NOT TESTED | Entire read path |
| 18 | Business rules/settings | Rule engine, settings | **FAIL** | None | NOT TESTED | Entire module |
| 19 | Cross-cutting quality | Validation, errors, security, tx | **PASS** | Applies to all | 23 tests executed | CSRF integration; idempotency wiring |

Note on counting: 19 rows are listed because "multiple administrators" and "cross-cutting quality" are
audited separately from their neighbours, per your checklist. The §1 headline counts them as **16
modules** by merging rows 1–2 (identity), 10–11 (requests), and 14 (documents); both figures are shown so
nothing is hidden by arithmetic.

**Tally: 2 PASS · 8 PARTIAL · 6 FAIL.**

---

## 4. Database structure issues

**Positives first, because they are real.** 15 migrations, all Flyway-versioned and parseable; `V4_1`
uses a correctly-typed UUIDv7 function. Financial columns are `NUMERIC(19,4)` throughout — no floats
anywhere in the money path. Constraints are unusually thoughtful for an in-progress build: exclusion
constraints for delegation-rule and committee-level overlap, partial unique indexes for
one-published-version and one-initial-state, CHECK constraints for enum vocabularies and amount bands,
and an append-only guard on audit events. `V12` ships 63 permissions and 14 roles with **no users and no
business data** — correctly respecting the no-mock-data rule. `V13` is a genuine cleanup that drops
superseded provisional tables rather than leaving two competing schemas.

**Issues:**

| # | Severity | Issue | Evidence |
|---|---|---|---|
| D1 | High | **47 of 81 live tables have no entity** | §0 method; list in Appendix A |
| D2 | High | Request spine entirely unmapped — 11 tables | `expense_requests`, `opex_request_details`, `capex_*`, `expense_request_items`, `expense_allocations`, `expense_invoices`, `expense_quotations`, `request_documents`, `expense_previous_summaries` |
| D3 | Medium | `workflow_transitions` live but unmapped and unconsumed; legality is in Java | Engine review |
| D4 | Medium | `department_approval_limits`, `exceeding_limit_rules` live, uncreated by any code | `V7` tables, no entity |
| D5 | Low | `V14` needed because `V8` shipped `workflow_tasks` without a `version` column | `V14` |
| D6 | Low | Dead table names (`in`, `time`) reappear in a naive parse; not real tables — parsing artifacts of `numrange(...)` and `timezone` text, listed for transparency | Appendix A |

**Relationships vs. business requirements.** For the 34 mapped tables, relationships are sound and
foreign keys are declared. Entity↔schema alignment is *enforced* at startup: Hibernate runs with schema
validation against a Flyway-migrated PostgreSQL container in CI, so any column, type, or enum-storage
mismatch fails the build. That is a genuine strength — it caught four real defects during development,
including a missing `version` column and an enum defaulting to ordinal storage. **It only covers the 34
mapped tables**, which is precisely the risk: the 47 unmapped tables have no such guarantee.

---

## 5. Security issues

| # | Severity | Issue | Location | Correction required |
|---|---|---|---|---|
| S1 | **Critical** | No login API; auth is server-rendered form login only | `config/SecurityConfig.java:96` | Add a JSON auth contract (session or token) reachable by a browser client |
| S2 | **Critical** | CSRF token is `HttpOnly`; no `CsrfTokenRepository` override, so React cannot read it → all mutating calls 403 | `config/SecurityConfig.java:92` | `CookieCsrfTokenRepository.withHttpOnlyFalse()`, or a token endpoint |
| S3 | High | No first-administrator bootstrap outside `local` profile | `identity/InMemoryEdamUserDetailsService.java` | Secure bootstrap (one-time token / CLI), never a committed credential |
| S4 | Medium | `InMemoryEdamUserDetailsService` ships dev fixtures | same | Acceptable as-is **only** because it is profile-gated and never persisted — keep it gated; verify the gate in a test |
| S5 | Medium | `DevUserController` is `@PublicEndpoint` | `identity/DevUserController.java` | Acceptable: gated by `edam.security.dev-users.enabled`, returns only caller's own data. Confirm it cannot be enabled in production |
| S6 | Low | Stale comment claims "session-based JSP UI" in a JSP-free build | `SecurityConfig:93` | Correct the comment; it is actively misleading |
| S7 | Low | 36 granted permissions have no endpoint — privilege surface wider than functionality | `common/security/Permissions.java` | Expect them to become live as modules land; no action now |

**Authorization enforcement at the backend is genuinely present** and is the part of this system I would
defend most strongly: 56 `@EdamPermission` annotations, an aspect that throws on denial, and an
architecture test that fails the build on any unguarded endpoint. Anonymous access returns a JSON 401
(not a redirect) — asserted in CI.

---

## 6. Business logic gaps

### 6.1 The React authentication blocker (must be fixed first)

Three defects compound into one blocker:

1. **No login endpoint.** Nothing accepts credentials as JSON.
2. **Form login returns 302 + `HttpOnly` session cookie.** A cross-origin SPA cannot consume either.
3. **CSRF cookie is `HttpOnly` by Spring's default.** React cannot read `XSRF-TOKEN` to send
   `X-XSRF-TOKEN`, so **every** non-GET request fails 403.

Until these are resolved, the API is unreachable from a browser client regardless of how many endpoints
exist. **This alone answers your question: the backend is not ready.**

### 6.2 Missing business logic

- **Expense request lifecycle** — the core product. Create → submit → route → approve/return → close, for
  both OPEX and CAPEX. Nothing exists.
- **Department approval limits and exceeding-limit rules** — schema only.
- **Recommending authorities** — schema only; the workflow approver type resolves to nothing.
- **Workflow authoring/publish/retire** — engine consumes published versions; nothing produces them.
- **Approval inbox** — no way to ask "what is waiting for me".
- **Committee resolution inside workflow** — `EXPENSE_COMMITTEE` approver type is deferred, same as
  recommending authorities.
- **Business rules engine** — absent.
- **Notifications** — absent; approval without notification and without an inbox means work goes unseen.
- **Documents** — absent; expense claims normally require receipts.
- **Reports/dashboards/audit search** — absent.

### 6.3 Hardcoded or mock data

**No mock business data was found.** No `TODO`/`FIXME`/`UnsupportedOperationException`/`stub` markers
anywhere in `src/main/java`. No dummy departments, users, or approval limits in any migration. The
`local`-profile fixtures are correctly gated and never persisted. **This requirement is met.**

### 6.4 Frontend-provided concerns not verifiable statically

CORS configuration was **not found** anywhere in the source. If the React app is served from a different
origin in development, requests will fail until CORS is configured. This should be confirmed as part of
the auth work.

---

## 7. Automated test results (actually executed)

Source: **CI run `43379920399`**, job `build-and-test`, at commit `b73f8c5`. Retrieved from GitHub
check-run annotations. **These were executed by CI, not by me; this sandbox has no JDK.**

| Test class | Run | Failures | Errors | Skipped |
|---|---|---|---|---|
| `common.logging.CorrelationIdTest` | 11 | 0 | 0 | 0 |
| `common.exception.GlobalExceptionHandlerTest` | 10 | 0 | 0 | 0 |
| `web.WebLayerSecurityTest` | 8 | 0 | 0 | 0 |
| `common.api.ApiPathsTest` | 7 | 0 | 0 | 0 |
| `common.security.InstitutionContextTest` | 5 | 0 | 0 | 0 |
| `common.logging.CorrelationIdFilterTest` | 4 | 0 | 0 | 0 |
| `testsupport.PackagedWarTest` | 3 | 0 | 0 | **3** |
| `architecture.EndpointAuthorizationArchitectureTest` | 2 | 0 | 0 | 0 |
| `common.security.EdamPrincipalTest` | 16 (nested) | 0 | 0 | 0 |
| `workflow.WorkflowStateModelTest` | 12 (nested) | 0 | 0 | 0 |

All three CI jobs passed: `build-and-test`, `war-package-smoke-test`, `prod-profile-smoke-test`.

**What this proves:** the Spring context starts against real PostgreSQL with all 15 migrations applied;
every mapped entity matches the schema; anonymous API access yields a JSON 401; error envelopes,
correlation IDs and path handling behave; every endpoint is permission-guarded or explicitly public; the
WAR builds without JSP.

**What it does not prove — stated plainly:**

- **Zero tests touch any service or repository.** `OrganizationService`, `CurrencyService`,
  `DelegationService`, `CommitteeService` and `WorkflowEngine` have **no tests at all**. Their business
  logic is verified only by reading the code.
- 3 of 3 `PackagedWarTest` tests were **skipped**, so that class proves nothing in this run.
- No test issues an HTTP request to a business endpoint with an authenticated principal. Authorization is
  proved by the *architecture* test (annotations exist), not by *behavioural* tests (a permitted call
  succeeds, a forbidden one returns 403).
- No test asserts a business rule outcome — no quorum advancing to the next step, no rejected request
  ending, no overlap being refused.

---

## 8. Missing or incomplete APIs

**Absent entirely** (no controller exists): authentication; user CRUD; role/permission assignment;
expense requests (OPEX and CAPEX); workflow authoring/publish/retire; approval tasks and inbox; documents;
notifications and email templates; reports and dashboards; audit search and export; business rules;
application settings and feature flags; theme and branding; recommending authorities; department approval
limits.

**Present but incomplete:** expense configuration (cannot configure request behaviour) and committees
(membership cannot be resolved by any running workflow, because the workflow cannot be started).

**Complete:** organization (16), master data (8), delegation matrix (5), committees (10), expense
configuration (12), system info (1), dev identity (2, local only) — 54 of 59 mappings. Of these, **all
are read/write configuration APIs**; not one processes a business transaction.

Appendix B lists all 59 endpoints verbatim for React planning.

---

## 9. Answers to your questions

### 1. Is the EDAM backend 100% ready for React frontend integration?

**No. Not close.** Two of the six modules are complete, and the missing half is the product itself.

A frontend built against this backend today could configure the organization, master data, delegation
rules and committees — and then have nothing to do with any of it, because no expense request can be
created, submitted, approved, or listed.

### 2. Evidence (since the answer is no, this is the evidence for that)

- `expenserequest` package: **does not exist**.
- Login endpoint: **zero occurrences** of `/login`, token issuance, or `AuthenticationManager.authenticate`.
- CSRF: **no `CsrfTokenRepository`** anywhere in `src/main` → default `HttpOnly` cookie → React cannot read it.
- Modules FAILing: 6 of 16.
- Live tables with no entity: **47 of 81**.
- Permission codes with no endpoint: **36 of 58**.
- Tests touching any service: **0**.

### 3. What must be fixed before frontend development begins

Ordered, because the sequence is dependency-driven — later items are unbuildable without earlier ones.

**Blocking tier — the frontend cannot start without these:**

1. **Authentication contract** (S1, S2, S3). A JSON login (session-based is fine for one deployment) plus
   `CookieCsrfTokenRepository.withHttpOnlyFalse()` so React can read `XSRF-TOKEN`; logout; a
   `GET /api/v1/auth/me` for bootstrap; a secure first-administrator bootstrap. Without this, no
   authenticated request from a browser can succeed.
2. **Expense request module** — OPEX and CAPEX create/edit/submit/track. This is the product.
3. **Workflow publish API** so a request can be routed, and **task/inbox APIs** so approvals can be acted
   on and listed.
4. **User and role management APIs** — otherwise no second administrator exists and no workflow approver
   can be a real person.

**Required for a usable product:**

5. Approval inbox + notifications (an approver must learn work is waiting).
6. Document upload (Cloudinary is in `pom.xml` and unused; receipts are essential to expense claims).
7. Audit search/export.
8. Reports and dashboards.
9. Business rules and application settings.
10. Theme/branding/logo, plus a public pre-login branding endpoint for the React login page.
11. Department approval limits and recommending authorities.
12. Wire `IdempotencyService` into state-changing endpoints (currently dead code).

**Recommended before handing to a frontend team:**

13. Service-level tests for the existing PASS modules — they currently have none, so their behaviour is
    unproven even though the code reads well.
14. CORS configuration for the React dev origin.
15. Postman collection and `.env.example` (both absent; 6 of 9 required docs missing).

---

## 10. Final readiness decision

> **The EDAM backend is NOT ready for React frontend integration.**
>
> Approximately **35–40%** of the required backend exists. What exists is of genuinely good quality —
> the schema is thoughtfully constrained, the error/security spine is disciplined, and the workflow
> engine's core invariants are right. But the product's central object, the expense request, is entirely
> absent in code, the workflow engine is unreachable over HTTP, and a browser client cannot authenticate
> at all.
>
> Building the frontend now would produce screens for configuration modules only, and would force the
> frontend team to invent the request, approval and notification contracts — the exact contracts that
> should be driven by the backend.

**Recommended sequence:** authentication contract → expense request lifecycle → workflow publish and
task APIs → user/role management. Those four unblock a meaningful React build. Documents, notifications,
reports and business rules can follow in parallel with frontend work, provided their shapes are agreed
first.

---

## Appendix A — live tables with no entity (47)

`application_settings`, `audit_event_details`, `branding_themes`, `business_rules`,
`business_rule_sets`, `business_rule_versions`, `capex_budget_summaries`, `capex_request_details`,
`capex_sta_details`, `dashboard_definitions`, `dashboard_widgets`, `department_approval_limits`,
`email_provider_configs`, `exceeding_limit_rules`, `expense_allocations`, `expense_invoices`,
`expense_previous_summaries`, `expense_quotations`, `expense_request_items`, `expense_requests`,
`feature_flags`, `integration_configs`, `integration_sync_records`, `integration_sync_runs`,
`login_attempts`, `media_assets`, `notification_deliveries`, `notification_outbox`,
`notification_preferences`, `notification_templates`, `notifications`, `opex_request_details`,
`password_reset_tokens`, `recommending_authorities`, `report_definitions`, `request_documents`,
`role_permissions`, `scheduled_jobs`, `system_error_events`, `system_parameters`, `theme_tokens`,
`user_dashboard_preferences`, `user_sessions`, `workflow_events`, `workflow_transitions`
(plus two parsing artifacts, `in` and `time`, from text containing `numrange(...)` and `timezone`; not
real tables, disclosed for transparency).

## Appendix B — all 59 REST endpoints present

Base `{V1}` = `/api/v1`. All require authentication; all non-public ones are `@EdamPermission`-guarded.

**Organization (16):** `GET {V1}/organization/profile` · `PATCH {V1}/organization/profile` ·
`GET {V1}/organization/unit-types` · `GET {V1}/organization/units` · `GET {V1}/organization/units/tree` ·
`POST {V1}/organization/units` · `PUT {V1}/organization/units/{id}` ·
`POST {V1}/organization/units/{id}/activation` · `GET {V1}/organization/departments` ·
`POST {V1}/organization/departments` · `PUT {V1}/organization/departments/{id}` ·
`POST {V1}/organization/departments/{id}/activation` · `GET {V1}/organization/branches` ·
`POST {V1}/organization/branches` · `PUT {V1}/organization/branches/{id}` ·
`POST {V1}/organization/branches/{id}/activation`

**Master data (13):** `GET/POST {V1}/master-data/currencies` · `PUT {V1}/master-data/currencies/{id}` ·
`POST {V1}/master-data/currencies/{id}/activation` · `GET/POST {V1}/master-data/exchange-rates` ·
`GET/POST {V1}/master-data/tax-authorities` · `GET/POST {V1}/master-data/vendors` ·
`GET/PUT {V1}/master-data/vendors/{id}` · `POST {V1}/master-data/vendors/{id}/activation`

**Expense configuration (12):** `GET/POST {V1}/expense-configuration/categories` ·
`PUT {V1}/expense-configuration/categories/{id}` ·
`POST {V1}/expense-configuration/categories/{id}/activation` ·
`POST {V1}/expense-configuration/categories/{categoryId}/subcategories` ·
`PUT {V1}/expense-configuration/subcategories/{id}` ·
`POST {V1}/expense-configuration/subcategories/{id}/activation` · `GET/POST {V1}/expense-configuration/natures` ·
`POST {V1}/expense-configuration/natures/{id}/activation` ·
`GET/POST {V1}/expense-configuration/request-types`

**Delegation matrix (5):** `GET/POST {V1}/delegation-matrix` · `POST {V1}/delegation-matrix/preview` ·
`POST {V1}/delegation-matrix/{id}/close` · `POST {V1}/delegation-matrix/{id}/activation`

**Committees (10):** `GET/POST {V1}/committees` · `GET {V1}/committees/coverage` ·
`GET/POST {V1}/committees/{id}` · `POST {V1}/committees/{id}/deactivate` ·
`POST {V1}/committees/{id}/levels` · `POST {V1}/committees/{id}/levels/{levelId}/deactivate` ·
`POST {V1}/committees/{id}/members` · `POST {V1}/committees/{id}/members/{memberId}/end`

**System (1):** `GET {V1}/system/info`

**Development only (2):** `GET {V1}/dev/me` · `GET {V1}/dev/me/permissions` (gated by
`edam.security.dev-users.enabled`, `local` profile only)

---

## 11. Phase 3 close-out — the current position

Written at the end of Phase 3 and appended rather than merged into the sections above, so the original
findings remain readable as the evidence that scheduled the work.

### 11.1 §2.12 and §2.13, revisited

| Field | At `b73f8c5` | Now |
|---|---|---|
| Authoring API | None | `WorkflowConfigurationController`, 11 endpoints |
| Publish / retire | No service | `WorkflowConfigurationService.publish/retire`, immutable once published |
| Submission integration | `WorkflowEngine.start` had no callers | `RequestWorkflowService.startForSubmission`, in the submit transaction |
| Transitions consulted | No — unmapped, ignored | Yes — `WorkflowTransition`, two-regime resolution |
| Approval API | None | `ApprovalController`, 8 endpoints |
| Approval inbox | None | `GET /approvals`, `GET /approvals/count` |
| Actions over HTTP | None | approve, reject, revert, forward |
| Assignment enforced | Only in the engine | Also in `ApprovalService.requireAssigned`, with a refusal reason exposed per action |

The two findings that mattered most are both addressed and both tested:

* **"the engine can only be invoked by code that does not exist"** — submission is now the caller, and
  `SubmissionIntegrationTest` asserts that a request cannot become `SUBMITTED` without a live instance.
* **"`workflow_transitions` rows are not consulted"** — they are consulted, and an action a revision does
  not declare is refused rather than defaulted. A revision that declares no transitions keeps the
  pre-existing built-in semantics, so no frozen workflow changed behaviour.

### 11.2 §8 "Missing or incomplete APIs" — workflow and approval entries

| Endpoint group | Status |
|---|---|
| Workflow definition CRUD | **Implemented** |
| Workflow version history and detail | **Implemented** |
| Draft, clone-from-published, validate | **Implemented** |
| Publish, retire | **Implemented** |
| Version compare / rollback | **Not implemented** — OD-P3-03 |
| Workflow simulation | **Not implemented** — OD-P3-02 |
| Approval inbox, count, case file, request detail | **Implemented** |
| Approve / reject / revert / forward | **Implemented** |
| Approval history | **Implemented** — `WorkflowAction` plus `audit_events` |

### 11.3 Requirement status for Phase 3

Stated as the brief requires: implemented, partial, not implemented, or blocked.

| Requirement | Status | Evidence |
|---|---|---|
| Manage definitions separately for OPEX and CAPEX | **Implemented** | `WorkflowDefinition.requestTypeId`; `WorkflowConfigurationApiTest.separateWorkflowsPerRequestType` |
| Steps, order, approver strategies | **Implemented** | `WorkflowStep`, `WorkflowStepApprover` (9 types); `ApproverResolutionTest` |
| Conditions/transitions via the business-rule model | **Partial** — transitions are data; conditions refuse | OD-P3-04 |
| Validate before publication | **Implemented** | `inspect`; validation tests |
| Publish immutable versions, retain prior versions | **Implemented** | Three guards; `secondPublicationRetiresFirst` |
| Reject invalid publication | **Implemented** | `publish` refuses any `ERROR` and names the first |
| Require a valid published workflow before submission | **Implemented** | `requirePublishedForRequestType`; `noWorkflowRefuses` |
| Validate → confirm version → capture → start → resolve → tasks → associate, transactionally | **Implemented** | `submissionStartsRealWorkflow`; `@Transactional(MANDATORY)` bridge |
| Startup failure leaves no submitted request | **Implemented** | `failedStartLeavesAnEditableDraft`, `inactiveUserResolvesToNobody` |
| In-flight requests keep their version | **Implemented** | `inFlightKeepsItsVersion` — asserts the published pointer moved too |
| My pending approvals, approver-visible detail | **Implemented** | inbox, count, case file, request detail |
| Approve/reject/revert/forward with mandatory remarks | **Implemented** | `@NotBlank` + service check; `blankRemarksRefused`, `forwardRequiresBoth` |
| Forward requires recipient and remarks | **Implemented** | `forwardRequiresBoth` |
| Only authorised **and assigned** users act | **Implemented** | `permissionIsNotAssignment` |
| A general permission never confers authority | **Implemented** | same, plus the read-access test |
| Enforce data scope and workflow state | **Implemented** | `requireCanRead`, `requireAssigned`; register vs assigned-task test |
| Reject duplicate/stale actions | **Implemented** | `duplicateApprovalRefused` — one recorded action, not two |
| Transactional transitions | **Implemented** | `@Transactional` on every action |
| Auditable history | **Implemented** | `WorkflowAction` + `audit_events`; `actionsAreRecorded` |
| Resolve department/unit, authorities, committee, individual/role | **Implemented** | 9 approver types; `ApproverResolutionTest` |
| No eligible approver must not skip a mandatory step | **Implemented** | engine names the step; `missingOfficeHolderRefuses`, `emptyRoleRefuses`, `emptyCommitteeRefuses` |
| Handle inactive users and missing data scope | **Implemented** | `inactiveUserResolvesToNobody` |
| Handle effective dates | **Implemented** | seats and designations resolved at one instant |
| Reuse the permission catalogue; document any new one | **Implemented — no new permission** | `PERMISSION_ENDPOINT_MAP.md` |
| Keep endpoint authorization separate from assignment | **Implemented** | §2.4 of the Phase 3 doc |
| One bank per deployment, multiple admins | **Implemented** | no institution switching added |
| Tests per capability, real PostgreSQL, Flyway, `ddl-auto: validate` | **Implemented** | 6 new/extended classes, 287 tests total |
| CEO cannot reject an EC request | **Not implemented** | OD-P3-01, awaiting a business decision |
| Selective revert to a chosen approver | **Not implemented** | OD-P3-01 |
| EC revert to the last Phase-1 approver | **Partial** — the declared target is honoured | OD-P3-01 |
| Simulation before publication | **Not implemented** | OD-P3-02 |
| Version compare and rollback | **Not implemented** | OD-P3-03 |
| Cloning between institutions | **Not implemented** — out of scope by design | OD-P3-05 |
| Currency rounding policy (OD-P2-01) | **Blocked — still undecided** | Not invented; recorded unresolved |

### 11.4 What is still not true

The audit's central complaint was that the product's core object did not exist. That is fixed. What remains:

1. **No documents.** An approver sees document *types*, not files. Upload is Phase 4.
2. **No notifications or escalation.** Nothing is emailed and nothing is scheduled; an approver learns of a
   task by opening the inbox.
3. **No reporting or dashboards.** `report:view` and `dashboard:manage` have no consumer.
4. **No business-rule evaluator**, so conditional transitions refuse rather than route (OD-P3-04).
5. **`request:view-all` has no endpoint** — a finance user cannot list every request in the bank.
6. **Currency rounding is undecided** (OD-P2-01). The converted amount is shown to an approver alongside its
   inputs but is not presented as a final figure, and no rounding rule has been invented.


---

## 12. Post-merge verification of Phase 3, and Phase 4 readiness

Written after Phase 3 was merged, and **after re-checking the code rather than the Phase 3 documents**. The
full evidence is in `PHASE_3_MERGE_VERIFICATION.md`; this section records only what changes the audit's own
conclusions.

**Verified at:** `main` = `7119739a7c839586f3aef3724886c20fb5e2ab73`, CI run
[43429695561](https://github.com/Jhamankhatri/edam/actions/runs/43429695561) — success on all three jobs.

### 12.1 The merge is real, established by hash not by pull-request state

The merge commit's parents are `4783a99` (previous `main`) and `60a5084` (Phase 3 branch head), and **`main`'s
tree is byte-identical to that branch head's tree** (`12b79f7d…`). No file was dropped in the merge and no
conflict resolution discarded work. Zero unmerged commits remain on the Phase 3 branch.

Note for anyone re-running this in the same shallow checkout: `git merge-base --is-ancestor` returns a **false
negative** and `git rev-list --count` returns a meaningless figure, because `origin/main` is grafted as a
single commit. Use the commit object instead.

### 12.2 §2.12 and §2.13 — confirmed IMPLEMENTED, with two read-side gaps

Every capability named in the Phase 3 brief was found in the source, not merely claimed: publication is
`@Transactional` and retires before publishing; `startForSubmission` is `Propagation.MANDATORY` so submission
cannot leave a request without a workflow; `requireAssigned` refuses both a settled task and an unassigned
caller; `@Version` is present on `AuditableEntity` and `WorkflowTask` and mapped to 409 by the exception
handler; all nine BRD approver types resolve in `WorkflowAssignmentResolver`.

Two capabilities are **PARTIAL**, both because of what the *reader* can do, not the writer:

| Capability | Status | What is missing |
|---|---|---|
| Workflow history | **PARTIAL** | Versions are listable, but no endpoint exposes a request's own action history |
| Audit history | **PARTIAL** | Every action is written; **nothing reads it** — no audit query endpoint exists in any of the 16 controllers |

An auditor today must use SQL. That elevates the audit read path from "nice to have" to the first Phase 4 item.

### 12.3 §3's status table — four rows move

The consolidated table in §3 predates Phase 3. Four rows move; the originals said "PARTIAL" or "FAIL" against
the `b73f8c5` code, and they are quoted exactly so the delta is checkable:

| §3 row | Original status | Now | Basis |
|---|---|---|---|
| 12 — Workflow config & versions | PARTIAL (model tests only) | **IMPLEMENTED** | Author/publish/retire service *and* controller; 19 configuration tests. OD-P3-02/-03/-05 are named gaps, not absences of the core |
| 13 — Approve/reject/revert/forward | PARTIAL (no task API) | **IMPLEMENTED** | Inbox, count, case file, all four actions; 16 approval tests. OD-P3-01's governance rules are unenforced |
| 14 — Documents/attachments | FAIL | **NOT IMPLEMENTED** | Unchanged. Now Phase 4's largest item |
| 17 — Reporting/dashboards/audit | FAIL (entire read path) | **PARTIAL** | Audit *writing* is now implemented by every phase; dashboards, reports and the audit *read* path are still absent |

Row 19 already listed "idempotency wiring" as missing work at `b73f8c5`. That judgement was correct and it is
still true after Phase 3 — §12.6 below. It is the one original finding Phase 3 neither fixed nor touched.

### 12.4 Two Phase 3 documents contain errors that this verification found

Recorded here because the audit is the index of record, and a reader should not trust those figures:

1. **`MIGRATION_ENTITY_REPORT.md` overstates the unmapped tables.** It reports 50 mapped / 32 unmapped.
   Measured on `main`: **80 live tables, 52 mapped, 28 unmapped, 0 entities without a table.** It lists
   `audit_events` and `idempotency_records` as unmapped — both **are** mapped (`AuditEvent`,
   `IdempotencyRecord`) — and it contains two rows, `in` and `time`, which are **not tables**: they are regex
   matches against comment prose in V4_1 and V7. The scan did not strip SQL comments.
   `tools/migration-entity-report.py` now produces the correct figure and exits non-zero if any entity names a
   table no migration creates.
2. **OD-P2-01 misstates the rounding.** `PHASE_2_OPEN_DECISIONS.md` says rounding is "inherited from the
   column rather than chosen". `ExchangeRateResolver.scale` explicitly applies `RoundingMode.HALF_UP`. A
   rounding mode **has** been chosen in code; only the log says otherwise. The decision remains open — it now
   needs ratifying rather than defining.

Neither error is in the implementation. Both would mislead a Phase 4 planner: the first would send someone to
map an already-mapped table, the second would hide an operative financial policy behind an "undecided" label.

### 12.5 Headline figures, re-measured on `main`

| Metric | Value | How |
|---|---|---|
| REST endpoints | **127** | `python3 tools/permission-map.py` |
| Controllers | **16** | same |
| Permissions consumed | **36 of 58** (22 unused) | same |
| **Unguarded mapped endpoints** | **0** | same — exits 1 if any exist |
| Live tables | **80** | `python3 tools/migration-entity-report.py` |
| Tables mapped by an entity | **52** (28 unmapped, 0 orphans) | same |
| Test classes / tests | **23 / 287 recorded → 284 executed, 0 failed, 3 skipped** | CI annotation |
| Migrations | **17** (V1–V17 with V4.1) | file listing |

On the test figure: the CI total counts every `<testcase>`, including skipped ones, so "287 tests, 3 skipped"
means 284 ran. The earlier habit of writing "287 tests passing" overstated by 3. Corrected here.

### 12.6 Phase 4 readiness — one verdict, stated once

**Not ready for frontend integration yet; ready to *start* Phase 4 after three decisions.** A frontend can build
the request and approval journeys today against tested contracts. It cannot build branding, documents,
notifications or an activity timeline, because those endpoints do not exist.

Six modules are entirely absent: `configuration`, `businessrules`, `documentmanagement`, `notification`,
`reporting`, `integration`.

**Three decisions gate the work:**

1. **Storage** for documents — object store vs secured filesystem, plus the malware-scanning hook.
2. **OD-P3-04** — the rule-expression language, before any business-rule evaluator is written. RULE-02 forbids
   arbitrary code and SQL, so this is a security decision, not a syntax preference.
3. **Audit retention, immutability and export approval** (BRD §26's own OPEN DECISION).

**One finding worth separating from the rest:** idempotency exists as a working mechanism with **zero
consumers** — `IdempotencyService` is referenced by no endpoint. For a system whose POSTs create money requests
and record approval decisions, that is a correctness risk, and it is cheap to close because the machinery is
already present. Earlier notes calling idempotency "implemented" are true of the component and false of the
system.

**Five open decisions still block real-money production use:** OD-P2-01 (rounding), OD-P2-03 (what exceeding a
limit escalates to), OD-P2-07 (CAPEX department eligibility), OD-P2-08 (personal vs departmental limit
precedence), OD-P3-01 (CEO/EC rejection rules). None blocks Phase 4, and every one is a policy the code
currently answers by refusing or by taking the conservative direction.

**Recommended Phase 4 order,** by value per unit of risk: audit search → idempotency on POSTs → branding and
settings → documents → notifications/email → reporting → business rules.
