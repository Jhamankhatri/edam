# Package Structure — EDAM Modular Monolith

## 1. Current Phase 1 Package Layout

```text
src/main/java/com/edam/
├── EdamApplication.java                         # Boot + SpringBootServletInitializer (Executable WAR)
│
├── common/                                      # Cross-cutting technical kernel (zero business-module deps)
│   ├── api/                                     # ApiResponse<T>, ErrorDetail, FieldValidationError
│   ├── audit/                                   # AuditableEntity, JpaAuditingConfig, SpringSecurityAuditorAware, AuditEventPublisher, AuditEventCommand
│   ├── exception/                               # ErrorCode, EdamException hierarchy, GlobalRestExceptionHandler, GlobalMvcExceptionHandler
│   ├── health/                                  # EdamDatabaseHealthIndicator, HealthCheckController, HealthSummaryResponse
│   ├── logging/                                 # CorrelationIdProvider, CorrelationIdFilter, LogSanitizer
│   ├── security/                                # InstitutionContext, InstitutionContextHolder, CurrentUserContext, EdamUserPrincipal,
│   │                                            # InstitutionAuthorizationChecker (@edamAuth), PermissionConstants,
│   │                                            # EdamAuthenticationEntryPoint, EdamAccessDeniedHandler, SecurityRequestSupport
│   └── validation/                              # ValidHexColor, HexColorValidator
│
├── config/                                      # Infrastructure & Spring Security wiring
│   ├── EdamProperties.java                      # Validated externalized config (edam.*)
│   ├── InstitutionContextFilter.java            # Authenticated institution context resolution & validation
│   ├── SecurityConfig.java                      # Spring Security 7 filter chain, headers, CSRF, BCrypt
│   └── WebMvcConfig.java                        # Static asset resource handler caching
│
├── institution/                                 # Multi-institution profile, branding & configurable settings
│   ├── api/                                     # Public contract: InstitutionBrandingService, InstitutionBrandingView
│   ├── domain/                                  # Institution, InstitutionBranding, InstitutionSetting, InstitutionStatus, ThemeMode
│   ├── internal/                                # Package-private JPA repositories + InstitutionConfigServiceImpl
│   └── web/                                     # InstitutionThemeController (/assets/theme/institution-theme.css),
│                                                # InstitutionContextRestController (/api/v1/institutions/current)
│
├── identity/                                    # Multi-institution identity, roles & permission assignments
│   ├── domain/                                  # UserAccount, UserInstitutionMembership, Role, Permission, UserStatus, MembershipStatus
│   └── internal/                                # Package-private repositories + EdamUserDetailsService
│
├── audit/                                       # Append-only audit event persistence
│   ├── domain/                                  # AuditEvent
│   └── internal/                                # Package-private AuditEventRepository + DatabaseAuditService
│
├── web/                                         # Presentation controllers & shared JSP model attributes
│   ├── CustomErrorController.java               # Safe HTML + JSON /error handling
│   ├── FoundationViewController.java            # /, /login, /access-denied
│   └── GlobalModelAttributesAdvice.java         # Populates branding, correlationId, activeUsername, activeInstitutionCode
│
└── [Phase 2–5 Boundary Placeholders — package-info.java only]
    ├── organization/                            # Departments, branches, vendors, master-data adapters (OD-16)
    ├── currency/                                # Currencies and conversion rates (FM-09, OD-07)
    ├── delegation/                              # Categories, sub-categories, EDM, Dept Head & exceeding limits (FM-07, FM-10, FM-11, FM-13)
    ├── committee/                               # EC member pool, approval levels, quorum rules (FM-08)
    ├── workflow/                                # Approval state machine, routing engine, revert/resubmit (OD-01..06, OD-09, OD-10)
    ├── expense/
    │   ├── opex/                                # OPEX requests, register, approval (FM-01..03)
    │   └── capex/                               # CAPEX requests, asset allocations, budget details (FM-04..06, OD-08)
    ├── reporting/                               # Registers, analytics, exports, print/PDF (FM-12, OD-22)
    └── notification/                            # Email templates, outbox & delivery retry (OD-15)
```

---

## 2. Web, Resource & Migration Layout

```text
src/main/
├── resources/
│   ├── application.yml                          # Shared defaults (ddl-auto: validate, open-in-view: false, JSP prefix/suffix)
│   ├── application-dev.yml                      # Local developer profile
│   ├── application-test.yml                     # Integration test profile
│   ├── application-uat.yml                      # UAT profile (secure session cookie, ECS logs)
│   ├── application-prod.yml                     # Production profile (secure session cookie, ECS logs, WARN root)
│   ├── logback-spring.xml                       # Profile-aware MDC console + ECS structured JSON logging
│   ├── db/migration/
│   │   ├── V1__foundation_schema.sql            # 10 foundation PostgreSQL tables, FKs, partial unique indexes
│   │   └── V2__platform_permissions.sql         # Neutral platform capability permission codes
│   └── static/assets/
│       ├── css/edam.css                         # Base platform styles consuming institution CSS variables
│       ├── js/edam.js                           # Unobtrusive DOM event handlers (no inline JS in JSP)
│       └── images/edam-logo.svg                 # Default fallback vector logo
└── webapp/WEB-INF/jsp/
    ├── layout/
    │   ├── header.jspf                          # Shared HTML head, theme CSS, header bar, sign-out form
    │   ├── navbar.jspf                          # Page title bar & sidebar toggle
    │   ├── sidebar.jspf                         # Primary navigation with planned Phase 2–5 indicators
    │   └── footer.jspf                          # Shared footer with branding text & correlation ID
    ├── auth/login.jsp                           # Form login view with CSRF token
    ├── foundation/dashboard.jsp                 # Phase 1 operational verification overview
    └── error/
        ├── error.jsp                            # Safe generic error view
        └── access-denied.jsp                    # Safe 403 view
```

---

## 3. Module Encapsulation Convention

Each business module follows a four-subpackage convention as features expand in Phases 2–5:
- `api`: Public DTOs, commands, and service interfaces exposed to `web` or other approved modules.
- `domain`: JPA entities, value objects, enums, and pure domain rules.
- `internal`: Package-private Spring Data JPA repositories and `@Service` implementations (never imported by other modules).
- `web`: Module-specific MVC / REST controllers consuming only DTOs and `api` services (never `@Entity` objects).
