# 00 — README & Backend Baseline

This directory (`docs/Frontend Integration/`) is the authoritative frontend-to-backend integration handover for EDAM (Expense Delegation and Approval Management). It documents every endpoint a React + TypeScript SPA needs to consume, the exact request/response/error contracts, authentication/session/CSRF/CORS behaviour, permission matrix, module integration flows, workflow and approval semantics, branding/theme, document handling, notifications, integrations, reporting/audit, pagination conventions, screen-to-API mapping, Postman coverage, open decisions/blockers and source traceability.

**The frontend team must not build against any other document in preference to this directory.** Where a discrepancy exists between these files and earlier Phase 4J documents (under `docs/phase-4j/`), the documents in this directory are authoritative because they were generated against the actual controller/DTO/security source at the baseline commit below. The earlier `docs/phase-4j/` deliverables remain as the broader product, architecture and user-guides package.

## 0.1 Backend baseline (commit the frontend must target)

| Item | Value |
|---|---|
| Repository | https://github.com/Jhamankhatri/edam |
| Base branch | `main` |
| Base commit (pre-PR) | `f3a8bc4a082dd120b7391221345683e31736bd5c` (Phase 4I merge) |
| Phase 4J PR | **#22** |
| PR state | **OPEN (not merged)** |
| PR source branch | `arena/9dd6c699-edam` |
| PR URL | https://github.com/Jhamankhatri/edam/pull/22 |
| Phase 4J baseline SHA (Phase 4J docs already on PR branch) | `f4e2bd3ab157724df1bb04ae02ac460c031d23e3` |
| Final feature-branch SHA after adding the Frontend Integration directory | (see the commit that adds this directory; recorded in §0.4) |

