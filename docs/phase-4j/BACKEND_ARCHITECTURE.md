# EDAM Backend Architecture

## Shape

- **Language/runtime:** Java 21, Spring Boot 3.x, Jakarta EE 9+.
- **Build:** Maven (`pom.xml`), package = `war` (per ADR-0003), executable via `java -jar` in production.
- **Database:** PostgreSQL 15+ (tested on 18-alpine in CI).
- **Schema ownership:** Flyway forward-only migrations `src/main/resources/db/migration/V1..V27`; Hibernate `ddl-auto=validate` on every profile. No `spring.jpa.hibernate.ddl-auto=update` anywhere.
- **JSON:** Jackson databind. The injectable JSON mapper bean in the application context is `tools.jackson.databind.json.JsonMapper` (Spring Boot auto-configured), not `com.fasterxml.jackson.databind.ObjectMapper`.
- **Persistence:** Spring Data JPA; JDBC only in repositories; business logic exclusively in services; controllers handle HTTP concerns only (mapping, DTOs, status codes, CSRF, correlation ids).
- **Module structure:** package-by-capability under `com.edam.*` (see ADR-0001):
  - `common` — ApiResponse, ErrorCode, exceptions, security annotations, logging/correlation, audit base classes.
  - `identity` — authentication seam (ADR-0004), installation bootstrap, password policy, user/password-change.
  - `usermanagement`, `rolemanagement` — users, role-permission grants, data-scope assignments.
  - `organization` — institution profile, units, departments, branches.
  - `masterdata` — currencies, vendors, tax authorities.
  - `expenseconfiguration` — categories, subcategories, natures, request types.
  - `approvallimits` — department approval limits, exceeding-limit rules, recommending authorities.
  - `delegation` — delegation matrix resolution.
  - `committee` — expense committees/members.
  - `workflow` — configurable workflow definitions/versions/publishing + approval actions engine.
  - `expenserequest` — OPEX/CAPEX lifecycle, validator, assembler, exchange-rate resolver.
  - `businessrules` — JSON-AST rule engine (no MVEL/SpEL).
  - `documents` — upload, metadata, signed-URL download, malware-scan port.
  - `communication` — SMTP config, templates, notification events, transactional outbox.
  - `configuration` — application settings, feature flags, branding/themes.
  - `reporting` — dashboard, register, CSV/XLSX exports.
  - `audit` — append-only event store, read/search, request timeline.
  - `integrations` — provider-adapter SPI, encrypted credentials, execution engine (Phase 4H/I).
  - `storage` — pluggable storage provider (Cloudinary adapter in production; `FakeStorageProvider`/`FakeMalwareScanner` only in tests).
  - `web` — system info endpoint, error dispatch, MVC config.

## Layering invariants

- **Controller → Service → Repository.** No repository access from controllers.
- **No business logic in controllers.** Validation that belongs to HTTP shape (required fields, JSON shape) lives in DTOs with `@Valid`/Bean Validation; business invariants live in services.
- **No SQL in services.** All SQL lives in repositories (Spring Data derived queries + `@Query` native/JPQL).
- **Transactional boundaries:**
  - Controller methods never carry `@Transactional`. Services do.
  - For network/external calls that must not hold a DB transaction open (e.g. integration execution, SMTP send), the service splits work across `@Transactional(propagation = REQUIRES_NEW)` per work unit and a non-transactional driver loop (see `IntegrationExecutionWorker` and `OutboxProcessor`).
  - Claim patterns use `SELECT ... FOR UPDATE SKIP LOCKED` for safe concurrent dequeue.
- **Optimistic locking:** `@Version` on `ExpenseRequest`, `WorkflowTask`, `WorkflowVersion`, `NotificationOutbox`, `IntegrationConfig`, `IntegrationExecution`; conflicting edits return `CONCURRENT_MODIFICATION` mapped to HTTP 409.

## Security envelope

