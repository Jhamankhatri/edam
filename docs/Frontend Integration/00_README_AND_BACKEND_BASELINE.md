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
| AuthN/AuthZ | Spring Security 6.x | session cookies + CSRF (double-submit), deny-by-default (see `SecurityConfig.publicMatchers`), `@EdamPermission` method security via `RequirePermissionAspect` |
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
4. **Smoke:** `curl http://localhost:8080/actuator/health` → `{"status":"UP"}` (public; no DB/host detail in prod). `curl -i http://localhost:8080/api/v1/auth/csrf` returns 200 and sets the `XSRF-TOKEN` cookie. After login, `curl -b JSESSIONID=... http://localhost:8080/api/v1/system/info` returns `{"success":true,"data":{"application":"edam","version":…,"status":"UP"},...}` (authenticated).
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


---

## §25 — Coverage audit (final verification pass)

Counts at audit time (baseline commit `f3a8bc4` + Phase 4J handover commit `308d51f`):

| Artefact | Count | Verification method |
|---|---|---|
| Frontend Integration documents | 21 (00–20) | directory listing |
| Spring controllers discovered | 26 | `tools/phase4j-introspect.py` parses `src/main/java/**/*Controller.java` |
| Controller-mapped HTTP endpoints (under `/api/v1`) | 206 | sum of `@*Mapping` annotations |
| Permission constants (`Permissions.java`) | 60 | static parse of `public static final String` fields |
| Public (anonymous) `/api/v1` endpoints | 8 business (`/auth/login`, `/auth/logout`, `/auth/csrf`, `/auth/installation`, `/auth/bootstrap`, `/auth/forgot-password`, `/auth/reset-password`, `/public/configuration`) | cross-referenced against `SecurityConfig.publicMatchers` |
| Non-business public paths (outside `/api/v1`) | `/error`, `/actuator/health[/…]`, `/actuator/info`, `/v3/api-docs[/…]`, `/swagger-ui.html`, `/swagger-ui/**` | `SecurityConfig.publicMatchers` |
| Authenticated-only (no business permission) endpoints | 2 (`GET /auth/me`, `POST /auth/change-password`) | deny-by-default filter chain + `@AuthenticatedEndpoint` |
| Authenticated system-meta endpoint (no business permission) | 1 (`GET /system/info`) | deny-by-default filter chain (no `@EdamPermission`, not in publicMatchers); verified by `WebLayerSecurityTest.anonymousApiGetsJson401` |
| Permission-guarded endpoints | 195 | every handler has `@EdamPermission(...)` (architecture test enforces zero-missing) |
| Endpoints missing any authorisation annotation | 0 | enforced by `EndpointAuthorizationArchitectureTest` |
| JPA entities | 72 | introspection of `@Entity` classes |
| Flyway migrations | 28 (`V1`…`V27` + `V4_1`) | `src/main/resources/db/migration/` listing |
| DTO records | 189 | record-component parser (nested-generic and balanced-paren annotation aware) |
| Postman requests | 207 (206 endpoints + `GET /actuator/health` smoke) | JSON validated; URL paths cross-checked against controllers.json |

