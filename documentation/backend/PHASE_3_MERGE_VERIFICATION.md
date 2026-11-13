# Phase 3 — Merge Verification and Phase 4 Readiness Review

**Verification date:** 2026-11-13
**Verified commit:** `7119739a7c839586f3aef3724886c20fb5e2ab73` (`main`)
**Method:** read-only inspection of the checked-out tree, `git` object inspection, GitHub API, and re-running
the repository's own measurement tooling. Nothing in `src/main` was modified to produce this document.

---

## 0. How this was verified, and what could not be

Read-only means exactly that. The following were performed against the live repository rather than accepted
from the Phase 3 delivery report:

| Check | Method | Trustworthy? |
|---|---|---|
| Branch and HEAD | `git rev-parse` | Yes |
| Phase 3 merged into `main` | merge commit parent inspection + tree hash comparison | Yes — exact, not inferred |
| Unmerged work | remote ref comparison against the Phase 3 branch head | Yes |
| Implementation present | direct source inspection of each named capability | Yes |
| Endpoint / permission counts | re-ran `tools/permission-map.py` on this tree | Yes — reproducible |
| Migration / entity counts | independent recount from the migration SQL and `@Table` annotations | Yes — **and it contradicts the committed report; see §2.10** |
| Test counts | CI annotation for the exact `main` commit | Yes |
| Test-class composition | CI annotation names all 23 classes | Yes |
| That tests *passed* | CI job conclusions | Yes |
| That a specific assertion is meaningful | reading the test source | Partially — see the limits below |

**This environment cannot run the build.** There is no JDK, no Maven, no Docker and no PostgreSQL, and no
access to Maven Central. Everything asserted about *execution* below is therefore read from CI, at an exact
commit SHA, not reproduced locally. That is a real limitation and is stated rather than glossed: a claim like
"287 tests passed" is a claim about run `43429695561`, and is verified only to the extent that GitHub's
record of that run is trusted.

One further limit, which matters for §2: **the clone is shallow.** `origin/main` arrives as a single grafted
commit, so `git merge-base --is-ancestor` returns a false negative and `git rev-list --count main..branch`
returns a meaningless 165. Ancestry was therefore established from **commit objects** — the merge commit's
parent list and its tree hash — which are complete and definitive. Anyone re-running naive ancestry commands
in this checkout will get wrong answers; that is an artifact of the clone, not of the repository.

---

## 1. Source control — VERIFIED

### 1.1 Current branch and HEAD

| Item | Value |
|---|---|
| Current branch | `arena/5de48ad1-edam` |
| HEAD | `7119739a7c839586f3aef3724886c20fb5e2ab73` — *Merge pull request #7 from Jhamankhatri/arena/2f919c55-edam* |
| Working tree | clean (`git status --porcelain` returns 0 lines) |
| Position relative to `main` | identical — 0 ahead, 0 behind |
| Local `main` == `origin/main` | yes, both `7119739a…` |

The verification branch was created from the merge commit, so it *is* `main`. No work exists on it that is not
on `main`, and none of the checks below depend on a feature branch.

### 1.2 Is Phase 3 actually merged? — YES, and this is established exactly

Not inferred from the PR's state, but from git objects:

```
$ git cat-file -p 7119739
tree 12b79f7d24210808091f99d1dbbb2780da63eb46
parent 4783a99b2e556798359e4bdb2f36e43f7275fc21   <- main before the merge
parent 60a5084b754b384f2d2720897b34734ce3de6fcd   <- Phase 3 branch head

$ main^{tree}          = 12b79f7d24210808091f99d1dbbb2780da63eb46
$ phase3-tip^{tree}    = 12b79f7d24210808091f99d1dbbb2780da63eb46
```

The merge commit has exactly the two expected parents, and **`main`'s tree is byte-identical to the Phase 3
branch head's tree**. There is no possibility of a partial merge, a dropped file or a conflict resolution that
silently discarded work: the content is the same content, by hash.

### 1.3 Comparison of `main` with the Phase 3 implementation branch

