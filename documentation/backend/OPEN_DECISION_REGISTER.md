# Consolidated Open-Decision Register

**Status as at:** `main` @ `7119739a7c839586f3aef3724886c20fb5e2ab73`, 2026-11-13.
**Supersedes nothing.** `PHASE_2_OPEN_DECISIONS.md` and `PHASE_3_OPEN_DECISIONS.md` remain the detailed records;
this file is the single place that answers "what is still undecided, and what does it block". Where the two
disagree with the code, **the code wins** — one such case is recorded in §5.

Fourteen identifiers exist across the two logs. Six are open and consequential, two are closed, one is
resolved, one is partial, and the rest are recorded-but-inert. Nothing was dropped for brevity.

---

## 1. The register

| ID | Decision | Status | Blocks real money | Blocks Phase 4 | Owner |
|---|---|---|---|---|---|
| **OD-P2-01** | Currency conversion direction and rounding | **OPEN** — and misdescribed; see §5 | **YES — primary** | No (but gates reporting) | Product + Finance |
| OD-P2-02 | CAPEX department eligibility | **CLOSED** → OD-P2-07 | — | — | — |
| **OD-P2-03** | What "exceeding a limit" escalates *to* | **OPEN** | **YES** | No | Product |
| OD-P2-04 | Department limit per user or per department | **PARTIAL** — both modelled; "lower wins" chosen | Verify the choice | No | Product + Risk |
| OD-P2-05 | Request numbering scheme | **OPEN** — implemented, format provisional | No | No | Operations |
| OD-P2-06 | Are the three CAPEX figures independent? | **CLOSED** — BRD §8.3 answers it | — | — | — |
| **OD-P2-07** | Configurable CAPEX department eligibility | **OPEN** | **YES** | Shapes config screens | Product + Admin |
| **OD-P2-08** | Personal vs departmental limit precedence | **OPEN** — conservative default chosen | **YES** | No | Risk + Governance |
| **OD-P3-01** | CEO/EC rejection and selective revert | **OPEN** | **YES** — control unenforced | No | Governance |
| OD-P3-02 | Workflow simulation | **OPEN** — not implemented | No | Natural Phase 4 item | Product |
| OD-P3-03 | Version compare and rollback | **OPEN** — not implemented | No | Natural Phase 4 item | Product |
| **OD-P3-04** | Conditional transitions and rule evaluation | **OPEN** — refused, not evaluated | If conditional routing is needed | **YES — hard gate** | Security + Product |
| OD-P3-05 | Cross-institution workflow cloning | **OPEN** — out of scope by design | No | No | Future |
| OD-P3-06 | Who may read a request in approval | **RESOLVED** — assignment grants read | — | — | — |

---

## 2. Blocking real-money production use — 5 decisions

These are not bugs. In each case the code behaves defensibly and says so; what is missing is a signature.

### OD-P2-01 — Currency direction and rounding  *(the most consequential)*

**What the BRD says:** explicitly unresolved (lines 1361–1363): *"PKR conversion formula and rounding behaviour
are not stated. Confirm rate direction, multiplication/division, decimal scale and rounding mode."*

**What the code does:** `base_currency_amount = requested_amount × rate`, using the `exchange_rates` row for
`(request currency → base currency)` effective on the request date, stored at `NUMERIC(19,4)` after
`RoundingMode.HALF_UP`. If no rate is effective it **refuses** rather than inverting an available pair.

**Why it blocks:** from Phase 3 onward an approver sees the converted figure at the moment of decision, and
approval limits are compared against it. A rate-direction error changes *who is authorised to approve*, not
merely what a report prints. This is the only decision whose error mode is a wrong approval rather than a
wrong display.

**To close:** confirm (a) whether rates are quoted as base-per-foreign or the inverse, (b) `HALF_UP` vs
`HALF_EVEN`, (c) round vs truncate. The change is confined to `ExchangeRateResolver`, by design.

### OD-P2-03 — What exceeding a limit does

The `exceeding_limit_rules` vocabulary exists (`NONE`, `ADD_CEO_APPROVAL`, `NOTIFY_CFO`, `BLOCK`), the table is
fully manageable with overlap prevention, and evaluation is implemented as a pure function behind a **preview**
endpoint. What does not exist is enforcement in approval routing, because `BLOCK` versus `ADD_CEO_APPROVAL` is
a policy the BRD never states.

**Consequence today:** a configured rule changes nothing about who approves. An administrator can believe they
have wired an escalation and have wired nothing — the single most misleading gap in the system. It is recorded
rather than guessed, but the recording is not visible in the configuration UI.

**To close:** state, per escalation type, whether it adds a workflow step, a notification, or both.

### OD-P2-07 — CAPEX department eligibility

BRD §8.3 restricts new CAPEX to Admin/IT/Digital **and** requires the target to make this configuration, not a
constant. Implemented as a single seam, `capexDepartmentEligible`, returning `true` — so any department may raise
CAPEX if it picks a CAPEX category. That is *wider* than the reference implementation, which is the safe
direction for an omission.

**To close:** choose the model (per-department flag vs department × expense-type mapping) and whether existing
departments default to eligible. Defaulting to ineligible needs a migration naming the eligible departments — a
data decision only the bank can make.

### OD-P2-08 — Personal vs departmental limit precedence

