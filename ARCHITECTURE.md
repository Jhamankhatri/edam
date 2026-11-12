# Architecture — EDAM Platform Foundation

This document defines the architectural decisions for **Phase 1 (Project Foundation)** and explicitly separates **confirmed BRD requirements**, **Phase 1 technical decisions**, **target proposals**, and **open BRD decisions**.

---

## 1. Architectural Direction & BRD Traceability

| Classification | Principle / Requirement | Source Basis |
| --- | --- | --- |
| **Confirmed BRD** | Replace legacy ASP.NET Web Forms + SQL Server stored procedures (`EDM_V3`) with Java domain services and PostgreSQL persistence (`OBJ-09`, Sec 15.2). | BRD Sec 1, 15.1–15.2 |
| **Confirmed BRD** | Support multi-institution configuration and branding without code rebuilds (`OBJ-08`, `AC-12`, `AC-13`, Sec 15.3–15.4). | BRD Sec 3, 15.3, 17 |
| **Phase 1 Decision** | Modular monolith deployed as an **executable WAR** (`SpringBootServletInitializer` + Embedded Tomcat + Jasper JSP). | Technical / Stack Mandate |
| **Target Proposal** | Enforce module boundaries at compile time via package-private JPA repositories inside `com.edam.<module>.internal`. | Architecture Quality Control |
| **Open Decision** | Final multi-tenancy isolation model (`OD-20`), auth/SSO provider (`OD-11`), role matrix (`OD-12`), and master-data source (`OD-16`). | BRD Sec 18 (`OD-01`–`OD-22`) |

---

## 2. Modular Monolith Design & Module Boundaries

EDAM avoids microservices complexity in Phase 1 while preventing a tightly coupled "big ball of mud" through strict business-capability module boundaries:

### Phase 1 Implemented Modules

1. **`com.edam.common`** — Cross-cutting kernel:
   - `api`: `ApiResponse<T>`, `ErrorDetail`, `FieldValidationError`.
   - `audit`: `AuditableEntity`, `JpaAuditingConfig`, `SpringSecurityAuditorAware`, `AuditEventCommand`, `AuditEventPublisher`.
   - `exception`: `ErrorCode`, `EdamException` hierarchy, `GlobalRestExceptionHandler`, `GlobalMvcExceptionHandler`.
   - `health`: `EdamDatabaseHealthIndicator`, `HealthCheckController`.
   - `logging`: `CorrelationIdProvider`, `CorrelationIdFilter`, `LogSanitizer`.
   - `security`: `InstitutionContext`, `InstitutionContextHolder`, `CurrentUserContext`, `EdamUserPrincipal`, `InstitutionAuthorizationChecker` (`@edamAuth`), `PermissionConstants`, `EdamAuthenticationEntryPoint`, `EdamAccessDeniedHandler`.
   - `validation`: `@ValidHexColor` / `HexColorValidator`.
2. **`com.edam.config`** — Spring Boot infrastructure wiring (`EdamProperties`, `SecurityConfig`, `InstitutionContextFilter`, `WebMvcConfig`).
3. **`com.edam.institution`** — Multi-institution profile, theme/branding (`Institution`, `InstitutionBranding`, `InstitutionSetting`), public API (`InstitutionBrandingService`, `InstitutionBrandingView`), theme CSS controller (`/assets/theme/institution-theme.css`), and context REST endpoint (`/api/v1/institutions/current`).
4. **`com.edam.identity`** — Authentication identity and multi-institution membership/authorization (`UserAccount`, `UserInstitutionMembership`, `Role`, `Permission`, `EdamUserDetailsService`).
5. **`com.edam.audit`** — Append-only `audit_events` persistence implementing `common.audit.AuditEventPublisher` (`DatabaseAuditService`).
6. **`com.edam.web`** — Shared MVC layout attributes (`GlobalModelAttributesAdvice`), sample foundation dashboard (`FoundationViewController`), and container error handler (`CustomErrorController`).

### Boundary Enforcement Rules
- **No circular module dependencies**: `common` depends on no business module; `audit` depends only on `common`; `institution` and `identity` depend on `common` and `config`; `web` depends on `common` and public `*.api` interfaces.
- **No cross-module internal repository access**: JPA repositories in `institution.internal`, `identity.internal`, and `audit.internal` are **package-private**. Other modules interact exclusively through public service interfaces (`InstitutionBrandingService`, `AuditEventPublisher`, `UserDetailsService`).
- **Automated enforcement**: `ArchitectureBoundariesTest` verifies constructor injection (no field `@Autowired`), no `@Entity` types in controller signatures, and no cross-module `*.internal.*` imports.

---

## 3. Multi-Institution Tenant Isolation Strategy (`OD-20` Evaluation)

Because BRD `OD-20` leaves final tenant isolation open for client/security sign-off, Phase 1 implements an explicit evaluation and prepares the domain model cleanly without locking in unapproved assumptions.