| | |
|---|---|
| Phase 3 branch | `arena/2f919c55-edam` |
| Remote head | `60a5084b754b384f2d2720897b34734ce3de6fcd` — *Record the pull-request run and correct the diff totals* |
| Merged at | `7119739a…`, 2026-11-13T12:58:47Z, merge-commit strategy |
| Tree difference | **none — identical, verified by hash** |
| Unmerged commits on that branch | **zero** |

Four commits landed after the Phase 3 tip on the original Phase 2 branch (`4783a99`) and are all contained in
`main` via the merge: the CI-record and diff-total corrections, the delivery report, the permission/migration
documentation, and the test-coverage documentation. Auditing against the *branch* rather than `main` would
have been the mistake the brief warned about; no such divergence exists here.

### 1.4 Uncommitted work

None. The tree is clean. Nothing was stashed, reset, rebased or deleted to perform this verification, and no
production code was touched — this document and the audit update are the only additions.

The other `arena/*` branches visible on the remote (`0f75b820`, `2de39563`, `35ee356d`, `79b46ab7`, `8a165b11`,
`a3508752`) are the historical Phase 0–2 branches. Their work is in `main` through the corresponding merge
commits (`a118364`, `4783a99`, `d69cf79`, `ff3a045`). They were left untouched, as instructed.

---

## 2. Phase 3 implementation — verified against the code

Each row states what was checked in the source, not what a document asserts. File paths below are relative to
`src/main/java/com/edam/`.

| Capability | Verdict | Evidence |
|---|---|---|
| Workflow configuration (definitions, drafts, states, steps, approvers) | **IMPLEMENTED** | `workflow/service/WorkflowConfigurationService.java` (646 lines), `WorkflowConfigurationController.java`, 11 endpoints |
| Version publication | **IMPLEMENTED** | `publish` at line 457 is `@Transactional`; retires the incumbent *before* publishing, because the unique index forbids two published revisions and the reverse order would leave the definition unroutable |
| Submission integration | **IMPLEMENTED** | `ExpenseRequestService.submit` calls `RequestWorkflowService.startForSubmission` at line 233; that method is `@Transactional(propagation = MANDATORY)` — it *cannot* run outside the submit transaction |
| Workflow transition resolution | **IMPLEMENTED, with a deliberate refusal** | `WorkflowEngine.resolveDeclaredTarget` (line 488) consults `workflow_transitions`. Any transition carrying a `condition_rule_id` throws rather than being treated as unconditional |
| Approval inbox | **IMPLEMENTED** | `ApprovalController` `GET /approvals` (paged) |
| Approval count | **IMPLEMENTED** | `GET /approvals/count` → `TasksRepository.countByAssignedUserIdAndStatus` |
| Approve / reject / revert | **IMPLEMENTED** | `ApprovalService.decide` switch at line 367–374 |
| Forward | **IMPLEMENTED** | `ApprovalService` line 404+, with an explicit refusal to forward to a deactivated account |
| Approver assignment enforcement | **IMPLEMENTED** | `requireAssigned` at line 348: rejects a settled task (`INVALID_STATE_TRANSITION`) and an unassigned caller (`ACCESS_DENIED`), the latter with the message that holding a permission does not confer authority over someone else's task |
| Recommending-authority and committee resolution | **IMPLEMENTED** | `WorkflowAssignmentResolver` resolves `USER`, `ROLE`, `DEPARTMENT_HEAD`, `MANAGER`, `CEO`, `CFO`, `HEAD_OF_ADMIN_GSD`, `RECOMMENDING_AUTHORITY`, `EXPENSE_COMMITTEE` |
| Transactional behaviour | **IMPLEMENTED** | `MANDATORY` propagation on both workflow entry points; publication is a single transaction |
| Optimistic locking | **IMPLEMENTED** | `AuditableEntity.version` `@Version` (line 78) inherited by `ExpenseRequest` (line 43); `WorkflowTask` has its own. `GlobalExceptionHandler` maps `OptimisticLockingFailureException` → `409 CONCURRENT_MODIFICATION` |
| Workflow history | **PARTIAL** | `GET /workflows/{id}/versions` exposes versions; there is *no* endpoint exposing a request's own action history |
| Audit history | **PARTIAL** | Writing is complete (`workflow_actions` + `audit_events`); **reading is not implemented** — no audit query endpoint exists |

