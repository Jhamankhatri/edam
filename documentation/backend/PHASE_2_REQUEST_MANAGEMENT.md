# Phase 2 — OPEX/CAPEX request management, approval limits and recommending authorities

Status: **implemented, not yet verified by CI.** No compile or test result exists for this work at the time of
writing; the workspace has no JDK, no Maven and no Docker, so evidence comes only from the GitHub Actions run.
Nothing in this document should be read as a passing result.

## 1. Scope delivered

| Capability | Where |
|---|---|
| OPEX request creation, edit, submit, cancel | `com.edam.expenserequest` |
| CAPEX request creation, edit, submit, cancel | `com.edam.expenserequest` |
| Request numbering | `RequestNumberGenerator` + `expense_request_number_seq` (V16) |
| Currency conversion with rate provenance | `ExchangeRateResolver` |
| Conditional-field validation | `RequestValidator` (49 unit assertions) |
| Department / personal approval limits | `com.edam.approvallimits` + V16 exclusion constraints |
| Exceeding-limit rules (configure + evaluate) | `ExceedingLimitRuleService` |
| Recommending authorities | `RecommendingAuthorityService`, wired into `WorkflowAssignmentResolver` |

## 2. Structural decisions and why

### One header, two detail tables
Already the shape of `V9`. The entities follow it exactly rather than inventing a parallel model. No new
request table was created.

### Domain rules live in `RequestValidator`, not in annotations
`@NotNull` and `@Size` cannot express "a recurring expense must state its cadence" or "the invoice total must
equal the approval amount". Those rules are a single pure function so they can be tested without Spring and
read in one place. Cross-field failures are collected exhaustively: a form with four mistakes returns four
field errors, not the first one.

### The total of a line item is computed, never accepted
`expense_request_items` has a database CHECK: `total_amount = round(quantity * unit_price, 4)`. The entity
computes the total with `HALF_UP` so the Java value and the constraint agree by construction. A
caller-supplied total could only ever disagree and surface as an opaque persistence error.

### `available_amount` is derived, not supplied
Same reasoning for `capex_budget_summaries` (`available = allocated - consumed`). The constructor takes two
figures, so a row that violates the CHECK cannot be built in Java either.

### Submission does not route
`ExpenseRequestService.submit` validates, marks the request `SUBMITTED` and stops. Approver selection is the
workflow engine's job in Phase 3. No approval ladder exists in this module, which is what the brief required:
"do not introduce hardcoded approval routing that bypasses the future workflow engine."

Consequence, stated plainly: **a submitted request currently has no workflow instance.** It is not faked. The
header column `current_workflow_instance_id` is nullable, the state machine already accepts the instance id,
and `WorkflowEngine.start(...)` is the documented next step.

### The number comes from a sequence
`MAX(number)+1` races under concurrency. The sequence allocates outside the transaction, so a rollback burns a
number and leaves a gap. Gaps are preferred to reuse: two requests sharing a number is an audit failure, a gap
is not.

### Approval limits: the lower of personal and departmental binds
Recorded as OD-P2-04/OD-P2-08. A personal limit can tighten authority but never widen it. The inverse would
let an individual approve more than the head of their own department. Implemented conservatively so a
misconfiguration refuses rather than over-approves.

### Recommending authorities now resolve
`WorkflowAssignmentResolver` previously logged a debug line for `RECOMMENDING_AUTHORITY` and resolved nobody,
so a step designated that way could never open a task. It now resolves from `recommending_authorities` at the
current instant. `EXPENSE_COMMITTEE` still resolves nobody and now logs a warning instead of a debug line —
the committee module is Phase 3.

## 3. Schema changes (V16, forward-only)

All additive. No column dropped, no constraint weakened, no existing row rewritten.

- `CREATE SEQUENCE expense_request_number_seq`
- `ex_department_approval_limits_no_overlap` — GiST exclusion, department + currency + type + holder + period
- `ex_recommending_authorities_no_overlap` — GiST exclusion, department + type + period
- `ex_exceeding_limit_rules_no_overlap` — GiST exclusion, currency + nature + type + threshold range
- Two lookup indexes matching the resolvers' predicates
- `expense_requests.cancelled_at / cancelled_by / cancel_reason`
- `request_documents.required_by_rule`

