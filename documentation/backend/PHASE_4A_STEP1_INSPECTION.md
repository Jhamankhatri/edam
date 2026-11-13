# Phase 4A Step 1 — Step 0 Inspection Report

Branch: `arena/729dc5eb-edam` at `c1bd50b` (merge of PR #8). Parents `7119739` (Phase 3) + `7ea9796` (verification docs/tooling). Working tree clean. No code changes yet.

## Existing audit architecture

- **Module:** `common.audit` package under `src/main/java/com/edam/common/audit/`.
- **Entity:** `AuditEvent` (`@Table(name = "audit_events")`). Builder-only construction; no setters; no `@Version`. Columns all `updatable = false`.
- **Write path:** `AuditRecorder.record / recordChange` — `@Transactional(REQUIRES_NEW)` so audit survives a rollback. Calls `AuditValueRedactor.redact(...)` on the `before`/`after` maps *before* serialising to JSON strings.
- **Repository:** `AuditRepository extends Repository<AuditEvent, UUID>` (narrowed — no delete/save-on-existing/findAll). Save is insert-only because the entity has no mutable state and the DB trigger blocks UPDATE/DELETE.
- **Append-only guard (three layers):** (1) no setters / no `@Version`, (2) narrowed repo, (3) PostgreSQL trigger `trg_audit_events_no_update/_no_delete` in V10, plus same on `audit_event_details`.
- **Failure policy:** configurable `edam.audit.fail-closed=false` (default is fail-open with ERROR log), set to `true` for banks requiring fail-closed audit.
- **Actor stored twice:** `performed_by` (FK to users) and `performed_by_username` (snapshot).
- **No read API exists yet.** There is no `AuditController`; nothing wires `AuditRepository.search` into an endpoint. The repository search method requires a bounded time window (`from`/`to` mandatory).
- **`audit_event_details` (V10):** a field-level child table exists in the schema but has no JPA mapping and zero writers. It is one of the 28 unmapped tables recorded in `PHASE_4_SCOPE_AND_DEPENDENCIES.md`; it is not used by this step.

## Audit event fields (`audit_events`, V10)

| Column | Type | Used for |
|---|---|---|
| `id` | UUID PK (uuidv7) | |
| `entity_type` | VARCHAR(100) NN | Module/resource (e.g. `ExpenseRequest`, `Role`, `User`, `WorkflowDefinition`). This is the "module" and "resource type" the BRD refers to. |
| `entity_id` | VARCHAR(100) | Resource id (string form of UUID or business key). |
| `action` | VARCHAR(60) NN | Closed enum `AuditAction` (CREATE, UPDATE, ACTIVATE, DEACTIVATE, DELETE, PUBLISH, ROLLBACK, SUBMIT, APPROVE, REJECT, REVERT, FORWARD, CANCEL, DOCUMENT_UPLOAD, DOCUMENT_DOWNLOAD, DOCUMENT_DELETE, SECRET_ROTATE, EXPORT, LOGIN_SUCCESS, LOGIN_FAILURE, ACCESS_DENIED). |
| `old_values`, `new_values` | JSONB | Redacted before/after maps; max 8000 chars per side. |
| `performed_by` | UUID → users | FK, indexed. |
| `performed_by_username` | VARCHAR(100) | Snapshot. |
| `ip_address`, `user_agent` | VARCHAR | Context. |
| `correlation_id` | VARCHAR(64) | Trace, indexed. |
| `created_at` | TIMESTAMPTZ NN default now | Timestamp, indexed. |

**Indexes already present:** `(entity_type, entity_id, created_at DESC)`, `(performed_by, created_at DESC)`, `(created_at DESC)`, `(correlation_id)`.

**Not present:** explicit `module` column, `outcome` column. Both appear in BRD §26. The BRD's "module" maps cleanly to `entity_type` (no institution column exists because the one-bank-per-deployment model applies — `InstitutionContext` is a stub). The BRD's "outcome" (success/failure) has no column and must be documented as a gap rather than faked: every record persisted to `audit_events` represents an *accepted* business event; failures are signalled by absence of an event plus error logs, not by a field on a row. I will not invent an outcome column in this step — I'll document the gap and leave it for a later decision (OD-P4A-01).

## Existing workflow action history

- `WorkflowAction` entity on `workflow_actions`: id, workflow_task_id, action_code (APPROVE/REJECT/REVERT/FORWARD/ESCALATE), performed_by, remarks, previous_state_id, next_state_id, action_at, correlation_id. Append-only by policy (no setters, no delete path in code).
- `WorkflowTask` on `workflow_tasks`: tracks assignment, status (PENDING/APPROVED/REJECTED/REVERTED/FORWARDED/CANCELLED/SKIPPED/ESCALATED), assigned_at, completed_at; has `@Version` for optimistic locking.
- `WorkflowInstance` on `workflow_instances`: `expense_request_id` is the link.
- `ActionsRepository.findByWorkflowTaskIdInOrderByActionAtAsc(taskIds)` returns the action history for an instance, oldest first. Tasks are loaded via `TasksRepository.findByWorkflowInstanceIdOrderByAssignedAtAsc(instanceId)`.
- The engine (`WorkflowEngine`) writes `WorkflowAction` rows for APPROVE/REJECT/REVERT/FORWARD — but it does **not** call `AuditRecorder`. This is deliberate: workflow_actions has richer vocabulary (previous/next state, remarks per step). The audit table receives SUBMIT and CANCEL from `ExpenseRequestService`, but APPROVE/REJECT/REVERT/FORWARD/ESCALATE currently live only in `workflow_actions`. Therefore the activity timeline must merge both sources.

## Missing functionality

1. No audit read controller/service — the repository already supports search but is not wired into HTTP.
2. No DTOs for audit responses; returning the entity directly would leak `ipAddress`, `userAgent`, and the internal JSON shape.
3. No service-layer authorization for audit search (RBAC must enforce `audit:view` plus scope checks where the events concern a specific request).
4. No request activity timeline endpoint.
5. Redaction is already applied on write; response must re-run a safety redaction pass to be defensive.
6. The search query requires `from`/`to` at the repo level but has no HTTP input validation, no maximum window size, no default window. Need to bound queries (BRD: "prevent unbounded queries").
7. Date-range validation missing (`from` must be `< to`, `to - from` must be bounded).
8. "Module" filter: implemented as entity_type filter (no separate module concept).
9. "Outcome" filter: not supported (column absent) — documented gap.
10. Actor-name search: existing repo supports a `search` LIKE over `performedByUsername`.
11. No dedup key exists between `audit_events` rows and `workflow_actions` rows for APPROVE/REJECT/REVERT/FORWARD — the only merge key is (timestamp ± epsilon, actor, action, correlation_id). This is unreliable, so the timeline must not try to dedupe by fuzzy matching. Instead, present APPROVE/REJECT/REVERT/FORWARD/ESCALATE from `workflow_actions` and explicitly suppress audit events for the same conceptual action where they exist. Given the engine currently writes only WorkflowAction (no audit rows) for those actions, the safe rule is: timeline pulls SUBMIT/CREATE/UPDATE/CANCEL from audit_events and APPROVE/REJECT/REVERT/FORWARD/ESCALATE from workflow_actions, ordered by (timestamp, event_id), with an assignment event derived from `WorkflowTask.assigned_at` (no current audit event covers task assignment, so synthesize it from the task row — but *do not* write new audit events for historical data, per the "do not create duplicate audit records" rule).

## Required database changes

None. All filters map to existing columns; existing indexes already cover the projected query patterns (time, entity, actor, correlation). Adding a composite `(action, created_at DESC)` index would help the action filter; with the time window always present and bounded, the existing `(created_at DESC)` index is acceptable for the expected volume and avoids speculative index bloat. No new Flyway migration in this step.

## Proposed API endpoints

(All under `ApiPaths.V1 = /api/v1`, matching every other controller.)

1. **`GET /api/v1/audit-events`** — paginated search. Query params: `actorId` (UUID), `actorName` (substring, safe LIKE over performedByUsername), `from`, `to` (ISO-8601 instant; bounded, mandatory), `entityType`, `entityId`, `action` (enum name), `correlationId`. Page/size/sort via `PageQuery`; sort allow-list: `createdAt,desc` (default) and `createdAt,asc`; maximum window = 31 days; default window = last 24 hours if neither bound supplied. Response envelope: `ApiResponse<PageResponse<AuditEventDto>>`.
2. **`GET /api/v1/audit-events/{id}`** — single event detail. Returns the same DTO plus parsed old/new value maps. Enforces `audit:view`; if the event's entityType is `ExpenseRequest`, also enforces request visibility using the existing `ExpenseRequestService.isVisible` (rule #2 of §7).
3. **`GET /api/v1/expense-requests/{id}/activity`** — merged timeline. Query params: page, size (timeline is bounded per request so no time window required). Returns `ApiResponse<PageResponse<TimelineEntryDto>>`. Entries from two sources ordered by `(timestamp ASC, source_sequence ASC, id ASC)` for deterministic tie-breaking:
   - Audit events for entity_type = ExpenseRequest and entity_id = the request id (CREATE, UPDATE, SUBMIT, CANCEL).
   - Workflow actions for the request's current workflow instance (APPROVE, REJECT, REVERT, FORWARD, ESCALATE), with state names resolved via StatesRepository; remarks surfaced verbatim (redaction runs only on secret-shaped strings per `AuditValueRedactor.SUSPICIOUS_VALUE` — remarks are free text, so a value-shaped string is still a user-supplied secret rather than a credential the system must protect; re-run the suspicious-value redaction to be safe).
   - Synthetic "task assigned" entries from `workflow_tasks` (ASSIGNED action), because assignment has no audit row and is a meaningful event on a timeline. Marked with source=`WORKFLOW_TASK` to make them distinguishable. These are read-model projections, not new audit records.

## Authorization requirements

- `audit:view` is required for both audit-events endpoints.
- `request:view-own` is *not* sufficient to browse audit events; `audit:view` is a separate, narrower permission (matches the existing Permissions catalogue and BRD Module K).
- Per rule #2 of the task §7: a user with `audit:view` must not see request activity they lack request-visibility for. Two enforcement points:
  - `/audit-events` list is unscoped (audit:view grants installation-wide audit search, consistent with BRD AUD-01). The "activity timeline" endpoint is scoped by request visibility — it uses the existing `ExpenseRequestService.isVisible(request, caller)` and reports 404 on an out-of-scope request, matching the existing GET `/expense-requests/{id}` behaviour.
  - `/audit-events/{id}` for an `ExpenseRequest` event requires request visibility of the referenced request; for other entity types, `audit:view` alone suffices (configuration/role/user events are installation-wide audit concerns).
- `/api/v1/expense-requests/{id}/activity` uses the same visibility rules as `GET /expense-requests/{id}`: annotated `@EdamPermission(Permissions.REQUEST_VIEW_OWN)` (mirroring the existing controller), and the service calls `ExpenseRequestService.isVisible` so `request:view-scope` (via data scopes) and `request:view-all` (via installation-wide scope) work exactly as for the request itself.

**Ambiguity documented, not invented:** `request:view-scope` is a catalogue code with no special enforcement beyond what `DataScope` and `isVisible` already provide. I will not introduce new semantics; the timeline endpoint will defer entirely to the existing visibility logic so it matches the register and detail endpoints exactly. If the team decides `view-scope` should differ from `view-own` in the future, the change belongs on the visibility helper, not on the timeline code.

## Test plan

New test class: `src/test/java/com/edam/audit/AuditReadApiTest.java` extending `AbstractPostgreSqlIntegrationTest`, using the same `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")` pattern as `ApprovalActionsApiTest`. Cover:

**Audit search**
- Search without filters (default 24-hour window).
- Search by actor id and actor name substring.
- Search by date range.
- Search by entity type, entity id, action, correlation id.
- Pagination (page 0 vs 1, size cap at MAX_SIZE=200).
- Sorting determinism (createdAt,asc vs createdAt,desc).
- Invalid ranges (from >= to, window > 31 days, malformed instant) → 400 with EDAM-1001/1002.
- Max page size enforcement (size > 200 clamped / rejected by PageQuery).

**Audit security**
- Unauthenticated → 401.
- Authenticated without `audit:view` → 403.
- Authorized (`audit:view`) → 200.
- Audit event for a request the auditor cannot see: list returns it (installation-wide audit), but `/audit-events/{id}` for such an event returns 404 (rule #2: "must not read another user's … request activity merely because [they have] audit:view" — applied at the detail level because a list filter by entity_type=ExpenseRequest cannot practicably be scoped per-row without loading every request; the list is a compliance tool for auditors who legitimately see metadata, while the details endpoint — which includes before/after — enforces scope). This is a deliberate policy choice.
- Sensitive value redaction: write an event whose payload contains a password-like key and a bearer-token-shaped value; assert the response body contains `***redacted***`.

**Request activity timeline**
- Create + submit + approve + reject/revert scenario drives multiple events; assert correct count and order.
- Deterministic ordering when two events share a timestamp (unit-test by inserting two workflow_actions with identical `actionAt` via the repository after construction).
- No duplicate events: prove the engine writes only WorkflowAction for APPROVE and the timeline surfaces exactly one APPROVE entry.
- Request not found → 404.
- User without request visibility → 404.
- Requester (owns the request) can see their own timeline.
- Department-scoped user sees a department request; outsider does not.
- Remarks containing a token-shaped string are redacted.

**Audit integrity**
- New endpoints are read-only: after every read test, assert no new `audit_events` rows are inserted (the audit search/activity endpoints must not themselves generate audit rows, per rule #8).
- Attempts to UPDATE/DELETE an audit event via JPA still fail (existing append-only behaviour — covered implicitly by the narrowed repo, but add a repo-level test that the search method runs in a read-only transaction and cannot call save).
- Existing audit events generated by request creation/submission remain unchanged after timeline reads.

## Audit coverage review (current call sites of AuditRecorder)

`recordChange` (before/after):
- `OrganizationProfileService` UPDATE OrganizationProfile — full.
- `OrganizationService` UPDATE OrganizationUnit, Department — full.
- `RoleManagementService` UPDATE Role — full.
- `UserManagementService` UPDATE User — full.

`record` (event-only, no before/after):
- `ApprovalLimitService` CREATE/UPDATE/ACTIVATE/DEACTIVATE DepartmentApprovalLimit — **partial** (no before/after).
- `ExceedingLimitRuleService` CREATE/UPDATE/ACTIVATE/DEACTIVATE ExceedingLimitRule — partial.
- `RecommendingAuthorityService` CREATE/UPDATE/ACTIVATE/DEACTIVATE RecommendingAuthority — partial.
- `DelegationMatrixService` CREATE/ACTIVATE/DEACTIVATE DelegationRule — partial.
- `DelegationService` UPDATE DelegationRule — partial.
- `ExpenseConfigurationService` (categories/subcategories/natures/types, vendor, tax authority, exchange rate, currency) — likely partial, will enumerate.
- `ExpenseRequestService` CREATE/UPDATE/SUBMIT/CANCEL ExpenseRequest — CREATE/SUBMIT/CANCEL are legitimately event-only (no prior state), but UPDATE lacks before/after — partial.
- `WorkflowConfigurationService` CREATE/UPDATE/ACTIVATE/DEACTIVATE/PUBLISH WorkflowDefinition and WorkflowVersion — all event-only — partial.
- `WorkflowEngine` — **no audit rows at all** for APPROVE/REJECT/REVERT/FORWARD/ESCALATE; relies on `workflow_actions`.
- `UserManagementService` (role assignment, password reset, activation) — need to enumerate.
- Login success/failure and access-denied events — event-only, correct.

I'll produce the full coverage matrix in the final documentation. In this step, improving before/after coverage is limited to the small, safe wins where existing code already has a before-snapshot in hand: ExpenseRequestService UPDATE already loads the prior request; I will pass a diff map there. I will **not** retrofit before/after for every admin entity in this PR because the task scope limits changes to what "the existing model and BRD support" and forbids "arbitrary serialization of entire entities" — that is a separate body of work.

## Open decisions documented (not invented)

- **OD-P4A-01 — Outcome column on audit_events.** BRD §26 and AUD-01 mention outcome/success-failure; current schema has no column. Adding one requires deciding how to represent failed business attempts (they currently never reach `audit_events` by design, because `record()` runs only after the operation commits — except for LOGIN_FAILURE and ACCESS_DENIED). This step does not add the column.
- **OD-P4A-02 — Audit list row-level scoping for ExpenseRequest events.** This step enforces scope at the detail endpoint and the timeline endpoint but not on the global list, because per-row scoping over audit_events is O(N) request lookups. The list is auditor-only (requires `audit:view`); metadata-only in list rows (no before/after). Documented for review.
- **OD-P4A-03 — Whether APPROVE/REJECT/REVERT/FORWARD/ESCALATE should also write audit_events.** The engine currently writes only `workflow_actions` for these. This step reads both sources but does not add new writes, to avoid creating duplicate records for historical data. A future step can add audit writes at the engine (and backfill is explicitly out of scope here).

Proceeding to implementation.
