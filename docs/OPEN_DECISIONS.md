# Open Decisions and BRD Compliance Register

The BRD is the source of truth. Where it is silent, inconsistent or unverified, EDAM records an open decision
and **does not implement a guess**. This register is the contract between the business and the codebase: a
feature that depends on an open decision is not built until the decision is signed off.

> A decision is closed only when the named owner records it here with a date and the implementing phase. Code
> changes alone do not close a decision.

## 1. Evidence labels used below

| Label | Meaning |
|---|---|
| **SOURCE-CONFIRMED** | Explicitly documented in the BRD |
| **SOURCE-INFERRED** | Reasonable reading of the BRD; requires confirmation |
| **OPEN DECISION** | The BRD does not define the behaviour |
| **TARGET PROPOSAL** | A proposed product decision, not a legacy behaviour |

## 2. Decisions that block later phases

| ID | Decision needed | Owner | Blocks | Foundation position |
|---|---|---|---|---|
| **OD-01** | Relationship between narrative EC Levels 1–3 and configurable approval levels; amount-band selection | Business owner | Committee, workflow | Not modelled |
| **OD-02** | Exact route order/combination for standalone CEO, CFO and HOD relative to EC levels — a signed routing decision table | Business owner + Finance | Workflow engine | Not modelled; `Features` holds the unverified flags |
| **OD-03** | Standalone CEO/CFO limits and HOD mechanics: trigger, threshold, approver, action set, completion | Business owner | Workflow | Not modelled |
| **OD-04** | CFO action permissions (approve/reject/revert/forward) and remarks rules | Business + Finance | Approval actions | Not modelled |
| **OD-05** | Whether the exceeding-limit configuration (FM-13) affects routing, warns, or is retired | Business owner | Workflow, config | Modelled as configuration `exceeding-limit-enforced-in-routing: false` |
| **OD-06** | Whether a single global Department Head threshold suffices, or category/department thresholds are required | Finance | Delegation matrix | Single nullable threshold + documented fallback |
| **OD-07** | PKR conversion formula and rounding: rate direction, multiply/divide, scale, rounding mode | Finance | Currency, OPEX/CAPEX amounts | Not implemented (no arithmetic anywhere yet) |
| **OD-08** | CAPEX item-wise amount and variance formulas | Finance | CAPEX | Not implemented |
| **OD-09** | Full request status state machine: statuses, transitions, terminal states, cancellation, deletion, reopen, concurrency | Business owner | Workflow, everything | Not modelled |
| **OD-10** | Selective-revert eligibility: who, which steps, restrictions | Business owner | Workflow | Not modelled |
| **OD-11** | **Authentication mechanism**: local, SSO/AD/OIDC/SAML, or client adapter | Information security + IT | All authenticated use | Seam + `local` fixtures only ([ADR-0004](adr/0004-authentication-seam.md)) |
| **OD-12** | **Role → permission → data-scope matrix** | Business owner + Information security | All authorization beyond authentication | Permission constants defined; no matrix mapping yet |
| **OD-13** | Whether CAPEX finance fields are entered by the requester or a separate finance approver | Finance | CAPEX | Not modelled |
| **OD-14** | Attachment storage provider, size/type limits, encryption, retention, virus scanning | Information security + IT | OPEX/CAPEX documents | Not implemented |
| **OD-15** | Email event matrix, templates, sender identity, retry/failure policy | Business + IT | Notifications | Not implemented |
| **OD-16** | Master-data source: integrate via adapter/API, synchronise, or maintain locally (users, departments, branches) | IT + Business | Organization module | Not modelled |
| **OD-17** | Reconciliation of EC member-pool vs committee tables and alternate EC log tables | Business + DBA | Committee | Not modelled |
| **OD-18** | Complete SQL schema and code impact analysis for legacy objects before migration | IT | Migration | Not started |
| **OD-19** | NFR/SLA sign-off: performance, availability, DR, security numeric targets | Business + IT + InfoSec | Architecture hardening | Foundation is NFR-neutral; no numeric target assumed |
| **OD-20** | **Tenant isolation model**: shared schema vs schema-per-tenant vs database-per-tenant | InfoSec + IT + hosting | Every tenant-owned table | No tenant-owned table exists; context seam only ([ADR-0002](adr/0002-tenant-isolation-deferred.md)) |
| **OD-21** | CAPEX allowed-department list and Department Head fallback must be configuration, not constants | Business owner | CAPEX | Configuration flags with conservative defaults ([ADR-0006](adr/0006-configuration-not-constants.md)) |
| **OD-22** | Print/PDF eligibility criteria, export formats, configuration-log formats and retention | Business owner | Reporting | Not implemented |

