# Postman Testing Guide — EDAM API

## 1. Import
1. In Postman, click **Import** and select:
   - `docs/phase-4j/postman/EDAM.postman_collection.json`
   - `docs/phase-4j/postman/EDAM.postman_environment.template.json`
2. Duplicate the environment and name it after your target (e.g. `EDAM local`). The template contains placeholders — **never commit real secrets or passwords**.
3. Set `baseUrl` to your running EDAM backend (default `http://localhost:8080`).
4. Before the first run, set `bootstrapToken` to the value of the `EDAM_BOOTSTRAP_TOKEN` environment variable configured in your backend; after Scenario 1 completes, clear it.

## 2. Authentication model (critical — EDAM does NOT use JWT)
EDAM uses Spring Security **session cookies** (HttpOnly `JSESSIONID`) plus double-submit **CSRF** protection:

1. **Get CSRF token:** `GET /api/v1/auth/csrf` (public). Postman's cookie jar captures `JSESSIONID` and `XSRF-TOKEN` automatically; the test script copies `XSRF-TOKEN` into the `xsrfToken` environment variable.
2. **Log in:** `POST /api/v1/auth/login` (public) with `{"username":"…","password":"…"}`. The test script refreshes `xsrfToken` from the post-login cookie.
3. **Mutating calls** (POST/PUT/PATCH/DELETE) must include the header `X-XSRF-TOKEN: {{xsrfToken}}`. The collection adds this header automatically; requests that fail with 403 and a missing/incorrect token message indicate the cookie was not yet set — re-run `GET /api/v1/auth/csrf` then log in again.
4. **`GET /api/v1/auth/me`** (authenticated-only) returns the current principal; use it to confirm session health.
5. **Log out:** `POST /api/v1/auth/logout` invalidates the session.

Every request that is not explicitly `@PublicEndpoint` returns **401** without a session. Every mutating request that omits or sends a stale CSRF token returns **403**. This is expected behaviour — it is the contract, not a bug.

> The `/api/v1/info` endpoint is public and returns `{"application":"edam","version":"…","status":"UP"}`; it is useful as a deployment smoke check.

## 3. Response envelope
All API responses follow the `ApiResponse<T>` envelope:

```json
{ "success": true, "correlationId": "…", "path": "/api/v1/…", "data": { … }, "message": "OK" }
```

Errors return `success:false`, `errorCode`, `message`, `correlationId` and HTTP status; see ADR-0005 for full semantics. The test scripts in each request assert `success === true` for 2xx responses.

## 4. Recommended run order
The folders in the collection are prefixed `01…29` in the order you should explore them:

1. **01 - Authentication and Session** — CSRF, login, me, logout, change/reset password.
2. **02 - Bootstrap and Initial Setup** — first-administrator bootstrap (one-time).
3. **03–13, 15–27** — the administrative and transactional modules (users, roles, organization, master data, categories, approval limits, committees, workflows, business rules, approvals, documents, SMTP, templates, notifications, branding, reporting, audit, integrations).
4. **08 - OPEX** and **09 - CAPEX** share the `/api/v1/expense-requests` API; CAPEX is created by selecting the CAPEX requestTypeId rather than a separate path.
5. **16 - Approval Inbox** lists open tasks; **17 - Approval Actions**, **18 - Rejection and Reverting**, **19 - Forwarding and Resubmission** contain POST calls on `/approvals/tasks/{taskId}`.
6. **28 - Idempotency** covers duplicate-safe mutating endpoints that respect `Idempotency-Key`.
7. **29 - Negative and Authorization Tests** is a placeholder for the tester to add explicit 401/403/400/409 scenarios.

## 5. 13 scenarios
See `POSTMAN_SCENARIO_MATRIX.md` for sequential end-to-end scenarios, with prerequisites, requests in order, and expected results.

## 6. Things that will NOT work out of the box (external dependencies)
- **Document uploads** require a real malware scanner to mark documents `CLEAN` before download. The default `FakeMalwareScanner` is test-only.
- **SMTP sends** require a configured SMTP server; with no configuration, outbox rows remain `FAILED` with a diagnostic message.
- **Integration providers** require a configured adapter; the Phase 4I release ships an HTTP webhook provider; other adapters are SPI slots.
- **Cloudinary document storage** requires `CLOUDINARY_URL` in production; otherwise uploads fail.
- **Forgot/reset password** depends on SMTP; without SMTP the reset token is returned in error diagnostics only in non-prod profiles.

## 7. What the test scripts do automatically
- Capture the CSRF token into `{{xsrfToken}}` on CSRF and login responses.
- Assert 2xx status and the `ApiResponse` envelope shape.
- Capture the last created UUID-shaped `id` from `data.id` into `{{lastCreatedId}}` for follow-up calls; replace the per-entity path variables (`{{id}}`, `{{taskId}}`, `{{configId}}`, …) manually or chain requests via environment variables.

## 8. Do not commit
- The environment file template contains placeholder values only; never save real passwords, SMTP credentials, Cloudinary secrets, or encryption keys into the repository. Postman's "type: secret" entries are obscured in the UI but are still stored in the exported JSON — keep your real environment files local or in a team secret store.
