# EDAM Architecture

> Status: Phase 1 (platform foundation). This document describes what exists **now**, the decisions that
> shaped it, and the seams left open for later phases. It deliberately does not describe OPEX, CAPEX or
> workflow behaviour that has not been approved.

## 1. Architectural drivers

| Driver | Source | Consequence |
|---|---|---|
| Product for **multiple financial institutions** | BRD §15.3, OBJ-08 | Institution identity/branding/configuration is data, never code. Tenant context is a first-class seam. |
| **Business rules in Java, not stored procedures** | BRD §2.1, OBJ-09 | PostgreSQL provides integrity, constraints, indexes and transactions only. No procedural SQL. |
| **Configurable delegation, limits, approvals** | BRD §10, FM-07/FM-08/FM-10/FM-13 | Thresholds and routes will be configuration rows; the foundation already externalises the legacy hard-coded values. |
| **Auditability** | BRD §12.4, AC-10 | Actor + timestamp + correlation ID on every change; append-only audit history later. |
| **Configurable, not forked, per client** | BRD §15.2 | Strategy/adapter seams (identity, institution theme, notification) instead of `if (client == ...)`. |
| **Maintainability** | BRD §14 NFR-07 | Modular monolith, package-by-capability, no speculative abstraction. |
| **Banking-grade security** | BRD §13 | Server-side authorization only, no internal detail leaked, no sensitive logging. |

## 2. System shape

```
                       ┌──────────────────────────────────────────────┐
   Browser  ─────────► │  Embedded Tomcat (executable WAR)            │
   (JSP UI +          │                                              │
    JSON API)         │  Filter chain                                │
                       │   1. CorrelationIdFilter      (MDC, header)  │
                       │   2. RequestLoggingFilter     (1 line/req)   │
                       │   3. Spring Security chain    (REQUEST only) │
                       │   4. errorDispatchSecurityContextFilter      │
                       │        (ERROR dispatch only)                 │
                       │                                              │
                       │  DispatcherServlet                           │
                       │   ├── MVC controllers ──► application svc    │
                       │   ├── View controllers ──► JSP (WEB-INF/jsp) │
                       │   └── @RestControllerAdvice / @ControllerAdvice│
                       │                                              │
                       │  Module services ──► Spring Data repositories│
                       └───────────────────┬──────────────────────────┘
                                           │ JDBC (Hikari)
                                    ┌──────▼───────┐
                                    │  PostgreSQL   │  Flyway-managed schema
                                    └──────────────┘
```

Deployment shape is a **modular monolith**: one WAR, one database, one release unit. Modules are separated
by package boundaries and a dependency rule, not by network hops. This is the right trade-off for a
product whose modules share a transactional consistency requirement (a request, its child rows, its route
and its audit event must commit together) and whose operational team is small.

## 3. Request lifecycle

1. **Correlation** — `CorrelationIdFilter` (highest precedence) accepts an inbound `X-Correlation-Id` only
   if it is a well-formed UUID, otherwise generates one; binds it to the SLF4J MDC and echoes it on the
   response. Everything logged afterwards, including framework logs, carries it.
2. **Access log** — `RequestLoggingFilter` emits exactly one line per request with method, path (no query
   string), status and duration. Static resources and health probes are excluded.
3. **Authentication/authorization** — the Spring Security chain runs for `REQUEST` dispatches. Public
   surface: `/login`, `/access-denied`, `/error`, static assets, `/actuator/health*`, `/actuator/info`.
   Everything else requires authentication. Method-level rules use `@PreAuthorize` with `PERM_*`
   authorities.
4. **Dispatch** — controllers return either a view name (JSP) or an `ApiResponse<T>` envelope.
5. **Rendering** — JSPs live under `WEB-INF/jsp` (unreachable by direct URL) and are built from layout
   fragments. They receive a `ModelMap` assembled by controllers plus the global `InstitutionThemeAdvice`
   attributes (`institutionTheme`, `correlationId`).
6. **Failure** — API requests are answered by `GlobalExceptionHandler` (`@RestControllerAdvice`); HTML
   failures reach the container's error dispatch and are rendered by `MvcExceptionHandler`. No stack trace,
   SQL, class name or infrastructure detail ever reaches a client; the correlation ID is the contract for
   support.

## 4. Module boundaries

| Module | Responsibility (now) | Responsibility (later phases) |
|---|---|---|
| `common` | Response envelope, error codes, exceptions, logging/correlation, security principal & institution context, audit superclass, API/HTML request discrimination | shared value objects, money, paging |
| `config` | Spring wiring: security, MVC, JPA auditing, servlet filters | scheduling, async, integration adapters |
| `institution` | Institution (tenant) entity, properties, read service, theme model | branding assets, per-tenant settings, feature flags |
| `identity` | Authentication seam (`EdamUserDetailsService`), development fixtures | SSO/OIDC/SAML adapter, local credential store |
| `organization` | — | departments, branches, employees, vendors |
| `currency` | — | currency master and PKR rates (FM-09) |
| `expense.opex` / `expense.capex` | — | request forms, registers, validation (FM-01..FM-06) |
| `workflow` | — | route evaluation, steps, actions, quorum (BRD §10) |
| `delegation` | — | delegation matrix (FM-07), department-head limit (FM-10), recommending authority (FM-11) |
| `committee` | — | EC member pool and levels (FM-08) |
| `reporting` | — | operational/management reports, exports (FM-12) |
| `notification` | — | email templates, outbox, delivery history |
| `audit` | — | queryable audit event store |

