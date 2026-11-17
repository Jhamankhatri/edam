# 18 — Source Code Traceability

Every contract documented in this directory can be traced to source. All paths are relative to the repository root.

## 18.1 Core API contracts

| Concern | File |
|---|---|
| API version prefix `/api/v1` | `src/main/java/com/edam/common/api/ApiPaths.java` |
| Response envelope `ApiResponse<T>` | `src/main/java/com/edam/common/api/ApiResponse.java` (record with `ErrorBody`, `FieldErrorDetail`) |
| Error code catalogue (`EDAM-1xxx…9xxx`) | `src/main/java/com/edam/common/api/ErrorCode.java` |
| Pagination request | `src/main/java/com/edam/common/api/PageQuery.java` (`DEFAULT_SIZE=20`, `MAX_SIZE=200`, zero-based page) |
| Pagination response | `src/main/java/com/edam/common/api/PageResponse.java` |
| Idempotency aspect + annotations | `src/main/java/com/edam/common/api/IdempotentOperation.java`, `IdempotencyAspect.java`, `IdempotencyService.java` |
| Global exception handling | `src/main/java/com/edam/common/exception/GlobalExceptionHandler.java` |
| Business / resource / validation / concurrent exceptions | `src/main/java/com/edam/common/exception/*.java` |

## 18.2 Security

| Concern | File |
|---|---|
| Permission constants (60 codes) | `src/main/java/com/edam/common/security/Permissions.java` |
| `@EdamPermission` annotation + authorization manager | `src/main/java/com/edam/common/security/EdamPermission.java`, (authorizer in `EdamPermissionAuthorizationManager` — see security config wiring) |
| `@PublicEndpoint`, `@AuthenticatedEndpoint` | `src/main/java/com/edam/common/security/PublicEndpoint.java`, `AuthenticatedEndpoint.java` |
| Principal shape (`EdamPrincipal`) | `src/main/java/com/edam/common/security/EdamPrincipal.java` |
| Endpoint-authorization architecture test (enforces annotation coverage on every handler) | `src/test/java/com/edam/architecture/EndpointAuthorizationArchitectureTest.java` |
| Filter chain, CSRF, session, CORS, public matchers | `src/main/java/com/edam/config/SecurityConfig.java` |
| CORS exact-origin configuration | `src/main/java/com/edam/config/CorsConfig.java` |
| JSON authentication entry point / access denied handler (JSON errors, no redirects) | `src/main/java/com/edam/common/security/JsonApiAuthenticationEntryPoint.java`, `JsonApiAccessDeniedHandler.java` |
| User details service / identity adapter | `src/main/java/com/edam/common/security/EdamUserDetailsService.java` |
| CSRF token repository bean | Bean defined in `SecurityConfig.csrfTokenRepository()` (cookie name `XSRF-TOKEN`). |
| Password encoding | `src/main/java/com/edam/config/PasswordEncodingConfig.java` |
| Data scopes (INSTALLATION / BRANCH / DEPARTMENT) | `src/main/java/com/edam/common/security/DataScope.java`, `InstitutionContext.java` |

## 18.3 Controllers (each maps to a module package)

| Module | Controller file(s) |
|---|---|
| Authentication (bootstrap, login, logout, CSRF, me, password change/forgot/reset, installation) | `src/main/java/com/edam/identity/controller/AuthController.java` |
| System info | `src/main/java/com/edam/web/SystemController.java` |
| Users | `src/main/java/com/edam/usermanagement/controller/UserController.java` |
| Roles | `src/main/java/com/edam/rolemanagement/controller/RoleController.java` |
| Organization (profile, units, departments, branches) | `src/main/java/com/edam/organization/controller/OrganizationController.java` |
| Master data (currencies, vendors, tax authorities, exchange rates) | `src/main/java/com/edam/masterdata/controller/MasterDataController.java` |
| Expense configuration (categories, subcategories, natures, request types) | `src/main/java/com/edam/expenseconfiguration/controller/ExpenseConfigurationController.java` |
| Approval limits / exceeding-limit / recommending authorities | `src/main/java/com/edam/approvallimits/controller/{ApprovalLimit,ExceedingLimitRule,RecommendingAuthority}Controller.java` |
| Delegation matrix | `src/main/java/com/edam/delegation/controller/DelegationMatrixController.java` |
| Committees | `src/main/java/com/edam/committee/controller/CommitteeController.java` |
| Workflow configuration (definitions, versions, validate, publish, retire) | `src/main/java/com/edam/workflow/controller/WorkflowConfigurationController.java` |
| Approvals (inbox, case file, approve/reject/revert/forward) | `src/main/java/com/edam/workflow/controller/ApprovalController.java` |
| Expense requests (OPEX/CAPEX lifecycle, submit/cancel/edit) + request timeline | `src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java`, `src/main/java/com/edam/audit/controller/RequestTimelineController.java` |
| Business rules | `src/main/java/com/edam/businessrules/controller/BusinessRuleController.java` |
| Documents (upload, metadata, download URL, scans, delete) | `src/main/java/com/edam/documents/controller/DocumentController.java` |
| SMTP | `src/main/java/com/edam/communication/controller/SmtpConfigurationController.java` |
| Email templates | `src/main/java/com/edam/communication/controller/EmailTemplateController.java` |
| Notification event triggers | `src/main/java/com/edam/communication/controller/NotificationEventConfigController.java` |
| Notification inbox | `src/main/java/com/edam/communication/controller/NotificationInboxController.java` |
| Branding/themes/settings/feature flags + public config | `src/main/java/com/edam/configuration/controller/ConfigurationController.java` |
| Dashboard / register / export / widget & report catalogues | `src/main/java/com/edam/reporting/controller/ReportingController.java` |
| Audit events | `src/main/java/com/edam/audit/controller/AuditController.java` |
| Integrations (providers, CRUD, test, execute, history, retry) | `src/main/java/com/edam/integrations/controller/IntegrationController.java` |

