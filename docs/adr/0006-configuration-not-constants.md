# ADR-0006 — Legacy hard-coded values become institution configuration

* **Status:** Accepted (structure); values require **business confirmation**
* **Date:** 2026-11-12
* **Related:** BRD §15.2, §15.3, OD-06, OD-21, FM-10, FM-13; AC-04, AC-13

## Context

The BRD documents three legacy behaviours that were hard-coded in the reference implementation and must not
be reproduced as constants in a multi-institution product:

1. **Department Head threshold fallback of PKR 20,000** when no configured limit exists (BRD §9, §10.2,
   OD-06/OD-21).
2. **CAPEX new-request restriction to three departments** — Admin, Information Technology and Digital
   (BRD §8.3, OD-21).
3. **Exceeding-limit configuration (FM-13)** whose enforcement in live routing is unverified (OD-05).

A constant cannot be changed per client without a build; a `if (client.equals("albaraka"))` cannot be
supported. Both violate the product objective (OBJ-08) and the configuration principles (BRD §15.2).

## Decision

Model each as **typed, validated configuration with a conservative default**, owned by the institution:

* `edam.institution.features.department-head-limit-fallback` (`BigDecimal`, default `20000.00`).
* `edam.institution.features.capex-department-restriction-enabled` (default `true`, i.e. restriction on).
* `edam.institution.features.exceeding-limit-enforced-in-routing` (default **`false`** — the BRD does not
  verify that the threshold affects routing, so the platform must not invent the behaviour).
* The persisted `institution` row carries the per-tenant values that are genuinely tenant data
  (`department_head_limit`, `capex_department_restriction_enabled`); the deployment properties are the safe
  default for a fresh install.
* `department_head_limit` is **nullable** on purpose: "not configured" is a business state distinct from
  zero, and it is what triggers the fallback.

Every property is documented on the Java field, validated (`@Validated`, `@Pattern`, `@NotNull`) and
described in the README's configuration table. Changing a value is a configuration change, never a code
change or a redeploy.

## Consequences

**Positive**

* A new institution is onboarded by configuration; the code does not grow a client branch.
* The three unverified legacy behaviours are **visible and named** instead of buried in code, which is
  exactly what the BRD asks for (no invented business rules).
* Reversing a decision (for example turning on exceeding-limit enforcement) is a property change with a
  recorded business sign-off.

**Negative / risks**

* Configuration can be wrong. Mitigation: validation at startup, conservative defaults, and a documented
  owner (institution administration, Phase 2).
* The fallback value still encodes a legacy number. It is acceptable **only** as a documented fallback; the
  business must confirm whether one global threshold is sufficient or category/department thresholds are
  required (OD-06). Until then, no routing logic consumes it.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Constants in Java | Cannot differ per institution; requires a rebuild per client |
| Full configuration in the database only | A fresh install must work before an administrator exists; deployment defaults are the safe bootstrap |
| Implementing routing with the fallback now | Depends on the unresolved approval state machine (OD-09) and route order (OD-02); would be an invented rule |
| Leaving the values out entirely | Fails AC-04/AC-13 and loses the documented legacy behaviour |
