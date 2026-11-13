# Phase 2 — decisions taken, and decisions deliberately left open

This file exists because Phase 2 touches money, approval authority and routing. Where the BRD is silent
or explicitly unresolved, the behaviour is recorded here rather than invented quietly in code. Each entry
states what was implemented, why, and what would change if the business decides otherwise.

Every entry marked **OPEN** is a decision the product owner must make. None of them blocks Phase 2
working; all of them should be settled before the system approves real money.

---

## OD-P2-01 — Currency conversion direction and rounding  **OPEN**

**BRD status:** explicitly unresolved. The BRD text says at line 1361–1363: *"PKR conversion formula and
rounding behaviour are not stated. Confirm rate direction, multiplication/division, decimal scale and
rounding mode."*

**What is implemented:** `base_currency_amount = requested_amount × rate`, where `rate` is the value of
the `exchange_rates` row whose `(from_currency, to_currency)` pair matches
`(request currency, base currency)` and whose effective period covers the request date. The result is
stored at `NUMERIC(19,4)` — the column's declared scale — using the rounding mode PostgreSQL applies on
assignment to that column.

**Why multiplication, and why that is still provisional:** the schema's rate is expressed
from-currency → to-currency, so multiplication is the only reading consistent with the stored data. The
*rounding* is inherited from the column rather than chosen, because choosing one (HALF_UP vs HALF_EVEN)
is a financial policy decision with a real, if small, effect on approved figures.

**What is deliberately NOT done:** no rounding is applied to the stored rate itself, and no
intermediate rounding is performed beyond the single storage step. Introducing a second rounding point
would make the figure irreproducible from its inputs.

**To close this:** confirm (a) that rates are quoted as "base units per one unit of foreign currency"
or the inverse, (b) the rounding mode, and (c) whether the base amount is rounded or truncated. If the
answer differs from the above, the change is confined to `ExchangeRateResolver` and the request service.

---

## OD-P2-02 — CAPEX department eligibility  **OPEN — consolidated into OD-P2-07**

**BRD status:** the reference implementation restricts new CAPEX requests to Admin, Information Technology
and Digital, and the same paragraph requires the target to make this institution configuration rather than a
code constant.

**What is implemented:** no department name, code or id appears in Java. Classification is driven by
`expense_categories.expense_type` (`OPEX`, `CAPEX` or `BOTH`), which decides the detail form and the
validation rules. A single seam, `ExpenseRequestService.capexDepartmentEligible(UUID)`, is where a
per-department restriction would be enforced; it currently permits every department.

**Correction:** an earlier revision of this entry claimed that restricting CAPEX to specific departments
could be achieved "by configuring categories". That is only true for the category dimension. It cannot
restrict *which departments* may raise CAPEX, because no such column or table exists. The claim is corrected
here and the substantive open question is carried by **OD-P2-07**, which is the entry to act on. This heading
is retained, pointing at OD-P2-07, so an earlier reference to "OD-P2-02" still resolves to the right
decision.

---

## OD-P2-03 — What "exceeding a limit" escalates *to*  **OPEN**

**BRD status:** the vocabulary exists (`exceeding_limit_rules.escalation_type` ∈ `NONE`,
`ADD_CEO_APPROVAL`, `NOTIFY_CFO`, `BLOCK`) but the BRD does not state when each is used, nor which office
a given exemption escalates to beyond the value's own name.

**What is implemented:** the rule table is fully manageable (create, update, activate, deactivate) with
overlap prevention, and the **evaluation** is implemented as a pure function that returns which rule
applies to a given (expense type, nature, currency, amount). What is **not** implemented is enforcement
inside approval routing, because that requires Phase 3's workflow engine, and because `BLOCK` versus
`ADD_CEO_APPROVAL` is a policy the BRD has not stated.

**Deliberate choice:** the evaluation is exposed through a *preview* endpoint so a configuration can be
tested before it governs anything. Nothing silently escalates.

**To close this:** decide for each escalation type what it does. `BLOCK` is unambiguous (refuse
submission). `ADD_CEO_APPROVAL` and `NOTIFY_CFO` need a ruling on whether they add a workflow step, a
notification, or both.

---

## OD-P2-04 — Department approval limit: per user or per department?  **implemented both, one must win**

**BRD status:** the table has a nullable `user_id` alongside a mandatory `department_id`, which permits
both readings: a limit for a department, or a limit for a person *within* a department.

**What is implemented:** both are stored and both are matched. Resolution order is **most specific
first**: a `user_id`-bearing rule beats a department-wide rule for the same department and expense type.
That is the only order that gives the nullable column a meaning — otherwise a per-user limit could never
take effect.

