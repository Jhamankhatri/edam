# 08 — Approval & Workflow API Contracts

## 8.1 Approval inbox & actions
Endpoints under `/api/v1/approvals`.

| ID | Method | Path | Permission | Request | Success |
|---|---|---|---|---|---|
| APR-001 | GET | `/approvals` | `approval:view` | Query: `page`, `size`, `sort`, `step`, `age`, `departmentId`, `type`, `q` | `PageResponse<TaskSummary>` (0-based page) |
| APR-002 | GET | `/approvals/count` | `approval:view` | — | `{ total: number, overdue?: number }` (badge counts) |
| APR-003 | GET | `/approvals/tasks/{taskId}` | `approval:view` | path: `taskId` | `TaskDetail` (task metadata, step, approver decisions to date, request summary) |
| APR-004 | GET | `/approvals/tasks/{taskId}/request` | `approval:view` | path: `taskId` | `RequestDetail` (full request detail, same shape as `GET /expense-requests/{id}`) |
| APR-005 | POST | `/approvals/tasks/{taskId}/approve` | `approval:act` | optional body `{ remarks?: string }` | 200; refreshed case file or workflow state |
| APR-006 | POST | `/approvals/tasks/{taskId}/reject` | `approval:act` | `{ remarks: string }` (required, max length enforced) | 200; request moves to RETURNED |
| APR-007 | POST | `/approvals/tasks/{taskId}/revert` | `approval:act` | `{ remarks: string }` (required) | 200; request returns to DRAFT, same workflow version pinned |
| APR-008 | POST | `/approvals/tasks/{taskId}/forward` | `approval:act` | `{ recipientUserId: UUID, remarks: string }` | 200; new task created for recipient |

### Quorum
- Steps can declare quorum (e.g. 3 of 5 committee members). APR-003 returns the step's quorum config and the current vote count. The step only advances automatically when quorum is met; late approvals are still recorded idempotently. The UI shows each voter's decision/remarks in the case file.

### Eligibility
- The backend enforces that the acting principal is an eligible approver for the task; non-eligible callers receive 403/404 (no leak). Do not assume the UI's button presence is sufficient.

## 8.2 Workflow configuration admin
Endpoints under `/api/v1/workflows`. Permissions: `workflow:view`, `workflow:manage`, `workflow:publish`.

| Purpose | Method | Path | Notes |
|---|---|---|---|
| List definitions | GET | `/workflows` | Returns definitions with their current published version id. |
| Create definition | POST | `/workflows` | Creates an empty definition with a first DRAFT. |
| Update definition metadata | PUT | `/workflows/{definitionId}` | Name/description only; does not touch versions. |
| Set active flag (deprecated by publish) | POST | `/workflows/{definitionId}/active` | Retained for compatibility; publish is the canonical operation. |
| List versions | GET | `/workflows/{definitionId}/versions` | All versions with statuses (DRAFT/PUBLISHED/RETIRED). |
| Get version detail | GET | `/workflows/versions/{versionId}` | States, transitions, steps, approvers, quorum, initial state. |
| Save (mutate) DRAFT | PUT | `/workflows/{definitionId}/draft` | Upserts the DRAFT's states/transitions/steps. |
| Clone from published | POST | `/workflows/{definitionId}/draft/from-published` | Creates new DRAFT equal to the currently-published version. |
| Clone from a specific version | POST | `/workflows/{definitionId}/draft/from-version/{sourceVersionId}` | Rollback/branch pattern. |
| Compare two versions | GET | `/workflows/versions/compare?from=&to=` | Diff of states/transitions/steps. |
| Validate a version | GET | `/workflows/versions/{versionId}/validate` | Returns `{ valid: boolean, errors: [], warnings: [] }`; call before publish. |
| Publish | POST | `/workflows/versions/{versionId}/publish` | Atomically marks version as PUBLISHED, demotes previous published to RETIRED (subject to rules). |
| Retire | POST | `/workflows/versions/{versionId}/retire` | Retires a non-active published version (safe). Retiring the active version is only allowed when no in-flight instances exist. |

### Immutable published versions
Once a version is PUBLISHED, it cannot be modified. DRAFTs are mutable; publishing creates an immutable snapshot. The case file always shows the `workflowVersionId` it is pinned to; subsequent publishes don't affect in-flight cases.

### Supported concepts (verifiable in code)
- **States** (e.g. DRAFT, SUBMITTED, HOD_REVIEW, COMMITTEE_REVIEW, APPROVED, REJECTED, CANCELLED) — defined per workflow; no hard-coded CEO/CFO/HOD routing.
- **Transitions** from a source state to a target state via a named action (submit/approve/reject/revert/cancel/forward).
- **Steps** within a state: order, approvers, quorum, whether remarks are required.
- **Step approver resolvers**: specific user, role, department head, committee/level, recommending authority, delegation-matrix resolver. (The UI should present these as selectable options in the workflow editor.)
- **Quorum**: `required` count out of `total` members at the step; `rejectOnAnyReject` flag.
- **Entry/exit side effects**: approval notifications, outbox entries, task creation.

What is NOT present: pre-seeded CEO/CFO/HOD routes (OD-02), automatic FX-based routing (OD-07), or any hard-coded "EC levels" constants. These must be configured per deployment through the workflow editor.

## 8.3 Workflow state at runtime
The approval case file returns the workflow runtime: current step, eligible approvers (already resolved to users/committees), task history with timestamps/actors/remarks, and the pinned `workflowVersionId`. Use this to render the stepper and action buttons — do not derive eligibility from role names client-side.