### 2.1 The missing audit read path — a genuine gap

Phase 3 writes an audit record for every decision (`ApprovalService` line 379, forward at 418, publication at
493). What does not exist is any way to *read* them: there is no `AuditController`, and grep for audit
endpoints across all 16 controllers returns only prose in javadoc. An auditor can therefore only read the trail
with direct SQL.

This is not a Phase 3 regression — it was never in Phase 3's scope — but it does mean "workflow and audit
history" cannot be marked IMPLEMENTED as a *user-facing* capability, and it is the single largest item
carried into Phase 4 (§5.4).

### 2.2 Transition resolution is real, not decorative

`V8` created `workflow_transitions` and recorded in its own comment that nothing read it. That is now false:
`resolveDeclaredTarget` reads it, and the "declares nothing → built-in semantics" rule keeps every workflow
published before this change behaving identically. An undeclared action is refused rather than defaulted,
which is the safe direction — the alternative approves a request through fewer approvers than configured.

---

## 3. CI evidence — for the exact merge commit

| Item | Value |
|---|---|
| CI run | https://github.com/Jhamankhatri/edam/actions/runs/43429695561 |
| Commit SHA | `7119739a7c839586f3aef3724886c20fb5e2ab73` |
| Branch | `main` (event: `push`) |
| Conclusion | **success**, attempt 1, 2m36s |
| Test classes executed | **23** (all named in the run annotation, listed below) |
| Total tests recorded | **287** |
| Executed (287 − 3 skipped) | **284** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **3** |

The 287 is a count of every `<testcase>` element in the Surefire XML, which **includes** the skipped ones —
`tools/ci-report.py` sums `len(cases)` per report without filtering on `<skipped>`. So 284 tests actually ran
and all 284 passed; the honest headline is "284 executed, 0 failed, 3 skipped of 287 recorded".

The 23 classes, read from the run's own annotation:

```
ApprovalLimitApiTest              EndpointAuthorizationArchitectureTest  ApiPathsTest
GlobalExceptionHandlerTest        CorrelationIdFilterTest               CorrelationIdTest
OptimisticLockingIntegrationTest  EdamPrincipalTest                     InstitutionContextTest
ApprovalLimitResolutionTest       ExpenseRequestApiTest                 ExpenseRequestLifecycleTest
RequestNumberGeneratorTest        RequestValidatorTest                  AuthenticationIntegrationTest
PasswordPolicyTest                PackagedWarTest                       WebLayerSecurityTest
ApprovalActionsApiTest            ApproverResolutionTest                SubmissionIntegrationTest
WorkflowConfigurationApiTest      WorkflowStateModelTest
```

### 3.1 Jobs

| Job | Result | What it proves |
|---|---|---|
| `build-and-test` | success | `mvn verify` — compile, unit + integration tests |
| `war-package-smoke-test` | success | The packaged WAR boots, renders JSP, enforces security, answers health |
| `prod-profile-smoke-test` | success | The production profile starts against a real database |

Both smoke jobs ran steps *Initialize containers* → success, i.e. real container startup, not a mock.

### 3.2 PostgreSQL / Testcontainers, Flyway and schema validation

Verified in the source rather than taken from the report:

* `src/test/java/com/edam/testsupport/PostgreSqlTestContainer.java` starts `postgres:18-alpine` as a singleton.
  The javadoc states the reason explicitly: real PostgreSQL so that *"Flyway migrations, partial indexes,
  regular-expression CHECK constraints, TIMESTAMPTZ and identity columns"* are exercised exactly as in
  production, because an H2 approximation would hide precisely the dialect problems that matter.
