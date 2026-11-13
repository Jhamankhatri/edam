# Phase 4 — Implementation Scope and Dependencies

**Baseline:** `main` @ `7119739a7c839586f3aef3724886c20fb5e2ab73` (Phase 3 merged), 284 tests executed / 284 passed
/ 3 skipped, CI run [43429695561](https://github.com/Jhamankhatri/edam/actions/runs/43429695561).

**Nothing here is implemented.** This is a scope document produced from the BRD (§22, §25–§33, requirement
register §37) and from what the tree actually contains, measured rather than assumed.

Status values used throughout: **IMPLEMENTED**, **PARTIAL**, **NOT IMPLEMENTED**, **BLOCKED BY BUSINESS
DECISION**, **NOT VERIFIED**.

---

## 1. Where Phase 4 starts from

Six modules are entirely absent — no package, no controller, no service:

```
configuration   businessrules   documentmanagement   notification   reporting   integration
```

**22 of the 58 permission constants have no consumer of any kind** (measured: `tools/permission-map.py` →
`used=36 unused=22`). They are Phase 4's vocabulary, and `PENDING` in that tool already maps each to a module:

| Module | Permissions | Count |
|---|---|---|
| Documents | `document:upload/download/delete` | 3 |
| Notifications & email | `notification:view`, `notification_template:manage`, `email_configuration:manage` | 3 |
| Reporting & dashboards | `report:view`, `report:export`, `dashboard:manage` | 3 |
| Audit | `audit:view`, `audit:export` | 2 |
| Business rules | `rule:view/manage/publish` | 3 (blocked with OD-P3-04) |
| Integrations | `integration:view/manage` | 2 |
| Settings / administration | `configuration:view`, `system:administer` | 2 |
| Register gaps | `request:view-all`, `request:view-scope` | 2 (**real gap** — see §2.8) |
| Register export / reversal | `request:export`, `request:reverse` | 2 |
| | **Total** | **22** |

**28 of 80 live tables are unmapped and inert** (`tools/migration-entity-report.py`), and they cluster on
Phase 4's areas:

| Group | Tables | Count |
|---|---|---|
| Notifications / email | `notifications`, `notification_templates`, `notification_outbox`, `notification_deliveries`, `notification_preferences`, `email_provider_configs` | 6 |
| Reporting / dashboards | `report_definitions`, `dashboard_definitions`, `dashboard_widgets`, `user_dashboard_preferences` | 4 |
| Integrations | `integration_configs`, `integration_sync_records`, `integration_sync_runs` | 3 |
| Documents | `media_assets` | 1 |
| Theming / settings | `branding_themes`, `theme_tokens`, `application_settings`, `system_parameters`, `feature_flags` | 5 |
| Business rules | `business_rules`, `business_rule_sets`, `business_rule_versions` | 3 |
| Audit detail / workflow events | `audit_event_details`, `workflow_events` | 2 |
| Ops / access | `scheduled_jobs`, `system_error_events`, `role_permissions`, `user_sessions` | 4 |
| | **Total** | **28** |

That distribution is the useful fact: **the schema was designed for Phase 4 and the code is not there.** The
tables define the contracts; they are not a plan the implementation has to invent.

---

## 2. Scope, area by area

### 2.1 Documents and attachments — NOT IMPLEMENTED

**BRD §31 / DOC-01.** The requirement is prescriptive: metadata separate from bytes, private object storage,
opaque keys, authorized downloads, extension/MIME/signature/size/filename validation, malware scanning where
available, TLS in transit, encryption at rest, retention/archive/replacement/delete, audit of
upload/download/replace/delete, and **no document contents in logs**.

**Present:** `media_assets` (V6, unmapped) and `request_documents` (V9). The request API already links an
*existing* asset and validates the document type; the upload path that would create one does not exist. The code
says so itself: *"The media asset itself is created by the upload path in Phase 4."*

**Missing:** upload, download, preview, metadata management, validation, scanning hook, retention, and the audit
events.

**Dependencies:**

| Dependency | Type | Notes |
|---|---|---|
| Storage backend choice | **Decision (blocks)** | Object store vs secured filesystem. Shape of the service depends on it. |
| Malware scanning hook | **Decision** | "Where available" — needs an interface even if unimplemented |
| `media_assets` entity | Code | One of the 28 unmapped tables |
| `document:upload/download/delete` | Code | 3 of the 22 unused permissions |
| Audit read path for §2.4 | Cross-area | Uploads must be audited; auditing without a reader is half a control |

**Risk: highest in Phase 4.** It is the only area with a file-system or object-store dependency and the only one
whose attack surface is an upload. It should not be parallelised with anything security-sensitive.

---

### 2.2 Notifications and email — NOT IMPLEMENTED

**BRD §28 / NOT-01, MAIL-01…04.**

**Present:** six tables (V10), all unmapped and inert: `notifications`, `notification_templates`,
`notification_outbox`, `notification_deliveries`, `notification_preferences`, `email_provider_configs`. A
`scheduled_jobs` table exists too (V11).

**Missing:** everything in Java, plus **infrastructure that does not exist anywhere in the application**:

```
$ grep -rn "@EnableAsync|@EnableScheduling|@Async" src/main/java    ->  no matches
```

There is no asynchronous execution and no scheduler. Delivery tracking, retry, and outbox draining all require
one. This is the single largest hidden prerequisite in Phase 4 and it is shared with §2.3.

**Dependencies:**

| Dependency | Type | Notes |
|---|---|---|
| Async/scheduling infrastructure | **Code (prerequisite)** | Needed by this area *and* reporting |
| MAIL-02 secret handling | **Decision + security** | Vault/KMS or envelope encryption for SMTP credentials; nothing exists |
| SMTP provider details | **Decision** | Institution-supplied |
| Template placeholders + versioning | Code | MAIL-03; interacts with OD-P3-04's evaluator only loosely |

**Note on the approval inbox:** it is *not* a notification system. It computes open tasks on read. Nothing is
pushed, nothing is queued, and an approver learns of a task only by opening the app. That gap is real but its
severity is a bank decision — see OD-P3-01's neighbourhood.

---

### 2.3 Reporting and dashboards — NOT IMPLEMENTED

**BRD §27, §33 / DASH-01, DASH-02.**

**Present:** `report_definitions`, `dashboard_definitions`, `dashboard_widgets`,
`user_dashboard_preferences` (V11) — all unmapped.

**Missing:** every metric, every report, all export, all drill-down.

**Dependencies:**

| Dependency | Type | Notes |
|---|---|---|
| **OD-P2-01 currency rounding** | **Decision (blocks meaningfulness)** | An aggregate in base currency is not reproducible while the rounding policy is unratified |
| Report semantics (§33) | **Decision** | KPI formulas, time zone, treatment of cancelled/rejected/reverted requests |
| Async generation + expiry | **Code** | Shares §2.2's missing infrastructure |
| Export audit record | **Code** | Requester, filters, row count, file id, timestamp, status |
| Data-scope inheritance | **Code** | §33: exports must not leak cross-institution records |

**Sequencing note:** dashboards over *workflow* data (pending approvals, ageing) do not need OD-P2-01. Financial
totals do. Splitting the two is the way to make progress before the decision lands.

---

### 2.4 Audit search and export — **PARTIAL** (writing complete, reading absent)

**BRD §26 / AUD-01, AUD-02, AUD-03.** This is the only Phase 4 area with anything implemented, and the split is
sharp.

**Implemented and verified:** `audit_events` is mapped (`common/audit/AuditEvent.java`) and written by every
material action — request lifecycle, approval decisions, workflow publication and retirement, role grants, data
scopes, organization and user changes. Redaction exists (`AuditValueRedactor`). Before/after capture exists via
`AuditRecorder.recordChange`.

**Not implemented — the entire read surface.** No audit controller exists among the 16. No search by actor,
institution, date, module, action, resource or outcome (AUD-01). No record-level timeline for a request. No
export (AUD-03).

**AUD-02 is only partly satisfied**, and precisely: `recordChange` has **5 call sites** —
`OrganizationProfile`, `OrganizationUnit`, `Department`, `Role`, `User`. Workflow definitions, business rules,
email settings and master data changes are audited as *events* without before/after values.

**Dependencies:**

| Dependency | Type | Notes |
|---|---|---|
| §26's own OPEN DECISION | **Decision** | Retention, immutability, SIEM, export approval policy |
| `audit:view`, `audit:export` | Code | 2 of the 22 unused permissions |
| Before/after for the remaining entities | Code | Extend `recordChange` coverage |

**Recommendation: do this first.** The data is already being collected, so the marginal cost is a query layer,
and it unblocks the request-activity timeline the frontend needs. It is also the cheapest credibility win with
an auditor.

---

### 2.5 Theme, branding and application settings — NOT IMPLEMENTED

**BRD §22 / BRD-UI-01…03.**

**Present:** `branding_themes`, `theme_tokens`, `application_settings`, `system_parameters`, `feature_flags`
(V6) — all unmapped, and `feature_flags` is referenced **nowhere** in Java (`grep -rn "feature_flags|FeatureFlag"`
returns no matches). Earlier phase notes described per-institution feature flags as delivered; nothing in the
application reads or writes that table, so treat it as a Phase 4 table like the rest.

**Missing:** the configuration API and the settings surface.

**Dependencies:** none external. This is the cheapest item to schedule and it is a **frontend blocker**: a React
shell cannot render a branded, themed application without it. `configuration:view` is among the unused
permissions.

---

### 2.6 Controlled business-rule evaluation — NOT IMPLEMENTED · **BLOCKED BY BUSINESS DECISION**

**BRD §25 / RULE-01, RULE-02; WF-03.**

**Present:** `business_rules` table with `condition_rule_id` now a real FK (V17); publication-time validation
reports conditional transitions; an action on a conditional transition is refused, never defaulted.

**Missing:** the evaluator. `com.edam.businessrules` does not exist.

**Blocked by OD-P3-04.** RULE-02 requires constrained expressions or decision tables and **prohibits arbitrary
code and SQL execution**. Selecting that language is a security decision whose blast radius includes every
approval route in the bank. Writing the evaluator first would mean inventing the policy in code.

**Dependency graph:** OD-P3-04 → evaluator → {RULE-01 administration, RULE-02 safety, WF-03 conditions} →
unblocks `rule:view|manage|publish` (3 of 22 unused permissions) and makes OD-P3-02 (simulation) actually useful.

**Do not start this area before the decision is signed.** Everything else in Phase 4 can proceed without it.

---

### 2.7 Idempotency — **NOT IMPLEMENTED** (mechanism present, zero consumers)

**BRD API-03** lists idempotency as a stable-contract requirement.

This is the most misleading item in the repository and it deserves exact language:

| Component | Exists? |
|---|---|
| `IdempotencyService` | Yes |
| `IdempotencyRecord` + repository | Yes |
| `IdempotentOperation` | Yes |
| `idempotency_records` table | Yes, and mapped |
| **Any endpoint using it** | **No** |

```
$ grep -rn "IdempotencyService" src/main/java | grep -vc "common/api/"
0
```

The mechanism works. The system does not use it. For a service whose POSTs create money requests and record
approval decisions, a retried submit or a duplicated approval has no protection beyond the state machine's own
refusals — and the state machine's refusals are *correct* but not *idempotent*: a client that retries receives a
422 where it expected the successful result of its first attempt.

**Dependencies:** none — a filter/interceptor plus a header contract.
**Recommendation: second, right after audit search.** Highest value per unit of effort in Phase 4.

---

### 2.8 Remaining API, security and integration gaps

| Item | Status | Detail |
|---|---|---|
| REST coverage | **PARTIAL** | 127 endpoints / 16 controllers / **0 unguarded**, but six modules absent |
| API-02 OpenAPI contracts | **PARTIAL** | springdoc 3.2.0 wired, `/v3/api-docs` explicitly permitted in `SecurityConfig`, controllers annotated — but no committed spec artifact and no examples. "Published" is not satisfied |
| API-01 versioned prefix | **NOT VERIFIED** | No version segment in paths. Whether that satisfies "versioned REST APIs" is a product call, not a defect |
| `request:view-all` | **NOT IMPLEMENTED** | No consumer. A finance user cannot list every request in the bank. **Real gap**, confirmed by `tools/permission-map.py` |
| SEC-01 MFA / SSO | **NOT IMPLEMENTED** | No MFA, no SSO path, no integration point |
| SEC-01 encryption at rest | **NOT VERIFIED** | An infrastructure property; not determinable from this repository |
| INT-01 master data adapters | **NOT IMPLEMENTED** | `integration_*` tables only |
| Async / scheduling | **NOT IMPLEMENTED** | Prerequisite for §2.2 and §2.3 |
| Workflow history read path | **PARTIAL** | Versions listable; a request's own action history is not |
| Workflow simulation (OD-P3-02) | **BLOCKED BY BUSINESS DECISION** | Needs the sample-facts contract |
| Version compare/rollback (OD-P3-03) | **BLOCKED BY BUSINESS DECISION** | Needs the "restore" semantics ruling |

---

## 3. Dependencies, ordered

```
OD-P3-04 (rule language)  ──►  business-rule evaluator  ──►  RULE-01/02, WF-03, rule:* permissions
                                                          └─►  OD-P3-02 simulation becomes useful

Storage decision          ──►  document upload/download  ──►  document:* permissions, audit events

Async + scheduling infra  ──►  notification outbox/delivery, report generation
                                  └─►  MAIL-02 secret handling (decision + security)

Audit retention decision  ──►  audit export design
                                                          └─►  AUD-03

OD-P2-01 (rounding)       ──►  financial report totals  ──►  dashboards with money
                                  (pending-approval dashboards need no decision)
```

Three decisions gate Phase 4 work: **storage**, **OD-P3-04**, **audit retention**. Everything else can proceed.

---

## 4. Recommended sequence

By value per unit of risk, and by what unblocks the frontend soonest:

| # | Item | Why here | Gate |
|---|---|---|---|
| 1 | **Audit search + request timeline** | Data already written; only a read layer missing; unblocks the activity timeline | None |
| 2 | **Idempotency on POSTs** | Machinery exists, zero consumers, protects money-moving calls | None |
| 3 | **Branding, theme, application settings** | Unblocks the frontend shell; no external dependency | None |
| 4 | **Documents** | Largest and riskiest; needs the storage decision before design | **Storage decision** |
| 5 | **Notifications + email** | Needs async infrastructure built first | Async infra + MAIL-02 |
| 6 | **Reporting / dashboards** | Split pending-approval metrics (no gate) from financial totals (OD-P2-01) | **OD-P2-01** for money totals |
| 7 | **Business-rule evaluator** | Strictly after the language is chosen | **OD-P3-04** |

Items 1–3 need no decision and no new infrastructure. Starting there is the lowest-risk way to begin Phase 4
while the three gating decisions are being obtained.

---

## 5. What Phase 4 must not do

Recorded because each is a specific temptation:

1. **Do not map `audit_events` or `idempotency_records`** — both are already mapped. `MIGRATION_ENTITY_REPORT.md`
   lists them as unmapped because its scan did not strip SQL comments; `tools/migration-entity-report.py`
   produces the correct list.
2. **Do not invent a rounding rule** for reports. OD-P2-01 is open, and a report that silently fixes the policy
   would make it harder to change later, not easier.
3. **Do not implement a rule evaluator before OD-P3-04**, even a "simple" one. RULE-02's prohibition on
   arbitrary code exists to prevent exactly the shortcut a simple evaluator represents.
4. **Do not treat the absence of notifications as a defect to patch** by emailing from inside a transaction.
   Delivery needs the outbox table that already exists precisely so that it is not done this way.
5. **Do not restore the phantom `in` and `time` tables** to any report.

---

## 6. Testing expectations for Phase 4

Phase 3 established the pattern Phase 4 should continue: every integration test runs against real PostgreSQL via
Testcontainers with Flyway applied and `ddl-auto: validate`, so a green run proves the schema and the mappings
agree. Phase 4 adds two new test classes of problem that pattern does not currently cover:

* **Object storage** — the document service will need a fake or an ephemeral container; uploading a real file
  through the real HTTP stack is the test that matters, because the attack surface *is* the endpoint.
* **Asynchronous behaviour** — outbox draining and report generation need deterministic tests, not `sleep()`.
  The existing `OptimisticLockingIntegrationTest` already demonstrates the technique (a barrier with a timeout
  rather than a race), and it should be the model.
