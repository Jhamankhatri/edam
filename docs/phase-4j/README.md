# EDAM Phase 4J — Final Backend Audit & Frontend Integration Blueprint

This directory contains the Phase 4J deliverables for EDAM (Expense Delegation and Approval Management).

EDAM is a modular-monolith Spring Boot / PostgreSQL backend providing configurable OPEX/CAPEX expense request and approval workflow capabilities. The frontend will be a separate React + TypeScript application (not built in this phase).

**Baseline commit:** `f3a8bc4a082dd120b7391221345683e31736bd5c` (Phase 4I merge).
**Feature branch:** `arena/9dd6c699-edam`.

## Read this first

| Audience | Start with |
|---|---|
| **Frontend (React + TypeScript) developer** | `FRONTEND_MASTER_INTEGRATION_GUIDE.md` → then `FRONTEND_ARCHITECTURE_BLUEPRINT.md` → then `EDAM_UI_UX_DESIGN_SYSTEM.md`. Use `postman/EDAM.postman_collection.json` to exercise the API. |
| **Backend developer / operator** | `BACKEND_ARCHITECTURE.md` → `production-readiness.md` → `EDAM_DATABASE_DATA_DICTIONARY.md`. |
| **Administrator setting up EDAM** | `EDAM_ADMINISTRATOR_USER_GUIDE.md` → `POSTMAN_TESTING_GUIDE.md` → Scenario 1 (Initial installation). |
| **Requester (employee submitting expenses)** | `EDAM_REQUESTER_USER_GUIDE.md`. |
| **Approver / manager** | `EDAM_APPROVER_USER_GUIDE.md`. |
| **Workflow administrator** | `EDAM_WORKFLOW_ADMINISTRATION_GUIDE.md`. |
| **QA engineer** | `EDAM_QA_TESTING_MANUAL.md` + `POSTMAN_SCENARIO_MATRIX.md` + Postman collection. |
| **Compliance / audit reviewer** | `BRD_COMPLIANCE_MATRIX.md` → `EDAM_PRODUCT_CAPABILITY_REGISTER.md` → `EDAM_DATABASE_DATA_DICTIONARY.md`. |

## Deliverables

### Compliance & audit
- `BRD_COMPLIANCE_MATRIX.md` — line-by-line trace to the original BRD (EDM-BRD-001-v2).
- `PRODUCT_CAPABILITY_REGISTER.md` — product/technical readiness matrix (backend status, API, DB, tests, Postman, frontend plan, open decisions, external deps).
- `production-readiness.md` — production deployment checklist, security posture, infrastructure dependencies.
- `open-decisions.md` — unresolved business/financial/workflow decisions preserved from earlier phases.

### Architecture & data
- `BACKEND_ARCHITECTURE.md` — module layout, layering, transaction patterns, key architectural invariants.
- `EDAM_DATABASE_DATA_DICTIONARY.md` — every PostgreSQL table/column/constraint/index.
- `EDAM_DATABASE_ERD.md` — entity-relationship diagram (Mermaid) and relationship notes.

### Frontend handover
- `FRONTEND_MASTER_INTEGRATION_GUIDE.md` — screen inventory and screen-by-screen specification (routes, fields, validations, buttons → API mappings).
- `FRONTEND_ARCHITECTURE_BLUEPRINT.md` — recommended React + TypeScript project structure, API clients, DTOs, auth/CSRF, guards, forms, tables, file I/O.
- `EDAM_UI_UX_DESIGN_SYSTEM.md` — premium banking SaaS visual language, tokens, responsive layout, motion/animation rules and backend-driven dynamic branding.

### User & QA guides
- `EDAM_ADMINISTRATOR_USER_GUIDE.md`
- `EDAM_REQUESTER_USER_GUIDE.md`
- `EDAM_APPROVER_USER_GUIDE.md`
- `EDAM_WORKFLOW_ADMINISTRATION_GUIDE.md`
- `EDAM_QA_TESTING_MANUAL.md`

### Postman package
- `postman/EDAM.postman_collection.json` — importable collection covering every implemented endpoint.
- `postman/EDAM.postman_environment.template.json` — environment template (no real secrets).
- `POSTMAN_TESTING_GUIDE.md` — authentication flow, CSRF handling, setup steps.
- `POSTMAN_SCENARIO_MATRIX.md` — 13 end-to-end scenarios with prerequisites, steps and expected results.

### Generated reference data (used by the docs; safe to read but not edit)
- `_generated/controllers.json`, `_generated/permissions.json`, `_generated/migrations.json`, `_generated/entities.json` — produced by `tools/phase4j-introspect.py`.

## Scope discipline

- No React project is created in this phase.
- No backend business logic is changed (documentation only; one Phase 4I idempotency concurrency fix is merged before this branch and validated here).
- No new database migrations in Phase 4J.
- Every documented endpoint, field, permission and behaviour is verified against the actual source code (controllers, services, DTOs, migrations, entities).
- Where a BRD requirement is not implemented, the matrix says so explicitly and names the open decision or dependency.

## Quick reference: API base, authentication

- All API endpoints live under `/api/v1`.
- Authentication is **session-cookie based** (HttpOnly `JSESSIONID`); there is no JWT.
- CSRF uses double-submit cookie `XSRF-TOKEN`; clients must read it and return it as the `X-XSRF-TOKEN` header on every mutating request.
- Bootstrap (`POST /api/v1/auth/bootstrap`) is the only account-creation path before the first administrator exists; it requires a one-time `EDAM_BOOTSTRAP_TOKEN` environment variable.
- A public `/actuator/health` endpoint returns `{"status":"UP"}` (no DB/host detail in prod) for deployment smoke. The full application-metadata endpoint `GET /api/v1/system/info` is authenticated (returns 401 anonymously) and returns name/version/status after login.

See `FRONTEND_MASTER_INTEGRATION_GUIDE.md` §Authentication for the full sequence diagram.

## Quick reference: counts (verified against source at `f3a8bc4`)

| Item | Count |
|---|---|
| REST controllers | 26 |
| HTTP endpoints | 206 (195 permission-guarded, 2 authenticated-only, 9 public) |
| Permission constants | 60 |
| Flyway migrations | V1 → V27 |
| JPA entities (mapped tables) | 72 |