| Option | Isolation Mechanism | Security & Compliance | Operational & Migration Impact | Recommendation / Phase 1 Position |
| --- | --- | --- | --- | --- |
| **A: Shared Schema + `institution_id` (Discriminator)** | Every tenant-owned table includes `institution_id` (`NOT NULL FK`) enforced by service/repository scoping (plus optional PostgreSQL RLS). | Requires disciplined query scoping and automated cross-tenant tests (`AC-12`). Highest blast radius if a query omits tenant filtering. | Single Flyway pipeline, lowest infrastructure overhead, simplest connection pooling and cross-tenant platform administration. | **Implemented in Phase 1 foundation tables** (`institutions`, `institution_branding`, `institution_settings`, `user_institution_memberships`, `audit_events`) so multi-institution identity and branding work immediately. |
| **B: Schema-per-Tenant** | Shared PostgreSQL cluster; each financial institution receives a dedicated PostgreSQL schema (`tenant_abc.*`), selected per request. | Strong logical boundary between institutions; easier per-client export/purge and regulatory audit proof. | Flyway must run per tenant schema during onboarding and upgrades; Hibernate `MultiTenantConnectionProvider` + schema routing required; higher metadata/pool overhead. | **Target Proposal for regulated banking deployments** if client IT/InfoSec mandates physical schema separation under `OD-20`. |
| **C: Database-per-Tenant** | Dedicated PostgreSQL database (or dedicated app instance) per institution. | Maximum physical isolation, per-bank encryption keys, independent backup/restore (`NFR-09`). | Highest cost and DevOps complexity; per-tenant Flyway orchestration and dynamic `DataSource` routing needed if served from one app tier. | Appropriate when banks mandate dedicated hosting or separate database instances. |

**Phase 1 Safeguard:** `InstitutionContextFilter` binds a validated `InstitutionContext(institutionId, institutionCode, ...)` strictly from authenticated memberships in `EdamUserPrincipal` (never trusting an arbitrary header without verifying active membership), while `InstitutionAuthorizationChecker` evaluates permissions strictly within the active institution.

---

## 4. Technical Risks & Mitigations

1. **Executable WAR + JSP Packaging Risk**:
   - *Risk*: Spring Boot executable JARs cannot serve JSP views from `META-INF/resources` consistently under Embedded Tomcat; mis-scoped `tomcat-embed-jasper` breaks JSP compilation at runtime or duplicates servlet containers in external Tomcat.
   - *Mitigation*: Explicit `<packaging>war</packaging>`, `<scope>provided</scope>` on `spring-boot-starter-tomcat` and `tomcat-embed-jasper`, JSTL 3.0 in compile scope, `SpringBootServletInitializer` subclass, and `EdamFoundationPostgresIT` verifying real HTTP `/login` Jasper compilation.
2. **Spring Security 7 + JSP Forward/Error Dispatch**:
   - *Risk*: Denying `DispatcherType.FORWARD` or `DispatcherType.ERROR` breaks internal JSP forwarding (`/WEB-INF/jsp/...`) and custom error pages.
   - *Mitigation*: `SecurityConfig` explicitly permits `DispatcherType.FORWARD` and `DispatcherType.ERROR`.
3. **Database Outage Cascade on Error JSPs**:
   - *Risk*: If PostgreSQL is unavailable and `/error` triggers `GlobalModelAttributesAdvice` -> `InstitutionBrandingService` DB lookup, rendering the error JSP could throw a second `DataAccessException`.
   - *Mitigation*: `GlobalModelAttributesAdvice` catches `DataAccessException` and delegates to `InstitutionBrandingService.getFallbackBranding()`, rendering externalized `edam.default-institution.*` defaults safely.
4. **Filter Double-Execution**:
   - *Risk*: Registering `InstitutionContextFilter` as a `@Component` would register it both in the servlet filter chain and Spring Security's filter chain.
   - *Mitigation*: `InstitutionContextFilter` is instantiated inside `SecurityConfig` via `addFilterAfter(..., AnonymousAuthenticationFilter.class)`.
5. **Spring Boot 4.1 / Hibernate 7 / Jackson 3 API Changes**:
   - *Risk*: Using legacy Spring Boot 3 package names (`org.springframework.boot.autoconfigure.*` moved test annotations, `com.fasterxml.jackson.databind.ObjectMapper`, `HttpStatus.UNPROCESSABLE_ENTITY`, or `Hibernate` implicit Jackson 2 JSON mapping).
   - *Mitigation*: Uses Spring Boot 4.1.2 `tools.jackson.databind.json.JsonMapper`, `HttpStatus.UNPROCESSABLE_CONTENT`, moved Boot 4 packages, and `TEXT` + `TIMESTAMPTZ` columns without Hibernate JSON format mapper coupling.

---

## 5. Unresolved BRD Decisions Blocking Phase 2+ Business Implementation

Phase 1 intentionally avoids inventing rules for these open BRD items (`OD-01`–`OD-22`):
- **`OD-20` (Tenant Isolation)**: Final selection between shared `institution_id` (+ optional PostgreSQL RLS), schema-per-tenant, or DB-per-tenant.
- **`OD-11` (Authentication / SSO)**: Choice of local credentials vs Active Directory / OIDC / SAML2 client adapter.
- **`OD-12` & `OD-13` (RBAC & Data Scope)**: Verified role-to-permission matrix, department visibility rules, and CAPEX finance approver role.
- **`OD-01`–`OD-06`, `OD-09`, `OD-10` (Workflow & Routing)**: EC level mapping, standalone CEO/CFO/HOD mechanics, exceeding-limit behavior, status state machine, and selective revert conditions.
- **`OD-07`, `OD-08`, `OD-21` (Calculations & Defaults)**: Currency PKR conversion rounding rules, CAPEX variance formulas, and externalized replacement for legacy hardcoded PKR 20,000 fallback / CAPEX departments.
- **`OD-14`–`OD-16`, `OD-19` (Storage, Notifications, Master Data, NFRs)**: Attachment storage & retention, SMTP/outbox templates, master-data ownership (`ApplicationPanel` sync vs local), and numeric SLA/RPO/RTO targets.