**Dependency rule:** modules depend on `common`; peer-to-peer dependencies must go through a published
service or read model (`*Summary`, `*Service`), never another module's repository or entity. Example:
`web` reads institutions through `InstitutionService` → `InstitutionSummary`, not through
`InstitutionRepository`. This is what makes it possible to move a module to its own schema or service later
without rewriting callers. See [PACKAGE_STRUCTURE.md](PACKAGE_STRUCTURE.md).

## 5. Key decisions

Full rationale and consequences are in [adr/](adr/). Summary:

| ADR | Decision |
|---|---|
| [0001](adr/0001-modular-monolith.md) | Modular monolith, package-by-capability |
| [0002](adr/0002-tenant-isolation-deferred.md) | Tenant isolation model **not yet chosen**; a context seam is provided and no tenant-owned tables exist |
| [0003](adr/0003-executable-war-for-jsp.md) | Executable WAR; embedded container and Jasper are `provided` |
| [0004](adr/0004-authentication-seam.md) | No authentication protocol invented; a `UserDetailsService` seam plus fixtures |
| [0005](adr/0005-error-contract.md) | Stable `EDAM-*` codes, envelope for API, MVC error page for HTML, no internal detail |
| [0006](adr/0006-configuration-not-constants.md) | Legacy hard-coded thresholds/departments become institution configuration |
| [0007](adr/0007-error-dispatch-not-authorized.md) | The error dispatch must never pass through authorization |

## 6. Security posture

* **Authentication** is a seam. The foundation ships form login and, in `local` only, in-memory fixtures.
  No SSO/AD/OIDC/SAML choice is made without an approved requirement (OD-11).
* **Authorization** is enforced server-side (`authorizeHttpRequests` + `@PreAuthorize`). Hiding a button in
  a JSP is never a control. A denied request is a `403` with the JSON envelope for API callers and the
  access-denied page for browsers.
* **Institution context** — `InstitutionContext` resolves the acting tenant for the request. A user with one
  membership resolves implicitly; a user with several must select one and the system **fails closed**
  rather than guessing. Membership is a `Set`, never a single assumed tenant.
* **CSRF** is enabled for the session-based UI; only `/actuator/**` is exempt (read-only, no session).
* **Headers** — CSP (`default-src 'self'`, no inline scripts), `X-Frame-Options: DENY`, HSTS via the
  container, no-store on API errors.
* **Session** — cookie-only tracking, `HttpOnly`, `SameSite=Lax`, `Secure` in `prod`, 30-minute timeout,
  fixation protection by session migration.

## 7. Persistence

* **Flyway owns the schema.** Hibernate runs with `ddl-auto: validate` in every profile, so a mapping
  drift fails the build/deploy instead of silently altering a production database.
* **Audit columns** are mandatory on every table (`created_at/by`, `updated_at/by`) and populated by JPA
  auditing (`AuditorAware` → employee ID, `system` for background processes).
* **Optimistic locking** (`version`) on every mutable entity: two approvers acting on the same task must not
  both succeed (AC-06).
* **Money/rates** are `NUMERIC(19,2)` / `NUMERIC(19,6)`; never floating point (BRD §11.3).
* **Timestamp** columns are `TIMESTAMPTZ` stored in UTC; rendering in the institution's zone is a
  presentation concern.
* **No stored procedures.** Business logic stays in Java; the database supplies constraints, indexes,
  transactions and referential integrity.

## 8. UI

* JSP/JSTL server-rendered views under `src/main/webapp/WEB-INF/jsp`, assembled from layout fragments
  (`_layout/head.jsp`, `theme.jsp`, `header.jsp`, `sidebar.jsp`, `topbar.jsp`, `footer.jsp`).
* No scriptlets, no business logic in views, `<c:out>` for every dynamic value, external CSS/JS only
  (CSP-compatible), skip-link and landmark structure for basic accessibility.
* Institution branding is injected as CSS custom properties from configuration/DB, so re-branding a tenant
  never requires a code change or redeploy (AC-13).
* A branded logo path is supported; the asset itself is client configuration.

## 9. Observability

* Structured, correlation-tagged logs (ECS JSON in `prod`, readable pattern elsewhere).
* `/actuator/health` with liveness/readiness probes and `show-details: never`.
* One access log line per request with duration, so latency can be derived without a tracing backend.
* `X-Correlation-Id` on every response; the same ID appears in the JSON error envelope and on the error
  page, which is what makes an incident traceable from a user's screenshot to the exact log lines.

## 10. Trade-offs and known gaps

| Trade-off | Why accepted | Revisit when |
|---|---|---|
| No tracing backend (no Micrometer Tracing/Zipkin) | Correlation IDs cover the single-service case; tracing adds infrastructure with no benefit yet | When an external integration (email, file storage, core banking) is added |
| Session-based authentication, no token/API auth | The UI is server-rendered; APIs are consumed by the same session today | When external systems call EDAM APIs (needs OAuth2/mTLS decision) |
| `InstitutionThemeAdvice` is global (not package-scoped) | Package scoping silently excludes view controllers — the login page renders blank (see ADR-0005) | Not expected; the cost is one skipped lookup on API requests |
| Branding lookup per HTML request | Correctness over micro-optimisation; a cache needs invalidation semantics | If profiling shows it matters; then add a tenant-branding cache |
| No tenant filter on JPA yet | No tenant-owned tables exist; a half-implemented discriminator is worse than none | Immediately with the first tenant-owned table (see ADR-0002) |