* `AbstractPostgreSqlIntegrationTest` injects that container's coordinates via `@DynamicPropertySource`; 12
  of the 23 classes are Spring/DB-backed.
* `application.yml` line 18 sets `jpa.hibernate.ddl-auto: validate` with the comment *"Flyway owns the
  schema; Hibernate only verifies it"*; `application-test.yml` line 10 agrees. Flyway is configured at line 26.
* Consequence: **every mapped entity must match a real migrated table at startup**, or the context fails and
  every DB-backed test errors. A green run is therefore direct evidence that the schema and the mappings agree.

### 3.3 The 3 skipped tests — identified, and benign

They are `PackagedWarTest` (3 `@Test` methods), skipped by `assumeThat(WAR).exists()`. Surefire runs before
`package`, so no WAR exists at test time. This is a deliberate, documented assumption — and the packaging
contract they guard is genuinely verified, by the `war-package-smoke-test` job that *runs* the WAR. The same
3 tests were skipped at Phase 2, so this is unchanged, not new.

### 3.4 222 → 287: explained exactly, not approximately

| | Phase 2 (`4783a99`) | Phase 3 (`7119739`) | Δ |
|---|---|---|---|
| Test classes | 17 | 23 | +6 |
| Tests | 222 | 287 | **+65** |
| Skipped | 3 | 3 | 0 |

The six new classes and their test counts, counted from source:

| Class | Tests |
|---|---|
| `WorkflowConfigurationApiTest` | 19 |
| `ApprovalActionsApiTest` | 16 |
| `ApprovalLimitApiTest` | 14 |
| `SubmissionIntegrationTest` | 7 |
| `ApproverResolutionTest` | 7 |
| `OptimisticLockingIntegrationTest` | 2 |
| **Total** | **65** |

`222 + 65 = 287` exactly. No existing test was deleted or disabled — the only two pre-existing test files
touched (`ExpenseRequestApiTest`, `ExpenseRequestLifecycleTest`) gained *fixture* lines, because from Phase 3
onward submission refuses a request type with no published workflow, so those fixtures must publish one. Their
assertions were not weakened to accommodate the new behaviour; they were made dependent on a precondition that
is now genuinely required.

Phase 2's figures were re-confirmed from run `43400205080` (17 classes / 222 / 3 skipped) rather than carried
over from documentation.

---

## 4. Consolidated open-decision register

Both logs were read in full and reconciled. Fourteen identifiers exist; two are closed and are retained for
continuity rather than silently dropped.

### 4.1 Register

| ID | Title | Status | Blocks real-money use | Blocks Phase 4 | Category |
|---|---|---|---|---|---|
| **OD-P2-01** | Currency conversion direction and rounding | **OPEN** | **Yes — the primary one** | No | Must close before money |
| **OD-P2-02** | CAPEX department eligibility | **CLOSED** → consolidated into OD-P2-07 | — | — | — |
| **OD-P2-03** | What "exceeding a limit" escalates *to* | **OPEN** | **Yes** | No | Must close before money |
| **OD-P2-04** | Department limit per user or per department | **PARTIAL** — both implemented, "lower wins" chosen | Yes (verify the choice) | No | Must confirm |
| **OD-P2-05** | Request numbering scheme | **OPEN** (implemented, format provisional) | No | No | Optional |
| **OD-P2-06** | Are the three CAPEX figures independent? | **CLOSED** — answered by BRD §8.3 | — | — | — |
| **OD-P2-07** | Configurable CAPEX department eligibility | **OPEN** | Yes | No — but shapes Phase 4 config screens | Must close before money |
| **OD-P2-08** | Personal vs departmental limit precedence | **OPEN** (implemented conservatively) | **Yes** | No | Must close before money |
| **OD-P3-01** | CEO/EC rejection and selective revert | **OPEN** | **Yes** — a governance control is unenforced | No | Must close before money |
| **OD-P3-02** | Workflow simulation | **OPEN** (not implemented) | No | No — but natural Phase 4 deliverable | Optional / Phase 4 |
| **OD-P3-03** | Version compare and rollback | **OPEN** (not implemented) | No | No | Optional / Phase 4 |
| **OD-P3-04** | Conditional transitions and rule evaluation | **OPEN** (refused, not evaluated) | Yes, if conditional routing is needed | **Yes** — Phase 4 needs the evaluator | **Must close before Phase 4 rules work** |
| **OD-P3-05** | Cross-institution workflow cloning | **OPEN** (out of scope by design) | No | No | Optional |
| **OD-P3-06** | Who may read a request in approval | **RESOLVED** | — | — | Closed |

