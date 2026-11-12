# EDAM Package Structure

## Principle

Organise by **business capability**, not by technical layer. A global `controller`, `service` and
`repository` package per layer becomes an unmaintainable bucket in a product with 13 functional modules;
a module package keeps everything that changes together in one place and makes module boundaries visible
in imports.

## Current layout

```
com.edam
├── EdamApplication                     @SpringBootApplication + SpringBootServletInitializer
│
├── common                              Shared kernel — no business rules
│   ├── api
│   │   ├── ApiResponse                 Standard response envelope (success/data/error/fieldErrors/correlationId)
│   │   ├── ApiPaths                    API-vs-HTML request discrimination, safe path derivation
│   │   └── ErrorCode                   Stable EDAM-* catalogue (public contract)
│   ├── audit
│   │   └── AuditableEntity             @MappedSuperclass: id, audit stamps, optimistic-lock version
│   ├── exception
│   │   ├── EdamException               Base for typed application exceptions
│   │   ├── BusinessException           Business rule rejected the operation
│   │   ├── ResourceNotFoundException   Addressed resource does not exist / is not visible
│   │   ├── ValidationFailedException   Field-level validation failures
│   │   ├── ConcurrentModificationException  Optimistic locking clash
│   │   ├── GlobalExceptionHandler      @RestControllerAdvice for API clients
│   │   └── MvcExceptionHandler         @Controller for the container error dispatch (HTML)
│   ├── logging
│   │   ├── CorrelationId               Generate/sanitize/read the correlation ID
│   │   ├── CorrelationIdFilter         Establish MDC + response header (first filter)
│   │   └── RequestLoggingFilter        One structured access-log line per request
│   └── security
│       ├── EdamPrincipal               Authenticated user; institution membership as a Set
│       ├── EdamUserDetailsService      Authentication seam (no protocol assumed)
│       ├── InstitutionContext          Resolves the acting tenant; fails closed
│       ├── Permissions                 Permission name constants used by @PreAuthorize
│       ├── JsonApiAuthenticationEntryPoint  401 envelope for API callers
│       └── JsonApiAccessDeniedHandler       403 envelope for API callers
│
├── config                              Framework wiring only; no business logic
│   ├── SecurityConfig                  Filter chain, CSP, form login, authentication provider
│   ├── WebMvcConfig                    JSP view resolver, view controllers
│   ├── JpaAuditingConfig               AuditorAware (actor identity)
│   └── ServletFilterConfig             Error-dispatch security context restoration
│
├── institution                         Tenant identity, branding, institution-level switches
│   ├── Institution                     JPA entity (anchor of the multi-institution model)
│   ├── InstitutionProperties           @ConfigurationProperties for the deployment's institution
│   ├── InstitutionRepository           Package-visible persistence
│   ├── InstitutionService              Published read API (transactional boundary)
│   ├── InstitutionSummary              Cross-module read model (record)
│   └── InstitutionNotFoundException    Module-specific not-found exception
│
├── identity                            Authentication concerns
│   ├── DevUserDetails                  Development-only UserDetails wrapper
│   ├── InMemoryEdamUserDetailsService  Development fixture store (local profile only)
│   └── DevUserController               Development-only identity inspection endpoint
│
└── web                                 Server-rendered UI controllers and view models
    ├── HomeController                  /, /dashboard
    ├── SystemController                /api/v1/system/info
    ├── InstitutionViewAttributesInterceptor  Branding + correlation ID for every page
    ├── InstitutionTheme                View model record for branding
    └── ViewConstants                   Shared view-attribute names
```

## Reserved module packages

The following packages are reserved by the architecture but **intentionally absent** until their phase and
its BRD decisions are approved. Do not create empty packages or placeholder entities for them.

| Package | Phase | Blocking decisions |
|---|---|---|
| `com.edam.organization` | 2 | OD-16 master-data source |
| `com.edam.currency` | 2 | OD-07 PKR conversion and rounding |
| `com.edam.delegation` | 2 | OD-01, OD-02, OD-06, OD-10 |
| `com.edam.committee` | 2 | OD-01, OD-17 |
| `com.edam.expense.opex` | 3 | OD-05, OD-22 |
| `com.edam.expense.capex` | 4 | OD-08, OD-13, OD-21 |
| `com.edam.workflow` | 3 | OD-02, OD-09, OD-10 |
| `com.edam.reporting` | 5 | OD-22 |
| `com.edam.notification` | 3 | OD-15 |
| `com.edam.audit` | 2 | OD-05, NFR-05 |

## Rules

1. **One capability per top-level package.** A package that needs "and" in its name is two packages.
2. **No repository escapes its module.** Repositories are package-private where possible and are only
   injected into services of the same module. Cross-module callers receive a `*Summary`/`*View` record or a
   published service interface.
3. **No cyclic dependencies.** `common` never imports a business module. Business modules never import
   `config` or `web`. If two modules need each other, the shared concept belongs in `common` or a third
   module.
4. **`common` holds no business rules.** It may hold envelopes, exception types, the principal and audit
   metadata — never "if expense above X then …".
5. **Layering inside a module** (when the module grows beyond a handful of types):
   ```
   <module>/
   ├── <Aggregate>.java            entity / aggregate root
   ├── <Aggregate>Repository.java  persistence (package-visible)
   ├── <Module>Service.java        application service; @Transactional boundary
   ├── <Module>Summary.java        published read model
   └── internal/                   helpers that must not be imported elsewhere
   ```
6. **`config` is wiring.** A bean declared there must be framework infrastructure. Business choices are
   configuration properties or services.
7. **`web` is a delivery adapter.** It maps HTTP to service calls and to view models; it never contains a
   business rule and never touches a repository directly.

## Why `web` and `config` are outside the business modules

They are *delivery* and *wiring* concerns shared by every capability. Keeping them separate prevents a
business module from depending on the HTTP layer, which is what allows an API-based or batch entry point to
be added later without moving code.
