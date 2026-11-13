# Phase 4A Step 1 — Audit Search and Request Activity Timeline

- Branch: `arena/729dc5eb-edam`
- Base: `main` at `c1bd50b` (post-PR #8 merge, includes Phase 3 at `7119739` and the verification docs).
- Commit: see `PHASE_4A_STEP1_INSPECTION.md` in the same folder; the latest commit on the branch is the tip of the PR.
- CI: run `43465133865` on push. All three jobs green. **Classes: 24, Tests run: 304, Failures: 0, Errors: 0, Skipped: 3.**

This step adds the read side of the audit trail. Write-side (Module K) was delivered in Phase 3 and is untouched here. Audit export (AUD-03) remains out of scope.

## New endpoints

All endpoints sit under `/api/v1` and return `ApiResponse<T>` envelopes matching the existing convention.

| Method | Path | Permission | Summary |
|---|---|---|---|
| GET | `/api/v1/audit-events` | `audit:view` | Paginated audit search with bounded date window. |
| GET | `/api/v1/audit-events/{id}` | `audit:view` (+ request visibility for request events) | Single event with redacted before/after diff. |
| GET | `/api/v1/expense-requests/{id}/activity` | `request:view-own` (ownership may suffice) | Merged activity timeline for a request. |

### Filters — `GET /api/v1/audit-events`

| Param | Type | Default | Notes |
|---|---|---|---|
| `from` | ISO-8601 instant | 24 hours before `to` | Inclusive. |
| `to` | ISO-8601 instant | `now()` | Exclusive. |
| `actorId` | UUID | null | Exact match on `performed_by`. |
| `actorName` | string | null | Case-insensitive substring over `performed_by_username` (the snapshotted username). |
| `entityType` | string | null | Exact match on `entity_type`; this is the BRD "module" field (no separate module column exists). |
| `entityId` | string | null | Exact match on `entity_id`. |
| `action` | `AuditAction` enum name | null | CREATE / UPDATE / SUBMIT / APPROVE / REJECT / … |
| `correlationId` | string | null | Exact match. |
| `page`, `size` | int, int | 0, 20 | `size` capped at `PageQuery.MAX_SIZE = 200`. |
| `sort` | string | ignored | The endpoint sorts `createdAt desc`; only the default is allowed (deterministic, indexed). |

Window bounds:
- The window is **mandatory at the repository level** (`AuditRepository.search` requires `from`/`to`); the service supplies a default 24-hour window when neither bound is supplied.
- A window wider than **31 days** is rejected with `EDAM-1001 VALIDATION_FAILED`.
- A reversed window (`from >= to`) is rejected with `EDAM-1001`.
- Malformed instants are rejected by Spring with `EDAM-1002 INVALID_REQUEST`.

Pagination: `PageQuery` clamps at 200 and rejects larger sizes with `EDAM-1003 INVALID_PAGINATION`.

### Response — list

`ApiResponse<PageResponse<AuditEventSummary>>`. A summary row carries: `id`, `entityType`, `entityId`, `action`, `performedBy`, `performedByUsername`, `correlationId`, `createdAt`. **Summary rows deliberately exclude `oldValues`, `newValues`, and `ipAddress`.** The diff and IP are returned only on the detail endpoint to keep list traffic light and to make bulk list scraping lower-value.

### Response — detail

`ApiResponse<AuditEventDetail>` with `id`, `entityType`, `entityId`, `action`, `oldValues`, `newValues`, `performedBy`, `performedByUsername`, `ipAddress`, `correlationId`, `createdAt`. `oldValues`/`newValues` are deserialized maps; redaction runs a second time on read so a future write-path regression cannot leak a secret through this API. An event whose payload cannot be parsed is returned as `{ "_unparseable": true }` rather than raw JSON.

### Authorization for detail

- For non-request entity types, `audit:view` is sufficient (roles, currencies, workflows are installation-wide configuration concerns).
- For events where `entityType == "ExpenseRequest"`, the caller must also be able to see the request (using `ExpenseRequestService.isVisible`, the same helper the existing GET `/expense-requests/{id}` uses). Out-of-scope requests return **404**, matching the existing controller's behaviour of not confirming existence.

### Response — timeline

`ApiResponse<PageResponse<TimelineEntry>>`. A timeline entry carries:

- `source` — `AUDIT`, `WORKFLOW_ACTION`, or `WORKFLOW_TASK` (the synthetic assignment events).
- `eventId`, `timestamp`, `actorId`, `actorName`, `action`, `description`, `previousState`, `newState`, `remarks`, `correlationId`.

Entries come from three sources, merged in a single query per request and sorted by `(timestamp ASC, source_order ASC, eventId ASC)`:

1. `audit_events` rows for `entity_type = 'ExpenseRequest'` and `entity_id = <id>` — covers CREATE, UPDATE, SUBMIT, CANCEL (and any APPROVE/REJECT/REVERT/FORWARD audit rows a future write path may add; no such rows are written today).
2. `workflow_actions` rows for the request's current workflow instance — covers APPROVE, REJECT, REVERT, FORWARD, ESCALATE with previous/next state names resolved from `workflow_states` and remarks verbatim (with suspicious-value redaction).
3. `workflow_tasks` rows projected to synthetic `ASSIGNED` entries — the existing engine never wrote assignment audit events, and fabricating historical audit rows would violate the "do not create duplicate records" rule. Projections are marked with source `WORKFLOW_TASK` so clients can distinguish them.

Hard cap: a request timeline is materialised in memory (acceptable because a single request's history is bounded) but capped at **2000 entries** to prevent pathological cases from exhausting memory; the truncated response sets `totalElements` to the cap so the UI can surface it.

Authorization for the timeline is **exactly the same** as for the existing request GET:

- Controller annotation: `@EdamPermission(REQUEST_VIEW_OWN)` — the base permission the request register already uses.
- Service layer: `ExpenseRequestService.isVisible(request, caller)` enforces ownership, department/branch scopes, and installation-wide (`request:view-all`) visibility.
- Out-of-scope requests return **404**.

## Service implementation

| Class | Role |
|---|---|
| `com.edam.audit.controller.AuditController` | `GET /api/v1/audit-events`, `GET /api/v1/audit-events/{id}`. Thin controller; DTO assembly lives in the service. |
| `com.edam.audit.controller.RequestTimelineController` | `GET /api/v1/expense-requests/{id}/activity`. Path placed under `/expense-requests` because it is a sub-resource; lives in the audit package because it merges audit and workflow sources. |
| `com.edam.audit.dto.AuditDtos` | Summary, detail, timeline entry records. |
| `com.edam.audit.service.AuditReadService` | `@Transactional(readOnly = true)`; bounds the window, delegates scoping, merges sources, re-runs redaction on read, paginates deterministically. No write calls; does not itself emit audit events. |
| `com.edam.common.audit.AuditRepository` | Existing write-side repository; added `count()` for tests only. The search query already supported every filter this step needs; no new method was added for the list endpoint. |
| `com.edam.expenserequest.service.ExpenseRequestService` | `isVisible(...)` widened to `public` so the audit module can reuse the visibility rule rather than reimplementing it. No behaviour change. |

## Database changes

None. Existing columns (`entity_type`, `entity_id`, `performed_by`, `correlation_id`, `created_at`) and their indexes (V10) cover the query patterns. The `(created_at DESC)` index supports the time-bounded list. No migration added.

## Audit coverage matrix

Call-site enumeration of `AuditRecorder.record` / `recordChange`. `recordChange` includes before/after maps; `record` writes an event with no diff (or a reason string only).

| Entity | Actions | Before/after | Status |
|---|---|---|---|
| OrganizationProfile | UPDATE | full | IMPLEMENTED |
| OrganizationUnit, Department | UPDATE | full | IMPLEMENTED |
| Role | UPDATE (permission grants) | full | IMPLEMENTED |
| User | UPDATE | full | IMPLEMENTED |
| DepartmentApprovalLimit | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL — events only, no diff |
| ExceedingLimitRule | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL |
| RecommendingAuthority | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL |
| DelegationRule | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL |
| ExpenseCategory / Nature / Subcategory / RequestType | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL |
| Currency | updates (rate edits) | none | PARTIAL |
| ExchangeRate | CREATE / UPDATE | none | PARTIAL |
| Vendor, TaxAuthority | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL |
| ExpenseRequest | CREATE / SUBMIT / CANCEL (legitimately event-only) | none | IMPLEMENTED (event-only); UPDATE is PARTIAL |
| ExpenseRequest | UPDATE | none | PARTIAL |
| WorkflowDefinition | CREATE / UPDATE / ACTIVATE / DEACTIVATE | none | PARTIAL |
| WorkflowVersion | CREATE-draft / UPDATE / PUBLISH / rollback | none | PARTIAL |
| Approval / rejection / revert / forward / escalate | — (written only to `workflow_actions`) | state + remarks in WA | IMPLEMENTED via `workflow_actions`; no audit_events rows — see OD-P4A-03 |
| LOGIN_SUCCESS / LOGIN_FAILURE / ACCESS_DENIED | event-only | N/A (security events) | IMPLEMENTED |

No existing before/after coverage was modified in this PR. Adding recordChange calls where the prior state is already loaded is a follow-up improvement; this PR does not retrofit wholesale entity snapshotting, which the task explicitly warns against.

## Tests

New class: `src/test/java/com/edam/audit/AuditReadApiTest.java`, extending `AbstractPostgreSqlIntegrationTest` (real PostgreSQL via Testcontainers, Flyway, ddl-auto validate).

- Unauthenticated → 401.
- Authenticated without `audit:view` → 403.
- Default-window search succeeds; reads do not write audit rows (count assertion).
- Filter by actor id returns only that actor's rows; all rows match the filtered actor.
- Filter by action returns only that action.
- Invalid date range (`from >= to`) → 400.
- Range > 31 days → 400.
- Malformed instant → 400.
- `size > 200` → 400; `size = 200` → 200.
- List rows do not contain `oldValues`/`newValues`/`ipAddress`.
- Detail endpoint for a request event returns 404 for an auditor without request visibility.
- Redaction: a synthetic role event with `password` and a bearer-token-shaped value returns `***redacted***` and does not contain the secret.
- Timeline: CREATE + SUBMIT + ASSIGNED + APPROVE entries are present and timestamps are strictly non-decreasing.
- Timeline: out-of-department user → 404.
- Timeline: unknown request → 404.
- Timeline: reads do not add audit rows or workflow actions (count assertion before/after).

## Open decisions (documented, not invented)

- **OD-P4A-01 — Outcome field.** BRD §26 lists success/failure as part of an audit record, but `audit_events` has no `outcome` column. Because `audit.record()` is called only after a successful commit (with `REQUIRES_NEW`), a row is by definition an accepted business event; failures are signalled by absence plus an error log. Adding an outcome column requires a policy for what to write for failed attempts (and when to write them) and is deferred.
- **OD-P4A-02 — Row-level scoping on the global list.** This step enforces request visibility at the detail endpoint and on the timeline, but not on the global list (installation-wide metadata). A list filter by entity_type=ExpenseRequest returns summary rows for all requests. Per-row request-scope enforcement would be O(N) request lookups and is not added here. Auditors hold `audit:view` explicitly for compliance work.
- **OD-P4A-03 — Whether APPROVE/REJECT/REVERT/FORWARD/ESCALATE should also write `audit_events` rows.** The engine currently writes only `workflow_actions` for those actions; this step reads both sources. Writing both would create duplication risk without a backfill decision.

## BRD requirement mapping

| Req | Status | Evidence |
|---|---|---|
| AUD-01 Central activity history (search by actor/action/resource/date) | **PARTIAL (implemented for this deployment's audit; outcome filter deferred per OD-P4A-01; institution filter not applicable per one-bank-per-deployment)** | `AuditController` GET `/audit-events`; service-enforced 31-day window; filters for actor id+name, action, entity type/id, correlation id, date range; deterministic sort; all three CI jobs green, 304/0/0/3. |
| AUD-02 Before/after diff with redaction | **IMPLEMENTED for write (Phase 3) and for read (this step).** Writes redact via `AuditValueRedactor`; reads re-run redaction; secrets/passwords/tokens/CNIC/NTN/iban/cvv are all denied. Detail endpoint returns maps; list endpoint does not. Synthetic audit-event writes in tests prove `***redacted***` is returned and the plaintext is absent. |
| AUD-03 Audit export | **NOT IMPLEMENTED** (explicitly out of scope for this step; `audit:export` remains unused). |

Audit retention, SIEM/WORM and archival policy remain open (BRD §26.2 open decision, unchanged).

## What was deliberately not done

- No new Flyway migration.
- No new tables, no new entities, no new columns.
- No changes to append-only protections.
- No modification of historical audit records.
- No new audit events created solely to populate the timeline (the synthetic ASSIGNED entries are projections, not rows).
- No audit writes from the read endpoints (verified by a count assertion in tests).
- No endpoint for audit export (AUD-03).
- No currency-rounding, notification, document, reporting, rule, idempotency or MFA work.
