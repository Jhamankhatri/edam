# Phase 4A Step 1 — Post-merge Corrections (final)

Branch: `feature/phase-4a-step1-audit-corrections` (PR #10, **unmerged**)
Base: `main @ 5073570a918eb6ce58ee67f1df47b35cfdb2b026` (PR #9 merge)
Head: `18532be7621dba18e4c76ff5abba8a1c22668b09`
Head CI: run [`43516091998`](https://github.com/Jhamankhatri/edam/actions/runs/43516091998) (pull_request) — **green**
`Classes: 24, Tests run: 321, Failures: 0, Errors: 0, Skipped: 3`
Jobs: `build-and-test` ✓, `war-package-smoke-test` ✓, `prod-profile-smoke-test` ✓.

## Summary of fixes (all review items closed)

This revision of PR #10 closes the two high-severity blockers raised by the independent review
(H-1 DRAFT audit visibility; H-2 UUID ordering) in addition to the corrections already present:

### 1. Chronological keyset pagination (corrected + signed-long UUID tie-break)

The earlier prototype fetched audit events via a DESC query and reversed them in-memory. That was wrong because it would miss early events if the timeline had more rows than the fetch window. Each source now uses a true **ASC keyset query** that returns the next eligible rows after the cursor key up to a bounded limit:

| Source | Query | Ordering | Limit |
|---|---|---|---|
| `audit_events` | `AuditRepository.findForEntityAfter` (native Postgres) | `created_at ASC, msb(id) ASC, lsb(id) ASC` | `size + 1` |
| `workflow_tasks` | `TasksRepository.findForInstanceAfter` (native Postgres) | `assigned_at ASC, msb(id) ASC, lsb(id) ASC` | `size + 1` |
| `workflow_actions` | `ActionsRepository.findForInstanceAfter` (native Postgres, sub-select via `workflow_tasks`) | `action_at ASC, msb(id) ASC, lsb(id) ASC` | `size + 1` |

The id tie-break splits each UUID into its two signed 64-bit halves using
`(('x'||substr(replace(id::text,'-',''),1,16))::bit(64))::bigint` for the MSB and the
`…,17,16)` variant for the LSB. This makes Postgres ordering byte-identical to Java's
`UUID.compareTo` (which compares MSB then LSB as signed longs), fixing a bug where any UUID
with the high bit set on either half would be ordered differently between the SQL predicate
and the in-memory k-way merge — causing duplicate or skipped rows across page boundaries.

All three queries:
- Apply `timestamp <= :boundedAt` so events after the snapshot are excluded.
- Use the composite keyset predicate `(ts > :afterTs) OR (ts = :afterTs AND (msb > :afterMsb OR (msb = :afterMsb AND lsb > :afterLsb)))` with `afterTs is null` for the first page.
- Explicitly cast nullable parameters to avoid Postgres "could not determine data type of parameter" errors.
- Use `limit :fetchLimit` so no source ever materialises more than `size + 1` rows.
- Are scoped by `entity_id` / `workflow_instance_id` so they remain indexed reads (no full-table scans); the action query reaches `workflow_actions` through a sub-select on `workflow_tasks (workflow_instance_id = :instanceId)` so the caller never materialises a list of task IDs.

The k-way merge (`TimelineKey.ORDER`) uses `a.eventId().compareTo(b.eventId())` (signed-long semantics, identical to the SQL ordering). The merge emits at most `size` entries per page and computes the next-page cursor by recording, **for each source**, the last `(timestamp, id)` that was actually emitted from that source in this page. Sources that had no emitted entry carry forward their previous cursor key unchanged so their next fetch continues from the same point.

### 2. HMAC-SHA256 cursor integrity (added)

The timeline cursor is an opaque Base64URL token of the form
`base64url( json({v,b,r,k,s}) )`:

| Field | Meaning |
|---|---|
| `v` | Cursor format version (currently `1`) |
| `b` | Bounded upper-bound instant (ISO-8601) fixed at the first request |
| `r` | Request id the cursor is bound to (prevents cross-request replay) |
| `k` | Per-source last-emitted keys `{audit|task|action: {t, i}}` |
| `s` | HMAC-SHA256 signature over the canonical JSON of `{v,b,r,k}`, Base64URL-encoded |

On decode, the signature is verified **before any other field is trusted**, using a
constant-time equality check to prevent timing attacks. Any tampering (flipping `b`, swapping
`r`, editing per-source keys, stripping the signature) yields a 400 `INVALID_REQUEST`. The
request id in the cursor is compared to the URL path parameter and rejected on mismatch;
even so, the cursor is never used as an authorization token — every page re-validates
visibility via `ExpenseRequestService.isVisible` and a mismatched caller receives 404,
never data.

**Key management.** Configure `edam.cursor-hmac-key` (or the `EDAM_CURSOR_HMAC_KEY`
environment variable) to a stable secret of at least 32 bytes. When unset (e.g. local dev),
the service generates a random per-process 32-byte key and logs a WARN. The ephemeral key is
regenerated on every restart, which means:

- In development: cursors issued before a restart become invalid and return 400 (benign; clients can drop the cursor and start over).
- In production: the key **must** be set; otherwise rolling restarts will invalidate in-flight pagination sessions and may indicate a misconfiguration.

No key, cursor payload, or audit payload is written to the application log.

### 3. Audit search authorization (DB-level, with DRAFT protection)

`AuditRepository.search` is a native query that left-joins `expense_requests` for `ExpenseRequest` events and applies the visibility predicate **before** pagination and count:

```
e.entity_type <> 'ExpenseRequest'
 OR ( r.id IS NOT NULL
      AND ( :installationWide = true
         OR r.requested_by = :callerId
         OR ( r.current_status_code <> 'DRAFT'
              AND ( r.department_id = any(cast(:deptIds as uuid[]))
                 OR (r.branch_id IS NOT NULL
                     AND r.branch_id = any(cast(:branchIds as uuid[]))) ) ) ) )
 OR ( r.id IS NULL AND :installationWide = true )
```

The DRAFT clause mirrors `ExpenseRequestService.isVisible()` exactly: department or branch scope alone never grants visibility to another user's unsubmitted filing — drafts are visible only to their requester and to installation-wide auditors. The same predicate is repeated verbatim in the `countQuery`, so `totalElements` cannot leak the existence of invisible drafts. Orphaned events (where `expense_requests.id IS NULL`) remain visible only to installation-wide auditors. Non-request events pass through to any `audit:view` holder (intended). Native parameters are explicitly cast to prevent Postgres type-inference regressions. No N+1 queries: filtering is a single SQL round-trip.

### 4. Bounded-snapshot consistency guarantee (documented honestly)

The first call to `requestTimeline` captures `boundedAt = Instant.now()`; subsequent pages echo that instant from the cursor. Events with `timestamp > boundedAt` are excluded from the entire session, which prevents page shifts and duplicates caused by new events arriving mid-browse. This is documented in the `AuditReadService` class Javadoc as a **bounded application-level snapshot** with explicit limitations:

- ✅ No events after `boundedAt` are ever returned in the session.
- ✅ No duplicates or misses for events that were commit-visible before the per-source queries ran.
- ⚠️ Events from long-running transactions that commit *after* a page is fetched but whose timestamp is ≤ `boundedAt` can appear on a later page — they appear in correct chronological position, never duplicated. We do **not** use REPEATABLE READ or SERIALIZABLE isolation, and we do not hold a transaction across HTTP requests, so we cannot guarantee a true cross-request database snapshot.
- Callers that need strict point-in-time semantics omit the cursor to start a new bounded snapshot.

### 5. Before/after audit capture (corrected to real diffs)

For each audited operation, `before` is captured **immediately before** the mutation, `after` immediately after, in the same `@Transactional` method, and passed as controlled field maps (never full Hibernate entities):

| Entity | Operation | Before | After | Notes |
|---|---|---|---|---|
| `ExpenseRequest` | `update` | `requestSnapshot(request)` before `applyUpdate` | `requestSnapshot(request)` after `applyUpdate` | Routing-relevant fields only: requestTypeId, categoryId, subcategoryId, natureId, departmentId, branchId, currencyId, requestedAmount, baseCurrencyAmount, justification. |
| `WorkflowTask` | `act` (APPROVE/REJECT/REVERT) | `{taskStatus, requestStatus, workflowStateId?, workflowCompleted}` pre-engine | `{remarks, taskStatus, requestStatus, workflowStateId?, workflowCompleted}` post-engine/outcome | Remarks routed through `recordChange` so existing `AuditValueRedactor` redacts password/token/secret/bearer patterns. |
| `WorkflowTask` | `forward` | `{taskStatus, assignedUserId, assignedRoleId}` pre-forward | `{remarks, taskStatus, forwardedToUserId, openedTaskIds}` post-forward | Same redaction for remarks. |

Pre-existing `recordChange` coverage is preserved for `OrganizationProfile`, `OrganizationUnit`, `Department`, `Role`, and `User` (profile/org/role/user services already build controlled before/after maps).

OD-P4A-03 preserved: `workflow_actions` remains the authoritative per-step history. The audit event carries the link to that history (`entityId = taskId`, after-map contains `workflowStateId` / `openedTaskIds`), not a duplicate of the full action row.

AUD-02 is **PARTIALLY IMPLEMENTED**:

| Category | AUD-02 before/after |
|---|---|
| Expense request routing fields (`update`) | IMPLEMENTED |
| Approve / Reject / Revert (outcome diff) | IMPLEMENTED |
| Forward (delegation diff) | IMPLEMENTED |
| Organization profile / OrganizationUnit / Department / Role / User updates | IMPLEMENTED |
| Configuration entities (DepartmentApprovalLimit, ExceedingLimitRule, RecommendingAuthority, DelegationRule, ExpenseCategory/Subcategory/Nature/RequestType) | NOT IMPLEMENTED (event-only `record` call; no diff) |
| Master-data CRUD (Currency, ExchangeRate, Vendor, TaxAuthority) | NOT IMPLEMENTED |
| Workflow definition/version/publish | NOT IMPLEMENTED |
| System/session events (login, auth, bootstrap, password changes) | N/A (message-string events are intentional) |

No historical rows are modified. No passwords/tokens/secrets are included; all diffs route through `AuditValueRedactor`.

### 6. Constraints preserved

- No institution/tenant identifiers added (one-deployment-per-bank architecture is unchanged).
- No endpoint URLs or HTTP methods changed (the timeline still accepts `size` and `cursor` query params; response shape `TimelinePage` was already in the PR).
- No financial-calculation or workflow-business-rule changes.
- No mock data added.
- No historical audit rows modified; `AuditRepository` extends `Repository` (not `JpaRepository`) with only `save`/reads exposed, so delete/update paths remain impossible from application code; the DB triggers `trg_audit_events_no_update` / `trg_audit_events_no_delete` (and matching triggers on `audit_event_details`) enforce append-only at the storage layer.
- No export / SIEM / WORM / retention / legal-hold / idempotency / documents / notifications / reporting / business-rule / MFA/SSO work.
- Phase 4A Step 2 not started.
- PR **not merged**.

### 7. Verification

| Check | Result |
|---|---|
| Commit (head) | `18532be7621dba18e4c76ff5abba8a1c22668b09` |
| CI run | [`43516091998`](https://github.com/Jhamankhatri/edam/actions/runs/43516091998) (pull_request) |
| Maven compile | ✅ zero errors |
| `mvn verify` (Postgres Testcontainers) | ✅ 24 classes, **321 tests, 0 failures, 0 errors, 3 skipped** |
| AuditReadApiTest (new + existing) | 32 tests, 0 failures, 0 errors — includes the authorization, timeline, and UUID ordering cases listed below. |
| Flyway migrate + Hibernate `ddl-auto=validate` | ✅ (part of test context startup; no new migrations needed — existing indexes cover the per-request/per-instance fetch shape; the action query reuses `ix_workflow_actions_task (workflow_task_id, action_at)` through the sub-select). |
| WAR packaging + embedded smoke | ✅ (`/actuator/health` 200, `/v3/api-docs` 200, anonymous rejected, no JSP/Jasper, executable launcher present). |
| `prod` profile smoke | ✅ JSON ECS logging, health UP, anonymous rejected, unknown path 401, correlationId in structured records. |
| Phase 1–3 regression | ✅ all 24 test classes executed. |
| Endpoints | unchanged at 130 (no new endpoints, no removals). |
| Entities / migrations | unchanged (52 mapped entities, 0 new tables). |

### Test evidence (from the green CI run at head)

**Audit search authorization:**

| Test | Evidence |
|---|---|
| `requestEventEnforcesVisibility` | Self-scoped auditor sees empty list + 404 detail for another user's submitted request. |
| `orphanedRequestEventsAreNotVisibleToScopedCallers` | SELF/DEPT scopes cannot list or view orphan events; installation-wide can. |
| `draftEventIsHiddenFromDepartmentScopedAuditor` | NEW — department- and self-scoped auditors see empty list for another user's DRAFT. |
| `draftEventNotCountedForScopedAuditor` | NEW — `totalElements` is 0 (not 1) for a scoped auditor querying a DRAFT. |
| `draftEventVisibleToOwner` | NEW — requester (with `audit:view`) sees their own DRAFT events. |
| `draftEventVisibleToInstallationWideAuditor` | NEW — ALL-scoped auditor sees DRAFT events. |
| `submittedRequestVisibleToDepartmentScoped` | NEW — post-submit dept/branch visibility preserved. |
| `redactsSensitiveValues` | Password/token/bearer strings are redacted in diff payloads. |

**Timeline pagination / cursor integrity / UUID ordering:**

| Test | Evidence |
|---|---|
| `mergedTimeline` | CREATE/SUBMIT/ASSIGNED/APPROVE appear in chronological order (ISO-8601). |
| `outOfScopeIsNotFound` | Outsider from a different department cannot read the timeline (404). |
| `unknown` | Unknown request returns 404. |
| `readDoesNotWrite` | Timeline read does not add audit events or workflow actions. |
| `keysetPaginationAcrossPages` | 20 seeded events walked via returned cursors; every event returned exactly once; chronological order; `boundedAt` stable across pages; ≥3 pages traversed. |
| `sameSourceEqualTimestampOrderedById` | Two same-ts events returned in deterministic id order on two independent fetches and on a size=1 page walk. |
| `uuidTieBreakIsDeterministicAcrossPages` | Six same-ts events (random UUIDs — which reliably produce high-bit MSB/LSB halves); size=50 page order is strictly Java-increasing; size=1 walk reproduces the order exactly (no duplicates/skips). |
| `highBitUuidsPageCorrectly` | NEW — hand-crafted high-bit MSB (`804d…`) and high-bit LSB (`0000…8000…0001`) UUIDs are ordered the same way by Postgres and Java, verified on a size=50 page and a size=1 walk. |
| `malformedCursorIs400` | Non-Base64 cursor returns 400. |
| `unsupportedCursorVersionIs400` | Hand-crafted `v=99` cursor returns 400. |
| `crossRequestCursorIs400` | Cursor bound to request A is rejected (400) against request B. |
| `tamperedCursorSignatureIsRejected` | Bit-flipping `b`/`r`/`k` or presenting an unsigned cursor returns 400. |
| `authorizationRecheckedOnEachPage` | Outsider gets 404 presenting a valid page-2 cursor. |
| `eventsAfterBoundedAtAreExcluded` | Late event (60 s future) absent from the bounded session; present after wall-clock passes lateTs with a fresh cursor. |
| `exhaustedCursorIsStable` | Two full size=5 walks return identical id set. |

### Remaining limitations / open decisions

1. AUD-02 before/after for the configuration / master-data / workflow-definition entities listed above is still NOT IMPLEMENTED — deferred until a principled per-entity field-selection review; AUD-02 is explicitly marked PARTIAL above.
2. Multi-page tests use deterministic but fixed historical anchors because `audit_events` is append-only (DB trigger forbids DELETEs) — tests cannot clean up after themselves. Anchors are spaced 1 day apart per test and carry unique tags, so interference is impossible.
3. Bounded-snapshot consistency is application-level, not a DB snapshot — documented above; callers that need strict point-in-time semantics re-issue without a cursor.
4. When `edam.cursor-hmac-key` is unset the service uses an ephemeral per-process key and logs a WARN; cursors do not survive restart. Production deployments **must** set `EDAM_CURSOR_HMAC_KEY`.
5. OD-P4A-01 (outcome report), export, retention, SIEM remain open per the standing Phase 4A scope.
