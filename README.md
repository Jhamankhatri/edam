# EDAM — Expense Delegation and Approval Management

**Phase 1: Project Foundation** (`arena/8a165b11-edam` — Spring Boot 4.1.2 / Java 21 / Executable WAR + JSP / PostgreSQL + Flyway)

---

## 1. Purpose & Scope

EDAM is a configurable enterprise **OPEX / CAPEX expense request, delegation, and approval platform** replacing a legacy ASP.NET Web Forms + SQL Server stored-procedure implementation (`EDM_V3`).

**This PR implements Phase 1 (Platform Foundation) only.** Per the BRD delivery roadmap, complete business workflows (`FM-01` through `FM-13`) and domain transaction tables are intentionally deferred until open business decisions (`OD-01`–`OD-22`) receive sign-off.

### Implemented in Phase 1
- **Executable WAR Packaging (`pom.xml`, `EdamApplication`)**: Embedded Apache Tomcat + Jasper JSP engine + JSTL 3.0.
- **Modular Monolith Structure (`com.edam.*`)**: Explicit foundation modules (`common`, `config`, `institution`, `identity`, `audit`, `web`) plus boundary `package-info.java` placeholders for Phases 2–5 (`organization`, `currency`, `delegation`, `committee`, `workflow`, `expense.opex`, `expense.capex`, `reporting`, `notification`).
- **Centralized Logging (`logback-spring.xml`, `CorrelationIdFilter`, `LogSanitizer`)**: Correlation ID propagation (`X-Correlation-ID`), HTTP method/path/status/duration logging, sensitive-field redaction, CRLF log-injection protection, and ECS structured JSON logging for `uat`/`prod`.
- **Standardized API & MVC Exception Handling**: `ApiResponse<T>`, `ErrorDetail`, `FieldValidationError`, `GlobalRestExceptionHandler`, `GlobalMvcExceptionHandler`, and `CustomErrorController` with consistent `EDAM-*` error codes and zero stack-trace/SQL leakage.
- **Spring Security 7 Foundation (`SecurityConfig`, `InstitutionContextFilter`, `EdamUserDetailsService`, `@edamAuth`)**: Multi-institution user memberships, institution-scoped permission checks, BCrypt password hashing (`strength=12`), CSRF protection, CSP/frame/referrer headers, and safe JSON/HTML error dispatch.
- **Multi-Institution Branding & Configuration (`institution`)**: Non-hardcoded tenant profile, hex-validated theme colors (`--edam-primary`, `--edam-secondary`, `--edam-accent`) served dynamically at `/assets/theme/institution-theme.css`, and externalized fallback defaults (`edam.default-institution.*`).
- **PostgreSQL + Flyway + JPA Auditing**: `V1__foundation_schema.sql` (10 foundation tables), `V2__platform_permissions.sql` (neutral permission codes), `AuditableEntity`, `SpringSecurityAuditorAware`, and append-only `audit_events`.
- **Shared JSP Layout & External Assets**: `header.jspf`, `navbar.jspf`, `sidebar.jspf`, `footer.jspf`, `dashboard.jsp`, `login.jsp`, `error.jsp`, `access-denied.jsp`, external `edam.css` / `edam.js` / `edam-logo.svg`.
- **Health Check Endpoint & Automated Tests**: `GET /api/v1/health` + unit, `@WebMvcTest`, architecture boundary, and Testcontainers PostgreSQL integration tests.

---

## 2. Technology Stack

| Layer | Technology / Version | Notes |
| --- | --- | --- |
| Language & Build | Java 21, Maven | `maven-war-plugin` + `spring-boot-maven-plugin` |
| Packaging | **Executable WAR** | Required for Embedded Tomcat JSP compilation (`tomcat-embed-jasper` in `provided` scope) |
| Framework | Spring Boot `4.1.2` | Spring Framework `7.0.10`, Spring Security `7.1.2`, Jackson 3 (`tools.jackson.*`) |
| Persistence | PostgreSQL, Spring Data JPA / Hibernate `7.4`, Flyway `12.4` | `spring.jpa.hibernate.ddl-auto=validate`, `open-in-view=false` |
| Web UI | JSP / JSTL `3.0.2` (`jakarta.tags.core`) | External CSS/JS/SVG assets, zero scriptlets, `<c:out>` XSS escaping |
| Logging | SLF4J + Logback `1.5` | Console + MDC (`dev`/`test`), ECS JSON (`uat`/`prod`) |

---

## 3. Quick Start

### Prerequisites
- JDK 21+
- Maven 3.9+
- PostgreSQL 15+ (or Docker for `EdamFoundationPostgresIT` via Testcontainers)

### Environment Configuration
Configure PostgreSQL connection details via environment variables (never commit credentials):

```bash
export SPRING_PROFILES_ACTIVE=dev
export EDAM_DB_URL=jdbc:postgresql://localhost:5432/edam
export EDAM_DB_USERNAME=edam
export EDAM_DB_PASSWORD=<your-db-password>
```

### Build Executable WAR & Run Tests

```bash
# Run unit, slice, and architecture boundary tests
mvn clean test

# Run full build + PostgreSQL Testcontainers integration test (requires Docker)
mvn clean verify

# Run the packaged executable WAR directly (NOT a JAR)
java -jar target/edam-0.1.0-SNAPSHOT.war
```

---

## 4. Documentation Index

| Document | Focus |
| --- | --- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Modular monolith design, module boundaries, multi-institution isolation analysis (`OD-20`), JSP packaging risks, and BRD traceability |
| [`PACKAGE_STRUCTURE.md`](PACKAGE_STRUCTURE.md) | Package layout, encapsulation rules, and Phase 2–5 module placeholders |
| [`DATABASE_GUIDELINES.md`](DATABASE_GUIDELINES.md) | PostgreSQL conventions, Flyway workflow, JPA auditing, Hibernate 7 / Jackson 3 rules, and schema governance |
| [`LOGGING_GUIDELINES.md`](LOGGING_GUIDELINES.md) | Correlation IDs, MDC context, safe logging/redaction rules, and profile output formats |
| [`SECURITY_GUIDELINES.md`](SECURITY_GUIDELINES.md) | Spring Security 7 authentication/authorization model, multi-institution switching, headers, CSRF, and open security decisions |
| [`DEVELOPMENT_GUIDELINES.md`](DEVELOPMENT_GUIDELINES.md) | Coding conventions, DI/DTO rules, JSP standards, testing discipline, and PR checklist |