### 4.2 As the brief grouped them

**Block real-money production use (5):** OD-P2-01, OD-P2-03, OD-P2-07, OD-P2-08, OD-P3-01. Every one of these
is a case where the current behaviour is defensible and *documented*, but a bank putting real money through the
system would be relying on a policy nobody signed off. The pattern is consistent: where the BRD was silent the
code chose the refusal or the conservative direction, which is the right engineering answer and still not a
business decision.

**Block Phase 4 (1):** **OD-P3-04.** Phase 4's business-rule module and the conditional transitions that need it
are the same evaluator. Building it without an answer about which conditions are permitted, and how precedence
resolves, would mean inventing the policy inside the evaluator — exactly what the decision log exists to
prevent.

**Optional enhancements (4):** OD-P2-05, OD-P3-02, OD-P3-03, OD-P3-05. None blocks anything. OD-P3-02 and
OD-P3-03 are natural Phase 4 items because they belong to the workflow designer's surface.

### 4.3 One correction to the record

**OD-P2-01's description of the rounding is inaccurate, and I could not leave it.** The Phase 2 log states that
rounding *"is inherited from the column rather than chosen, because choosing one (HALF_UP vs HALF_EVEN) is a
financial policy decision"*.

The code does the opposite of that. `ExchangeRateResolver.scale` (line 97) is:

```java
return value.setScale(4, RoundingMode.HALF_UP);
```

A rounding mode **has** been chosen, explicitly, in one place. Its own javadoc (lines 37–41) is candid about
it — *"rounds to four decimal places with RoundingMode#HALF_UP"* — so only the decision log is wrong, not the
implementation. The distinction matters for two reasons: an approver comparing `requestedAmount` against
`baseCurrencyAmount` is seeing a figure rounded by a policy no one approved; and the log's stated closure path
("change is confined to `ExchangeRateResolver`") is still true, but only because the choice is isolated in one
method — not because no choice was made.

**Impact:** does not change the status (still OPEN, still blocking real-money use). Changes what a reader is
told is *pending*: there is an existing `HALF_UP` decision awaiting ratification, not an absence of one.

---

## 5. Phase 4 scope and dependencies

Assessed against BRD §22, §26–§33 and the v2.0 requirement register (36 ids, §37), and against what actually
exists in the tree.

**Six modules are entirely absent:** `configuration`, `businessrules`, `documentmanagement`, `notification`,
`reporting`, `integration`. No package, no controller, no service.

### 5.1 Documents and attachments — NOT IMPLEMENTED

**Requirements:** BRD §31, DOC-01.
**Exists:** `media_assets` table (V6, currently unmapped); `request_documents` (V9) links an *existing* asset
to a request and is honoured by the request API — but nothing can create an asset.
**Missing:** upload, download, preview, metadata management, validation (extension/MIME/signature/size/
filename), malware scanning hook, retention/archive/replacement, opaque storage keys, authorized download,
and audit of upload/download/replace/delete. The BRD is prescriptive here: *"never expose predictable public
file paths"*, TLS in transit, and no document contents in logs.
**Dependency:** object storage or a secured filesystem — an infrastructure decision, not just code. The
`media_assets` table must be mapped (it is one of the 28 unmapped).
**Risk:** highest first-mover risk in Phase 4. It is the only area with a file-system/object-store dependency
and a security surface (upload) that cannot be deferred.