**Why this is still listed as open:** the BRD does not say whether a per-user limit may *exceed* the
department limit. Implemented conservatively: the effective limit for a user is the **lower** of their
personal limit and their department's, so a personal limit can tighten but never loosen departmental
authority. If the business intends personal limits to be authority *delegations* that can exceed the
department figure, this rule must be inverted — and that is a material governance change, not a tweak.

---

## OD-P2-05 — Request numbering scheme  **implemented, format provisional**

**BRD status:** silent on format.

**What is implemented:** `{CATEGORY_CODE}-{YYYYMM}-{sequence}` where the sequence is per category per
month, derived from a database sequence so two concurrent submissions cannot collide. The number is
unique installation-wide by constraint.

**Why provisional:** banks usually have an existing numbering convention (by branch, by cost centre, by
fiscal year). The generator is isolated in one class so adopting theirs is a local change.

---

## OD-P2-06 — Are the three CAPEX figures independent?  **CLOSED — answered by the BRD**

**Original concern:** the CAPEX detail table carries `invoice_total`, `approval_amount` and `total_charges`,
and it was unclear whether they are three independent figures or three views of one amount.

**Resolution:** the BRD answers this directly, so it was not a genuine open decision. §8.3 states:

> "Invoice total, approval amount and total charges must be positive and equal."

FR-REQ-006 states the same rule generally ("invoice total to equal approval amount and be greater than
zero"), and the register requirements repeat it. There is therefore no ambiguity to escalate: the equality
is a requirement, not a preference.

**What is implemented:** all three values are optional individually (a draft may be incomplete), each must
be greater than zero when supplied, and any two that are both present must be equal. Comparison uses
`BigDecimal.compareTo`, not `equals`, so `1000.00` and `1000.0000` are treated as the same money rather than
rejected over scale. Violations are reported per field.

**Correction to this document:** an earlier revision of this file recorded OD-P2-06 as OPEN, having been
written before §8.3 was read in full. That entry was wrong and is superseded here. It is left visible rather
than deleted because a decision log that silently loses its mistakes cannot be audited.

---

## OD-P2-07 — Per-department CAPEX eligibility has no configuration surface  **OPEN**

**BRD status:** §8.3 requires new CAPEX requests to be "restricted to Admin, Information Technology and
Digital departments in the reference implementation", and states that "the target must make this an
institution configuration, not a code constant."

**What exists:** `expense_categories.expense_type` restricts CAPEX by *category*, not by *department*. There
is no eligibility column on `departments`, no `capex_eligible` flag and no configuration table linking
departments to expense types.

**What is implemented:** a single seam, `ExpenseRequestService.capexDepartmentEligible(UUID)`, currently
returning `true`. It exists so the restriction has exactly one place to live. Nothing is hard-coded — in
particular the reference implementation's three department names appear nowhere in Java, because doing so
would violate the explicit instruction in the same BRD paragraph.

**Why it is not simply added:** the brief forbids creating tables without reconciling the schema first, and
a per-department eligibility matrix is a schema decision (one column? a join table keyed by expense type?)
that also determines what the CAPEX configuration screen will look like. It is a business decision, so it is
recorded.

**Consequence, stated plainly:** until this is decided, any department can raise a CAPEX request if it
selects a CAPEX category. That is a *wider* permission than the reference implementation, not a narrower
one, and it is the safe direction for an omission — a wrongly excluded department cannot do its job, and
would work around the system.

**To close this:** confirm the model (per-department flag, or department × expense-type mapping) and whether
existing departments should default to eligible or ineligible. Defaulting to ineligible would need a
migration that names the eligible departments, which is a data decision only the bank can make.

---

## OD-P2-08 — Approval-limit "most specific wins" versus "lowest wins"  **OPEN (implemented conservatively)**

**BRD status:** the BRD requires department approval limits and per-user limits to exist
(`department_approval_limits` has an optional `user_id`) but does not state how the two combine.

**What is implemented (OD-P2-04, restated here because it is the higher-risk half):** when both a personal
and a departmental limit apply, the **lower** figure binds. A personal limit can therefore *tighten* a
person's authority but never *widen* it beyond their department's ceiling.

**Why this direction:** the alternative — personal always wins — means a manager with a generous personal
limit can approve more than the head of the department they sit in, which is a governance inversion no bank
would intend. Choosing the conservative direction means the failure mode of a wrong configuration is a
refusal, not an over-approval.

**Consequence:** a bank that intends a senior individual to approve *above* their department's ceiling cannot
express that with a personal limit; they must raise the department limit. That is a real limitation and is
recorded rather than hidden.

**To close this:** confirm whether personal limits may exceed departmental limits, and if so whether that
requires a separate "override" designation rather than reusing the same table.
