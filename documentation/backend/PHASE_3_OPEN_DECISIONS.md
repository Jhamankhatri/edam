# Phase 3 — open decisions

Each entry states what the BRD does and does not say, what was implemented in its absence, and what would
close it. Nothing here is a silently invented rule: where the BRD is silent the code refuses rather than
guesses, and the refusal is recorded here with its impact.

---

## OD-P3-01 — Approval actions that depend on an undefined business rule  **OPEN**

**BRD status:** partially stated, partially self-declared unresolved.

Three rules appear in BRD 8.5 and 7.4 but cannot be implemented as written without a decision:

1. *"CEO may approve or revert but cannot reject an EC request."* The BRD says this once, in a table row, and
   never defines what an "EC request" is for this purpose — is it any request whose current step is a
   committee step, or any request that will ever reach one?
2. *"An EC-stage revert returns the request to the last Phase-1 approver."* "Phase 1 approver" is the legacy
   implementation's vocabulary. The rebuilt engine has steps, not phases, and a step may be parallel.
3. *"Selective revert to a chosen previous approver is supported for eligible requests; eligibility criteria
   remain an open decision."* The BRD states the gap itself.

**What is implemented:**

* Revert goes to the state the published revision declares for `REVERT` from the current state, or to the
  revision's initial state when it declares nothing. This can express "back to the last Phase-1 approver"
  for a sequential chain, but expressing it requires the administrator to declare it.
* There is no selective revert: an approver cannot choose the destination.
* CEO-cannot-reject is not enforced anywhere.

**Impact:** a bank relying on the CEO/EC rule would find it unenforced — a CEO could reject a committee
request. That is a governance gap, not a crash, and it is visible because it is documented here and the rule
is absent from the code rather than half-implemented.

**To close:** define (a) the scope of "EC request", (b) whether "last Phase-1 approver" means the previous
sequential step, the initial step, or the requester, and (c) the eligibility criteria for selective revert.

---

## OD-P3-02 — Workflow simulation  **OPEN (not implemented)**

**BRD status:** required. 24.1: *"Support workflow validation and simulation using sample request attributes
before publication."* 24.2 lists "Simulate" as a designer capability.

**What is implemented:** validation (`GET /workflows/versions/{id}/validate`) returns structural errors and
operational warnings. It answers "can this workflow run at all".

**What is not:** simulation. Nothing answers "given department X, category Y and amount Z, which steps would
this request pass through, and who would be assigned". That question needs the assignment resolver run
against hypothetical facts, and the resolver currently resolves against a real request's department and
requester.

**Impact:** an administrator can publish a structurally valid workflow whose first step resolves to nobody
for one particular department, and will only find out when a request from that department fails to submit.

**To close:** decide the sample-facts contract (which request attributes a simulation takes) and whether
simulation must reflect a specific revision or the currently published one. The resolver already returns
"no approver" explicitly, so the work is assembling a synthetic `AssignmentContext`, not new resolution
logic.

---

## OD-P3-03 — Version compare and rollback  **OPEN (not implemented)**

**BRD status:** required. 24.2: *"Version history / compare — Inspect previous versions and differences"*,
and *"Retire / restore — ... restore an eligible version under controlled approval"*.

**What is implemented:** version history (`GET /workflows/{id}/versions`) and retirement. A previous revision
can be restored *manually* by cloning it into a draft (`POST /{id}/draft/from-published` only clones the
**published** one) — restoring an arbitrary older revision has no endpoint.

**Impact:** recovering from a bad publication means re-authoring the graph by hand, or re-publishing the
retired one if it is still the most recent draft source. Both are safe but slow, and a hand re-authoring is
where transcription errors happen.

**To close:** decide whether "restore" means re-publishing a retired revision (which would violate its own
immutability history) or cloning it forward into a new version number. The second is the safe reading and is
what the clone endpoint does today for the published revision; extending it to take a version id is the
implementation.

---

## OD-P3-04 — Conditional transitions  **OPEN (refused, not evaluated)**

**BRD status:** required. 24.1: *"Support conditions, branching, fallback approvers and explicit handling when
no eligible approver is found."*

**What is implemented:** `workflow_transitions.condition_rule_id` is a real foreign key to `business_rules`,
so an invalid reference cannot be stored. A transition carrying a condition is **refused at action time**
with `INVALID_CONFIGURATION` rather than treated as unconditional.

**Why refusal rather than a default:** treating an unevaluated condition as true would apply an approval
route the bank did not configure — the failure mode is a request approved by fewer people than intended.

**Impact:** a revision that declares a condition cannot process that action. Because validation reports this
at publication time, an administrator learns before requests are submitted, not after.

**To close:** the closed business-rule evaluator (`com.edam.businessrules`, absent) must interpret
`condition_definition` JSONB against request facts. That evaluator is a module of its own and was not in this
phase's scope.

---

## OD-P3-05 — Workflow cloning between institutions  **OPEN (out of scope by design)**

**BRD status:** stated as a target capability, 24.1: *"Support controlled workflow cloning from a template to
another institution, with explicit review and publish in the target institution."*

**What is implemented:** nothing, deliberately. Every deployment represents one bank with multiple
administrators, and the brief for this phase states that explicitly and forbids an institution-switching
model.

**Impact:** none today. Cloning assumes a multi-tenant deployment, which the product does not have.

**To close:** only if the product moves to a multi-institution deployment. At that point cloning is a copy
of the definition graph across an institution boundary plus a mandatory publish in the target — the draft
mechanism already supports the second half.

---

## OD-P2-01 — Currency conversion direction and rounding  **STILL OPEN (carried forward)**

**BRD status:** explicitly unresolved (BRD lines 1361–1363). The product owner has not supplied the decision.

**What is implemented:** `base_currency_amount = requested_amount × rate`, with the rate read from
`exchange_rates` for `(request currency → base currency)` covering the request date, stored at
`NUMERIC(19,4)` with the column's rounding.

**Why this matters more in Phase 3 than it did in Phase 2:** an approver now sees `requestedAmount` and
`baseCurrencyAmount` side by side *at the moment of decision*, and an approval limit is compared against the
converted figure. A rate direction error would therefore change who is authorised to approve, not merely
what a report shows.

**What this phase deliberately does not do:** it does not present the converted figure to an approver as
final, and it does not add rounding. The request response carries both amounts and the exchange-rate id, so
the figure is reproducible from its inputs. No approval decision in this phase displays a converted total as
authoritative.

**To close:** confirm rate direction, rounding mode and whether the base amount is rounded or truncated.

---

## OD-P3-06 — Who may read a request once it is in approval  **RESOLVED in this phase**

Recorded because it was a real ambiguity and the resolution is a decision, not an obvious truth.

**Question:** an approver covering department A was assigned a task on a request from department B. Phase 2
visibility is data-scope based, so the approver could not read the request they were asked to approve.

**Resolution:** assignment grants read access to that one request (`getForAssignedApprover`). It is narrower
than a scope — it applies to one request, and only while a task exists naming the caller — and drafts stay
invisible because they have no workflow. Alternative considered and rejected: widening the approver's data
scope, which would have given them every request in department B.
