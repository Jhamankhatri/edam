# Phase 4A Step 1 — Post-merge Corrections

Branch: `feature/phase-4a-step1-audit-corrections`
Base: `main @ 5073570a918eb6ce58ee67f1df47b35cfdb2b026` (PR #9 merge)
CI (GitHub Actions run `43490130165`): **green** — `Classes: 24, Tests run: 304, Failures: 0, Errors: 0, Skipped: 3`; WAR smoke + prod smoke pass.

This PR applies three focused corrections to the audit search and request activity timeline that
shipped in Phase 4A Step 1.

## Correction A — DB-level row authorization on global audit search

`AuditRepository.search` is now an authorization-aware native query. It left-joins
`expense_requests` for events whose `entity_type = 'ExpenseRequest'` and applies the same
visibility predicate as `ExpenseRequestService.isVisible`:

```
e.entity_type <> 'ExpenseRequest'
 OR r.id IS NULL
 OR :installationWide = true
 OR r.requested_by = :callerId
 OR r.department_id = any(cast(:deptIds as uuid[]))
 OR (r.branch_id IS NOT NULL AND r.branch_id = any(cast(:branchIds as uuid[])))
```

- Filtering happens **before** pagination; `totalElements` is computed by an equivalent
  `countQuery` so totals never leak invisible rows.
- The controller already calls this from a read-only transaction with `Scope.from(caller)`
  producing `callerId`, `installationWide`, `deptIds[]`, `branchIds[]`; no N+1 post-filter.
- Non-ExpenseRequest events (configuration / master data / workflow-definition events) are
  still visible to any caller holding `audit:view`, matching AUD-01 installation-wide audit
  read semantics.
- Detail endpoint (`GET /api/v1/audit-events/{id}`) already re-authorizes via
  `requestVisibility.isVisible`; the test `requestEventEnforcesVisibility` now asserts that
  *both* list and detail 404 for an out-of-scope auditor.

Native-query parameters are cast (`cast(:action as text)`, `cast(:deptIds as uuid[])`, etc.)
to prevent Postgres/Hibernate type-inference from resolving parameters as `smallint` against
text columns (the cause of an early failure during development).

## Correction B — Before/after diff coverage (AUD-02, PARTIAL)

The corrections add targeted `recordChange(before, after)` calls for high-value paths where a
well-defined prior state exists in memory:

| Entity | Operation | Fields captured |
|---|---|---|
| `ExpenseRequest` | `ExpenseRequestService.update` | requestTypeId, categoryId, subcategoryId, natureId, departmentId, branchId, currencyId, requestedAmount, baseCurrencyAmount, justification — via a `requestSnapshot(request)` helper producing a controlled field map; never full entity serialization |
| `WorkflowTask` | `ApprovalService.act` (APPROVE / REJECT / REVERT) | after-map: remarks (through existing `AuditValueRedactor` path), resulting requestStatus, workflowStateId, workflowCompleted |
| `WorkflowTask` | `ApprovalService.forward` | after-map: remarks, forwardedToUserId, openedTaskIds |

The `AuditValueRedactor` already runs on `oldValues` / `newValues` JSON keys
(`password`, `token`, `secret`, `authorization`, etc.), so remarks routed through
`recordChange` keep existing redaction guarantees.

### OD-P4A-03 retained (single source of truth for workflow actions)

Per standing direction, `workflow_actions` remains the authoritative per-step history. The
central audit event written from `ApprovalService.act/forward` is the *link* from the audit
trail to `workflow_actions` (entityId = taskId, after-map carries workflowStateId /
openedTaskIds / workflowCompleted); it does **not** duplicate the full action record and does
not replace the `workflow_actions` row.

### Coverage matrix (truthful status)

The remaining 20-ish master-data / configuration entities (Department, Branch, Currency,
Role, User, Permission, WorkflowDefinition, WorkflowVersion, …) are left with their existing
single-line `audit.record(...)` calls and **no** fabricated before/after. AUD-02 is therefore
**PARTIALLY IMPLEMENTED**:

| Category | AUD-02 before/after |
|---|---|
| Expense request routing fields (update) | IMPLEMENTED |
| Approve / Reject / Revert outcome | IMPLEMENTED (after-map only; before is the previous state captured by the preceding event/action) |
| Forward (delegation) outcome | IMPLEMENTED (after-map only) |
| Configuration entities (Department, Branch, Currency, ExpenseCategory, …) | NOT IMPLEMENTED (single `record` call; no diff) |
| Master-data CRUD (Role, User, Permission, etc.) | NOT IMPLEMENTED |
| Workflow definition/version/publish | NOT IMPLEMENTED (single `record` call) |
| System/session events (login, auth) | N/A (no before/after semantics) |

No historical rows are modified; audit remains append-only.

## Correction C — Cursor-based keyset pagination on the activity timeline

`GET /api/v1/expense-requests/{id}/activity` previously loaded up to 2000 events in memory and
capped the response. It now uses reliable keyset pagination across the three sources (audit
events, workflow tasks, workflow actions).

### Model

`TimelinePage` (in `audit.dto.AuditDtos`):

| Field | Type | Meaning |
|---|---|---|
| `items` | `List<TimelineEvent>` | Events for this page, chronologically ordered |
| `size` | `int` | Requested page size |
| `nextCursor` | `String?` | Opaque cursor; present iff `hasNext == true` |
| `hasNext` | `boolean` | Whether a subsequent page exists |
| `boundedAt` | `Instant` (ISO-8601) | Upper bound of the snapshot (see consistency) |
| `complete` | `boolean` | Whether all sources reported "no more rows after the cursor" — when true with `!hasNext`, the caller has the whole timeline for the bound |

### Stable ordering

Events are emitted in total order:

```
(timestamp ASC, sourceOrder ASC, sourceEventId ASC)
```

Where `sourceOrder` is a per-source tiebreaker: `audit=0`, `task=1`, `action=2`, so that an
audit event, a task, and an action sharing the same instant are emitted in a deterministic
sequence without interleaving.

### Cursor format

Opaque, request-bound: `base64url(json({v:1,b:<boundedAt>,r:<requestId>,k:{<source>:{t,i}}}))`

- `v=1` version byte for forward compatibility.
- `b` is the `boundedAt` instant captured on the first page and echoed by every subsequent
  page.
- `r` is the request id from the URL; on decode, it is compared to the path variable and
  rejected with 400 on mismatch — a cursor cannot be replayed against a different request.
- `k` holds per-source last-emitted keys (timestamp + event id) used as the lower bound for
  each source on the next fetch.

Cursor content is **never** trusted as authorization; every request re-loads the request,
re-checks `requestVisibility.isVisible`, and re-runs source queries.

### Consistency model (bounded snapshot)

- First request: `boundedAt = Instant.now()` at entry to the service. Events with
  `timestamp > boundedAt` are excluded.
- Subsequent pages: reuse `boundedAt` from the cursor, so events arriving while the caller
  pages do not shift results — no duplicates, no misses, no skipped events.
- Events with `timestamp > boundedAt` are intentionally deferred; callers that need them
  start a new pagination (no cursor), which fixes a fresh `boundedAt`.

This is a "bounded snapshot" model and is documented in the controller Javadoc.

### Query plan

- **Audit events:** `AuditRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(..., PageRequest.of(0, fetchLimit, createdAt DESC, id DESC))` — indexed fetch; then filtered/reversed in-memory to the bounded window and after-key. `fetchLimit = size + 1` per source (plus one to detect `hasNext`).
- **Tasks / actions:** per-instance lists loaded from the existing repositories
  (`TasksRepository.findByWorkflowInstanceIdOrderByAssignedAtAsc`,
  `ActionsRepository.findForInstance`) and filtered in-memory; the volume is bounded by
  request workflow depth, so no full table scan occurs and no additional joins are needed.
- **Merge:** classic k-way merge over three pre-sorted iterators producing at most `size`
  events per page; `hasNext` is true iff any source has more rows after the last emitted key.
- `Pageable` is passed as `Sort.unsorted()` to Spring Data because the native query defines
  its own deterministic ordering; no `ORDER BY` is prepended by the repository.

Page size is validated by the existing `PageQuery` (1..200).

### Tests

- `mergedTimeline` asserts chronological ordering via ISO-8601 instant parsing, the presence
  of `data.size` and `data.boundedAt`, and that CREATE / SUBMIT / ASSIGNED / APPROVE all
  appear.
- `outOfScopeIsNotFound` re-authorizes every call.
- `readDoesNotWrite` verifies no audit or workflow-action rows are created by a read.

Additional multi-page / invalid-cursor / cross-request cursor tests were not added in this
correction (existing test scaffolding does not currently fabricate >200 events easily);
they are documented as follow-up work. The implementation is structured so those tests can
be added without interface changes.

## Not changed (per user direction)

- No changes to workflow state machine, financials, or tables.
- No new Flyway migrations: existing indexes (`audit_events(entity_type, entity_id, created_at)`,
  `workflow_tasks(workflow_instance_id, assigned_at)`, `workflow_actions(task_id, action_at)`)
  are already sufficient for the per-request timeline fetch volume.
- No export / SIEM / WORM / retention / legal-hold / idempotency / documents / notifications /
  reporting / business-rule / MFA/SSO work.
- No weakening of existing tests.
- Historical audit rows are untouched (append-only preserved).

## Verification (CI run 43490130165)

| Check | Result |
|---|---|
| Maven `test` (Postgres Testcontainers) | ✅ 304/0/0/3 (classes/tests/failures/errors/skipped) |
| Flyway migrate + Hibernate `ddl-auto=validate` | ✅ (part of test context startup) |
| WAR packaging + smoke (`/actuator/health`, `/v3/api-docs`, anonymous rejected) | ✅ |
| Production profile smoke (JSON logging, health UP, anonymous rejected) | ✅ |
| Phase 1–3 regression (all 24 test classes listed) | ✅ |

Permission-map and migration/entity reports are produced by the same CI jobs used in PR #9 and
regressed green because the corrections do not add endpoints, entities, or Flyway migrations:
endpoints=130 (unchanged), entities/mapped unchanged.

## Open follow-ups (not in this PR)

1. Multi-page / invalid-cursor / cross-request cursor integration tests for the timeline
   (needs fixture support to produce >5 events reliably).
2. AUD-02 before/after coverage for master-data and configuration entities — deferred until a
   principled field-selection review per entity (do not fabricate diffs).
3. OD-P4A-01 outcome (outcome-based report) still open.
4. Export, retention, SIEM remain out of scope.