> The frontend team should check out the PR branch `arena/9dd6c699-edam` at the final SHA listed above (or merge main after PR #22 merges) and run the backend from that commit. Every reference in this directory — endpoint path, DTO field, permission code, error code, state name — was verified against that code.

## 0.2 Backend technology stack

| Layer | Technology | Version / notes |
|---|---|---|
| Language | Java | **21** (`<java.version>21</java.version>`) |
| Framework | Spring Boot | **4.1.2** (parent POM) |
| Persistence | Spring Data JPA + Hibernate | `ddl-auto=validate` on every profile |
| Migrations | Flyway | forward-only `V1…V27` (+ `V4_1__uuid_v7_function.sql`) |
| Database | PostgreSQL | 15+ (CI runs against 18-alpine) |
| Build | Maven | packaging = WAR (executable), per ADR-0003 |
| JSON | Jackson | via Spring Boot auto-config; `@JsonInclude(NON_NULL)` on `ApiResponse` |
| AuthN/AuthZ | Spring Security 6.x | session cookies + CSRF (double-submit), deny-by-default, `@EdamPermission` method security |
| CSRF | `CookieCsrfTokenRepository` | cookie name `XSRF-TOKEN`; header name `X-XSRF-TOKEN` |
| Validation | Jakarta Bean Validation | `@NotBlank`, `@Size`, `@Email`, `@Min/@Max`, etc. |
| API prefix | `/api/v1` (constant `ApiPaths.V1`) | every REST endpoint lives under this prefix |

## 0.3 Where to find companion artefacts

| Artefact | Path |
|---|---|
| Broader Phase 4J documentation package (architecture, BRD matrix, user guides, data dictionary, ERD, UX design system) | `docs/phase-4j/` |
| Postman collection | `docs/phase-4j/postman/EDAM.postman_collection.json` |
| Postman environment template (no real secrets) | `docs/phase-4j/postman/EDAM.postman_environment.template.json` |
| Postman testing guide | `docs/phase-4j/POSTMAN_TESTING_GUIDE.md` |
| Postman scenario matrix (13 flows) | `docs/phase-4j/POSTMAN_SCENARIO_MATRIX.md` |
| Database data dictionary (72 tables) | `docs/phase-4j/EDAM_DATABASE_DATA_DICTIONARY.md` |
| Database ERD (Mermaid) | `docs/phase-4j/EDAM_DATABASE_ERD.md` |
| Introspection script that produced the reference JSON | `tools/phase4j-introspect.py` |
| Generated reference data (controllers, permissions, migrations, entities, DTOs) | `docs/phase-4j/_generated/*.json` |
| Open decisions register | `docs/phase-4j/open-decisions.md` |
| Production-readiness checklist | `docs/phase-4j/production-readiness.md` |
| Frontend architecture blueprint (React + TS) | `docs/phase-4j/FRONTEND_ARCHITECTURE_BLUEPRINT.md` |
| UI/UX design system | `docs/phase-4j/EDAM_UI_UX_DESIGN_SYSTEM.md` |

## 0.4 Directory contents

```
docs/Frontend Integration/
├── 00_README_AND_BACKEND_BASELINE.md           (this file)
├── 01_COMPLETE_API_ENDPOINT_INVENTORY.md
├── 02_COMPLETE_API_REQUEST_RESPONSE_CONTRACTS.md
├── 03_SUCCESS_ERROR_AND_VALIDATION_CONTRACTS.md
├── 04_AUTHENTICATION_SESSION_CSRF_AND_CORS.md
├── 05_PERMISSION_AND_AUTHORIZATION_MATRIX.md
├── 06_MODULE_WISE_API_INTEGRATION_GUIDE.md
├── 07_REQUEST_LIFECYCLE_AND_STATE_TRANSITIONS.md
├── 08_APPROVAL_AND_WORKFLOW_API_CONTRACTS.md
├── 09_BRANDING_THEME_AND_CONFIGURATION_APIS.md
├── 10_DOCUMENT_UPLOAD_DOWNLOAD_AND_SCANNING.md
├── 11_SMTP_EMAIL_TEMPLATES_AND_NOTIFICATIONS.md
├── 12_INTEGRATIONS_AND_SECRET_MANAGEMENT.md
├── 13_REPORTS_EXPORTS_AND_AUDIT_APIS.md
├── 14_PAGINATION_FILTERING_SORTING_AND_COMMON_TYPES.md
├── 15_FRONTEND_SCREEN_TO_API_MAPPING.md
├── 16_POSTMAN_COVERAGE_AND_API_TEST_EVIDENCE.md
├── 17_OPEN_DECISIONS_AND_FRONTEND_BLOCKERS.md
├── 18_SOURCE_CODE_TRACEABILITY.md
└── 19_FRONTEND_LOCAL_SETUP_AND_INTEGRATION_RUNBOOK.md
```

## 0.5 Deployment prerequisites for the frontend developer's local backend

1. **PostgreSQL 15+** instance. Local Docker works:
   `docker run -d --name edam-pg -e POSTGRES_PASSWORD=edam -e POSTGRES_DB=edam -p 5432:5432 postgres:16-alpine`
2. **Environment variables** (minimum for local dev):
   - `EDAM_DB_URL=jdbc:postgresql://localhost:5432/edam`
   - `EDAM_DB_USERNAME=edam`, `EDAM_DB_PASSWORD=edam`
   - `EDAM_BOOTSTRAP_TOKEN=<one-time-token>` (set for the first bootstrap, unset afterwards)
   - `EDAM_MAIL_MASTER_KEY=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f` (hex-encoded 32-byte AES key; dev value shown — rotate for real environments)
   - `EDAM_INTEGRATION_MASTER_KEY=<hex-32-byte>` (required before saving integrations; use a fixed dev value locally)
   - `edam.cors.allowed-origins=http://localhost:5173` (or whatever Vite dev-server origin you use)
   - Optional: `CLOUDINARY_URL=...` for document uploads; without it uploads fail.
   - Optional: SMTP (`spring.mail.host`, etc.) for outbound mail; without it mail lands in `comm_notification_outbox` as FAILED with a redacted diagnostic.
3. **Run:** `./mvnw spring-boot:run` (or `java -jar target/edam.war` after `./mvnw package`).
4. **Smoke:** `curl http://localhost:8080/api/v1/info` → `{"success":true,"data":{"application":"edam","version":…,"status":"UP"},"correlationId":…,"path":"/api/v1/info"}`.
5. **Frontend dev server:** configure Vite `server.proxy` to forward `/api` → `http://localhost:8080` so the browser never makes cross-origin calls to the API in dev (CORS is still exact-origin enforced).

## 0.6 Integration contract invariants

1. **Session cookies only — no JWT.** Do not look for `Authorization: Bearer` headers. See §04.
2. **CSRF double-submit cookie on every mutation.** See §04.
3. **All responses are JSON.** Even errors use the `ApiResponse<T>` envelope (§03).
4. **The backend is authoritative.** The frontend uses permissions for UX gating only; every endpoint re-checks authorisation server-side.
5. **No secret is ever returned.** SMTP/integration passwords return blank/masked; the public configuration endpoint returns a whitelist-only subset of theme/name data.
6. **No arbitrary CSS/JS in themes.** Theme tokens are whitelisted on both server and client.
7. **Pagination is zero-based** and capped at 200 rows per page (§14).
8. **Ids are UUIDs.** Treat them as opaque strings in TypeScript (`type Uuid = string`). Do not parse them client-side; pass them as-is.
9. **All timestamps are ISO-8601 `Instant`** (UTC, e.g. `2026-11-17T14:05:47.123Z`).
10. **Independent deployments.** Every bank gets its own EDAM deployment and PostgreSQL database. There is no in-product bank switcher, no multi-bank tenant picker and no cross-bank access. Do not implement one.
