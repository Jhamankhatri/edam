# 19 — Frontend Local Setup & Integration Runbook

## 19.1 Prerequisites
- Node 20+, npm/pnpm/yarn (frontend project to be created in a separate repository — not part of this PR).
- Docker (for Postgres) OR access to a PostgreSQL 15+ instance.
- Java 21 JDK and Maven (only to run the backend locally for frontend development).
- Optional: MailHog/Mailpit for SMTP; Cloudinary account for document storage; ClamAV or SaaS malware scanner for production-like scanning.

## 19.2 Start Postgres (local Docker)
```bash
docker run -d --name edam-pg \
  -e POSTGRES_PASSWORD=edam \
  -e POSTGRES_DB=edam \
  -p 5432:5432 \
  postgres:16-alpine
```

## 19.3 Run the backend
1. `cd` to the backend repo root.
2. Create an `application-local.yml` (or pass env vars) with:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/edam
       username: edam
       password: edam
   edam:
     cors:
       allowed-origins: http://localhost:5173
   ```
3. Export the required environment variables (Linux/macOS):
   ```bash
   export EDAM_BOOTSTRAP_TOKEN=bootstrap-dev-token-change-me
   export EDAM_MAIL_MASTER_KEY=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
   export EDAM_INTEGRATION_MASTER_KEY=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f
   ```
   On Windows PowerShell use `$env:EDAM_BOOTSTRAP_TOKEN="..."`.
4. Run: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (or just `spring-boot:run` if you put config in `application.yml`).
5. Wait for: "Started EdamApplication in …"; `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`; `curl -i http://localhost:8080/api/v1/auth/csrf` returns 200 and sets the `XSRF-TOKEN` cookie. After login, `curl -b JSESSIONID=... http://localhost:8080/api/v1/system/info` returns the application metadata envelope.

## 19.4 Frontend dev-server proxy
In the Vite config:
```ts
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true, credentials: 'include' }
    }
  }
});
```
With a proxy the frontend is same-origin with the API at runtime; cookies flow automatically and CORS headers are not required from the browser's perspective (still configure `edam.cors.allowed-origins` for non-proxy callers like Postman).

## 19.5 Postman environment setup
1. Duplicate `docs/phase-4j/postman/EDAM.postman_environment.template.json` → `EDAM local.postman_environment.json`.
2. Set:
   - `baseUrl` = `http://localhost:8080`
   - `bootstrapToken` = the `EDAM_BOOTSTRAP_TOKEN` you exported
   - `adminUsername` = `admin`
   - `adminPassword` = (choose on bootstrap — fill after Scenario 1)
   - `xsrfToken` will be set automatically by the tests
3. Import the collection + environment into Postman; select the environment.

## 19.6 Safe first-run sequence (mirrors Scenario S1 in `POSTMAN_SCENARIO_MATRIX.md`)
1. `GET /api/v1/system/info` → `status: UP`.
2. `GET /api/v1/auth/installation` → `bootstrapped: false`.
3. `GET /api/v1/auth/csrf` → cookies set.
4. `POST /api/v1/auth/bootstrap` (with `X-EDAM-Bootstrap-Token` header).
5. `GET /api/v1/auth/me` → `system:administer` permission present.
6. Clear/remove `EDAM_BOOTSTRAP_TOKEN` from the backend env after bootstrap.
7. Change the bootstrap admin password via `POST /api/v1/auth/change-password` and create named admin users (do not use the bootstrap account for daily testing).

## 19.7 Seeding minimal reference data for UI development
After bootstrap you need at least:
1. Organization profile: `PATCH /organization/profile` with name, base currency `PKR`.
2. At least one department and one branch (organization endpoints).
3. PKR currency activated (`/master-data/currencies`).
4. OPEX + CAPEX request types (already seeded by migrations; verify via `GET /expense-configuration/request-types`).
5. At least one category + subcategory.
6. A test requester user (`POST /users`) with `request:create`, `request:view-own`, `request:edit`, `request:submit`, `request:cancel`, `document:upload`, `document:download`; create a role with those permissions and assign it to the user, plus data scopes for the test department/branch.
7. A test approver user with `approval:view`, `approval:act`, `request:view-own`, etc.
8. A published workflow for OPEX (and CAPEX, if testing it). Without a published workflow, submit returns a validation error.

Once those exist you can walk S9 (OPEX submit→approve→complete) end to end.

## 19.8 Common integration errors
| Symptom | Likely cause | Fix |
|---|---|---|
| Every authenticated request returns 401 | No session cookie (didn't call `/auth/csrf` then `/auth/login`, or proxy is stripping cookies) | Ensure `credentials: 'include'`; proxy uses `changeOrigin:true`; `withCredentials` is true for axios clients. Note: `/api/v1/system/info` is authenticated (deny-by-default); the public smoke endpoint is `/actuator/health`. |
| POST/PUT returns 403 with "Could not verify the provided CSRF token" | CSRF header missing or stale | Re-read `XSRF-TOKEN` cookie before every mutation; after login re-read because of rotation. |
| CORS error in the browser console | `edam.cors.allowed-origins` does not include your Vite origin, or you're calling the API directly without the proxy | Add the origin to `edam.cors.allowed-origins`, or use the proxy. |
| Submit returns 409 "No published workflow for request type" | No published workflow | Publish a workflow via the workflow admin endpoints/UI. |
| Document download URL returns 409 | `scanStatus` not CLEAN (or scanner not configured, so it stays PENDING_SCAN) | Plug in a real scanner; for dev only, run tests using `FakeMalwareScanner.CLEAN`-style fixtures (backend disables fake in non-test profiles). |
| SMTP test fails | No SMTP server running; redacted diagnostic | Use MailHog/Mailpit locally; ignore for UI dev. |
| `EDAM-1003 INVALID_PAGINATION` | `size > 200` or `page < 0` | Cap size to ≤200, normalize page to ≥0. |
| `EDAM-3002 CONCURRENT_MODIFICATION` on edit | Optimistic lock mismatch (another user edited, or you sent a stale `version`) | Reload and merge. |
| `EDAM-1004 IDEMPOTENCY_CONFLICT` | Reused `Idempotency-Key` with different body | Generate a new key for each distinct user action; only reuse on retry of the same payload. |

## 19.9 Verifying the integration (sanity checklist for frontend devs)
1. Login flow works (CSRF → login → me → theme → notifications).
2. CSRF is re-read after login/logout; mutations succeed with `X-XSRF-TOKEN` header.
3. 401 redirects to `/login?reason=expired` and clears local user.
4. 403 permission failures show a friendly page; buttons for missing permissions are hidden but the backend still rejects direct calls.
5. Field-level errors (400 VALIDATION_FAILED) are shown inline next to their inputs.
6. Optimistic lock 409 offers a reload action.
7. Notifications poll every 30 s; unread badge updates.
8. Document upload respects size/type limits and only enables download on CLEAN.
9. Active theme tokens are applied to CSS variables; pre-login page uses `/public/configuration` only.
10. Export POST returns a CSV blob that the browser downloads.
11. Correlation id is visible in technical details on every error.
12. Logout invalidates the session and returns to login.