## 18.4 DTOs (request/response records)

DTOs live under `src/main/java/com/edam/<module>/dto/`:

| Module | DTO file |
|---|---|
| Authentication | `identity/dto/AuthDtos.java` (LoginRequest, CurrentUserResponse, DataScopeView, PasswordPolicyView, ChangePasswordRequest, ForgotPasswordRequest, ResetPasswordRequest, NeutralResponse, InstallationStatusResponse, BootstrapRequest, BootstrapResponse, CsrfTokenResponse) |
| Users | `usermanagement/dto/UserDtos.java` |
| Roles | `rolemanagement/dto/RoleDtos.java` |
| Organization | `organization/dto/OrganizationDtos.java` |
| Master data | `masterdata/dto/MasterDataDtos.java` |
| Expense configuration | `expenseconfiguration/dto/ExpenseConfigurationDtos.java` |
| Approval limits / delegations / recommending authorities / exceeding limits | `approvallimits/dto/ApprovalLimitDtos.java`, `delegation/dto/DelegationDtos.java` |
| Committees | `committee/dto/CommitteeDtos.java` |
| Approvals / workflow | `workflow/dto/ApprovalDtos.java`, `workflow/dto/WorkflowDtos.java` |
| Expense requests (OPEX/CAPEX) | `expenserequest/dto/ExpenseRequestDtos.java` |
| Business rules | `businessrules/dto/RuleDtos.java` |
| Documents | `documents/dto/DocumentDtos.java` |
| Communication (SMTP, templates, events, notifications) | `communication/dto/{EventConfigRequest,EventConfigResponse,NotificationResponse,SmtpConfigurationRequest,SmtpConfigurationResponse,TemplateResponse,TemplateUpsertRequest,TemplateVersionResponse}.java` |
| Configuration (themes, settings, feature flags, public config) | `configuration/dto/ConfigurationDtos.java` |
| Reporting / audit | `reporting/dto/ReportingDtos.java`, `audit/dto/AuditDtos.java` |
| Integrations | `integrations/dto/IntegrationDtos.java` |

The generated JSON registry `docs/phase-4j/_generated/dtos.json` contains field-level detail for every discovered DTO record.

## 18.5 Services (business logic lives here)
Each controller delegates to a service in the same module package: e.g. `ExpenseRequestController` → `ExpenseRequestService`; `ApprovalController` → `ApprovalService`; `WorkflowConfigurationController` → `WorkflowService`; `DocumentController` → `DocumentService`; etc. Services perform authorization checks, state transitions and side effects. Cross-cutting concerns:
- **Object visibility:** implemented per service (e.g. `ExpenseRequestService.isVisible`).
- **Optimistic locking:** entities with `@Version` — `WorkflowTask`, `ExpenseRequest`, `WorkflowVersion`, `IntegrationConfig`, `IntegrationExecution`, `NotificationOutbox`, etc.
- **Outbox / integration claim pattern:** `REQUIRES_NEW` per-row claim + deliver loop (see `communication/service/OutboxProcessor`, `integrations/worker/IntegrationExecutionWorker`).
- **Secret encryption:** `communication/service/MailCredentialCrypto`, `integrations/security/IntegrationSecretCrypto`.
- **Audit redaction:** `common/audit/AuditValueRedactor`, `common/logging/DiagnosticRedactor`.
- **SSRF validation:** `integrations/security/SafeDestination`.

## 18.6 Persistence / entities / migrations
- Entities: `src/main/java/com/edam/<module>/model/*.java`. 72 JPA `@Table` entities (see `docs/phase-4j/EDAM_DATABASE_DATA_DICTIONARY.md`).
- Migrations: `src/main/resources/db/migration/V1…V27` (+ `V4_1__uuid_v7_function.sql`). Flyway forward-only; Hibernate `ddl-auto=validate` on every profile.
- Permission catalogue seed: `V12__permission_catalogue_and_roles.sql`.
- Audit append-only trigger: `V10__business_rules_audit_notifications.sql`, `V25__business_rules_audit_columns.sql`.
- Idempotency unique constraint: `V27__phase4i_idempotency_unique_fix.sql`.

## 18.7 Tests most relevant for contract verification
- `src/test/java/com/edam/architecture/EndpointAuthorizationArchitectureTest.java` — enforces every endpoint has `@EdamPermission/@PublicEndpoint/@AuthenticatedEndpoint` and uses only permission constants that exist.
- `*Controller*Test` / `*IT` tests under each module's test package exercise success paths and many error paths (validation, 401, 403, 404, 409).
- `AbstractPostgreSqlIntegrationTest` base for tests running against real Postgres.

## 18.8 Postman
- Collection generator source: this directory's reference documents + the `tools/phase4j-introspect.py` script. The generated collection lives at `docs/phase-4j/postman/EDAM.postman_collection.json`.