Both are stored and both are matched; the **lower** binds, so a personal limit can tighten but never widen
authority beyond the department ceiling. A senior individual who must approve *above* their department cannot
express that without raising the department limit.

**To close:** confirm whether personal limits may exceed departmental ones, and if so whether that needs a
distinct "override" designation rather than reusing the same table.

### OD-P3-01 — CEO/EC rejection and selective revert

Three BRD rules cannot be implemented as written: "CEO may approve or revert but cannot reject an EC request"
(what is an *EC request*?), "an EC-stage revert returns the request to the last Phase-1 approver" (*phase* is
legacy vocabulary; the rebuilt engine has steps, possibly parallel), and selective revert (the BRD itself
declares eligibility open).

**Implemented:** revert goes where the revision's `REVERT` transition says, or to the initial state. No
selective revert. **CEO-cannot-reject is enforced nowhere** — a CEO can reject a committee request today.

**To close:** define the scope of "EC request", what "last Phase-1 approver" means, and selective-revert
eligibility.

---

## 3. Blocking Phase 4 — 1 hard gate

### OD-P3-04 — Conditional transitions and the rule evaluator

**What exists:** `workflow_transitions.condition_rule_id` is a real foreign key (V17), validation reports a
conditional transition at publication time, and an action on a conditional transition is **refused** with
`INVALID_CONFIGURATION` rather than treated as unconditional.

**What does not:** the evaluator. `com.edam.businessrules` does not exist.

**Why this gates Phase 4:** RULE-01 and RULE-02 *are* Phase 4's business-rule module, and they are the same
evaluator as WF-03's conditional routing. RULE-02 requires "constrained expressions/decision tables; prohibit
arbitrary code and SQL execution" — so choosing the expression language is a **security** decision with a large
blast radius. Writing the evaluator first would mean inventing that policy inside the code, which is precisely
what this register exists to prevent. It also determines whether the four `rule:*` permissions can ever have a
consumer.

**Also affects:** OD-P3-02 (simulation) becomes far more useful once conditions exist, because simulating a
conditional route is the only way to check one.

---

## 4. Optional enhancements — 4 decisions

| ID | Decision | Why optional |
|---|---|---|
| OD-P2-05 | Request numbering format | Isolated in one generator; the current `{CATEGORY}-{YYYYMM}-{seq}` is unique and collision-safe. Banks usually have their own convention, so adopting it is a local change whenever they say so. |
| OD-P3-02 | Workflow simulation | Validation already answers "can this run at all". Simulation answers "who would approve *this* request". Real value, no dependency, no block. |
| OD-P3-03 | Version compare and rollback | Recovery from a bad publication is possible manually (clone published → draft → publish). Slow and transcription-prone, not unsafe. |
| OD-P3-05 | Cross-institution cloning | Assumes a multi-tenant deployment the product deliberately does not have. Zero impact today. |

---

## 5. Discrepancy between the decision logs and the code

**OD-P2-01's description of the rounding is wrong, and it is the kind of error that matters.**

`PHASE_2_OPEN_DECISIONS.md` states:

> *The **rounding** is inherited from the column rather than chosen, because choosing one (HALF_UP vs HALF_EVEN)
> is a financial policy decision with a real, if small, effect on approved figures.*

`ExchangeRateResolver.scale` (line 97) states:

```java
return value.setScale(4, RoundingMode.HALF_UP);
```

A mode **has** been chosen — explicitly, in one place. The class's own javadoc is accurate about it; only the
decision log is not.

**Why it is worth correcting rather than noting:** the log's framing invites a reader to believe no financial
policy is operative until they decide, when in fact `HALF_UP` is being applied to every converted amount shown to
an approver. The correct ask is "ratify or change the existing choice", not "make one". The stated closure path
is still accurate — the change is confined to one method — but for the right reason: the choice is isolated, not
absent.

**Action:** left in place in the Phase 2 log (a record that silently loses its mistakes cannot be audited) and
corrected in `PHASE_3_MERGE_VERIFICATION.md` §4.3 and `BACKEND_COMPLETION_AUDIT.md` §12.4.

---

## 6. Decisions that are already settled

Recorded so nobody re-opens them:

| ID | Resolution |
|---|---|
| OD-P2-02 | Consolidated into OD-P2-07 — the category dimension cannot restrict *departments*. |
| OD-P2-06 | BRD §8.3 requires invoice total = approval amount = total charges. Not ambiguous. Implemented with `compareTo`, so scale differences are not treated as inequality. |
| OD-P3-06 | Assignment grants read access to that one request (`getForAssignedApprover`), rather than widening data scope. Narrower and auditable; drafts stay invisible because they have no workflow. |

---

## 7. Summary for a decision meeting

**To unblock real money (5):** OD-P2-01, OD-P2-03, OD-P2-07, OD-P2-08, OD-P3-01.
**To unblock Phase 4 (1):** OD-P3-04.
**To stop misleading administrators (1):** OD-P2-03 — an configured escalation that does nothing is worse than an
absent one.
**Everything else (4):** OD-P2-05, OD-P3-02, OD-P3-03, OD-P3-05 — schedule freely.

The pattern across all five money-blocking entries is consistent enough to be worth stating once: where the BRD
was silent, the implementation chose refusal or the conservative direction. That is the correct engineering
answer and it is still not a business decision. Nothing in the system approves money on an invented rule — which
is why the list is this long, and why the bank's answers are the only thing that shortens it.
