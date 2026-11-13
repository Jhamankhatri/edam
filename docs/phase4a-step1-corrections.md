# Phase 4A Step 1 — Post-merge Corrections (final)

Branch: `feature/phase-4a-step1-audit-corrections` (PR #10, **unmerged**)
Base: `main @ 5073570a918eb6ce58ee67f1df47b35cfdb2b026` (PR #9 merge)
Head CI: run [`43503499005`](https://github.com/Jhamankhatri/edam/actions/runs/43503499005) — **green**
`Classes: 24, Tests run: 313, Failures: 0, Errors: 0, Skipped: 3`
Jobs: `build-and-test` ✓, `war-package-smoke-test` ✓, `prod-profile-smoke-test` ✓.

## Summary of fixes (all seven items from the review)

### 1. Chronological keyset pagination (corrected)

The earlier prototype fetched audit events via a DESC query and reversed them in-memory. That was wrong because it would miss early events if the timeline had more rows than the fetch window. Each source now has a true **ASC keyset query** that returns the next eligible rows after `(afterTs, afterId)` up to a bounded limit:

| Source | Query | Ordering | Limit |
|---|---|---|---|
| `audit_events` | `AuditRepository.findForEntityAfter` (native Postgres) | `created_at ASC, id ASC` | `size + 1` |
| `workflow_tasks` | `TasksRepository.findForInstanceAfter` (native Postgres) | `assigned_at ASC, id ASC` | `size + 1` |
| `workflow_actions` | `ActionsRepository.findForTasksAfter` (native Postgres) | `action_at ASC, id ASC` | `size + 1` |

All three queries:
- Apply `timestamp <= :boundedAt` so events after the snapshot are excluded.
- Use the composite keyset predicate `(ts > :afterTs) OR (ts = :afterTs AND id > :afterId)` with `afterTs is null` for the first page.
- Explicitly cast nullable parameters (`cast(:afterTs as timestamptz)`, `cast(:afterId as uuid)`) to avoid Postgres "could not determine data type of parameter" errors.
- Use `limit :fetchLimit` so no source ever materialises more than `size + 1` rows.
- Are scoped by `entity_id` / `workflow_instance_id` / `workflow_task_id = any(:taskIds)` so they remain indexed reads (no full-table scans).

The k-way merge (`TimelineKey.ORDER`) is preserved. The merge emits at most `size` entries per page and computes the next-page cursor by recording, **for each source**, the last `(timestamp, id)` that was actually emitted from that source in this page (previously only the single last event was tracked, which broke multi-source interleaving). Sources that had no emitted entry carry forward their previous cursor key unchanged so their next fetch continues from the same point.

### 2. Timeline pagination integration tests (added)

All twelve cases run against the real PostgreSQL Testcontainers database; no mocked repositories.

| # | Test | What it proves |
|---|---|---|
| 1 | `mergedTimeline` | CREATE/SUBMIT/ASSIGNED/APPROVE appear and are chronologically ordered (ISO-8601 instants parsed, not epoch millis). |
| 2 | `outOfScopeIsNotFound` | Outsider from a different department cannot read the timeline (404). |
| 3 | `unknown` | Unknown request returns 404. |
| 4 | `readDoesNotWrite` | A timeline read does not add any audit events or workflow actions. |
| 5 | **`keysetPaginationAcrossPages`** | 20 seeded events (size=5 → ≥5 pages) walked via returned cursors; every event returned exactly once; chronological order across the entire set; equal-timestamp events respect sourceOrder + id tie-breaker; `boundedAt` is stable across all pages; assertion that ≥3 pages are traversed. |
| 6 | **`sameSourceEqualTimestampOrderedById`** | Two events sharing one timestamp at a per-test unique band are returned in a deterministic order across two independent fetches, and that same order is preserved in a size=1 page walk. |
| 7 | **`malformedCursorIs400`** | Garbage cursor returns 400. |
| 8 | **`unsupportedCursorVersionIs400`** | Hand-crafted `v=99` cursor returns 400. |
| 9 | **`crossRequestCursorIs400`** | Cursor bound to request A is rejected (400) when presented against request B. |
| 10 | **`authorizationRecheckedOnEachPage`** | Valid cursor returned to an out-of-scope caller on page 2 → 404. |
| 11 | **`eventsAfterBoundedAtAreExcluded`** | Inserts an event with timestamp 60 s in the future before capturing `boundedAt`; walks every page of the session and asserts the late id never appears; waits for wall-clock to pass the late timestamp and verifies a fresh pagination (new `boundedAt`) does include it. |
| 12 | **`exhaustedCursorIsStable`** | Walks all pages with size=5 then walks again with a fresh cursor; asserts the set of returned event ids is identical. |

Synthetic events use unique per-test time-bands (100+ day offsets from the 2000-01-01 anchor) and a unique tag in `new_values` so they do not collide across tests — the audit table is append-only and the database trigger forbids DELETEs, so tests cannot clean up after themselves.

### 3. Bounded-snapshot consistency guarantee (documented honestly)

The first call to `requestTimeline` captures `boundedAt = Instant.now()`; subsequent pages echo that instant from the cursor. Events with `timestamp > boundedAt` are excluded from the entire session, which prevents page shifts and duplicates caused by new events arriving mid-browse. This is documented in the `AuditReadService` class Javadoc as a **bounded application-level snapshot** with explicit limitations:

- ✅ No events after `boundedAt` are ever returned in the session.
- ✅ No duplicates or misses for events that were commit-visible before the per-source queries ran.
- ⚠️ Events from long-running transactions that commit *after* a page is fetched but whose timestamp is ≤ `boundedAt` can appear on a later page — they appear in correct chronological position, never duplicated. We do **not** use REPEATABLE READ or SERIALIZABLE isolation, and we do not hold a transaction across HTTP requests, so we cannot guarantee a true cross-request database snapshot.
- Callers that need strict point-in-time semantics omit the cursor to start a new bounded snapshot.

This matches what the code actually does; we do not claim stronger isolation than is provided.

### 4. Before/after audit capture (corrected to real diffs)

For each audited operation, `before` is captured **immediately before** the mutation, `after` immediately after, in the same `@Transactional` method, and passed as controlled field maps (never full Hibernate entities):

| Entity | Operation | Before | After | Notes |
|---|---|---|---|---|
| `ExpenseRequest` | `update` | `requestSnapshot(request)` before `applyUpdate` | `requestSnapshot(request)` after `applyUpdate` | Routing-relevant fields only: requestTypeId, categoryId, subcategoryId, natureId, departmentId, branchId, currencyId, requestedAmount, baseCurrencyAmount, justification. |
| `WorkflowTask` | `act` (APPROVE/REJECT/REVERT) | `{taskStatus, requestStatus, workflowStateId?, workflowCompleted}` pre-engine | `{remarks, taskStatus, requestStatus, workflowStateId?, workflowCompleted}` post-engine/outcome | Remarks routed through `recordChange` so existing `AuditValueRedactor` redacts password/token/secret/bearer patterns. |
| `WorkflowTask` | `forward` | `{taskStatus, assignedUserId, assignedRoleId}` pre-forward | `{remarks, taskStatus, forwardedToUserId, openedTaskIds}` post-forward | Same redaction for remarks. |

OD-P4A-03 preserved: `workflow_actions` remains the authoritative per-step history. The audit event carries the link to that history (`entityId = taskId`, after-map contains `workflowStateId` / `openedTaskIds`), not a duplicate of the full action row.

AUD-02 remains marked **PARTIALLY IMPLEMENTED**:

| Category | AUD-02 before/after |
|---|---|
| Expense request routing fields (`update`) | IMPLEMENTED |
| Approve / Reject / Revert (outcome diff) | IMPLEMENTED |
| Forward (delegation diff) | IMPLEMENTED |
| Configuration entities (Department, Branch, Currency, ExpenseCategory, …) | NOT IMPLEMENTED (single `record` call; no diff) |
| Master-data CRUD (Role, User, Permission, …) | NOT IMPLEMENTED |
| Workflow definition/version/publish | NOT IMPLEMENTED |
| System/session events (login, auth) | N/A |

No historical rows are modified. No passwords/tokens/secrets are included; remarks are redacted.

### 5. Audit search authorization (reviewed + tightened + tested)

`AuditRepository.search` is a native query that left-joins `expense_requests` for ExpenseRequest events and applies the visibility predicate **before** pagination:

```
e.entity_type <> 'ExpenseRequest'
 OR ( r.id IS NOT NULL
      AND ( :installationWide = true
         OR r.requested_by = :callerId
         OR r.department_id = any(cast(:deptIds as uuid[]))
         OR (r.branch_id IS NOT NULL AND r.branch_id = any(cast(:branchIds as uuid[]))) ) )
 OR ( r.id IS NULL AND :installationWide = true )
```

The previous version made orphaned events (where `expense_requests` row no longer exists) visible to everyone. **Policy corrected:** orphaned ExpenseRequest events are visible only to installation-wide auditors. The detail endpoint mirrors this policy. Count query applies the **identical** predicate so `totalElements` excludes invisible rows and cannot be used as a leak vector. No N+1: filtering happens once in the SQL, `Page<AuditEvent>` is a single round-trip.

Native parameters are cast (`cast(:action as text)`, `cast(:deptIds as uuid[])`, `cast(:installationWide as boolean)`, etc.) to prevent Postgres type-inference regressions.

**Added test `orphanedRequestEventsAreNotVisibleToScopedCallers`** inserts an ExpenseRequest audit event for a non-existent request id, and verifies:
- self-scoped auditor does NOT see it in list or detail (404);
- department-scoped auditor does NOT see it;
- installation-wide auditor DOES see it in list and detail (200).

### 6. Constraints preserved

- No institution/tenant identifiers added.
- No endpoint URLs or HTTP methods changed (the timeline still accepts `size` and `cursor` query params; response shape `TimelinePage` was already in PR #10).
- No financial-calculation or workflow-business-rule changes.
- No mock data added.
- No historical audit rows modified; `AuditRepository` extends `Repository` (not `JpaRepository`) with only `save`/reads exposed, so delete/update paths remain impossible from application code.
- No export / SIEM / WORM / retention / legal-hold / idempotency / documents / notifications / reporting / business-rule / MFA/SSO work.
- Phase 4A Step 2 not started.
- PR **not merged**.

### 7. Verification

| Check | Result |
|---|---|
| Commit (head) | `84bbaf420ccf7d8c314ce9c7c47d1b6229d793d8` |
| CI run | [`43503499005`](https://github.com/Jhamankhatri/edam/actions/runs/43503499005) (pull_request) |
| Maven compile | ✅ (zero errors, zero warnings-as-errors) |
| `mvn test` (Postgres Testcontainers) | ✅ 24 classes, **313 tests, 0 failures, 0 errors, 3 skipped** |
| AuditReadApiTest (new + existing) | 26 tests, 0 failures, 0 errors — includes the 12 timeline/search cases listed above. |
| Flyway migrate + Hibernate `ddl-auto=validate` | ✅ (part of test context startup; no new migrations added — none needed because existing indexes cover the per-request/per-instance fetch shape). |
| WAR packaging + embedded smoke | ✅ (`/actuator/health` 200, `/v3/api-docs` 200, anonymous rejected, no JSP/Jasper). |
| `prod` profile smoke | ✅ JSON ECS logging, health UP, anonymous rejected, unknown path 401, correlationId in structured records. |
| Phase 1–3 regression | ✅ all 24 test classes executed (see test list in CI output). |
| Endpoints | unchanged at 130 (no new endpoints, no removals). |
| Entities / migrations | unchanged (52 mapped entities, 0 new tables). |

### Per-pagination-test evidence (from the CI green run)

| Test | Evidence |
|---|---|
| ≥3-page walk, every event exactly once, in order, boundedAt stable | `keysetPaginationAcrossPages` passes: 20 seeded events walked in 5+ pages, `seen.add(id)` asserted true (no duplicates), all 20 ids contained, `allTimestamps` sorted via AssertJ `isSorted()`, equal-timestamp cross-source entries in sourceOrder, `pages >= 3`, `boundedAt` identical on every page. |
| Deterministic same-timestamp ordering | `sameSourceEqualTimestampOrderedById` passes: two events with identical `createdAt` appear in a stable id-order on two independent size=50 fetches and in a size=1 page walk; first id never appears after the second id. |
| Malformed cursor → 400 | `malformedCursorIs400` passes (non-Base64 string). |
| Unsupported version → 400 | `unsupportedCursorVersionIs400` passes (hand-crafted `v=99` JSON). |
| Cross-request cursor → 400 | `crossRequestCursorIs400` passes. |
| Authorization re-checked on every page | `authorizationRecheckedOnEachPage` passes (outsider gets 404 using a valid page-2 cursor). |
| Events after boundedAt excluded; fresh pagination sees them | `eventsAfterBoundedAtAreExcluded` passes: late event (60 s in future) absent from the bounded session (all pages walked), present after wall-clock passes lateTs with a fresh cursor. |
| Exhausted cursor stable | `exhaustedCursorIsStable` passes: two full size=5 walks return identical set. |

### Authorization / count-query evidence

- `orphanedRequestEventsAreNotVisibleToScopedCallers` exercises list visibility for SELF/DEPARTMENT/ALL scopes against an orphan event AND asserts the detail endpoint matches; green.
- `requestEventEnforcesVisibility` (existing, updated) asserts that list returns empty for a self-scoped auditor against another user's request and detail returns 404; green.
- `defaultWindow` exercises `totalElements` over the default window; green.
- Native `countQuery` mirrors `select e.*` predicate exactly (single source of truth in `AuditRepository.search`).

### Remaining limitations / open decisions

1. AUD-02 before/after for master-data / configuration entities is still NOT IMPLEMENTED — deferred until a principled per-entity field selection review.
2. Multi-page tests use deterministic but fixed historical anchors because `audit_events` is append-only (a DB trigger forbids DELETEs) — tests cannot clean up after themselves. The anchors are spaced 1 day apart per test and carry unique tags, so interference is impossible.
3. Bounded-snapshot consistency is application-level, not a DB snapshot — documented above; callers that need strict point-in-time semantics re-issue without a cursor.
4. OD-P4A-01 (outcome report), export, retention, SIEM remain open per the standing Phase 4A scope.
