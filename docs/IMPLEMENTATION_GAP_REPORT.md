# EDAM — Implementation Gap Report

**Baseline:** EDAM Business Requirements Document v2.0 (Expanded Draft) — `docs/brd/EDM-BRD-001-v2.txt`
**Audited repository state:** `main` @ `ff3a045` (foundation phase, merged via PR #2)
**Audit date:** 12 November 2026
**Auditor:** backend implementation pass (Phase 1 of the backend completion scope)

---

## 0. How to read this report

The BRD is the authority. The repository is *evidence of what exists*, never evidence of what is
required. A class or table whose name resembles a requirement is not counted as implementation.

Status vocabulary (mandated):

| Status | Meaning |
|---|---|
| **IMPLEMENTED** | Behaviour exists, is reachable over a documented API, and is covered by an automated test. |
| **PARTIALLY IMPLEMENTED** | Some behaviour exists; a named part of the requirement is missing or not enforced. |
| **NOT IMPLEMENTED** | No behaviour exists. |
| **INCORRECTLY IMPLEMENTED** | Behaviour exists and contradicts the BRD, or is insecure/unsound as built. |
| **NEEDS VALIDATION** | Exists but cannot be confirmed correct from code + tests alone (needs business or infra confirmation). |
| **BLOCKED BY BUSINESS DECISION** | Cannot be built correctly until the named open decision is approved. |

**Honesty constraint for this report:** the authoring sandbox has no JDK, Maven, PostgreSQL or
Docker. Compilation and test execution happen in GitHub Actions. Every status below is therefore
based on **static reading of the code**, and any claim about *executed* behaviour is marked as
verified only where a CI job was actually observed. Where this audit and a later CI run disagree,
CI wins and this report is corrected.

---

## 1. What actually exists before this work

39 production classes, 11 JSP views, 2 Flyway migrations, 9 test classes.

| Area | Contents |
|---|---|
| Build | Maven, Boot 4.1.2 parent, Java 21, **WAR** packaging, enforcer rules |
| Packages | `common.{api,audit,exception,logging,security}`, `config`, `identity`, `institution`, `web` |
| Persistence | **One table**: `institution`. JPA auditing, optimistic locking, `ddl-auto=validate`, Flyway V1/V2 |
| Security | Form login, BCrypt, `PERM_*` authorities, method security enabled, CSRF, CSP, session fixation, `EdamPrincipal` with multi-institution membership set |
| Cross-cutting | `ApiResponse<T>` envelope, `ErrorCode` catalogue, correlation ID filter + MDC, request log, two exception advices |
| Views | JSP shell: login, dashboard, access-denied, error (kept for operator/browser access only) |
| Config | `local`/`test`/`prod` profiles, ECS JSON logging under `prod` |
| Tests | Security/MVC contract, view-model contract, institution mapping, correlation, principal, exception handler, WAR packaging |

**There is no business domain in the repository.** No expense, no approval, no workflow, no
configuration administration, no user/role administration beyond compile-time constants, no
notifications, no documents, no reporting, no audit trail beyond `created_by/updated_at` stamps.

That is correct for the previous phase (foundation only). It means the remainder of the BRD is
almost entirely *not implemented*, and the honest report is mostly red.

---

## 2. Cross-cutting findings that affect everything below

These are the highest-value results of the audit, because each one would have caused rework later.

### F-1 — The v2.0 target replaces the JSP UI with REST + React (BRD §29, §41) — **scope conflict**

The repository deliberately renders JSP views and asserts their HTML in CI. BRD v2.0 states: *"Do
not implement the React frontend as JSP or embed frontend pages inside the Spring Boot application.
The target frontend is a separate React application."*

The instruction for this phase is backend-only, no frontend.

**Resolution taken:** the JSP shell is retained as an **operator login/denial page only** (it is
the only interactive surface, and removing it would break the usable login flow without providing
a replacement). It is explicitly *not* developed further, contains no business screens, and the new
work is 100% REST/JSON. This is recorded rather than silently changed, because deleting a working
authentication UI while the React app does not exist would leave the product unusable.

### F-2 — Tenant isolation (OD-23/OD-20) is unresolved, but business tables must be built now

The BRD requires institution isolation (INS-02, NFR-SEC-01, AC-02) while explicitly leaving the
*model* (shared schema / schema-per-tenant / database-per-tenant) open.

**Resolution taken:** implement **shared schema with a mandatory `institution_id` discriminator**,
enforce it in application code at the repository/service boundary, add a database-level guard where
it is expressible, and document the choice as reversible + ADR-backed. This is the only strategy
that is safe to build against and migratable away from; it is recorded as a **provisional technical
decision**, not as resolution of OD-23. Isolation is proven by negative tests (AC-02).

### F-3 — No authorization model exists beyond compile-time permission constants

`Permissions` is a hardcoded class of 7 strings. BRD §23/IAM-01..05 requires institution-scoped
**custom roles**, granular permissions (module/action/data-scope), permission administration,
permission review, and audited grants. The current model cannot express any of it.

Also: `SecurityConfig` authorizes by **URL**, and `EdamPrincipal.permissions` is a flat global set —
not institution-scoped. A user who holds `configuration:manage` in institution A holds it everywhere.
That is an **INCORRECTLY IMPLEMENTED** authorization model for a multi-tenant product.

### F-4 — `ErrorCode` cannot express an error catalogue; `ApiResponse` cannot express pagination

The envelope deviates from either conventional option the BRD allows (§29.1: "consistent response
envelopes **or** problem-details"). It is usable, so it is kept (changing it would break the agreed
contract), but it needs additive fields for pagination and an expanded error catalogue. Both are
additive and backward compatible.

### F-5 — Foundation has no idempotency, no optimistic concurrency on the API surface, no rate limiting

Required by API-03, WF-06, NFR-REL-01. Nothing exists. `@Version` exists on the entity but no
mechanism exposes or checks an entity version over HTTP.

### F-6 — Cloudinary is entirely absent, and naive integration would be a security defect

No storage abstraction, no file validation, no tenant-scoped document access. BRD §31/DOC-01
requires private storage, opaque keys, authorized download endpoints, MIME/extension/signature
validation, and tenant + record-level authorization. A "store the URL in the DB" implementation
would fail AC-13 outright. Note also OD-35 (storage selection) and the banking data-residency
question: **Cloudinary is not approved for confidential financial documents**, so the design must
support provider substitution and must classify assets.

### F-7 — No workflow, rule, audit, notification or reporting persistence

Everything in §24–§28, §33 is greenfield. Two of them (workflow semantics OD-27/OD-28, rule
scope OD-31/§25) are partially open decisions, which means the *engine* must be data-driven rather
than encoding a guessed approval policy.

---

## 3. Requirement-by-requirement assessment

### 3.1 Functional modules FM-01 … FM-13 (BRD §6–§8)

| ID | Requirement | Existing location | Status | Gap | Required change | APIs / entities / DB | Tests |
|---|---|---|---|---|---|---|---|
| **FM-01** | Head Office OPEX request: create, validate, submit, edit returned, selective revert, vendor addition | — | **NOT IMPLEMENTED** | No header, no child rows, no validation, no reference generation (FR-REQ-001..010) | Header + invoice rows + allocations + checklist entities; service with all §8.1/§8.2 validations; submit inside one transaction | `POST /api/v1/opex/requests`, `GET|PUT /api/v1/opex/requests/{id}`, `POST .../submit`, `POST .../revert`; tables `opex_request`, `opex_invoice`, `expense_allocation`, `expense_document`, `vendor` | Validation matrix (invoice=approval>0, non-recurring justification, quotation rules, conditional attachments), RBAC, tenant |
| **FM-02** | OPEX register: My Request / My Approvals, search/filter, export, print | — | **NOT IMPLEMENTED** | No register query at all | Paged, filtered, scope-aware register; default 30-day window; Excel export with 10k cap | `GET /api/v1/opex/requests?scope=mine|approvals&…`, `GET .../export` | Filter/scope/tenancy/pagination tests |
| **FM-03** | OPEX approval: review, approve/forward/revert/reject | — | **NOT IMPLEMENTED** | No approval action, no history | Approval service: assignment check, remark requirements, CEO-cannot-reject, EC revert target | `GET /api/v1/approvals**, `POST /api/v1/approvals/{taskId}/actions`; `approval_task`, `approval_action` | Action-permission matrix, double-action race, remark rules |
| **FM-04** | CAPEX request incl. asset allocations, item-wise budget, checklist | — | **NOT IMPLEMENTED** | — | CAPEX header + item rows + budget/charge rows; §8.3 validations (warranty not past, unbudgeted reason ≤500, totals equal) | `POST /api/v1/capex/requests`…; `capex_request`, `capex_item`, `capex_charge` | Field/validation matrix |
| **FM-05** | CAPEX register + department eligibility for new requests | — | **NOT IMPLEMENTED** | Eligibility is a code constant in legacy; must be configuration | Register API + `capex_eligible_department` config table | `GET /api/v1/capex/requests`, config API | Eligibility enforced server-side |
| **FM-06** | CAPEX approval | — | **NOT IMPLEMENTED** | — | Shared approval engine (see FM-03) | as FM-03 | as FM-03 |
| **FM-07** | Category / sub-category / EDM delegation matrix administration | `institution` table only | **NOT IMPLEMENTED** | Entire configuration plane absent | CRUD + activation + delete guards + audit + overlap validation | `/api/v1/config/categories`, `/subcategories`, `/delegation-rules`; `expense_category`, `expense_subcategory`, `delegation_rule` | Unique/active constraints, overlap, delete guards |
| **FM-08** | EC member pool, designations, approval levels, quorum, CEO/CFO | — | **NOT IMPLEMENTED** | — | Member pool + level config with all §8.6 validations (single active CEO/CFO/HOD, min approvals ≤ member count, ranges) | `/api/v1/config/committee/**`; `ec_member`, `ec_level`, `ec_level_member` | Validation matrix |
| **FM-09** | Currency code/name/PKR rate + status + audit | — | **NOT IMPLEMENTED** | — | CRUD with uppercase unique code, 4-dp rate, in-use delete guard | `/api/v1/config/currencies`; `currency` | Rate precision, in-use guard |
| **FM-10** | Department Head general limit (single record) | `institution.department_head_limit` column | **PARTIALLY IMPLEMENTED** | Column exists; no API, no single-record rule, no audit, not enforced in routing | Config entity/API + enforcement in routing | `/api/v1/config/department-head-limit`; `department_head_limit` | Single-record + audit |
| **FM-11** | Recommending authority assignment | — | **NOT IMPLEMENTED** | — | CRUD + department/user dependency + delete guard | `/api/v1/config/recommending-authorities` | Title read-only on edit |
| **FM-12** | Reporting: metrics, filters, details, analytics, Excel export | — | **NOT IMPLEMENTED** | — | Summary + list + analytics + export with 10k cap and export audit | `/api/v1/reports/**` | KPI correctness (BLOCKED for formulas: OD-34) |
| **FM-13** | Exceeding-limit config per nature | `edam.institution.features.exceeding-limit-enforced-in-routing` flag | **INCORRECTLY IMPLEMENTED** | A boolean property pretends to resolve an open business rule; no config record, no enforcement | Config table/API; enforcement **remains disabled and documented** pending the open decision | `/api/v1/config/exceeding-limits`; `exceeding_limit` | Config validation; enforcement absence documented |

### 3.2 Detailed creation/validation requirements (BRD §8.1–§8.5, §9)

| ID | Requirement | Status | Note |
|---|---|---|---|
| FR-REQ-001 | Unique request reference | **NOT IMPLEMENTED** | Must be institution-configurable format, not the legacy algorithm (source-confirmed gap) |
| FR-REQ-002 | Requester/department/location from authenticated context | **NOT IMPLEMENTED** | Requires master data (see INT-01) |
| FR-REQ-003 | Require type/category/sub-category/vendor/currency | **NOT IMPLEMENTED** | |
| FR-REQ-004 | Charge allocation to branches/HO departments | **NOT IMPLEMENTED** | |
| FR-REQ-005 | Invoice rows + invoice total | **NOT IMPLEMENTED** | |
| FR-REQ-006 | Invoice total = approval amount > 0 | **NOT IMPLEMENTED** | Core financial invariant; must be in code, not a rule table |
| FR-REQ-007 | PKR equivalent via configured rate | **NOT IMPLEMENTED** | **BLOCKED** for formula/rounding (BRD says formula and rounding are open) — implement rate lookup + explicit scale, record open decision |
| FR-REQ-008 | GST/WHT flags + Sales Tax Authority | **NOT IMPLEMENTED** | |
| FR-REQ-009 | Checklist, uploads, conditional attachments | **NOT IMPLEMENTED** | Depends on document storage (F-6) |
| FR-REQ-010 | Single transaction boundary for request + children + route | **NOT IMPLEMENTED** | Explicit target proposal |
| §8.2 OPEX | Quotation/justification/attachment conditionals | **NOT IMPLEMENTED** | |
| §8.3 CAPEX | Totals equality, item details, warranty, unbudgeted reason | **NOT IMPLEMENTED** | Eligibility department list must be configuration |
| §8.4 Register | 30-day default, filters, scope, edit destination | **NOT IMPLEMENTED** | |
| §8.5 Approval | Remark rules, CEO restriction, EC revert target | **NOT IMPLEMENTED** | |
| §8.6 Config | All configuration validations | **NOT IMPLEMENTED** | |

### 3.3 Approval, workflow and delegation (BRD §10, §24, WF-01..06)

| Requirement | Status | Notes |
|---|---|---|
| Documented Phase 1 → RA → EC route | **NOT IMPLEMENTED** | To be expressed as a **configurable workflow definition**, not hardcoded |
| Department Head limit routing (at/below ⇒ Phase 1 only) | **NOT IMPLEMENTED** | Legacy 20,000 fallback must stay configuration |
| EC levels, ranges, quorum, CEO/CFO participation | **NOT IMPLEMENTED** | |
| Standalone CEO/HOD routing | **BLOCKED BY BUSINESS DECISION** | BRD §10.4 forbids assumption |
| Complete state machine / transition matrix | **BLOCKED BY BUSINESS DECISION** | BRD §10.5: "a formally approved state-transition matrix is a release prerequisite" |
| WF-01..03 definition, patterns, conditions | **NOT IMPLEMENTED** | Build a data-driven engine; sequential + parallel(quorum) stages, safe condition language |
| WF-04 versioning, in-flight pinning | **NOT IMPLEMENTED** | Version snapshot on submission is mandatory |
| WF-05 validate/simulate | **NOT IMPLEMENTED** | |
| WF-06 execution integrity | **NOT IMPLEMENTED** | Optimistic lock + idempotency |
| RULE-01/02 rule sets, decision tables | **NOT IMPLEMENTED** | Constrained evaluator only; no arbitrary code |

### 3.4 Institution, configuration and branding (BRD §21, §22, INS-01..04, BRD-UI-01..03)

| ID | Requirement | Status | Gap / change |
|---|---|---|---|
| INS-01 | Institution CRUD + lifecycle + audit | **PARTIALLY IMPLEMENTED** | Entity + seed + read service exist. No create/update/suspend API, no lifecycle states, no audit trail |
| INS-02 | Tenant isolation across APIs, jobs, files, reports, audit | **INCORRECTLY IMPLEMENTED** | `InstitutionContext` resolves a tenant but nothing enforces it: no `institution_id` on any table, no repository filter, no test |
| INS-03 | Institution settings (locale, tz, currency, formats) | **PARTIALLY IMPLEMENTED** | Columns exist on `institution`; no API |
| INS-04 | Feature flags per institution, enforced by backend | **INCORRECTLY IMPLEMENTED** | Flags live in `application.yml` (global), not per institution; not enforced anywhere |
| BRD-UI-01/02 | Branding + theme lifecycle (version, publish, rollback) | **NOT IMPLEMENTED** | Branding is a handful of columns; no versioning/lifecycle |
| BRD-UI-03 | Theme/config API for the frontend | **NOT IMPLEMENTED** | |
| — | `/api/v1/me/context`, `/me/features`, `/platform/branding` (BRD §22 illustrative contracts) | **NOT IMPLEMENTED** | Required before React integration |

### 3.5 Identity, roles and permissions (BRD §23, IAM-01..05)

| ID | Requirement | Status | Gap |
|---|---|---|---|
| IAM-01 | Institution-scoped custom roles | **NOT IMPLEMENTED** | No `role` table; roles are strings inside a dev-only in-memory map |
| IAM-02 | Module/action/data-scope permissions | **NOT IMPLEMENTED** | 7 hardcoded constants |
| IAM-03 | Server-side enforcement for every operation | **PARTIALLY IMPLEMENTED** | URL rules + `@PreAuthorize` exist, but no endpoint has a permission check yet, and permissions are not institution-scoped (**defect**, F-3) |
| IAM-04 | Multi-institution membership | **PARTIALLY IMPLEMENTED** | Modelled on the principal; no persisted membership, no API |
| IAM-05 | Audit of access changes | **NOT IMPLEMENTED** | |
| — | Predefined role templates | **NOT IMPLEMENTED** | |
| — | Segregation of duties / maker-checker | **BLOCKED BY BUSINESS DECISION** | OD-26 |
| — | Permission review/export for recertification | **NOT IMPLEMENTED** | |

### 3.6 Audit, activity history, dashboards (BRD §26, §27, AUD-01..03, DASH-01..02)

| ID | Requirement | Status | Gap |
|---|---|---|---|
| AUD-01 | Central searchable activity history | **NOT IMPLEMENTED** | Only `created_by`/`updated_at` columns |
| AUD-02 | Before/after diff + redaction | **NOT IMPLEMENTED** | |
| AUD-03 | Permission-controlled, itself-audited export | **NOT IMPLEMENTED** | |
| DASH-01 | Institution-scoped metrics | **NOT IMPLEMENTED** | |
| DASH-02 | Authorized drill-down | **NOT IMPLEMENTED** | |
| — | KPI formulas (committed spend, utilisation, turnaround) | **BLOCKED BY BUSINESS DECISION** | OD-34 — must not invent formulas |

### 3.7 Notifications, email, documents (BRD §28, §31, NOT-01, MAIL-01..04, DOC-01)

| ID | Requirement | Status | Gap |
|---|---|---|---|
| NOT-01 | In-app inbox, read/unread, deep links | **NOT IMPLEMENTED** | |
| MAIL-01 | Institution email provider administration | **NOT IMPLEMENTED** | |
| MAIL-02 | Secret-safe credential handling (vault/KMS/envelope) | **NOT IMPLEMENTED** | Needs an encryption seam with keys outside the DB; provider selection is OD-30 |
| MAIL-03 | Template designer: visual + HTML, placeholders, preview, test-send, versioning | **NOT IMPLEMENTED** | Must sanitize HTML |
| MAIL-04 | Delivery tracking, retries, sanitized errors | **NOT IMPLEMENTED** | Must be non-blocking relative to approval transactions |
| DOC-01 | Secure document storage, tenant + record authorization | **NOT IMPLEMENTED** | See F-6 |
| — | Cloudinary integration | **NOT IMPLEMENTED** | Requires provider abstraction, classification, validation |

### 3.8 API, security, integration, reporting spine (BRD §29, §30, §32, §33)

| Requirement | Status | Gap |
|---|---|---|
| API-01 versioned REST | **PARTIALLY IMPLEMENTED** | `/api/v1` prefix + 2 endpoints only |
| API-02 OpenAPI | **NOT IMPLEMENTED** | No springdoc, no spec |
| API-03 DTOs, errors, pagination, idempotency, concurrency | **PARTIALLY IMPLEMENTED** | DTOs + errors exist; pagination, idempotency, ETag/version concurrency absent |
| SEC-01 bank controls (TLS, least privilege, MFA/SSO, monitoring) | **PARTIALLY IMPLEMENTED** | CSP/headers/CSRF/session fixation done; SSO/MFA/rate limiting/secret management absent (OD-24/OD-30) |
| INT-01 master-data adapters | **NOT IMPLEMENTED** | Blocked in detail by OD-36; adapter interface can be defined |
| §33 reporting/export governance | **NOT IMPLEMENTED** | 10k cap, export audit, async generation |

### 3.9 Open decisions (BRD §38)

All 42 (`OD-01`…`OD-42`) remain **unresolved**. This work does **not** resolve any of them. Sections
of this repository that would otherwise silently pick a side are explicitly marked provisional and
listed in §5 below and in `docs/OPEN_DECISIONS.md`.

---

## 4. Defects found in existing code (not just gaps)

These are things that exist and are wrong, so they are fixed rather than merely added to.

| # | Defect | Consequence | Severity |
|---|---|---|---|
| D-1 | Permissions are global, not institution-scoped | Cross-tenant privilege escalation | **High** |
| D-2 | `InstitutionContext` exists but nothing enforces isolation | AC-02 cannot pass; tenant leakage by construction once tables exist | **High** |
| D-3 | Feature flags are global properties, not institution configuration | Violates INS-04 and the "no institution-specific code/config" guardrail | Medium |
| D-4 | `exceeding-limit-enforced-in-routing` boolean in config pretends to resolve a business rule | Misrepresents an open decision as implemented | Medium |
| D-5 | Some endpoints declare no permission requirement | Deny-by-default is not actually enforced | Medium |
| D-6 | `ApiResponse` has no pagination/list metadata contract | Every future list API would invent its own shape | Low (additive) |

---

## 5. Provisional decisions taken in this phase (and why they are reversible)

| # | Decision | Reason | Reversal cost |
|---|---|---|---|
| P-1 | Shared schema + `institution_id` discriminator | Only option that is buildable now and migratable to schema/DB-per-tenant later | Low-medium (additive schema per tenant strategy) |
| P-2 | Data-driven workflow definitions + version pinning | BRD forbids hardcoded hierarchies; semantics partly open | Low (definitions are data) |
| P-3 | Constrained rule expressions (typed facts + operators), not a script engine | RULE-02 prohibits arbitrary code | Low |
| P-4 | Storage provider abstraction with Cloudinary as one adapter | OD-35 unapproved for confidential documents | Low |
| P-5 | Envelope encryption with a KEK supplied from environment/vault | Only viable option without a bank-provided vault | Medium (adapter swap) |
| P-6 | Legacy Department Head 20,000 fallback retained as *configuration*, never a constant | BRD documents it as a legacy helper default | None |

Every one of these is recorded in `docs/adr/` with consequences, and every one remains subject to
the corresponding open decision.

---

## 6. Blockers requiring a business decision (not worked around)

| Blocker | OD | What is implemented instead |
|---|---|---|
| Approval state machine / transition matrix | §10.5, OD-27/28 | Engine + explicit, documented default transitions marked provisional; every transition audited |
| Standalone CEO/HOD approval routing | §10.4 | **Not implemented.** No code path guesses it |
| PRD/printing eligibility | §8.4 | Not implemented |
| PKR conversion rounding/formula | FR-REQ-007 | Rate lookup + `HALF_UP` at documented scale, flagged as unapproved |
| Dashboard KPI formulas | OD-34 | Raw counts/sums by status only (no derived KPIs) |
| Master-data source of truth | OD-36 | Local admin CRUD behind an adapter interface |
| Identity provider / SSO | OD-24 | Existing seam + dev profile only |
| Secret manager / KMS choice | OD-30 | Envelope encryption with external KEK |
| Tenant isolation model | OD-23 | Provisional P-1, tested |
| Exceeding-limit enforcement | FM-13 | Config CRUD only; routing enforcement absent and asserted absent |

---

## 7. Test cases required (summary; full mapping in REQUIREMENT_TRACEABILITY_MATRIX.md)

1. **Financial invariants** — invoice total = approval amount > 0; totals equality; two-decimal
   scale; BigDecimal only.
2. **Tenant isolation (negative)** — every read/write endpoint refuses another institution's IDs;
   access-denied does not reveal existence.
3. **Authorization** — deny-by-default: every mutating endpoint rejects a caller lacking the
   scoped permission, even with a valid role in a *different* institution.
4. **Workflow** — route resolution for at/below and above the DH limit; version pinning;
   double-action rejection; remark requirements; CEO cannot reject; EC revert target.
5. **Configuration validation** — the whole §8.6 matrix (uniqueness, ranges, quorums, delete guards).
6. **Documents** — upload validation (type/size), authorized download, cross-tenant refusal.
7. **Secrets** — no plaintext secret in DB/logs/API; masked read model; rotation replaces value.
8. **API contract** — envelope, error codes, pagination, idempotency replay, ETag/version conflict.
9. **Regression** — all pre-existing tests keep passing.

---

## 8. Conclusion

The foundation is sound and is kept. Everything business-facing is missing, and three things that
do exist would leak or over-grant once business tables are added (D-1, D-2, D-3). The plan is:
fix the isolation/authorization spine first, then build configuration → workflow → expense →
approval → documents → notifications/reporting on top of it, with the open decisions left visibly
open.