### 5.2 Notifications and email — NOT IMPLEMENTED

**Requirements:** BRD §28, NOT-01, MAIL-01…04.
**Exists:** six tables from V10 (`notifications`, `notification_templates`, `notification_outbox`,
`notification_deliveries`, `notification_preferences`, `email_provider_configs`) — all unmapped and inert.
**Missing:** everything in Java. No in-app inbox, no template designer, no provider administration, no
delivery tracking, no retry, **and no async/scheduling infrastructure** — grep for `@EnableAsync`,
`@EnableScheduling` and `@Async` across `src/main` returns nothing. There is a `scheduled_jobs` table too.
**Dependency:** MAIL-02 (vault/KMS envelope encryption for SMTP credentials) is a security decision with no
existing implementation. Async execution is a prerequisite for both delivery and report generation.
**Note:** the approver inbox is *not* a notification system — it computes open tasks on read. Nothing pushes.

### 5.3 Reporting and dashboards — NOT IMPLEMENTED

**Requirements:** BRD §27, §33, DASH-01/02.
**Exists:** tables only (`report_definitions`, `dashboard_definitions`, `dashboard_widgets`,
`user_dashboard_preferences`), all unmapped.
**Missing:** every metric, every report, all export, drill-down.
**Dependency:** §33 makes several *report semantics* decisions: KPI formulas, the currency-conversion basis for
aggregates, time zone, and the treatment of cancelled/rejected/reverted requests. The conversion basis is
**OD-P2-01** again — a report that aggregates in base currency cannot be reproducible while the rounding policy
is unratified. Asynchronous generation, expiry and export audit are also required, so this shares §5.2's
missing infrastructure.

### 5.4 Audit search and export — PARTIAL

**Requirements:** BRD §26, AUD-01/02/03.
**Exists and verified:** `audit_events` is **mapped** (`common/audit/AuditEvent.java`) and every decision,
publication, role grant and profile change writes to it. Redaction exists (`AuditValueRedactor`).
Before/after capture exists via `AuditRecorder.recordChange` — but is used by **5 call sites only**
(`OrganizationProfile`, `OrganizationUnit`, `Department`, `Role`, `User`).
**Missing:** the entire read surface. No search by actor/date/action/resource/outcome (AUD-01). No record-level
timeline for a request. Before/after is **not** captured for workflow definitions, business rules, email
settings or master data — AUD-02 is only partly satisfied. No export (AUD-03).
**Verdict:** writing IMPLEMENTED, reading NOT IMPLEMENTED. The cheapest high-value Phase 4 item, because the
data is already being collected — only the query layer is absent.
**Dependency:** §26's OPEN DECISION on retention, immutability, SIEM and export approval is a *business*
decision, and it gates export design.

### 5.5 Theme, branding and application settings — NOT IMPLEMENTED

**Requirements:** BRD §22, BRD-UI-01…03.
**Exists:** `branding_themes`, `theme_tokens`, `application_settings`, `system_parameters`, `feature_flags`
(V6) — all unmapped. `feature_flags` is referenced in configuration-plane code from earlier phases but has no
entity.
**Missing:** the configuration API. This is the area the React frontend needs *before* it can render a branded
shell, which makes it a frontend-integration blocker rather than a nicety.

### 5.6 Controlled business-rule evaluation — NOT IMPLEMENTED, and gated

**Requirements:** BRD §25, RULE-01/02, WF-03 (conditional routing).
**Exists:** `business_rules` table with a `condition_rule_id` foreign key now enforced (V17) and validation
that reports a conditional transition at publication time. A transition carrying a condition is **refused** at
action time.
**Missing:** the evaluator. `com.edam.businessrules` does not exist.
**Verdict: BLOCKED BY BUSINESS DECISION — OD-P3-04.** RULE-02 requires "constrained expressions/decision
tables; prohibit arbitrary code and SQL execution". Choosing that expression language is a security decision
with a large blast radius, and it also determines whether the four `rule:*` permissions can ever be used. This
must be decided before any code is written, not discovered during it.

