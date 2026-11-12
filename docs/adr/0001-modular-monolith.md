# ADR-0001 — Modular monolith, package by capability

* **Status:** Accepted
* **Date:** 2026-11-12
* **Deciders:** Solution architecture
* **Related:** BRD §2.1, §15.2, NFR-07; [ARCHITECTURE.md](../ARCHITECTURE.md), [PACKAGE_STRUCTURE.md](../PACKAGE_STRUCTURE.md)

## Context

EDAM replaces an ASP.NET/SQL Server application whose behaviour is concentrated in stored procedures and
shared master tables. The rebuild must be maintainable, testable and reusable for several institutions, and
must keep business rules in Java (BRD §2.1, OBJ-09).

The product has 13 functional modules, but they share one transactional consistency requirement: a request
header, its child rows, its resolved approval route and its audit event must commit together (BRD §7.2
step 13, FR-REQ-010). The operational team is small, and the initial client count is low.

## Decision

Build a **modular monolith**:

* A single deployable executable WAR.
* Modules are **packages** (`com.edam.<capability>`), not services or separate Maven modules.
* Modules are separated by a dependency rule enforced in review: a module may use `common` and another
  module's **published** service/read model, never its repository or entity.
* Business rules live in application/domain services inside the module. PostgreSQL supplies integrity,
  constraints, indexes and transactions — no stored procedures.
* Delivery (`web`) and wiring (`config`) are separate top-level packages so no business module depends on
  HTTP.

## Consequences

**Positive**

* One transaction boundary can span a request, its children, its route and its audit event — exactly what the
  BRD demands, with no distributed-saga machinery.
* One build, one deploy, one database to operate and back up.
* Refactoring a module boundary is a compile-time import change, not an API migration.
* Testable: business rules are plain Java, exercised without a container.

**Negative / risks**

* A module boundary can be eroded because nothing enforces it at compile time (`package-private` and review
  are the controls). Mitigation: the dependency rule is written in `PACKAGE_STRUCTURE.md`, checked in review,
  and the reserved packages are documented so they are not created prematurely.
* One JVM is a shared failure domain. Acceptable at the expected volume; revisit if a module needs
  independent scaling.
* A single schema means a single migration train. Acceptable; the modules' tables are additive.

**Revisit when** a module has genuinely independent scaling, availability or regulatory-isolation needs, or
when the operational team grows to own a module independently.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Microservices per module | Distributed transactions for request + children + route + audit; operational overhead with no current benefit |
| Layered `controller`/`service`/`repository` packages | 13 modules share those packages, which become an unmaintainable bucket; module ownership is invisible |
| Separate Maven module per capability | Real enforcement value only when the module boundary needs independent versioning; adds build complexity now |
