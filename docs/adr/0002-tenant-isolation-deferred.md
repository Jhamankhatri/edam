# ADR-0002 — Tenant isolation: model chosen later, context seam now

* **Status:** **Proposed / blocking** — the isolation model itself is *not* decided
* **Date:** 2026-11-12
* **Deciders:** Product owner, Solution architecture, Information security, DBA/hosting (**sign-off required**)
* **Related:** BRD §15.3, AC-12, OD-20; [SECURITY_GUIDELINES.md](../SECURITY_GUIDELINES.md)

## Context

EDAM must be sold to multiple financial institutions with minimal code changes, and "a tenant must only
access its own configuration, requests, files, reports and audit data" (BRD §15.3, AC-12). The BRD names
three candidate models and explicitly forbids silently choosing one:

* **(A) Shared database, `tenant_id`/`institution_id` on tenant-owned rows.**
* **(B) One PostgreSQL schema per institution.**
* **(C) One database per institution.**

The choice depends on factors this team does not control: hosting model (single-tenant VPC vs shared SaaS),
regulatory expectations for a bank, backup/restore granularity per client, DBA tooling, cost, and how many
clients are expected in the first year. Getting it wrong is a data breach and a re-platforming exercise.

## Decision

1. **No tenant-owned business table is created in this phase.** The foundation schema contains exactly one
   table, `institution`, which is *not* tenant-owned data — it is the tenant registry.
2. **A context seam exists now**: `InstitutionContext` resolves the acting institution per request, refuses
   to guess when a user belongs to several, and exposes membership as a `Set`.
3. **No tenant discriminator, Hibernate filter, schema routing or data-source routing is implemented.**
   A half-built discriminator invites code that believes isolation is enforced when it is not.
4. **Branding/configuration already honours the tenant**: the institution record overrides deployment
   configuration, so per-client identity never requires a code change (AC-13).
5. The choice is recorded as a blocking open decision (OD-20) in [OPEN_DECISIONS.md](../OPEN_DECISIONS.md).

## Consequences

**Now**

* The foundation cannot be used for two institutions' *business data* until the decision is made. This is
  intended: the first business tables arrive in Phase 2 and require the answer.
* No security theatre: nothing claims to isolate data it does not isolate.
* Switching later is cheap because zero tenant-owned queries exist to rewrite.

**When the decision lands**

The approved option dictates concrete work:

| Option | Additional work |
|---|---|
| A — shared schema | `institution_id` on every tenant-owned table; central predicate enforcement (repository base or Hibernate `@TenantId`/filter); composite unique keys scoped by tenant; tenant-aware indexes; cross-tenant reporting becomes an explicit privilege |
| B — schema per tenant | Flyway `schemas` per tenant, routing data source/schema resolver, tenant-aware connection handling, per-schema migrations and upgrade orchestration |
| C — database per tenant | Per-tenant data-source routing, connection-pool-per-tenant management, cross-tenant operational tooling, backup/restore per client |

All three require: a test that Tenant A cannot read or mutate Tenant B data (AC-12/AC-14), an audited
cross-tenant operation path if one is ever needed, and a decision about where institution branding/assets
are stored for each model.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Decision delayed past Phase 2 | Tenant-owned tables get built without isolation | Treat OD-20 as a Phase 0/1 exit criterion; this ADR is linked from every relevant document |
| "Temporary" shared-schema without enforcement | Silent cross-tenant data exposure | The dependency rule forbids tenant queries until a decision; isolation tests required with the first tenant table |
| Client demands dedicated database after go-live | Migration project | Data model is normalised and institution-keyed; export per institution is feasible |