### 5.7 Idempotency — **NOT IMPLEMENTED (mechanism present, zero consumers)**

**This is the most misleading item in the repository, and it deserves precision.**

`IdempotencyService`, `IdempotencyRecord`, `IdempotencyRecordRepository`, `IdempotentOperation` and the
`idempotency_records` table all exist and work. But:

```
$ grep -rn "IdempotencyService" src/main/java | grep -vc "common/api/"
0
```

**Not one endpoint uses it.** For a system whose POST endpoints record money requests and approval decisions,
the absence of idempotency on those POSTs is a real correctness risk, not a cosmetic gap: a duplicated submit
or a retried approval has no protection beyond the state machine's own refusals. Earlier phase notes describe
idempotency as "implemented"; that is true of the mechanism and false of the system. API-03 lists idempotency
as a stable-contract requirement.

**Recommendation:** treat as the highest value-per-unit-effort Phase 4 item after audit search. The machinery
already exists; what is missing is a filter or interceptor plus a header contract.

### 5.8 Remaining API, security and integration gaps

| Item | Verdict | Detail |
|---|---|---|
| REST API coverage | **PARTIAL** | 127 endpoints / 16 controllers, but six modules absent entirely |
| OpenAPI contracts (API-02) | **PARTIAL** | springdoc 3.2.0 present; `/v3/api-docs` explicitly permitted in `SecurityConfig` (line 90); controllers are annotated. But no committed spec artifact and no examples — API-02 asks for *published* contracts |
| Versioned API prefix | **NOT VERIFIED** | API-01 says "versioned JSON REST APIs". Paths carry no version segment. Whether this is acceptable to the bank is a decision, not a defect |
| `request:view-all` gap | **NOT IMPLEMENTED** | No endpoint consumes it; no finance user can list every request in the bank. Confirmed by re-running `tools/permission-map.py`: 22 of 58 permissions unused |
| MFA / SSO (SEC-01) | **NOT IMPLEMENTED** | No MFA or SSO path exists |
| Master data adapters (INT-01) | **NOT IMPLEMENTED** | `integration_*` tables only |
| Encryption at rest (SEC-01) | **NOT VERIFIED** | No application-level encryption; would be an infrastructure property. Not verifiable from this repository |
| Async / scheduling infrastructure | **NOT IMPLEMENTED** | Required by §5.2 and §5.3 |

### 5.9 Two counts in the Phase 3 documents that do not survive re-measurement

The brief says not to accept documentation as evidence. Applying that to the Phase 3 documents themselves
found three errors — all in documentation, none in code, and none affecting the merge verdict:

1. **`MIGRATION_ENTITY_REPORT.md` overstates the unmapped tables.** It reports 50 mapped / 32 unmapped. Measured
   on this tree: **80 live tables, 52 mapped, 28 unmapped, 0 entities without a table.** Two causes:
   (a) `audit_events` and `idempotency_records` are listed as unmapped, but both are mapped
   (`common/audit/AuditEvent.java`, `common/api/IdempotencyRecord.java`); (b) the list contains `in` and
   `time`, which are not tables at all — they are regex matches against the comment prose *"every CREATE TABLE
   in V5..V12 would fail"* (V4_1) and *"resolves the referenced unique constraint at CREATE TABLE time"*
   (V7). The committed report was generated by a scan that did not strip SQL comments, and two comment lines
   became phantom rows.
2. **`PHASE_3_DELIVERY_REPORT.md`'s diff totals were stale** (34 files / 6,559 insertions). The branch tip
   corrects this to 35 / 6,774; re-measured against `main` it is consistent, so this was already fixed.
3. **OD-P2-01's rounding description** — corrected in §4.3 above.

Item 1 is the one worth acting on: a migration report that lists `audit_events` as unmapped would tell a
Phase 4 planner to map a table that is already mapped, and the two phantom rows inflate the gap by 2. A corrected generator
(`tools/migration-entity-report.py`, comment-stripped) has been added on this branch, and the corrected
figures are used throughout §5.