## 3. Confirmed BRD requirements implemented in this phase

| Requirement | Implementation | Evidence |
|---|---|---|
| Business rules in Java, not stored procedures (OBJ-09, §2.1) | No procedural SQL; all logic in services | SOURCE-CONFIRMED |
| No automatic schema generation in production (§8) | `ddl-auto: validate` in every profile; Flyway owns the schema | SOURCE-CONFIRMED |
| Version-controlled migrations (§8) | `db/migration/V1`, `V2`; forward-only policy | SOURCE-CONFIRMED |
| Auditing of important changes (§8, §12.4, AC-10) | JPA auditing on every entity; append-only event store later |
| Unique constraints, FKs, delete guards (§8, §11.3) | `CHECK`/`UNIQUE`/partial indexes in `V1`; delete guard is a documented rule |
| Authenticated access to all pages and endpoints (§13.1) | Deny-by-default chain; public list reviewed |
| Server-side authorization for every operation (§13.1) | `authorizeHttpRequests` + `@EnableMethodSecurity` |
| No sensitive data in logs (§13.1, §4) | Redaction policy + access log without query strings/bodies |
| Institution branding/config without a code change (§15.3, §15.4, AC-13) | `InstitutionProperties` + `institution` table + theme tokens |
| No hard-coded institution name/logo/colours/currency (§7) | Values come from config/DB; `base_currency` validated ISO-4217 |
| A user is not assumed to belong to one institution (§6, §13.1) | `EdamPrincipal.institutionRoles` is a `Map`/`Set`; fixture user belongs to two |
| Consistent error handling without internal detail (§13.1) | ADR-0005 |
| Correlation IDs / traceability (§4, §12.4, NFR-12) | ADR-0005 + LOGGING_GUIDELINES |
| Health checks (§14 NFR-12) | `/actuator/health` with no detail |
| Separated OPEX/CAPEX modules for later phases (§15.2) | Reserved packages documented, not created |

## 4. Deliberately not implemented in this phase

| Item | Why | Phase |
|---|---|---|
| All OPEX/CAPEX forms, registers, approval pages | BRD: Phase 3/4; depends on OD-02/OD-09 | 3–4 |
| Delegation matrix, EC pool, EC levels, currency, authorities | BRD: Phase 2; depends on OD-01/OD-06/OD-17 | 2 |
| Workflow engine, route resolution, quorum | Depends on OD-02/OD-09/OD-10 | 3 |
| Email/notification | Depends on OD-15 | 3 |
| Reporting, dashboards, exports, print/PDF | Depends on OD-22 | 5 |
| Attachment storage | Depends on OD-14 | 3 |
| Legacy data migration | Depends on OD-18 | 6 |
| Excel export | Depends on OD-22 | 5 |
| Full audit event store | Needs an audit schema decision (retention, tamper protection) | 2 |

## 5. Technical decisions taken by this project (not BRD decisions)

These are engineering decisions, recorded as ADRs; the business does not need to approve them but should be
aware of their consequences.

| Decision | ADR |
|---|---|
| Modular monolith, package by capability | [0001](adr/0001-modular-monolith.md) |
| Executable WAR (JSP constraint) | [0003](adr/0003-executable-war-for-jsp.md) |
| Authentication seam, fail-closed | [0004](adr/0004-authentication-seam.md) |
| Error envelope + MVC error page | [0005](adr/0005-error-contract.md) |
| Legacy constants → configuration | [0006](adr/0006-configuration-not-constants.md) |
| Error dispatch is not authorized | [0007](adr/0007-error-dispatch-not-authorized.md) |

## 6. How to close a decision

1. The owner records the decision, the date and the rationale in this file (or a linked minute).
2. Replace the row's "Foundation position" with the implemented behaviour and the phase.
3. If the decision changes an architectural assumption, supersede the relevant ADR (never edit an accepted
   ADR in place).
4. Add the acceptance test that proves the decision is honoured (e.g. AC-12's isolation test for OD-20).
