# 07 — Request Lifecycle & State Transitions

The request state machine lives on `ExpenseRequest.Status` (`src/main/java/com/edam/expenserequest/model/ExpenseRequest.java`). States are serialized as UPPER_SNAKE_CASE strings in JSON.

## 7.1 States

| State | Meaning | Terminal? | Visible to requester? |
|---|---|---|---|
| `DRAFT` | Created but never submitted; editable by requester; not visible to approvers. | No | Yes (own) |
| `SUBMITTED` | Submitted; awaiting workflow routing / first step activation. | No | Yes |
| `IN_PROGRESS` | Actively in approval (at least one task open). | No | Yes |
| `RETURNED` | Rejected by an approver (returned to requester for correction). | No (editable; resubmits) | Yes |
| `APPROVED` | Final approval granted. | Yes | Yes |
| `REJECTED` | *(Note: although present in some code paths, final rejection is represented by the decision outcome tracked in workflow history; the steady-state after an approver's rejection is `RETURNED`. The `REJECTED` enum constant exists for history/compatibility and is treated as terminal for audit.)* | Yes | Yes |
| `CANCELLED` | Withdrawn by requester; no further action. | Yes | Yes |
| `REVERSED` | Post-approval reverse (used by `request:reverse`). **Not yet exposed via a controller endpoint in Phase 4J**; reverts during active approval use `POST /approvals/tasks/{taskId}/revert` which returns the case to DRAFT via workflow. | Yes | Yes |

## 7.2 Transitions

| From | Action | Endpoint | Actor (permission) | Required body | To | Side effects |
|---|---|---|---|---|---|---|
| (none) | Create draft | `POST /api/v1/expense-requests` | Requester (`request:create`) | Request header + typeId + (optional) items | `DRAFT` | Request number is **not** assigned yet. |
| `DRAFT`, `RETURNED` | Update | `PUT /api/v1/expense-requests/{id}` | Requester (`request:edit`) | Full payload | unchanged | Overwrites draft content; optimistic-locked. |
| `DRAFT`, `RETURNED` | Submit | `POST /api/v1/expense-requests/{id}/submit` | Requester (`request:submit`) | none (optional remarks in some deployments) | `SUBMITTED` → `IN_PROGRESS` (first step opens atomically) | Request number assigned; workflow instance pinned to current published version; outbox notifications created. |
| `DRAFT`, `SUBMITTED`, `IN_PROGRESS`, `RETURNED` | Cancel | `POST /api/v1/expense-requests/{id}/cancel` | Requester (`request:cancel`) | `{ reason: string }` (max 500 chars) | `CANCELLED` | Open tasks closed; cancelReason/cancelledBy/cancelledAt set. |
| `IN_PROGRESS` | Approve (current task) | `POST /api/v1/tasks/{taskId}/approve` | Eligible approver (`approval:act`) | optional remarks | stays `IN_PROGRESS` or → `APPROVED` (if final step) | Records approval; advances step / creates next-step tasks; quorum checked. |
| `IN_PROGRESS` | Reject (current task) | `POST /api/v1/tasks/{taskId}/reject` | Eligible approver (`approval:act`) | `{ remarks: string }` required | `RETURNED` | Open tasks closed; requester notified. |
| `IN_PROGRESS` | Revert case | `POST /api/v1/tasks/{taskId}/revert` | Eligible approver (restricted by configuration; `approval:act`) | `{ remarks: string }` required | `DRAFT` (pins same workflow version for resubmission) | All tasks closed; revert action recorded. |
| `IN_PROGRESS` | Forward task | `POST /api/v1/tasks/{taskId}/forward` | Eligible approver (`approval:act`) | `{ recipientUserId: UUID, remarks: string }` | remains `IN_PROGRESS` | Closes current approver's task; creates new task for recipient. |

## 7.3 Concurrency & idempotency
- `ExpenseRequest` and `WorkflowTask` carry `@Version`; concurrent edits return 409 `EDAM-3002 CONCURRENT_MODIFICATION`. The frontend must reload and retry.
- Mutating request actions (create, submit, cancel, edit) and approval actions are annotated with `@IdempotentOperation(...)`. Send an `Idempotency-Key: <uuid>` header to guarantee at-most-one effect across retries.

## 7.4 Client state machine
```
        ┌────────┐  submit   ┌──────────┐  first step opens  ┌─────────────┐
        │ DRAFT  │──────────►│SUBMITTED│──────────────────►│ IN_PROGRESS │
        └───┬────┘           └──────────┘                   └┬────────────┘
            │                                                │ approve/reject/revert/forward
            │ cancel                                         │
            ▼                                                ▼
       ┌──────────┐      reject                      ┌──────────────────────────┐
       │CANCELLED │◄─────cancel (any non-terminal)   │  RETURNED (editable)      │
       └──────────┘                                 │  → edit → resubmit       │
                                                    └──────────┬───────────────┘
                                                               │ resubmit
                                                               ▼
                                                         SUBMITTED → IN_PROGRESS
                                                    approve (final step)
                                                               ▼
                                                         ┌──────────┐
                                                         │ APPROVED │
                                                         └──────────┘
```

## 7.5 UI visibility rules
- Edit/Submit/Cancel buttons appear only in DRAFT/RETURNED per the state rules above (and only if the user is the requester and within data scope).
- In IN_PROGRESS the requester sees a read-only view (unless cancel is allowed by policy).
- Approvers only see Approve/Reject/Forward on tasks assigned to them; Revert appears when configured for the user's role/step.
- After APPROVED/REJECTED/CANCELLED/REVERSED all action buttons are disabled; only the timeline and documents remain browsable.
