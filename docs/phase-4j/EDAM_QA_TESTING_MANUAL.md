# EDAM QA Testing Manual

This manual tells QA engineers how to validate the EDAM backend end-to-end. It complements the Postman collection (`postman/EDAM.postman_collection.json`) and the 13 scenarios in `POSTMAN_SCENARIO_MATRIX.md`.

## 0. Prerequisites

- A running EDAM backend pointing at a clean Postgres database (e.g. local Docker or the dev environment).
- Postman with the EDAM collection and an environment based on `EDAM.postman_environment.template.json`.
- `baseUrl` set correctly; `bootstrapToken` matches the backend `EDAM_BOOTSTRAP_TOKEN`.
- For full runs, configure SMTP (e.g. MailHog/Mailpit), Cloudinary (or a test bucket) and a fake malware scanner profile if available; otherwise test the failure paths explicitly.

## 1. Smoke checks before every run

| # | Action | Expected |
|---|---|---|
| 1.1 | `GET /api/v1/info` | 200, `{application:"edam", status:"UP"}` |
| 1.2 | `GET /api/v1/auth/csrf` | 200; response sets `XSRF-TOKEN` and `JSESSIONID` cookies |
| 1.3 | Without a session, hit any business endpoint (e.g. `GET /api/v1/users`) | 401 |
| 1.4 | Mutating endpoint without `X-XSRF-TOKEN` (use an authenticated session but omit header) | 403 CSRF error |
| 1.5 | `GET /actuator/health` | 200 `{"status":"UP"}`; no DB/host details in prod profile |

## 2. Authentication tests

| # | Action | Expected |
|---|---|---|
| 2.1 | Wrong password login | 401 with `INVALID_CREDENTIALS`; no session established |
| 2.2 | Successful login | 200; session + rotated CSRF; `GET /auth/me` returns principal with `system:administer` after bootstrap |
| 2.3 | After N failed attempts (configured threshold), next attempt | 429 / account locked depending on policy; unlock via admin endpoint |
| 2.4 | Change password → log out → old password | 401; new password accepted |
| 2.5 | Forgot password with unknown username | 200 (same response as known user — no user enumeration) |
| 2.6 | Reset password with an invalid/expired token | 400/404 with `INVALID_RESET_TOKEN` |
| 2.7 | Bootstrap when already installed | 409 `ALREADY_INSTALLED` |

## 3. Authorization tests (repeat for every controller)

For each endpoint in the collection:

| # | Assertion |
|---|---|
| 3.1 | No session → 401 |
| 3.2 | Authenticated user **without** the required permission → 403 `AUTHORIZATION_FAILURE` |
| 3.3 | Authenticated user **with** the permission → 2xx |
| 3.4 | Object-level: user with permission but outside their data scopes (e.g. a different department's request) → 404 or empty page (never leak existence/contents) |
| 3.5 | Public endpoints (`/info`, `/auth/csrf`, `/auth/login`, `/auth/logout`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/installation`, `/auth/bootstrap` before install, `/configuration/public/configuration`) respond without a session. |

## 4. Input validation tests

For each POST/PUT, cover:
- Missing required field → 400 `VALIDATION_ERROR` with `fieldErrors` entry.
- Wrong type (string for number, malformed UUID) → 400.
- Violations of length/format (e.g. ISO currency code, email) → 400.
- Cross-field rules (end date before start date, amount < 0) → 400/422 with domain error code.
- Duplicate unique (duplicate username/currency code/role key) → 409 `DATA_INTEGRITY_VIOLATION`.
- FK violation (reference to a non-existent department/user/category) → 409/400 depending on endpoint.

## 5. Workflow end-to-end

Walk Scenarios S9, S10, S11 (see `POSTMAN_SCENARIO_MATRIX.md`) asserting:
- Submit creates a workflow instance pinned to the current published version; subsequent publish does NOT move the in-flight case.
- Each approval is recorded in `workflow_actions`; the stepper endpoint returns the correct current step.
- Quorum steps advance only when N/M approvers approve; extra approvals after quorum are recorded idempotently.
- Reject returns to requester with remarks; resubmit starts from the beginning of the chain but retains timeline history.
- Revert returns to DRAFT; resubmit uses the SAME pinned version.
- Forward replaces the current approver task; recipient sees it in their inbox; original approver no longer sees it.
- Concurrent approval on the same task → one succeeds, the other gets 409 `CONCURRENT_MODIFICATION`.
- Cancel from non-terminal state → CANCELLED; no further actions possible.

## 6. Documents & malware scanning

| # | Action | Expected |
|---|---|---|
| 6.1 | Upload a document with a type/size outside policy | 400 |
| 6.2 | Upload valid document | 201, `scanStatus=PENDING_SCAN` initially (when using test fake that hasn't scanned) |
| 6.3 | Download while PENDING_SCAN | 4xx / blocked (fail-closed) |
| 6.4 | After scan=CLEAN (simulated via FakeMalwareScanner.CLEAN file name in tests), download URL | 200 signed URL; URL is short-lived |
| 6.5 | After scan=INFECTED | Download blocked; status visible to requester/approver |
| 6.6 | Cross-user access: user B tries to download user A's document outside visibility scope | 404 / 403 (must not leak existence if outside scope) |
| 6.7 | Delete document | Requires `document:delete`; not allowed once request is in a terminal state per policy (verify behaviour per configuration). |

## 7. Communications / SMTP / outbox

| # | Action | Expected |
|---|---|---|
| 7.1 | Save SMTP config without master key env var | 503 / fail-closed; no plaintext password persisted |
| 7.2 | GET SMTP config | Password field never returned (blank/masked) |
| 7.3 | Test mail with valid SMTP | Success; test history entry; no credentials in diagnostics |
| 7.4 | Trigger a notification (e.g. approve) with SMTP unavailable | Outbox row created in FAILED with redacted diagnostic; approval itself still commits (TX-split guarantee) |
| 7.5 | Two notifications in the same transaction | Both end up in outbox; one failing SMTP send does not roll back the sibling |
| 7.6 | Repeated outbox sweep | Each claimed row is delivered at most once (claim pattern) |

## 8. Branding & themes

| # | Action | Expected |
|---|---|---|
| 8.1 | Unauthenticated access to `/configuration/public/configuration` | 200; returns name, primary color, logo; no secrets, no SMTP host, no feature flags |
| 8.2 | Update theme with unknown token keys | Ignored/validation error (whitelist enforcement) |
| 8.3 | Attempt to inject arbitrary CSS/JS in a theme string (e.g. `"color": "red; background: url(x)"`) | Rejected or sanitised; frontend drops it as well |
| 8.4 | Activate theme | Subsequent GET active returns new theme; public endpoint returns safe subset |

## 9. Integrations & idempotency

| # | Action | Expected |
|---|---|---|
| 9.1 | Save an integration with a loopback/private/IMDS URL | 400 SSRF validation error |
| 9.2 | Save credentials; GET config back | Secret field not returned |
| 9.3 | Execute integration twice with the same `Idempotency-Key` and same body | One execution record; second call returns the original response |
| 9.4 | Execute with same key but different body | 422 `IDEMPOTENCY_KEY_REPLAY` |
| 9.5 | Timeout/error from provider | Execution status FAILED/TIMEOUT with redacted diagnostic; dead-letter after retries |
| 9.6 | Concurrent POSTs with the same key | Exactly one execution record (unique constraint V27) |

## 10. Audit & timeline

| # | Action | Expected |
|---|---|---|
| 10.1 | Create, edit, approve, reject a request | Audit rows created for each mutation; search by correlationId retrieves them |
| 10.2 | Request timeline aggregates workflow actions, document events, audit events | Correct chronological order; actor names visible |
| 10.3 | Direct SQL UPDATE/DELETE on `audit_events` | Blocked by trigger (append-only) |

## 11. Concurrency / performance

- Approve two concurrent approvals on the same task (e.g. two browser tabs) → exactly one success, one 409.
- Edit a request in two tabs; submit second edit with stale `@Version` → 409 `CONCURRENT_MODIFICATION`.
- List endpoints at size=50 return within 500ms on a seeded database (baseline benchmark for go-live; tune after OD-19 NFR sign-off).

## 12. Post-run checklist

- Verify no ERROR/SEVERE logs in the server log (expected/handled validation errors are INFO/WARN).
- Verify redaction: search logs for configured SMTP password, Cloudinary URL, integration secrets — none should appear.
- Verify CSRF and session cookies are `Secure; HttpOnly; SameSite=Lax` when the prod profile is active.
- Verify CORS pre-flight rejects random origins.
- Verify `/actuator/env`, `/actuator/heapdump`, `/actuator/shutdown` are not exposed.
