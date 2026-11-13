# Phase 3 — Workflow configuration and approval management

Status: **implemented; verification evidence recorded in §10.**

Phase 2 ended with a workflow engine that nothing could reach: the state machine was implemented and
correct, but there was no service to configure a workflow, no caller of `WorkflowEngine.start`, and no
approval endpoint. Phase 3 builds exactly those three things, reusing the engine rather than replacing it.

## 1. What Phase 3 added

| Capability | Where |
|---|---|
| Workflow definitions bound to request types | `WorkflowConfigurationService` |
| Draft authoring: steps, order, approver designations, states, transitions | same |
| Validation before publication (BRD 24.1) | `WorkflowConfigurationService.inspect` |
| Publish immutable versions, retire the one replaced | same + `WorkflowVersion.publish/retire` |
| Submission starts a real, pinned workflow instance | `RequestWorkflowService` → `WorkflowEngine.start` |
| Approval inbox, case file, per-action availability | `ApprovalService` |
| Approve, reject, revert, forward with assignment checks | `ApprovalService` + `WorkflowEngine` |
| Committee resolution | `CommitteeResolutionService` → `WorkflowAssignmentResolver` |
| Transitions consulted, not merely stored | `WorkflowEngine.resolveTarget` + `TransitionsRepository` |

## 2. The decisions that shaped it

### 2.1 Published versions are immutable, and that is enforced in three places

`WorkflowVersion.isEditable()` returns false once published; `applyGraph` refuses a non-draft; and the
unique index permits one `PUBLISHED` row per definition. Publishing a replacement retires the incumbent in
the same transaction, before the new one is published, because the index permits only one published row at
a time.

A partial application of that pair is the worst outcome available: new published while old is still
published violates the index, and old retired while new is not published leaves the definition unable to
route anything.

### 2.2 In-flight requests keep the version they started under

This was already true of the engine — `WorkflowInstance.workflowVersionId` is written at start and never
updated — and Phase 3 did not weaken it. It is now also true of resubmission: a reverted request re-enters
**its original instance**, re-opening the first step of the pinned revision, rather than starting a second
instance on whatever is published now.

Test evidence: §7, "publishing a newer version does not alter existing workflow instances".

### 2.3 Transitions are data; the meaning of an action is code

V8 created `workflow_transitions` and noted that nothing consulted it, and that this "would mislead whoever
configures it next". Phase 3 closes it with a deliberate split:

* **Data** decides which actions are permitted from which state and what state each leads to.
* **Code** decides what the action *means* — that an approval counts toward quorum, that a rejection closes
  sibling tasks, that a revert cancels them.

A revision that declares no transitions is treated as using the engine's built-in semantics. This is not a
silent fallback: every revision authored before Phase 3 is in that position, and refusing their actions
outright would break workflows already in production. Once a revision declares *any* transition for a
state, that state's permitted actions are exactly what it declares, and an undeclared action is refused with
the action named.

**Conditional transitions cannot be evaluated yet.** `condition_rule_id` is now a real foreign key to
`business_rules`, but the rule evaluator is not part of this phase. A transition carrying a condition is
refused rather than evaluated-as-true, because silently ignoring a configured condition would apply an
approval route the bank did not intend. Recorded as **OD-P3-04**.

### 2.4 A permission is not an assignment

BRD section 24: *"A valid role alone shall not grant approval authority for a request unless the workflow
assigns or permits that user to act."*

`@EdamPermission(APPROVAL_ACT)` answers "may this person approve anything?". `ApprovalService.requireAssigned`
answers "is this person the approver of *this* task?". An administrator holding every permission still cannot
approve a task addressed to someone else, and no endpoint takes an actor id from the client.

The ordering is deliberate: remarks are validated first (the client can fix that without understanding the
workflow), then assignment, then state. A caller who is not the approver learns *that*, not that the task is
already closed — the latter would disclose that it was acted on.

### 2.5 Assignment grants read access to the request

`ExpenseRequestService.get` applies the register's data scope, so an approver covering another department
would get 404 on the request they are asked to approve. `getForAssignedApprover` adds assignment as a second,
narrower authority: it applies to one request, and only because a task on it names the caller. Drafts stay
invisible, because a draft has no workflow and so no task can point at one.

