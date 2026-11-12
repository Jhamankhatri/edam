# EDAM

**Expense Delegation and Approval Management** — a configurable expense request, delegation and approval
platform for Head Office OPEX and CAPEX, built as a multi-institution enterprise product for banks and
financial institutions.

This repository currently contains **Phase 1: the platform foundation only**. No OPEX, CAPEX, delegation
matrix, Expense Committee, workflow or reporting business logic is implemented yet; those arrive in later
phases once the corresponding BRD open decisions are signed off.

| | |
|---|---|
| **Stack** | Java 21 · Spring Boot 4.1.2 · Spring Framework 7 · Spring Security 7 · Spring Data JPA / Hibernate · PostgreSQL · Flyway · JSP / JSTL (Jasper) · SLF4J / Logback |
| **Packaging** | Executable **WAR** (JSP cannot be rendered from an executable JAR) |
| **Build** | Maven |
| **Status** | Phase 1 foundation. Not production-ready: authentication, tenant isolation and several business rules are unresolved BRD decisions. |

---

## 1. Quick start

### Prerequisites

* JDK 21
* Maven 3.9+
* PostgreSQL 16+ (the CI pipeline uses `postgres:18-alpine`)
* Docker (optional — only needed to run the Testcontainers-backed integration tests locally)

### Run locally

```bash
# 1. Create the database and a role (once)
createdb edam
psql -c "CREATE ROLE edam LOGIN PASSWORD 'edam-local-only';"
psql -c "ALTER DATABASE edam OWNER TO edam;"

# 2. Build and run the executable WAR
mvn clean package
java -jar target/edam.war --spring.profiles.active=local
```

Open <http://localhost:8080>. Flyway creates the schema on first start and seeds one demonstration
institution (`demo-bank`).

The `local` profile enables **development fixture identities** (`edam.security.dev-users.enabled=true`):

| Username | Password | Institutions | Purpose |
|---|---|---|---|
| `admin` | `admin-change-me` | `demo-bank`, `albaraka` | Proves a user is never assumed to belong to one institution |
| `approver` | `approver-change-me` | `albaraka` | Single-institution approver |
| `requester` | `requester-change-me` | `albaraka` | Single-institution requester |

> These credentials are fixtures, not secrets. They exist only under the `local` profile and must never
> be copied into a deployed environment. See [SECURITY_GUIDELINES.md](docs/SECURITY_GUIDELINES.md).

### Configuration

Everything environment-specific is injected; no credentials are committed.

| Variable | Purpose | Default (`local`) |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Environment profile: `local`, `test`, `prod` | `local` |
| `EDAM_DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/edam` |
| `EDAM_DB_USERNAME` | Database user | `edam` |
| `EDAM_DB_PASSWORD` | Database password | *(local only)* |
| `EDAM_DB_POOL_SIZE` | Hikari pool size (`prod`) | `20` |
| `EDAM_DEV_USERS_ENABLED` | Enable fixture identities | `true` in `local` only |
| `EDAM_INSTITUTION_*` | Institution profile overrides (Spring relaxed binding) | see `application.yml` |

The `prod` profile has **no default database credentials**: a misconfigured deployment fails at startup
instead of silently connecting to a developer database.

### Build and test

```bash
mvn clean verify     # compile, unit + integration tests, WAR packaging, packaging-contract test
mvn clean package    # package only
```

Integration tests require Docker because they run against **real PostgreSQL** via Testcontainers — the
Flyway migrations, partial indexes, `CHECK` constraints and `TIMESTAMPTZ` columns are dialect-specific and
an H2 approximation would hide exactly the defects this foundation must catch.

| Endpoint | Purpose |
|---|---|
| `GET /` | Redirects to the dashboard |
| `GET /login` | Server-rendered sign-in page (institution branding from configuration/DB) |
| `GET /dashboard` | Authenticated landing page; shows the session's institution context |
| `GET /actuator/health` | Liveness/readiness (no component detail exposed) |
| `GET /actuator/health/liveness`, `/readiness` | Probes for orchestration |
| `GET /api/v1/system/info` | Versioned application metadata, standard envelope, authentication required |
| `GET /api/v1/dev/me` | Development-only identity inspection (`local` profile only) |

---

## 2. What exists in this phase

