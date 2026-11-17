# 16 — Postman Coverage & API Test Evidence

**Important scope note.** The Postman collection contains a request per controller-mapped endpoint discovered in source; this is a **coverage scaffold**, not an end-to-end execution proof. Backend unit/integration/architecture tests in `src/test/java/...` cover the envelope, CSRF pipeline, security and business logic; the Postman requests themselves were **JSON-validated and URL-audited against source** but were **not end-to-end executed** by this documentation handover. The frontend/QA team should run the scenario matrix against a local instance before treating any contract as golden.

## 16.1 Collection

| Item | Value |
|---|---|
| File | `docs/phase-4j/postman/EDAM.postman_collection.json` |
| Schema | Postman Collection v2.1.0 (`https://schema.getpostman.com/json/collection/v2.1.0/collection.json`) |
| Collection ID | `4a3d7e4c-8d4f-4ed0-9f30-9b92e4a7c111` |
| Controller-mapped `/api/v1` endpoints discovered in source | **206** (§01) |
| Postman requests total | **207** (206 endpoint requests + `GET /actuator/health` public smoke probe; System/Info folder holds `GET /system/info` which is correctly marked authenticated) |
| Folders | **29** (01 Authentication, 02 Bootstrap, 03 Users…29 Negative tests + 99 System/Info) |
| Environment template | `docs/phase-4j/postman/EDAM.postman_environment.template.json` (no real secrets) |

## 16.2 Folder structure (summary)
01 Authentication and Session, 02 Bootstrap and Initial Setup, 03 Users and User Management, 04 Roles and Permissions, 05 Organization Management, 06 Master Data, 07 Expense Configuration, 08 OPEX, 09 CAPEX (placeholder in collection — same `/expense-requests` endpoints, distinguished by requestTypeId), 10 Department Approval Limits, 11 Delegation and Recommending Authorities, 12 Expense Committees, 13 Workflow Configuration, 15 Business Rules, 16 Approval Inbox, 17 Approval Actions, 20 Document Management, 21 SMTP Configuration, 22 Email Templates and Triggers, 23 Notifications, 24 Branding and Themes, 25 Dashboard and Reporting, 26 Audit, 27 Integrations, 28 Idempotency, 29 Negative and Authorization Tests, 99 System/Info.

## 16.3 Request coverage vs endpoint inventory
Every frontend-consumable endpoint listed in §01 has a matching Postman request (folder → request naming: `<METHOD> <path>`). Path parameters use Postman variables (e.g. `{{id}}`, `{{taskId}}`, `{{configId}}`) captured from prior create responses via test scripts.

Per-request tests included automatically:
1. Assert 2xx status code (expected family).
2. Assert `ApiResponse.success === true`.
3. Capture `XSRF-TOKEN` cookie into `{{xsrfToken}}` after `GET /auth/csrf` and `POST /auth/login` (CSRF rotation).
4. Auto-capture UUID `data.id` from create responses into `{{lastCreatedId}}` for follow-up calls.
5. `POST /auth/bootstrap` clears the bootstrap token variable.

## 16.4 Environment variables (template)
- `baseUrl` (default `http://localhost:8080`)
- `bootstrapToken`, `adminUsername`, `adminPassword`, `adminDisplayName`
- `username`, `password`, `newPassword`
- `xsrfToken`, `resetToken`, `lastCreatedId`

The template ships with placeholders only. Password fields are typed `secret` in Postman but are stored in the exported JSON — keep your real environment file out of version control.

## 16.5 Scenarios
`docs/phase-4j/POSTMAN_SCENARIO_MATRIX.md` defines 13 sequential scenarios (S1 bootstrap … S13 branding/report/audit/integrations) with expected outcomes. They map 1:1 to Postman folder runs.

## 16.6 Automated backend tests (not Postman)
- Architecture tests under `src/test/java/com/edam/architecture/` enforce: every controller handler has exactly one of `@EdamPermission`/`@PublicEndpoint`/`@AuthenticatedEndpoint`; permission constants used are registered in `Permissions.java`; layering (controller → service → repository); error envelope shape.
- `WebLayerSecurityTest` (in `src/test/java/com/edam/web/`) confirms: anonymous `GET /api/v1/system/info` → 401 `EDAM-2001` (i.e. `/system/info` is **NOT public**); `GET /actuator/health` → 200 `{status:"UP"}` and does not leak DB/host details in prod; authenticated `GET /api/v1/system/info` → 200 with standard `ApiResponse` envelope; correlation-id sanitisation.
- `AbstractPostgreSqlIntegrationTest` runs all integration tests against a real Postgres container (shared per build). CI runs three jobs: `build-and-test`, `war-package-smoke-test`, `prod-profile-smoke-test`.
- Verification basis: static introspection of source plus existing unit/integration/architecture tests; **Postman requests were not end-to-end executed** in this handover. The frontend team should run `docs/phase-4j/POSTMAN_SCENARIO_MATRIX.md` against a local instance before relying on any contract.

## 16.7 Frontend-consumable endpoint count
Of the 206 `/api/v1` controller-mapped endpoints:
- **8 public** (pre-login/anonymous): `POST /auth/login`, `POST /auth/logout`, `GET /auth/csrf`, `GET /auth/installation`, `POST /auth/bootstrap`, `POST /auth/forgot-password`, `POST /auth/reset-password`, `GET /public/configuration`. Plus non-business public paths `/error`, `/actuator/health[/…]`, `/actuator/info`, `/v3/api-docs[/…]`, `/swagger-ui.html`, `/swagger-ui/**`.
- **2 authenticated-only** (no business permission, but require a session): `GET /auth/me`, `POST /auth/change-password`.
- **1 system-meta endpoint (`GET /system/info`)** protected by deny-by-default Spring Security (authenticated, not permission-guarded); available to the React shell after login.
- **195 permission-guarded** (require the caller to hold a specific `EdamPermission` code; list in §05). A small subset (e.g. audit pagination internals) are admin-only endpoints the React UI doesn't call directly; the screen-to-API mapping in §15 marks those explicitly.

Total: 8 + 2 + 1 + 195 = **206**. The Postman collection has **207** requests because it also contains `GET /actuator/health` (public smoke probe, not under `/api/v1`).

## 16.8 Known Postman/test limitations
- Negative/authorization folder (29) contains placeholders; QA should add 401/403/400/409/422/429 scenarios following `EDAM_QA_TESTING_MANUAL.md`.
- CAPEX folder (09) shares the same request endpoints as OPEX (type discriminator only); no separate CAPEX HTTP routes exist.
- Multipart document upload requests have a basic body placeholder; set the file path in Postman's "File" part before upload.
- SMTP sends and outbound integration calls need a configured external service to succeed; failure-path tests (redacted diagnostics) are covered.
- The Postman collection does not assert 4xx/5xx codes on negative branches out of the box; QA should add those per the QA manual.