---

## 6. Backend readiness verdict

### 6.1 Is Phase 3 merged?

**Yes.** `main` is `7119739`, its tree is byte-identical to the Phase 3 branch head, and the CI run at that
exact SHA is green on all three jobs (287 tests, 0F/0E/3 skipped). This is not inferred from a PR page; it is
established from commit objects and the run's own annotation.

### 6.2 Is the backend ready for frontend integration?

**Not yet — and the obstacles are now specific rather than general.**

What a React frontend needs, checked one by one:

| Need | State |
|---|---|
| Authentication, CSRF, session, bootstrap | **Ready** — Phase 1, plus the CSRF cookie defect fixed in Phase 3 |
| Authorization and 403/401 envelope | **Ready** |
| Request CRUD, drafts, submission | **Ready** |
| Approval inbox, case file, four decisions | **Ready** |
| Workflow configuration screens' data | **Ready** (11 endpoints) |
| Branding, theme, logo, application settings | **NOT IMPLEMENTED** — §5.5 |
| An audit timeline to show on a request | **NOT IMPLEMENTED** — no read path, §5.4 |
| Document upload/preview | **NOT IMPLEMENTED** — §5.1 |
| In-app notification list | **NOT IMPLEMENTED** — §5.2 |
| Published OpenAPI spec to code against | **PARTIAL** — live docs exist, no committed artifact |

So: a frontend *can* build the request and approval journeys today against stable, tested contracts. It cannot
build branding, document handling, notifications or an activity timeline, because the endpoints do not exist.
Claiming "backend ready" would be false; claiming "nothing is buildable" would be equally false.

### 6.3 Verdict

**Phase 3: COMPLETE AND MERGED.** Verified at commit `7119739a`, CI run `43429695561`: **284 tests executed,
284 passed, 0 failed, 0 errored, 3 skipped** out of 287 recorded; all three jobs green.

**Phase 4: READY TO START, WITH THREE DECISIONS REQUIRED FIRST.**

Phase 4 is not blocked in general. It is blocked specifically on:

1. **Storage decision** (§5.1) — object store vs secured filesystem, and the malware-scanning hook. Gates the
   largest module.
2. **OD-P3-04** (§5.6) — the rule-expression language. Gates RULE-01/02 and WF-03, and cannot be designed
   around without inventing policy.
3. **Audit retention and immutability** (§5.4) — gates audit export design.

Everything else in Phase 4 can proceed without a decision.

**Recommended ordering within Phase 4,** highest value per unit of risk:

1. **Audit search** — data already collected, only a query layer missing; unblocks the request timeline the
   frontend needs.
2. **Idempotency on POSTs** — machinery exists, zero consumers, and it protects money-moving operations.
3. **Branding and application settings** — unblocks the frontend shell; no external dependency.
4. **Documents** — largest and riskiest; start once the storage decision is made.
5. **Notifications and email** — needs async infrastructure first.
6. **Reporting and dashboards** — needs the currency-rounding decision (OD-P2-01) to be meaningful.
7. **Business rules** — strictly after OD-P3-04.

### 6.4 What this verification did **not** establish

Stated so the verdict is not read as broader than the evidence:

* Nothing was **executed** here. No build, no tests, no migrations were run in this environment; execution
  evidence is CI's, at a named SHA.
* The **superseded Phase 3 documentation was not rewritten.** Two errors were found and are corrected here
  rather than by editing the historical reports in place, on the principle used throughout this project that a
  record which silently loses its mistakes cannot be audited. `MIGRATION_ENTITY_REPORT.md` still carries its
  original figures and its two phantom rows; `tools/migration-entity-report.py` now produces the correct ones,
  and the corrected figures are what §5 relies on.
* **Behaviour under real money was not tested** — no end-to-end scenario with an actual bank's currency pairs,
  limits and committees has been exercised. The five money-blocking decisions in §4.2 are precisely the places
  where such a test would fail.