| Area | Implemented | Notes |
|---|---|---|
| Modular monolith package structure | ✅ | Capability modules under `com.edam`; boundaries documented in [PACKAGE_STRUCTURE.md](docs/PACKAGE_STRUCTURE.md) |
| Spring Boot configuration, profiles | ✅ | `local`, `test`, `prod`; fail-fast posture |
| Centralized logging + correlation IDs | ✅ | MDC correlation ID on every line; access log with method/path/status/duration |
| Structured logging | ✅ | ECS JSON under the `prod` profile (encoder selected in `logback-spring.xml`); parsed and asserted in CI |
| Global exception handling (API) | ✅ | `@RestControllerAdvice`; stable `EDAM-*` codes; no internal detail |
| Global exception handling (HTML) | ✅ | MVC error controller rendered from the container error path |
| Standard application response envelope | ✅ | `ApiResponse<T>` |
| Validation error reporting | ✅ | Field-level errors; rejected values never echoed |
| Spring Security foundation | ✅ | Form login shell, method security, CSRF, CSP, authentication seam |
| PostgreSQL + Hikari configuration | ✅ | Per-profile; fail-fast when unconfigured |
| Flyway migrations | ✅ | `V1` baseline, `V2` reference institution; `ddl-auto: validate` |
| JPA auditing foundation | ✅ | `AuditedEntity`/`AuditableEntity` with actor, timestamps, optimistic locking |
| JSP view layer | ✅ | Reusable layout fragments, external CSS/JS, no scriptlets, `<c:out>` escaping |
| Institution branding from configuration | ✅ | Theme tokens injected as CSS variables; DB override wins |
| Health checks | ✅ | Actuator + no-detail policy |
| Automated tests | ✅ | Unit, MVC-contract, packaging-contract, PostgreSQL integration |
| Documentation | ✅ | `docs/` (this is the entry point) |
| **OPEX / CAPEX / workflow / committee / reporting** | ❌ | Deliberately out of scope for Phase 1 |

---

## 3. Documentation

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | System shape, module boundaries, request lifecycle, decisions and trade-offs |
| [PACKAGE_STRUCTURE.md](docs/PACKAGE_STRUCTURE.md) | Package-by-capability layout and dependency rules |
| [DATABASE_GUIDELINES.md](docs/DATABASE_GUIDELINES.md) | Schema conventions, migration policy, auditing, indexing, tenant columns |
| [LOGGING_GUIDELINES.md](docs/LOGGING_GUIDELINES.md) | Levels, correlation IDs, what must never be logged, structured output |
| [SECURITY_GUIDELINES.md](docs/SECURITY_GUIDELINES.md) | Authentication/authorization model, institution context, unresolved decisions |
| [DEVELOPMENT_GUIDELINES.md](docs/DEVELOPMENT_GUIDELINES.md) | Coding standards, testing strategy, review checklist |
| [VERIFICATION.md](docs/VERIFICATION.md) | How the foundation was verified and how to reproduce it |
| [/adr](docs/adr/) | Architecture Decision Records (tenant isolation, JSP packaging, auth seam, …) |
| [OPEN_DECISIONS.md](docs/OPEN_DECISIONS.md) | BRD open decisions that block later phases |
| [docs/brd/EDM-BRD-001.txt](docs/brd/EDM-BRD-001.txt) | The Business Requirements Document this phase was scoped from (single source of business truth) |

---

## 4. Scope and compliance

The attached BRD is the source of truth. This repository distinguishes:

* **Confirmed BRD requirements** — implemented where they apply to the foundation (security, audit,
  configuration externalisation, no stored procedures, transactional integrity).
* **Technical implementation decisions** — recorded as ADRs with rationale and consequences.
* **Open business decisions** — listed in [OPEN_DECISIONS.md](docs/OPEN_DECISIONS.md) and *never* guessed at
  in code. Where a legacy behaviour is known but unverified (for example the PKR 20 000 Department Head
  fallback), it is modelled as configuration with the conservative default, not as a constant.

### Known limitations of this phase

* No SSO/LDAP/OIDC/SAML integration (OD-11). Authentication is a seam plus local fixtures.
* Tenant isolation is **designed but not enforced** — no tenant-owned business tables exist yet (OD-20).
* Branding supports name, short name, colours, locale and logo path; logo upload/asset management is not
  implemented.
* No email/notification delivery (OD-15).
* No Excel export, print/PDF, or reporting (Phase 5).
* `mvn verify` could not be executed in the development sandbox that produced this branch (no JDK, Maven
  or Docker available); verification ran in GitHub Actions. See [VERIFICATION.md](docs/VERIFICATION.md).

---

## 5. Contributing

Read [DEVELOPMENT_GUIDELINES.md](docs/DEVELOPMENT_GUIDELINES.md) before changing code. In short:

* Constructor injection only; no field injection.
* Business rules live in application/domain services, never in JSPs, controllers or the database.
* DTOs/records at the boundary; never expose JPA entities or repositories across module boundaries.
* Versioned Flyway migrations only; never `ddl-auto: update`.
* Every PR: `mvn clean verify` green, documentation updated, no secrets, no sensitive logging.

Branch protection requires human review; the author does not merge their own pull request.
