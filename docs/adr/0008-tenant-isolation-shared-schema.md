# ADR-0008 — Tenant isolation: shared schema with a mandatory discriminator

**Status:** Provisional (does not resolve BRD OD-23)
**Date:** 12 November 2026
**Supersedes:** the deferral recorded in ADR-0002

## Context

BRD OD-23 requires a choice between shared schema + `tenant_id`, schema-per-tenant and
database-per-tenant, and states it must be approved by bank security and operations. It is still
open. Meanwhile the product cannot be built without tenant-owned tables: the configuration plane,
requests, approvals, documents, notifications and audit all belong to an institution.

ADR-0002 deferred the decision entirely, which was correct for a foundation with no business
tables, and is no longer tenable.

## Decision

Implement **shared schema with a mandatory `institution_id` discriminator**, treated as a
provisional technical decision that is explicitly revisable:

1. Every tenant-owned entity extends `InstitutionOwnedEntity`, which supplies
   `institution_id BIGINT NOT NULL REFERENCES institution(id)` and marks it `updatable = false`.
   An entity cannot be moved between tenants.
2. The service layer stamps the institution from `InstitutionContext`, which resolves it from the
   authenticated identity and an explicit selection. A tenant id supplied in a request body is
   never trusted as authorization.
3. Every repository finder takes `institutionId` as an argument. There is no tenant-agnostic
   finder to forget to constrain.
4. Composite indexes lead with `institution_id`, so tenant-scoped access is the natural query plan.
5. Cross-tenant probes return the same response as a non-existent record, so access denial does
   not disclose existence (BRD §23.2).

## Why not the alternatives, now

| Option | Why not now |
|---|---|
| Schema-per-tenant | Requires DDL at onboarding and a connection/routing layer; Flyway must run per schema. Defensible later, but it makes the current phase's migrations and tests materially harder for no present benefit while the model is unapproved. |
| Database-per-tenant | Operationally heaviest; nothing in the BRD requires physical separation, and OD-38 (deployment model) is unresolved. |

## Why shared schema is the safe provisional choice

It is the only option whose **business data model is unchanged** by a later migration: moving to
schema-per-tenant or DB-per-tenant is a data-migration exercise, whereas starting with physical
separation and moving back is not. The discriminator is also the artefact the other options keep.

## Consequences

* A missed `where institution_id = ?` is a data-breach class of defect. Mitigations: no
  tenant-agnostic repository methods, negative cross-tenant tests for every module, and review
  checklist entries. This is a real residual risk and is recorded as such.
* Reports, exports, jobs, documents and audit must all apply the discriminator; the BRD (AC-02)
  requires negative tests for each of those surfaces.
* If OD-23 lands on physical separation, the migration is additive: rows carry their tenant, so
  they can be partitioned out.

## Not decided here

Retention, data residency (OD-41), deployment topology (OD-38), cross-border restrictions. Those
remain with the bank.