- Deny-by-default Spring Security filter chain; explicit public endpoints only (login, logout, CSRF, bootstrap, installation status, forgot/reset password, system info, actuator health).
- CSRF: `CookieCsrfTokenRepository` with readable `XSRF-TOKEN` cookie; mutating requests must send the matching `X-XSRF-TOKEN` header.
- Session: cookie-based, `HttpOnly`, `Secure` + `SameSite=Lax` in `prod` profile; session fixation → `migrateSession()` on login.
- Method security: `@EnableMethodSecurity` with a custom `@EdamPermission` annotation (checked by `EdamPermissionAuthorizationManager`). Every endpoint is either `@EdamPermission`, `@PublicEndpoint(reason=...)` or `@AuthenticatedEndpoint(reason=...)` — enforced by `EndpointAuthorizationArchitectureTest` (fails the build if a new controller method is added without one).
- Service-layer authorization for object-level access: e.g. `ExpenseRequestService.isVisible(request, caller)` implements requester/department/branch/installation-scope visibility and is invoked by both request and document services.
- Secrets: AES-256-GCM via dedicated crypto components (`MailCredentialCrypto`, `IntegrationSecretCrypto`); master key from environment variables; fail-closed if missing; ciphertext never logged or returned.
- Sensitive data redaction: `AuditValueRedactor`, `DiagnosticRedactor`, and SMTP/integration diagnostic paths redact credentials before persisting or logging.
- SSRF: `SafeDestination.requireSafeHttps` validates endpoints at config time (blocks loopback/private/link-local/IMDS); Phase 4H ships no outbound HTTP adapter, but every future adapter must route URL config through this guard.
- Error responses: `GlobalExceptionHandler` translates exceptions into a uniform `ApiResponse` envelope (correlation id, error code, message, no stack traces, no SQL state, no secrets).
- CORS: exact-origin allowlist driven by `edam.cors.allowed-origins`, credentials allowed only with exact match.

## Transactional e-mail / notifications

- `OutboxProcessor` (see `communication.service.OutboxProcessor`) uses the same `REQUIRES_NEW` per-row claim-and-deliver pattern as the integration worker:
  - Business transaction writes one or more `notification_outbox` rows in the same TX that mutates business state; commit = guaranteed at-least-once delivery.
  - Sweep loop claims one batch (`claimBatch`, REQUIRES_NEW), then delivers each row in its own `REQUIRES_NEW` transaction; a single SMTP failure cannot roll back business work or poison siblings.

## Configurable workflow engine

- Definitions (states, transitions, steps, quorum) are versioned. A published version is immutable; publishing creates a new version rather than mutating an in-flight one.
- When a request is submitted, the currently-published workflow for its request type is pinned to the resulting instance; resubmission after a revert reuses the same version.
- The engine validates that state transitions are declared before applying them; rejects close all open tasks; approval requires quorum per step; reverts move the instance back to its initial state; forwards replace the caller's task with a new task for the recipient.
- `@Version` on `WorkflowTask` prevents two actors from approving the same task concurrently.

## Business-rule engine

- Declarative JSON-AST evaluator (`com.edam.businessrules.service.RuleEngine`). No MVEL/SpEL/`ScriptEngine`/reflection.
- Closed operator set, scalar-only values, fixed field/action whitelists per contributor, depth and leaf-count limits, validation both at publish and at evaluation. See security verification in the Phase 4I final report.

## Testing infrastructure

- `AbstractPostgreSqlIntegrationTest` uses a single PostgreSQL container per build (shared across all integration tests).
- Test fixtures in `com.edam.testsupport.RequestFixtures` create users with the full `EdamPrincipal` signature (including employeeCode, displayName, permissions, data scopes).
- Architecture tests in `src/test/java/com/edam/architecture/` enforce: every endpoint has `@EdamPermission/@PublicEndpoint/@AuthenticatedEndpoint`, every permission is declared in the `Permissions` catalogue, layering (no controller→repository, no service→controller), etc.
- CI runs three jobs: `build-and-test`, `war-package-smoke-test` (executable WAR boots, `/actuator/health` UP, health does not leak DB/host info), `prod-profile-smoke-test` (same with prod profile + env credentials).
