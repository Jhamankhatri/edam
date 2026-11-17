# Production Readiness

## Checklist before deploying EDAM to production

### Required environment variables (fail-closed)
| Variable | Purpose | Required |
|---|---|---|
| `EDAM_DB_URL` | JDBC URL to PostgreSQL | **Yes** |
| `EDAM_DB_USERNAME` | DB user | **Yes** |
| `EDAM_DB_PASSWORD` | DB password | **Yes** |
| `EDAM_MAIL_MASTER_KEY` | Hex-encoded 32-byte AES-256 key for SMTP password encryption | **Yes** (any mail configured) |
| `EDAM_INTEGRATION_MASTER_KEY` | Hex-encoded 32-byte AES-256 key for integration credentials | Required when integrations are configured |
| `EDAM_BOOTSTRAP_TOKEN` | One-time token required for the first bootstrap administrator. Remove/unset after bootstrap. | **Yes** for first-run |
| `EDAM_DB_POOL_SIZE` | Hikari max pool size | Default 20 |
| `edam.cors.allowed-origins` | Comma-separated exact origin list for the React frontend host | **Yes** |
| `edam.documents.scanner.*` | Malware scanner provider configuration | **Yes** before allowing uploads (fail-closed download otherwise) |
| `spring.mail.*`, `edam.communication.mail.*` | SMTP host/port/auth | Required for outbound mail |
| `edam.storage.*` | Document storage provider (e.g. Cloudinary) credentials | Required for document upload |

### Application profiles
- `default` (used in dev) — H2-unsuitable; uses Postgres via env or local config.
- `test` — for CI; schema validated (`ddl-auto: validate`); fixed mail master-key for tests; CORS allows `http://localhost:5173`.
- `prod` — DB URL/user/pass from environment, secure session cookies, actuator exposes only `health,info` with `show-details: never`, JSON logging (ECS layout) selected via `logback-spring.xml`, forward-headers-strategy=framework for reverse-proxy deployments.

### Reverse proxy / TLS
- EDAM must be deployed behind TLS (HTTPS) in production; the prod profile sets `server.servlet.session.cookie.secure: true`.
- Set `server.forward-headers-strategy: framework` (already in prod YAML) and ensure the proxy sets `X-Forwarded-Proto: https`.
- CSRF cookie is readable by JavaScript by design (double-submit pattern); CORS is exact-origin restricted. Do not add `*` origins in production.

### Database
- PostgreSQL 15+ required. Migrate with Flyway automatically on boot (`flyway.locations=classpath:db/migration`). Do not run with `ddl-auto=update`.
- Backup strategy: point-in-time recovery covering all business tables + append-only `audit_events`.
- Hikari defaults are tuned for production (20 max / 5 idle).
- DB user needs SELECT/INSERT/UPDATE/DELETE/TRIGGER; superuser not required after migrations are applied.

### Observability
- Correlation id is attached to every request/response via `CorrelationIdFilter` and logged in structured format.
- Logs are redacted: no query strings, no request bodies, no credential values, no encrypted ciphertext.
- `/actuator/health` returns `{"status":"UP"}` without component detail in prod; `/actuator/info` returns app name + version.
- No actuator exposure of `env`, `heapdump`, `shutdown`, etc. in any profile.

### Security controls verified
- Deny-by-default filter chain.
- CSRF on all mutating endpoints.
- Method-level `@EdamPermission` enforced everywhere (architecture test).
- Object-level visibility enforced in services (requests, documents, workflow cases).
- AES-256-GCM encryption at rest for SMTP and integration credentials; env-sourced keys.
- SSRF protection for integration URLs.
- Malware-scanner fail-closed (download only CLEAN).
- Audit table append-only (trigger blocks DELETE/UPDATE of audit rows outside the application path).
- Stack traces and internal details not surfaced to API clients.

## Production blockers that must be resolved before real-money use

These are tracked in `open-decisions.md`:

1. **Malware-scanner backend (OD-14):** Plug in a real scanner (ClamAV, SaaS) via the `MalwareScanner` port before enabling user uploads. The code already refuses downloads of non-CLEAN documents, but with no scanner configured every uploaded document stays `PENDING_SCAN` and cannot be downloaded.
2. **PKR conversion/rounding (OD-07):** No FX arithmetic exists yet. When multi-currency requests are enabled, Finance must sign off on rate direction, scale and rounding mode.
3. **Workflow routing table (OD-02/OD-03):** The workflow engine supports configurable stages/approvers/quorum but no pre-seeded "CEO/CFO/HOD" routing is shipped. Banks must publish their own configuration through Workflow Administration before approvals can flow.
4. **Role → permission → data-scope matrix (OD-12):** Permission constants are defined; no role matrix is pre-seeded beyond SYSTEM_ADMINISTRATOR grants for security-critical permissions. Customer/business owner must map their roles to EDAM permissions before go-live.
5. **Master-data source (OD-16):** Users, departments, branches are maintained locally. HR/AD integration is out of scope.
6. **Tenant isolation model (OD-20):** The product is currently a single-tenant deployable. Multi-tenancy is a seam only (ADR-0002/0008); deploy one EDAM instance per institution until tenancy is delivered.
7. **Key rotation procedure for `EDAM_INTEGRATION_MASTER_KEY`/`EDAM_MAIL_MASTER_KEY`:** No automatic re-encryption migration. Document a planned rotation window (re-enter credentials through the admin UI after key change).