### 2.6 The four engine states are derived, not demanded from the caller

`DRAFT`, `IN_APPROVAL`, `APPROVED` and `REJECTED` (plus `CANCELLED`) are added by `requestedStates` if the
caller did not name them. A designer therefore cannot publish a chain that the engine can start but never
finish — which would fail at the first rejection, long after the person who configured it had gone home.

### 2.7 No hardcoded ladder, no bank names, no CEO/CFO rules in Java

OPEX and CAPEX get separate chains because a definition *names a request type* and a request *carries one*.
Nothing in `com.edam.workflow` mentions OPEX, CAPEX, a department name, an amount threshold or a person. A
bank that invents a third request type gets a third workflow without a code change.

The engine's `ApproverType` enum does name `CEO`, `CFO` and `HEAD_OF_ADMIN_GSD`. Those are **designations**
in the schema and the BRD's own vocabulary, not rules: what they resolve to is a role code, and whether a
workflow uses them at all is configuration.

## 3. Database changes (V17, forward-only)

| Change | Why |
|---|---|
| `fk_workflow_transitions_condition` → `business_rules(id)` | The column was an unconstrained UUID; a dangling rule id would be silently ignored |
| `workflow_versions.retired_by`, `retired_at`, `superseded_by_version_id` | "Who stopped this route, when, and what replaced it" is not queryable from audit JSON |
| `ck_workflow_versions_retired` | A `RETIRED` row must name its retirer, symmetrical with `ck_workflow_versions_published` |
| `ck_workflow_versions_supersede_self` | A version cannot supersede itself |
| `ix_workflow_tasks_instance_step_status` | The shape quorum counting and step closure query |
| `ix_workflow_tasks_pending_inbox` (partial) | The inbox reads only `PENDING` rows |

No constraint was weakened. The V8 status, quorum and approver-reference CHECKs are what stop an invalid
workflow being stored at all, and they remain.

## 4. Permissions

No permission was added. The catalogue from V12 already contains `workflow:view`, `workflow:manage`,
`workflow:publish`, `approval:view` and `approval:act`, all previously unreferenced by any endpoint. Phase 3
is their first consumer, which is why no migration was needed.

| Endpoint group | Permission | Why that one |
|---|---|---|
| Read definitions, versions, validation | `workflow:view` | Needed by anyone explaining where a request went |
| Author drafts, create/rename definitions | `workflow:manage` | A draft routes nothing, so an author need not be a publisher |
| Publish, retire | `workflow:publish` | The operation that changes how money is approved |
| Inbox, case file, request view | `approval:view` | Read access without the power to decide |
| Approve, reject, revert, forward | `approval:act` | Plus assignment, checked in the service |

The `manage`/`publish` split is what makes four-eyes possible on the configuration itself.

## 5. Endpoints added

All under `/api/v1`.

| Method | Path | Permission |
|---|---|---|
| GET | `/workflows` | `workflow:view` |
| POST | `/workflows` | `workflow:manage` |
| PUT | `/workflows/{id}` | `workflow:manage` |
| POST | `/workflows/{id}/active` | `workflow:manage` |
| GET | `/workflows/{id}/versions` | `workflow:view` |
| GET | `/workflows/versions/{vid}` | `workflow:view` |
| PUT | `/workflows/{id}/draft` | `workflow:manage` |
| POST | `/workflows/{id}/draft/from-published` | `workflow:manage` |
| GET | `/workflows/versions/{vid}/validate` | `workflow:view` |
| POST | `/workflows/versions/{vid}/publish` | `workflow:publish` |
| POST | `/workflows/versions/{vid}/retire` | `workflow:publish` |
| GET | `/approvals` | `approval:view` |
| GET | `/approvals/count` | `approval:view` |
| GET | `/approvals/tasks/{id}` | `approval:view` |
| GET | `/approvals/tasks/{id}/request` | `approval:view` |
| POST | `/approvals/tasks/{id}/approve` | `approval:act` |
| POST | `/approvals/tasks/{id}/reject` | `approval:act` |
| POST | `/approvals/tasks/{id}/revert` | `approval:act` |
| POST | `/approvals/tasks/{id}/forward` | `approval:act` |

