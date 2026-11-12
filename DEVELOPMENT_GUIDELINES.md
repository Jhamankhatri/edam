# Development Guidelines — EDAM Engineering Standards

## 1. Core Coding Rules

1. **Constructor Injection Only**: Never use field `@Autowired` injection. Keep dependencies explicit and `final`.
2. **DTOs at All Boundaries**: Never expose JPA `@Entity` classes in controller parameters, controller return types, or cross-module `api` interfaces.
3. **Strict Exception Discipline**:
   - Use specific domain exceptions extending `EdamException` (`BusinessException`, `ResourceNotFoundException`, `ConflictException`, `AccessDeniedDomainException`).
   - Do not swallow exceptions or write broad `catch (Exception e)` blocks in services; let `GlobalRestExceptionHandler` / `GlobalMvcExceptionHandler` map failures cleanly.
4. **Externalized Configuration**: Never hardcode institution names, logos, colors, currencies, approval thresholds, department lists, or banking role names in Java or JSP code. Bind configurable properties via `@ConfigurationProperties` (`EdamProperties`) or institution tables (`institution_branding`, `institution_settings`).
5. **No Cross-Module Internal Access**: Repositories and internal service helpers stay package-private inside `com.edam.<module>.internal`.

---

## 2. JSP & Frontend Standards

- **Zero Business Logic in JSPs**: Never use `<% ... %>` Java scriptlets. Prepare view models in controllers/services and use JSTL (`jakarta.tags.core`) tags only.
- **No Inline CSS or JS**: All styling goes in `src/main/resources/static/assets/css/edam.css` (consuming institution CSS variables from `/assets/theme/institution-theme.css`); all behavior goes in `src/main/resources/static/assets/js/edam.js`.
- **XSS & Context-Path Safety**: Always escape dynamic values with `<c:out value="..." />` and wrap URLs with `<c:url value="..." />`.
- **CSRF Compliance**: Every HTML `<form method="post">` must include the `_csrf` token parameter.

---

## 3. API Contract Standards

All REST endpoints return `ApiResponse<T>`:
```json
{
  "success": true,
  "status": 200,
  "correlationId": "a1b2c3d4-...",
  "message": "OK",
  "timestamp": "2026-11-12T10:15:30Z",
  "data": { ... },
  "error": null
}
```
Validation errors return `400 Bad Request` (`EDAM-VAL-400`) with structured `error.fieldErrors`. Business rule violations return `422 Unprocessable Content` (`EDAM-BUS-422`).

---

## 4. Testing Standards

Every new module or endpoint must include automated tests covering:
- Pure unit tests for domain rules and services (`*Test.java`).
- Web slice tests (`@WebMvcTest`) verifying validation, standardized error envelopes, CSRF, and authorization (`401`/`403`/institution boundary).
- Architecture checks (`ArchitectureBoundariesTest`) verifying constructor injection, entity encapsulation, and module isolation.
- PostgreSQL + Flyway + Embedded Tomcat JSP integration tests (`*IT.java` via Maven Failsafe + Testcontainers).

---

## 5. Pre-PR Verification Checklist

- [ ] `pom.xml` remains `<packaging>war</packaging>` with `SpringBootServletInitializer`.
- [ ] No hardcoded secrets, credentials, passwords, or institution constants in source or YAML.
- [ ] No `System.out.println`, broad `catch (Exception)`, or sensitive-data logging.
- [ ] New Flyway migrations follow sequential `V<n>__*.sql` naming and `ddl-auto: validate` passes.
- [ ] `mvn clean verify` passes cleanly against PostgreSQL, and executable WAR renders JSP views without errors.
- [ ] Documentation remains accurate and flags open BRD decisions (`OD-*`) instead of inventing business rules.
