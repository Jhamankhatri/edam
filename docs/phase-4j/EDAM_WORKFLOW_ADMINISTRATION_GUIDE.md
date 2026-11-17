# EDAM Workflow Administration Guide

Audience: the workflow administrator(s) who design and publish approval workflows.

## 1. Concepts

- **Workflow definition** — the container for a request type (e.g. "OPEX", "CAPEX"). A definition has versions over time.
- **Workflow version** — an immutable snapshot of states, transitions, steps and approvers. Only one version is `PUBLISHED` at a time per definition.
- **State** — a named position in the lifecycle (e.g. `DRAFT`, `SUBMITTED`, `HOD_REVIEW`, `COMMITTEE_REVIEW`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`).
- **Transition** — connects a source state to a target state via a named action (submit, approve, reject, revert, cancel, forward).
- **Step** — a stage of approval within a state (e.g. the `HOD_REVIEW` state has one step; `COMMITTEE_REVIEW` may have one step with quorum).
- **Step approver** — who can act at a step: an individual user, a role, a committee, the department head, a delegation-matrix resolver, or a recommending authority.
- **Quorum** — how many approvers must vote at a step before it advances (e.g. 3/5 committee members).
- **Published vs Draft:** when you edit a workflow, you edit a DRAFT. Publishing creates an immutable version. **In-flight requests continue to use the version they were submitted on** — new submissions pick up the newly published version.

## 2. Preconditions before publishing a workflow

Before any workflow can be published you must have:
- At least one request type configured for it (OPEX, CAPEX).
- Departments and branches defined (for department-head resolution).
- Categories configured (for category-based approval limits).
- Committees (and their members/levels) configured if any step uses a committee approver.
- Recommending authorities configured where applicable.

The `GET /workflows/versions/{versionId}/validate` endpoint reports errors and warnings (e.g. "step X references committee Y which has no active members", "state Z is unreachable from the initial state"). Fix all errors before publishing; warnings are informational.

## 3. Workflow editor screens

The frontend Workflow editor (to be built) is structured as:

1. **Definitions list** — `/app/admin/workflows`; shows definitions per request type with current published version, draft status and retirement flag.
2. **Definition editor** — three panels:
   - **States & transitions canvas** (left).
   - **Step detail** (right): approvers, quorum, remarks-required, SLA, allowed actions.
   - **Versions panel** (bottom or side): clone-from-published, clone-from-version, compare, validate, publish, retire.
3. **Compare view** — shows added/removed/changed states, transitions, steps, approvers between two versions.

## 4. Recommended OPEX workflow (reference example)

> **This is an illustrative starting point, not a mandate.** Per OD-02/OD-03, your institution must sign off on the exact routing order for CEO/CFO/HOD.

```
DRAFT ──submit──► SUBMITTED ──► HOD_REVIEW ──(approve)──► RECOMMENDING_AUTHORITY
                                                       ──(reject)──► REJECTED
        REJECTED ──resubmit──► SUBMITTED
        HOD_REVIEW ──(forward)──► HOD_REVIEW (new approver)
        RECOMMENDING_AUTHORITY ──(approve)──► [AMOUNT > LIMIT?]
              ├── no ──► APPROVED
              └── yes ──► COMMITTEE_REVIEW ──(quorum met)──► CFO_REVIEW ──(approve)──► APPROVED
        APPROVED ──(final)──► COMPLETED
        any non-terminal ──(cancel)──► CANCELLED
        any non-terminal ──(revert)──► DRAFT
```

Approver resolution sources available per step:
- **User** (specific user id)
- **Role** (all users holding a role; any one can claim)
- **Department Head** (resolved from the request's department via approval limits)
- **Delegation matrix** (routes to approver defined in `expense_delegation_rules` for department × category × amount)
- **Recommending authority** (configured for department × category)
- **Committee** (committee + level, quorum required)

## 5. Publishing a version

1. Save the draft.
2. Click **Validate**. Resolve all errors.
3. Review the comparison against the currently published version; confirm in-flight requests will not be affected (they won't — the engine pins versions at submission).
4. Click **Publish**. The new version becomes active for all new submissions. The previous published version is kept for history and continues to serve in-flight cases.

## 6. Retiring a version

A version can only be retired when:
- It is not the currently-published version; OR
- It is the currently-published version but a newer version exists and no in-flight cases reference it (or your policy allows forcing retirement after draining in-flight cases).

If a retirement attempt is rejected due to in-flight references, the API returns a list of active case ids; complete or cancel those before retrying.

## 7. Editing a published workflow

You never edit a published version. Instead:
1. **Clone from published** (or clone from any historical version) to create a new DRAFT.
2. Make your changes in the draft.
3. Validate and publish.

## 8. Business rules

If you use the business-rule engine in conjunction with a workflow (e.g. auto-approve under a threshold, or require additional documentation):
- Rule sets are versioned just like workflows.
- Rules run against the request AST (closed operator set — no arbitrary expressions).
- A rule can **block submission** (validation error), **warn** the approver, or **populate defaults**. It cannot bypass an approval step unless the workflow explicitly defines a transition triggered by a rule outcome.

## 9. Common pitfalls

- **Forgetting quorum** — a committee step with one member behaves like a single approver; with N members and no quorum set, the step waits forever.
- **Unreachable states** — validate catches these; do not publish a workflow where APPROVED cannot be reached from SUBMITTED.
- **Orphan steps** — a step with no approvers (e.g. a department with no HOD configured) causes submissions to fail with a configuration error; configure fallback delegation before publishing.
- **Mutating history** — never SQL-edit `workflow_versions`; clone and publish instead.
- **Tight coupling to roles** — if you hard-code a role approver and later rename the role, existing versions keep the role id (correct behaviour) but you must re-select it in new drafts.