No existing endpoint changed shape. `POST /expense-requests/{id}/submit` keeps its contract but now starts a
workflow — which is the behaviour its Phase 2 documentation promised as the next step, not a breaking change.

## 6. Requirement-to-code mapping

| Requirement | Status | Where |
|---|---|---|
| 24.1 Configure by OPEX/CAPEX nature | **Implemented** | `WorkflowDefinition.requestTypeId` |
| 24.1 Sequential and parallel stages | **Implemented** | `WorkflowStep.ApprovalMode` + `WorkflowEngine.assignStep` |
| 24.1 Configurable approver designations | **Implemented** (9 of 9 types) | `WorkflowStepApprover.ApproverType` + resolver |
| 24.1 Minimum approval counts / quorum | **Implemented** | `WorkflowStep.minimumApprovals`, `effectiveQuorum` |
| 24.1 Amount bands with non-overlap | **Implemented via committee levels** | `CommitteeLevel` + `ex_committee_levels_no_overlap` |
| 24.1 Draft/published, versioning, retirement | **Implemented** | `WorkflowVersion.Status`, §2.1 |
| 24.1 In-flight request keeps its version | **Implemented** | `WorkflowInstance.workflowVersionId` |
| 24.1 Validate and simulate before publication | **Validate implemented; simulate not** | `validate`; **OD-P3-02** |
| 24.1 Prevent publishing invalid workflows | **Implemented** | `inspect` → `publish` refusal |
| 24.1 Conditions and branching | **Partially** — transitions are data, conditions refuse | **OD-P3-04** |
| 24.1 Workflow cloning between institutions | **Not implemented** — single-deployment model | **OD-P3-05** |
| 24.2 Draft creation, update, validate, publish, history | **Implemented** | `WorkflowConfigurationController` |
| 24.2 Compare and rollback/restore | **Not implemented** | **OD-P3-03** |
| 24.3 Transactional execution with task creation | **Implemented** | `@Transactional` on submit and every action |
| 24.3 Duplicate-action protection | **Implemented** | task `@Version` + engine state check |
| 24.3 Record actor, action, remarks, states, version | **Implemented** | `WorkflowAction` + `audit_events` |
| 8.5 Remarks required for approve/reject/revert | **Implemented** | `@NotBlank` + service check |
| 8.5 Forward requires recipient and remarks | **Implemented** | `ForwardRequest` + `forward` |
| 8.5 Actions only for a valid pending assignment | **Implemented** | `requireAssigned`, `availableActions` |
| 8.5 CEO cannot reject an EC request | **Not implemented** | **OD-P3-01** |
| 8.5 EC revert routes to the last Phase-1 approver | **Partially** — declared target honoured | **OD-P3-01** |
| 7.4 Selective revert to a chosen approver | **Not implemented** | **OD-P3-01** |
| 7.4 Revert requires remarks | **Implemented** | `RevertRequest` |
| 6.1 Request data scope enforced | **Implemented** | §2.5 |

## 7. Known gaps

1. **Conditional transitions are refused, not evaluated** (OD-P3-04). A revision that uses one cannot
   publish a working chain until the evaluator exists.
2. **No simulate endpoint** (OD-P3-02). Validation covers structural defects; it does not answer "who would
   this particular request reach".
3. **No version compare or rollback** (OD-P3-03). History is readable; reverting to an earlier revision means
   cloning it into a draft and publishing it, which is safe but manual.
4. **CEO-cannot-reject-an-EC-request is not enforced** (OD-P3-01), and neither is selective revert nor the
   EC-revert target beyond what a transition declares. All three need a business decision, not more code.
5. **Document upload is still Phase 4**, so an approver sees document *types* and not the files.
6. **No escalation timers or reminders.** BRD 24.1 calls these "a configurable enhancement"; nothing is
   scheduled.
7. **Reversal of an approved request** remains unimplemented; `request:reverse` exists in the catalogue with
   no endpoint.

