# EDAM Administrator User Guide

Audience: the system administrator(s) who install, configure and maintain EDAM. This guide walks through bootstrapping, configuring organization, users, master data, approval policy, communications, branding and integrations.

> EDAM is delivered as an **API-first backend**. This guide describes what the backend supports and how to exercise it through the Postman collection or via the frontend (once built). All API examples refer to the Postman collection shipped in `postman/EDAM.postman_collection.json`.

## 1. Installing EDAM

1. Provision a PostgreSQL 15+ database.
2. Configure environment variables (see `production-readiness.md`): `EDAM_DB_URL`, `EDAM_DB_USERNAME`, `EDAM_DB_PASSWORD`, `EDAM_BOOTSTRAP_TOKEN` (one-time), `EDAM_MAIL_MASTER_KEY` and `edam.cors.allowed-origins` at minimum.
3. Deploy the executable WAR (`java -jar edam.war` or deploy to a servlet container).
4. Confirm `GET https://<host>/actuator/health` returns `{"status":"UP"}` (public smoke probe). After signing in, `GET https://<host>/api/v1/system/info` returns `{"success":true,"data":{"application":"edam","status":"UP","version":"…"},...}`.
5. Confirm `GET /api/v1/auth/installation` returns `{"installed":false}`.

## 2. First-administrator bootstrap

1. Call `POST /api/v1/auth/bootstrap` with:
   ```json
   {
     "installationToken": "<EDAM_BOOTSTRAP_TOKEN>",
     "username": "admin",
     "password": "<strong-password>",
     "displayName": "System Administrator"
   }
   ```
2. The response sets an authenticated session. Call `GET /api/v1/auth/me` to confirm you have `system:administer`.
3. **Remove/unset `EDAM_BOOTSTRAP_TOKEN`** from the environment after bootstrap; subsequent calls return 409 (already installed).
4. Change the bootstrap admin's password via `POST /api/v1/auth/change-password` and create named administrator users (do not share the bootstrap account).

## 3. Initial configuration order

Follow this order to ensure referential integrity:

1. **Institution profile** (`POST/PATCH /organization/profile`) — name, tax ID, address, base currency (PKR by convention).
2. **Organization units, departments, branches** — POST units tree, then departments, then branches; activate as needed.
3. **Master data** — enable PKR and other currencies; add vendors and tax authorities. Set exchange rates for non-PKR currencies (note: FX arithmetic is not performed until OD-07 is signed off).
4. **Expense configuration** — natures, categories, subcategories, request types (OPEX and CAPEX already seeded if you use the seed migration; verify via `GET /expense-configuration/request-types`).
5. **Approval limits & delegation** — for each (department, category, currency) define approval limits; configure recommending authorities and exceeding-limit rules (warn-only by default).
6. **Committees** — define expense committees and their levels/members.
7. **Workflow definitions** — draft and publish at least one workflow per request type before users can submit requests. Without a published workflow, submit returns a validation error.
8. **Business rules** — if your bank uses rule-based validation/population, draft rule sets and publish them; otherwise leave the default ruleset.
9. **Users, roles, data scopes** — create user accounts, create roles mapped to the permission catalogue, assign roles and data scopes.
10. **SMTP** — configure SMTP credentials (password is encrypted at rest) and send a test mail before enabling notification triggers.
11. **Email templates & event triggers** — review templates; enable notifications per event.
12. **Branding** — upload logo, set theme tokens, activate the theme.
13. **Document storage** — configure Cloudinary (or compatible) and attach a malware scanner before enabling uploads in production.
14. **Integrations** (optional) — configure outbound webhook/SFTP providers; store credentials (encrypted); run test execution.

## 4. Day-to-day administration

### User management
- Create users with a temporary password; force password change on first login (self-service `POST /auth/change-password`).
- Lock/unlock: users are locked after repeated failed logins (configurable threshold); unlock via `POST /users/{id}/unlock`.
- Reset password: `POST /users/{id}/password-reset` generates a reset token (delivered via email if SMTP is configured; otherwise surfaced only via admin channels in dev).
- Roles & scopes: assign roles to grant permissions; assign data scopes (INSTALLATION / BRANCH / DEPARTMENT with ID lists) to limit visibility. Always verify with `GET /users/{id}/effective-permissions`.

### Organization changes
- When departments/branches are added or renamed, update via the Organization endpoints; **do not delete** rows with historical requests — use activation instead (active=false removes from pickers but preserves history).

### Approval-policy changes
- Approval limits are date-bounded; create a new row for future effective dates rather than mutating history.
- Workflow changes: edit the DRAFT, validate, then publish a new version. **In-flight requests continue against the version they were submitted against;** only new submissions use the new version. Retire a published version only after you are sure no in-flight requests reference it (or after they complete).
- Delegation matrix changes take effect immediately for new routing decisions; tasks already created keep their original approvers.

### Communications
- SMTP password changes: PUT the SMTP config with a new password; the old ciphertext is rotated. The password is never returned by the API.
- Test mail: use the dedicated test endpoint; if it fails, the diagnostic is redacted (credentials do not appear in logs).
- Outbox: failed messages remain in `comm_notification_outbox` with a redacted error; fix SMTP and the background processor retries. Do not edit outbox rows manually.

### Branding
- Logo upload is a document asset; the backend stores the reference, not the binary. Theme tokens (colors, radii, font) are validated against an allowlist — arbitrary CSS/JS is rejected.
- Preview theme tokens before activation; activation is near-instantaneous for all users.

### Integrations
- Credentials are encrypted with AES-256-GCM using `EDAM_INTEGRATION_MASTER_KEY`. Rotating the master key requires re-entering credentials through the admin UI.
- Endpoint URLs are SSRF-validated at save time (loopback/private/link-local/IMDS blocked); this is a hard security control and cannot be disabled.
- Executions are claimed with `SELECT ... FOR UPDATE SKIP LOCKED`; a failed execution records a redacted diagnostic and moves to DEAD after retries.
- Use the `Idempotency-Key` header for manual retries of a failed execution to guarantee exactly-one effect.

## 5. Audit & traceability
- All important mutations write an `audit_events` row with actor, timestamp, correlation id, before/after values (redacted for secrets). Use `GET /audit-events` with filters to investigate.
- The request timeline (`GET /expense-requests/{id}/activity`) aggregates workflow, document and audit events for a single request — use it for dispute resolution.
- Correlation id is returned on every response (`ApiResponse.correlationId`) and is present in every server log line for the same request.

## 6. Backup & operations
- Schedule Postgres backups covering all tables including `audit_events` (point-in-time recovery).
- Monitor `/actuator/health`; it returns `{"status":"UP"}` without DB/host details in prod.
- Logs are JSON-formatted in prod and redacted; never lower log levels to include request/response bodies in production.
- Rotate encryption keys during a planned change window.
