# Frontend Handover — README

**Package:** EDAM React + TypeScript frontend blueprint (documentation only)
**Backend baseline:** `main` at `38cedbc` (merge of PR #24 — handover remediation, D-09 seed fix, idempotency transaction fix)
**Document set:** 19 files under `docs/Frontend Handover/`
**Total `/api/v1` endpoints documented:** 206 (8 public, 3 authenticated-only, 195 permission-guarded)

This directory is the **authoritative handover** from the EDAM backend team to the frontend team.
It supersedes the earlier `docs/Frontend Integration/` draft (which contained multiple verified
inaccuracies). Two PRs brought it to its current state: PR #23 delivered the initial 19-file
package, PR #24 corrected D-01…D-27 against source, added Flyway V28 to fix the seeded-role
permission gap (D-09), and fixed a PostgreSQL/transaction-bug in idempotency collision handling.
This package is documentation only: no source code, no `package.json`, no Vite scaffold, and no
backend logic changes beyond the two forward-only Flyway migrations already present in
`src/main/resources/db/migration/`.

## How to read this package

Read in this order, then use the rest as reference.

| # | File | Purpose |
|---|------|---------|
| 01 | H01_FRONTEND_HANDOVER_BASELINE.md | Baseline commit, conventions, environment variables, glossary |
| 02 | H02_API_CONTRACTS_AND_RESPONSE_ENVELOPE.md | `ApiResponse`, `PageResponse`, error envelope, HTTP→code map, OpenAPI codegen recipe, BigDecimal-as-number behaviour, raw CSV/XLSX stream, idempotency collision contract |
| 03 | H03_AUTH_SECURITY_CSRF.md | Session cookie, CSRF (`XSRF-TOKEN` / `X-XSRF-TOKEN`), bootstrap header `X-Installation-Token`, bootstrap→explicit-login, CSRF cookie lifecycle |
| 04 | H04_PERMISSIONS_NAVIGATION_AND_ACCESS_CONTROL.md | All 60 permissions, data-scope model, role→nav mapping, UI gate rules, D-09 resolution (V28) |
| 05 | H05_END_TO_END_BUSINESS_WORKFLOWS.md | 17 sequenced workflows (W1–W17): user journey → route → API sequence → perm → state → errors |
| 06 | H06_VALIDATION_FORM_AND_ERROR_HANDLING.md | Client validation matrix, server validation parsing, optimistic-lock/idempotency replay, UX patterns |
| 07 | H07_FRONTEND_ARCHITECTURE.md | React 18 + TS + Vite + TanStack + shadcn/ui + Tailwind stack, folder layout, data/query/error/security layers |
| 08 | H08_DESIGN_SYSTEM_AND_UI_UX.md | Design tokens (driven by `themeTokens`), typography, components, responsive, motion, accessibility |
| 09 | H09_SCREEN_AND_ROUTE_INVENTORY.md | Every Phase 1 route/screen, layout slot, component contract, empty/loading/error/denied states |
| 10 | H10_API_TO_SCREEN_MAPPING.md | Cross-reference: Phase 1 screen → APIs it calls → required perms |
| 11 | H11_FILE_AND_DOCUMENT_INTEGRATION.md | Multipart upload, metadata, signed download, malware-scan polling, delete; NO inline preview in Phase 1; NO standalone document register |
| 12 | H12_REPORTING_AND_EXPORT_INTEGRATION.md | Dashboard, register (custom `RegisterResponse` paging, max 500), CSV + XLSX streaming (PDF/print out of scope), filter model |
| 13 | H13_TESTING_STRATEGY.md | Unit, integration (MSW), E2E (Playwright), contract/OpenAPI drift detection |
| 14 | H14_PHASED_ROADMAP.md | Phase 1 scope (core expense management), Phase 2–4 deferred admin, done gates |
| 15 | H15_OPEN_DECISIONS_AND_RISKS.md | Outstanding decisions (OD-1…), known risks (R-…); no silent assumptions |
| 16 | H16_TRACEABILITY_AND_VERIFICATION.md | Coverage matrix, D-01…D-27 status, source-file evidence, runtime vs static-inspection statement |
| 17 | H17_DEPLOYMENT_AND_LOCAL_SETUP.md | Maven backend runbook, Vite proxy, prod build, CSP/CORS checklist |

Supporting appendix:

- `APPENDIX_A_ENDPOINT_INVENTORY.md` — the full 206-endpoint table used by every H-file.
- `docs/phase-4j/_generated/{{controllers,dtos,entities,permissions,migrations}}.json` — machine introspection that seeded this package (handler names re-verified against source).

## Corrections applied vs. the initial handover (PR #23)

These are the canonical statements of what the backend actually does as of merge commit
`38cedbc`:

1. Bootstrap header = `X-Installation-Token` (source `AuthController.java`).
2. XLSX export IS implemented (`ExportFormat.EXCEL` + `ReportingService.streamExcel`). PDF/print are NOT.
3. Appendix A regenerated from source: correct handler names (`approve/reject/revert/forward/update/submit/cancel/listForRequest`), correct paths, correct DOC endpoint order (`DELETE` is `DOC-004`, scans `DOC-006`), correct Audit base path `/audit-events`, correct timeline keyset pagination (`cursor`+`size`).
4. Document permissions are `document:upload` / `document:download` / `document:delete` (there is no `document:view`). There is no standalone documents register — the only list endpoint is `GET /documents/requests/{{requestId}}`.
5. Reporting register returns `RegisterResponse` (NOT `PageResponse`), default page size 50, cap 500; dashboard uses GET query filters + `lookbackDays`; widgets/reports catalogues return `List<String>` and require `dashboard:manage` (Phase 2).
6. Monetary values travel as JSON **numbers** (`BigDecimal` with `@Digits(integer=15, fraction=4)`); do not treat them as strings on the wire — use `decimal.js-light` after parsing.
7. Approval actions ALL require `@NotBlank remarks`; forward body is `{ recipientUserId, remarks }` (not `targetUserId`/`comment`).
8. Expense request list endpoint uses query params `search, status, departmentId, categoryId, mineOnly, page, size, sort` (no `branchId` filter at list level); cancel takes a body `{ reason }`.
9. CreateRequest carries `requestTypeId, expenseCategoryId, expenseNatureId, departmentId, currencyId, ...` + `opex` XOR `capex`; STA is a list under `staDetails`; `DocumentReferencePayload` references an uploaded `mediaAssetId`.
10. OPEX/CAPEX/STA fields aligned to `ExpenseRequestDtos.java`.
11. Public config (`PublicConfigurationResponse`) returns `applicationName, organizationDisplayName, organizationName, supportEmail, logoAssetId, faviconAssetId, defaultLocale, defaultTimeZone, dateFormat, defaultCurrencyCode, activeThemeName, activeThemeType, themeTokens` — NOT feature flags, password policy, or logo URL.
12. CSRF cookie behaviour: Spring uses deferred CSRF tokens; GET requests do NOT by themselves set the cookie; the client MUST call `GET /auth/csrf` before its first write.
13. `GET /api/v1/system/info` is authenticated (deny-by-default at the filter chain) despite carrying `@PublicEndpoint` annotation. `@PublicEndpoint` is declarative (architecture-tested); the `SecurityConfig.publicMatchers` permit-all list is the authoritative runtime gate.
14. Notifications unread-count returns `{"count": N}`; mark-all-read returns `{"marked": N}`; all four endpoints require `notification:view`.
15. Audit lives at `/api/v1/audit-events` (keyset pagination, date window max 31 days); audit export is NOT implemented yet (no endpoint; permission `audit:export` reserved for future use — deferred).
16. Bootstrap returns HTTP 201 with `BootstrapResponse` but does NOT establish a session; the administrator must explicitly log in afterward (D-23).
17. PageQuery caps list endpoints at 200/page (separate from the reporting register's 500 cap).
18. Error codes include `INVALID_STATE_TRANSITION`, `BUSINESS_RULE_VIOLATION`, `EXTERNAL_SERVICE_FAILURE`, `INVALID_DOCUMENT`, `INVALID_CONFIGURATION`, plus the full idempotency set (`IDEMPOTENCY_CONFLICT`, `IDEMPOTENCY_IN_PROGRESS`, `IDEMPOTENCY_UNKNOWN_OUTCOME`).
19. Route file naming uses TanStack Router conventions (`app/requests/$id/index.tsx`, `app/approvals/tasks.$taskId.tsx`).
20. Idempotency endpoints: the seven endpoints annotated `@IdempotentOperation` (create, update, submit, cancel, approve, reject, forward, revert) plus `POST /auth/login`. Concurrent same-key submissions collapse to one execution; the client MUST treat `EDAM-1005 IDEMPOTENCY_IN_PROGRESS` as a normal "retry later" response (see H02 §6, H06).
21. CORS: same-origin deploy preferred; cross-origin requires `allowCredentials(true)` + `X-XSRF-TOKEN` / `X-Installation-Token` / `Idempotency-Key` allowed headers.
22. Backend uses Maven (`mvn spring-boot:run`); no Maven wrapper (`./mvnw`) is checked in.
23. D-09 resolved via Flyway V28: roles that hold `request:view-scope` or `request:view-all` are now also granted `request:view-own` at the controller gate, preserving service-level scope filtering (see H04 §5).
24. Phase 1 scope locked: core workspace (auth, dashboard, my/all requests, create/edit, submit/cancel, approval inbox + 4 actions, request timeline, documents per request, notifications, required lookups). Admin CRUD screens deferred. Audit viewer deferred.
25. Document preview deferred: Phase 1 uses signed-URL download only (new tab); inline PDF/image preview added only after storage/CDN URL and deployed CSP are verified (D-21).
26. H15 open decisions retained and NOT silently converted into assumptions (PDF/print timeline, idle timeout, polling vs websocket, file size cap, locale, etc.).
27. Verification in H16 is static source inspection plus the CI run at merge; no runtime backend was executed outside CI.

## What is NOT in this package (Phase 1 scope)

- Full admin CRUD (users, roles, organization, master data, expense config, approval limits, committees, delegation, workflows, rules, branding/themes/settings, SMTP/templates/events, integrations).
- Audit log viewer / audit export.
- PDF export / print view.
- WebSocket / SSE (60-second polling in Phase 1).
- Inline document preview (signed-URL download only).
- PWA / offline / SSO / dark mode / localization beyond en-GB / 2FA.
- Frontend source code or scaffolding.

## Source of truth

- Controllers: `src/main/java/com/edam/**/*Controller.java`
- Security: `src/main/java/com/edam/config/SecurityConfig.java`
- Permissions: `src/main/java/com/edam/common/security/Permissions.java`
- Envelope/errors/pagination: `src/main/java/com/edam/common/api/*`, `src/main/java/com/edam/common/exception/GlobalExceptionHandler.java`
- Idempotency: `src/main/java/com/edam/common/api/{IdempotencyService,IdempotencyAspect}.java`, `src/main/java/com/edam/integrations/service/IntegrationService.java`
- Request visibility: `src/main/java/com/edam/expenserequest/service/ExpenseRequestService.java#isVisible`
- Seeded roles/permissions: `src/main/resources/db/migration/V12__access_management.sql`
- D-09 migration: `src/main/resources/db/migration/V28__grant_view_own_to_wider_view_roles.sql`
- Regression test: `src/test/java/com/edam/rolemanagement/SeededRolePermissionsD09Test.java`