## 8. Open decisions

See `PHASE_3_OPEN_DECISIONS.md`. OD-P3-01 through OD-P3-05, plus the carried-forward OD-P2-01.

## 9. Test coverage

Nine test classes were added or extended in this phase. Every one runs against real PostgreSQL (Testcontainers)
with Flyway applied and `ddl-auto: validate` retained, and none is `@Transactional` — so a rollback assertion
observes committed state rather than a session that will be discarded.

| Class | Tests | What it proves |
|---|---|---|
| `WorkflowConfigurationApiTest` | 19 | Definitions, drafts, validation, publication, immutability, per-request-type isolation, retirement leaving a type unsubmittable |
| `ApprovalActionsApiTest` | 16 | Inbox, count, case file, approve/reject/revert/forward, the assignment rule, duplicate refusal, recorded history |
| `SubmissionIntegrationTest` | 7 | Atomic start, failed start leaving an editable draft, version pinning across republication, withdrawal ending the workflow and leaving an audit trace |
| `ApproverResolutionTest` | 7 | Configured individual, inactive user, empty role, unstaffed committee, missing office holder, duplicate designations, missing configuration |
| `ApprovalLimitApiTest` | 14 | Authorization, validation and persistence for approval limits and recommending authorities over HTTP — the Step 0 gap |
| `OptimisticLockingIntegrationTest` | 2 | Two concurrent writers produce exactly one conflict; a stale read-and-write is refused and the stored row is the winner's value |

**Total suite: 23 classes, 287 tests, 0 failures, 0 errors, 3 skipped** (from 17 classes / 222 tests at the end of
Phase 2). The three skips are pre-existing and unrelated.

### What the tests found

Four of the tests failed on first run for reasons that were defects rather than wrong expectations:

1. **`GET /auth/csrf` returned a token in the body and wrote no cookie.** Spring Security 6 defers token
   creation, and only the `CsrfFilter`'s dereference on an unsafe method saves the cookie — a `GET` never does.
   An SPA bootstrapping with that endpoint had nothing to send on its first write. Fixed by saving the token
   explicitly against the same repository the filter uses. This is a Phase 1 endpoint, and the defect was
   latent until the new tests changed the context's timing.
2. **A duplicate approval attempt returned a status the test did not expect.** The task correctly refused the
   second action; the test had assumed the wrong code. The correct behaviour (422, not 409) is now asserted with
   a comment explaining why a retry would not help.
3. **`submit` correctly refused a draft-shaped payload.** The validator, built in Phase 2, requires an
   allocation and a quotation before submission. The new test fixture was building a draft payload; the rule
   fired before the workflow was ever reached, which is the ordering Phase 2 intended.
4. **The "outside their data scope" test was not actually outside it.** The approver shared the request's
   department, so the register legitimately served the row and the assertion proved nothing about assignment
   granting access. The approver is now scoped to a different department.

## 10. Verification method and its limits

This workspace has no JDK, Maven, Docker or reachable Maven Central, so every result below is from GitHub
Actions, not from a local build. What that does and does not establish:

* **Establishes:** the code compiles under Java 21; every test passes against PostgreSQL 18 with all migrations
  applied and Hibernate validating the mapping against the real schema; the WAR packages and serves its views.
* **Does not establish:** interactive behaviour, browser-side flows, or performance. No frontend exists yet.
* **Not claimed as verified:** conditional transitions (refused, never evaluated), simulation, version compare
  and rollback, and the CEO/EC rules. Those are unimplemented, not untested.

## 11. CI evidence

| Run | Commit subject | Result |
|---|---|---|
| [43426368171](https://github.com/Jhamankhatri/edam/actions/runs/43426368171) | Qualify the withdrawal test's outer references | **success** — 23 classes, 287 tests, 0F, 0E |

Every earlier run on the branch and the reason it failed is in the commit history; the failures were compile
errors, import paths moved in Boot 4, `@Nested` `this` qualification, record-vs-entity accessors, and four
genuine expectation mismatches described above. No test was weakened and no assertion was removed to obtain a
green build.