Verified: `pglast` parses the migration. No seed rows exist in any of the three configuration tables, so the
exclusion constraints cannot fail on existing data.

## 4. Entity ↔ schema mapping

Mapped by hand from the DDL, then machine-checked column by column against the `CREATE TABLE` /
`ALTER TABLE ADD COLUMN` statements, because `ddl-auto: validate` makes any drift a startup failure.

11 request entities and 3 configuration entities. Every `@Column(name=...)` was compared to the real column
list; the only initial mismatches were the four V16 columns, which exist.

`expense_previous_summaries` and `expense_invoices` were added to the aggregate during this phase: V9 created
them, and leaving them unmapped would have meant the BRD's "previous expense summary" and invoice-total rules
had no storage path.

## 5. Authorization

Every endpoint carries `@EdamPermission` from the existing catalogue. No new permission codes were invented,
so the 30/58 orphan count from the Phase 1 audit is unchanged rather than worsened.

Service-layer authorization is separate and narrower: authorship for edit/submit/cancel, data scope for
read. A caller holding `request:submit` still cannot submit someone else's request. Out-of-scope reads return
404, not 403.

## 6. Tests written

| Class | Kind | Assertions |
|---|---|---|
| `RequestValidatorTest` | pure unit, no context | 49 |
| `ApprovalLimitResolutionTest` | unit with mocked repositories | 15 |
| `RequestNumberGeneratorTest` | pure unit | 4 + parameterised cases |
| `ExpenseRequestLifecycleTest` | Spring + Testcontainers PostgreSQL | 20 |

The integration class is deliberately not `@Transactional`, so assertions observe committed state: a
test-owned transaction cannot catch a deferred constraint, an unread generated id, or a failed rollback.
Fixtures use random codes and build their own organization unit per department, because
`uk_departments_unit` makes `organization_unit_id` unique.

## 7. Open decisions raised or changed

- **OD-P2-01** conversion direction and rounding — still open, isolated in `ExchangeRateResolver.scale`.
- **OD-P2-02** CAPEX eligibility — corrected and consolidated into **OD-P2-07** (no per-department
  configuration surface exists; the seam permits everyone, which is the safe direction for an omission).
- **OD-P2-06** CAPEX figure equality — **closed**: BRD §8.3 states the three figures "must be positive and
  equal". An earlier revision wrongly listed it as open.
- **OD-P2-08** personal vs departmental limit precedence — recorded as a governance decision.

## 8. Known gaps in this phase

1. **Submission does not start a workflow.** Needs a published version per request type, which is Phase 3
   configuration. Until then a submitted request is visible but unrouted.
2. **No request reversal** (`request:reverse` exists in the catalogue but has no endpoint). Reversal is a
   post-approval operation and belongs with the approval machinery.
3. **Document upload is not implemented.** `request_documents` links an existing `media_assets` row; creating
   that row is Phase 4. The conditional-attachment rules are therefore enforced against document *types*, and
   cannot yet verify that the bytes behind an asset are the right kind of file.
4. **CAPEX department eligibility permits everyone** (OD-P2-07).
5. **Exceeding-limit rules are evaluated but not enforced** (OD-P2-03).
6. **No API-level (HTTP) tests yet.** The service is covered; the controllers are covered by the endpoint
   authorization architecture test and by Spring's own binding, but no test drives a request through HTTP
   with a real principal. That is a gap against the brief's requirement for API tests.
7. **Concurrent-modification behaviour is not exercised.** `@Version` is present on every entity and
   `GlobalExceptionHandler` maps `OptimisticLockingFailureException` to 409, but no test provokes a conflict.

## 9. What must be true before Phase 3 starts

- CI green on this branch, including the four new test classes against real PostgreSQL.
- Items 6 and 7 above closed: at least one HTTP test per controller group and one optimistic-lock conflict
  test.