Audit corrections applied during this pass (document-only):
- Corrected `GET /api/v1/info` → **`GET /api/v1/system/info`** (the original draft used a non-existent path; the real controller is `SystemController` mapped to `/api/v1/system` with handler `info()`).
- Corrected the public-vs-authenticated classification of `/api/v1/system/info`: it is **NOT** public (confirmed by `WebLayerSecurityTest` which expects 401 anonymously). The public deployment smoke endpoint is `GET /actuator/health` (returns `{status:"UP"}`, no component detail in prod). The introspection script now classifies public endpoints against the authoritative `SecurityConfig.publicMatchers` list, not by scanning `@PublicEndpoint` annotations (those annotations document intent and are enforced by an architecture test, not by the filter chain itself in Phase 4J).
- Corrected configuration controller base-path parsing for `ConfigurationController` (maps directly to `ApiPaths.V1`) and for controllers in sub-packages (communication/*) so all 206 endpoints resolve to the correct full URL.
- Moved OD-20 (multi-tenant isolation) from the open-decisions table to a new **Settled architectural decisions** subsection in §17; added an explicit "do not build a tenant picker or bank switcher" directive.
- Added explicit wording in §16 that the Postman collection is a **coverage scaffold**, not an end-to-end execution proof; Postman requests were JSON-validated and URL-audited against source, but were not live-executed in this handover.
- Split the global "206 endpoints" count into public / authenticated-only / system-meta / permission-guarded buckets; noted that a small subset of permission-guarded endpoints are admin-only internals the React UI doesn't call directly (marked explicitly in §15 screen mapping).
- Fixed the Postman collection to use `/api/v1/system/info` (authenticated) and added `GET /actuator/health` to the System/Info folder; fixed the bootstrap folder ordering.

Out-of-scope items consistently marked unavailable across all docs:
- XLSX/PDF/print export (only CSV is implemented — §13, §17, §15).
- SSO / AD / OIDC / SAML (local form login only — §04, §17).
- WebSockets / real-time push (poll notifications every 30 s — §11, §17).
- Full audit log export/download beyond paginated list (§13).
- Multi-bank / tenant switcher (§17, §00).
- JWT / bearer-token auth (§04).

## §27 — Frontend integration readiness report

**Per-module status (vs source code at baseline):**

| Module | Docs section | Endpoints | Contracts verified against source | Postman request present | Behaviour verified in backend tests | Frontend ready? |
|---|---|---|---|---|---|---|
| Auth / session / CSRF | §04 | 8 | ✅ | ✅ | ✅ (`WebLayerSecurityTest`, CSRF pipeline) | ✅ READY |
| System meta (`/system/info`) | §04 | 1 | ✅ | ✅ | ✅ (anonymous→401, authenticated→200) | ✅ READY |
| Public branding/configuration | §09 | 1 (`/public/configuration`) | ✅ | ✅ | ✅ (whitelist-only DTO) | ✅ READY |
| Branding / themes / settings / feature flags | §09 | 16 | ✅ | ✅ | partial (DTO structure parsed) | ✅ READY |
| Users / roles / permissions | §05, §06 | 21 | ✅ | ✅ | partial | ✅ READY |
| Organization (branches/departments) | §06 | 17 | ✅ | ✅ | partial | ✅ READY |
| Master data | §06 | 16 | ✅ | ✅ | partial | ✅ READY |
| Expense configuration | §06 | 12 | ✅ | ✅ | partial | ✅ READY |
| Expense requests (OPEX/CAPEX) | §06, §07, §08 | 6 | ✅ | ✅ | ✅ (state machine tests) | ✅ READY |
| Approval limits / delegation / committees | §06, §08 | 33 | ✅ | ✅ | partial | ✅ READY (with configuration work) |
| Workflow configuration/publishing | §08 | 13 | ✅ | ✅ | partial | ⚠️ READY (publish a workflow before end-users can submit) |
| Approval inbox / actions | §08 | 7 | ✅ | ✅ | ✅ (action tests exist) | ✅ READY |
| Documents (upload/download/scan) | §10 | 6 | ✅ | ✅ | partial (fail-closed download verified) | ⚠️ READY WITH LIMITATIONS (requires production scanner + object storage) |
| SMTP / email templates / events | §11, §09 | 17 | ✅ | ✅ | partial (SET/KEEP/CLEAR semantics documented) | ⚠️ READY WITH LIMITATIONS (requires SMTP provisioning) |
| Notifications inbox | §11 | 4 | ✅ | ✅ | — | ✅ READY (polling; no WebSockets) |
| Reports / dashboard | §13 | 5 | ✅ | ✅ | partial (CSV export verified) | ⚠️ READY (CSV only; XLSX/PDF/print not implemented) |
| Audit log | §13 | 3 | ✅ | ✅ | — | ✅ READY (no bulk export) |
| Integrations / secrets | §12 | 13 | ✅ | ✅ | partial (SET/KEEP/CLEAR documented) | ⚠️ READY WITH LIMITATIONS (requires AES master keys) |

### Remaining items the frontend team should track
1. **Run the Postman scenarios end-to-end** against a local instance before treating any contract as golden — the collection is a scaffold and was not live-executed in this handover.
2. **Production provisioning dependencies** (SMTP, Cloudinary-compatible object storage, malware scanner, HTTPS+CORS exact-origin, AES master keys, workflow publication, role seeding) must be in place before go-live (§17.3). Local dev can proceed with placeholder/in-memory stubs.
3. **Open product decisions** (OD-02/03 routing, OD-07/08 amount math, OD-14 scanner provider, OD-15 event matrix, OD-22 export formats) are all **UI-blockable** — the UI can ship with conservative defaults per §17 and be refined once business signs off. None requires frontend changes to backend contracts.
4. **OpenAPI spec from the runtime** (`GET /v3/api-docs`) is the source of truth for code generation after any backend change; the skeleton YAML in §20 is a bootstrap aid, not a substitute.

### Recommendation

> **READY WITH LIMITATIONS.** The React + TypeScript frontend can begin implementation immediately against the contracts in this directory. The HTTP surface, envelope, authN/authZ, pagination, error codes and state machines are all settled in source and documented with traceable controller/file references (§18). The limitations are operational, not contractual: production environment dependencies (SMTP, object storage, malware scanner, workflow publication, role seeding) must be provisioned before go-live, and the Postman collection must be executed end-to-end by the frontend/QA team during their first integration pass.
