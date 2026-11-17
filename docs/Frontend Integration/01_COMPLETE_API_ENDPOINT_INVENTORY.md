# 01 — Complete API Endpoint Inventory

This table lists every controller-mapped endpoint in the backend at the baseline commit. It is generated from the Spring `@*Mapping` annotations; each entry carries a stable endpoint ID (used across all other documents in this directory) and the controller handler.

**Total endpoints discovered:** 206 (195 permission-guarded, 9 public, 2 authenticated-only).

| ID | Module | Method | URL | Controller | Handler | AuthN | CSRF | Permission | Req DTO | Resp DTO | Success | Postman | Source |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| APR-001 | Approval Inbox & Actions | GET | `/approvals` | ApprovalController | inbox | required | No | approval:view | — | ? | 200 | ✓ | `ApprovalController#inbox` |
| APR-002 | Approval Inbox & Actions | GET | `/approvals/count` | ApprovalController | count | required | No | approval:view | — | ? | 200 | ✓ | `ApprovalController#count` |
| APR-003 | Approval Inbox & Actions | GET | `/approvals/tasks/{taskId}` | ApprovalController | caseFile | required | No | approval:view | — | ? | 200 | ✓ | `ApprovalController#caseFile` |
| APR-004 | Approval Inbox & Actions | GET | `/approvals/tasks/{taskId}/request` | ApprovalController | requestFor | required | No | approval:view | — | ? | 200 | ✓ | `ApprovalController#requestFor` |
| APR-005 | Approval Inbox & Actions | POST | `/approvals/tasks/{taskId}/approve` | ApprovalController | ? | required | Yes | approval:act | — | ? | 201 | ✓ | `ApprovalController#?` |
| APR-006 | Approval Inbox & Actions | POST | `/approvals/tasks/{taskId}/reject` | ApprovalController | ? | required | Yes | approval:act | — | ? | 201 | ✓ | `ApprovalController#?` |
| APR-007 | Approval Inbox & Actions | POST | `/approvals/tasks/{taskId}/revert` | ApprovalController | ? | required | Yes | approval:act | — | ? | 201 | ✓ | `ApprovalController#?` |
| APR-008 | Approval Inbox & Actions | POST | `/approvals/tasks/{taskId}/forward` | ApprovalController | ? | required | Yes | approval:act | — | ? | 201 | ✓ | `ApprovalController#?` |
| APL-001 | Approval Limits | GET | `/approval-limits` | ApprovalLimitController | list | required | No | approval-limit:view | — | ? | 200 | ✓ | `ApprovalLimitController#list` |
| APL-002 | Approval Limits | GET | `/approval-limits/{id}` | ApprovalLimitController | get | required | No | approval-limit:view | — | ? | 200 | ✓ | `ApprovalLimitController#get` |
| APL-003 | Approval Limits | POST | `/approval-limits` | ApprovalLimitController | create | required | Yes | approval-limit:manage | SaveApprovalLimitRequest | ? | 201 | ✓ | `ApprovalLimitController#create` |
| APL-004 | Approval Limits | PUT | `/approval-limits/{id}` | ApprovalLimitController | update | required | Yes | approval-limit:manage | SaveApprovalLimitRequest | ? | 200 | ✓ | `ApprovalLimitController#update` |
| APL-005 | Approval Limits | POST | `/approval-limits/{id}/activation` | ApprovalLimitController | setActive | required | Yes | approval-limit:manage | — | ? | 201 | ✓ | `ApprovalLimitController#setActive` |
| APL-006 | Approval Limits | POST | `/approval-limits/authority-check` | ApprovalLimitController | checkAuthority | required | Yes | approval-limit:view | AuthorityCheckRequest | ? | 201 | ✓ | `ApprovalLimitController#checkAuthority` |
| APL-001 | Approval Limits | GET | `/exceeding-limit-rules` | ExceedingLimitRuleController | list | required | No | exceeding-limit:view | — | ? | 200 | ✓ | `ExceedingLimitRuleController#list` |
| APL-002 | Approval Limits | GET | `/exceeding-limit-rules/{id}` | ExceedingLimitRuleController | get | required | No | exceeding-limit:view | — | ? | 200 | ✓ | `ExceedingLimitRuleController#get` |
| APL-003 | Approval Limits | POST | `/exceeding-limit-rules` | ExceedingLimitRuleController | create | required | Yes | exceeding-limit:manage | SaveExceedingLimitRuleRequest | ? | 201 | ✓ | `ExceedingLimitRuleController#create` |
| APL-004 | Approval Limits | PUT | `/exceeding-limit-rules/{id}` | ExceedingLimitRuleController | update | required | Yes | exceeding-limit:manage | SaveExceedingLimitRuleRequest | ? | 200 | ✓ | `ExceedingLimitRuleController#update` |
| APL-005 | Approval Limits | POST | `/exceeding-limit-rules/{id}/activation` | ExceedingLimitRuleController | setActive | required | Yes | exceeding-limit:manage | — | ? | 201 | ✓ | `ExceedingLimitRuleController#setActive` |
| APL-006 | Approval Limits | GET | `/exceeding-limit-rules/evaluation` | ExceedingLimitRuleController | evaluate | required | No | exceeding-limit:view | — | ? | 200 | ✓ | `ExceedingLimitRuleController#evaluate` |
| APL-001 | Approval Limits | GET | `/recommending-authorities` | RecommendingAuthorityController | list | required | No | recommending-authority:view | — | ? | 200 | ✓ | `RecommendingAuthorityController#list` |
| APL-002 | Approval Limits | GET | `/recommending-authorities/{id}` | RecommendingAuthorityController | get | required | No | recommending-authority:view | — | ? | 200 | ✓ | `RecommendingAuthorityController#get` |
| APL-003 | Approval Limits | POST | `/recommending-authorities` | RecommendingAuthorityController | create | required | Yes | recommending-authority:manage | SaveRecommendingAuthorityRequest | ? | 201 | ✓ | `RecommendingAuthorityController#create` |
| APL-004 | Approval Limits | PUT | `/recommending-authorities/{id}` | RecommendingAuthorityController | update | required | Yes | recommending-authority:manage | SaveRecommendingAuthorityRequest | ? | 200 | ✓ | `RecommendingAuthorityController#update` |
| APL-005 | Approval Limits | POST | `/recommending-authorities/{id}/activation` | RecommendingAuthorityController | setActive | required | Yes | recommending-authority:manage | — | ? | 201 | ✓ | `RecommendingAuthorityController#setActive` |
| APL-006 | Approval Limits | GET | `/recommending-authorities/resolved` | RecommendingAuthorityController | resolved | required | No | recommending-authority:view | — | ? | 200 | ✓ | `RecommendingAuthorityController#resolved` |
| AUD-001 | Audit | GET | `/audit-events` | AuditController | search | required | No | audit:view | — | ? | 200 | ✓ | `AuditController#search` |
| AUD-002 | Audit | GET | `/audit-events/{id}` | AuditController | get | required | No | audit:view | — | ? | 200 | ✓ | `AuditController#get` |
| AUD-001 | Audit | GET | `/expense-requests/{id}/activity` | RequestTimelineController | timeline | required | No | request:view-own | — | ? | 200 | ✓ | `RequestTimelineController#timeline` |
| AUTH-001 | Authentication | POST | `/auth/login` | AuthController | login | public | No | — | LoginRequest | ? | 200 | ✓ | `AuthController#login` |
| AUTH-002 | Authentication | POST | `/auth/logout` | AuthController | logout | public | No | — | — | ? | 200 | ✓ | `AuthController#logout` |
| AUTH-003 | Authentication | GET | `/auth/me` | AuthController | currentUser | authenticated | No | — | — | ? | 200 | ✓ | `AuthController#currentUser` |
| AUTH-004 | Authentication | GET | `/auth/csrf` | AuthController | csrf | public | No | — | — | ? | 200 | ✓ | `AuthController#csrf` |
| AUTH-005 | Authentication | POST | `/auth/change-password` | AuthController | changePassword | authenticated | Yes | — | ChangePasswordRequest | ? | 200 | ✓ | `AuthController#changePassword` |
| AUTH-006 | Authentication | POST | `/auth/forgot-password` | AuthController | forgotPassword | public | No | — | ForgotPasswordRequest | ? | 200 | ✓ | `AuthController#forgotPassword` |
| AUTH-007 | Authentication | POST | `/auth/reset-password` | AuthController | resetPassword | public | No | — | ResetPasswordRequest | ? | 200 | ✓ | `AuthController#resetPassword` |
| AUTH-008 | Authentication | GET | `/auth/installation` | AuthController | installationStatus | public | No | — | — | ? | 200 | ✓ | `AuthController#installationStatus` |
| AUTH-009 | Authentication | POST | `/auth/bootstrap` | AuthController | bootstrap | public | No | — | — | ? | 201 | ✓ | `AuthController#bootstrap` |
| BRD-001 | Branding / Configuration | GET | `/public/configuration` | ConfigurationController | publicConfiguration | public | No | — | — | ? | 200 | ✓ | `ConfigurationController#publicConfiguration` |
| BRD-002 | Branding / Configuration | GET | `/themes` | ConfigurationController | listThemes | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#listThemes` |
| BRD-003 | Branding / Configuration | GET | `/themes/active` | ConfigurationController | activeTheme | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#activeTheme` |
| BRD-004 | Branding / Configuration | GET | `/themes/{id}` | ConfigurationController | getTheme | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#getTheme` |
| BRD-005 | Branding / Configuration | POST | `/themes` | ConfigurationController | createTheme | required | Yes | configuration:manage | SaveThemeRequest | ? | 201 | ✓ | `ConfigurationController#createTheme` |
| BRD-006 | Branding / Configuration | PUT | `/themes/{id}` | ConfigurationController | updateTheme | required | Yes | configuration:manage | SaveThemeRequest | ? | 200 | ✓ | `ConfigurationController#updateTheme` |
| BRD-007 | Branding / Configuration | POST | `/themes/{id}/activation` | ConfigurationController | setThemeActive | required | Yes | configuration:manage | — | ? | 201 | ✓ | `ConfigurationController#setThemeActive` |
| BRD-008 | Branding / Configuration | POST | `/themes/{id}/default` | ConfigurationController | setDefaultTheme | required | Yes | configuration:manage | — | ? | 200 | ✓ | `ConfigurationController#setDefaultTheme` |
| BRD-009 | Branding / Configuration | GET | `/settings` | ConfigurationController | listSettings | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#listSettings` |
| BRD-010 | Branding / Configuration | GET | `/settings/categories/{category}` | ConfigurationController | listByCategory | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#listByCategory` |
| BRD-011 | Branding / Configuration | GET | `/settings/{key}` | ConfigurationController | getSetting | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#getSetting` |
| BRD-012 | Branding / Configuration | POST | `/settings` | ConfigurationController | saveSetting | required | Yes | configuration:manage | SaveSettingRequest | ? | 201 | ✓ | `ConfigurationController#saveSetting` |
| BRD-013 | Branding / Configuration | PATCH | `/settings/bulk` | ConfigurationController | bulkUpdateSettings | required | Yes | configuration:manage | BulkSettingsUpdateRequest | ? | 200 | ✓ | `ConfigurationController#bulkUpdateSettings` |
| BRD-014 | Branding / Configuration | GET | `/feature-flags` | ConfigurationController | listFlags | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#listFlags` |
| BRD-015 | Branding / Configuration | GET | `/feature-flags/{key}` | ConfigurationController | getFlag | required | No | configuration:view | — | ? | 200 | ✓ | `ConfigurationController#getFlag` |
| BRD-016 | Branding / Configuration | POST | `/feature-flags` | ConfigurationController | saveFlag | required | Yes | configuration:manage | SaveFeatureFlagRequest | ? | 201 | ✓ | `ConfigurationController#saveFlag` |
| BRD-017 | Branding / Configuration | POST | `/feature-flags/{key}/activation` | ConfigurationController | setFlagEnabled | required | Yes | configuration:manage | — | ? | 201 | ✓ | `ConfigurationController#setFlagEnabled` |
| BR-001 | Business Rules | GET | `/business-rules` | BusinessRuleController | list | required | No | rule:view | — | ? | 200 | ✓ | `BusinessRuleController#list` |
| BR-002 | Business Rules | GET | `/business-rules/{id}` | BusinessRuleController | get | required | No | rule:view | — | ? | 200 | ✓ | `BusinessRuleController#get` |
| BR-003 | Business Rules | POST | `/business-rules` | BusinessRuleController | create | required | Yes | rule:manage | CreateRuleSetRequest | ? | 201 | ✓ | `BusinessRuleController#create` |
| BR-004 | Business Rules | PUT | `/business-rules/{id}` | BusinessRuleController | update | required | Yes | rule:manage | UpdateRuleSetRequest | ? | 200 | ✓ | `BusinessRuleController#update` |
| BR-005 | Business Rules | POST | `/business-rules/{id}/drafts` | BusinessRuleController | newDraft | required | Yes | rule:manage | — | ? | 201 | ✓ | `BusinessRuleController#newDraft` |
| BR-006 | Business Rules | POST | `/business-rules/{id}/publish` | BusinessRuleController | publish | required | Yes | rule:publish | — | ? | 200 | ✓ | `BusinessRuleController#publish` |
| BR-007 | Business Rules | POST | `/business-rules/{id}/retire` | BusinessRuleController | retire | required | Yes | rule:publish | — | ? | 200 | ✓ | `BusinessRuleController#retire` |
| BR-008 | Business Rules | PUT | `/business-rules/versions/{versionId}/rules` | BusinessRuleController | upsertRule | required | Yes | rule:manage | RuleUpsertRequest | ? | 200 | ✓ | `BusinessRuleController#upsertRule` |
| BR-009 | Business Rules | DELETE | `/business-rules/rules/{ruleId}` | BusinessRuleController | deleteRule | required | Yes | rule:manage | — | ? | 204 | ✓ | `BusinessRuleController#deleteRule` |
| CMT-001 | Committees | GET | `/committees` | CommitteeController | list | required | No | committee:view | — | ? | 200 | ✓ | `CommitteeController#list` |
| CMT-002 | Committees | GET | `/committees/{id}` | CommitteeController | get | required | No | committee:view | — | ? | 200 | ✓ | `CommitteeController#get` |
| CMT-003 | Committees | POST | `/committees` | CommitteeController | create | required | Yes | committee:manage | SaveCommitteeRequest | ? | 201 | ✓ | `CommitteeController#create` |
| CMT-004 | Committees | POST | `/committees/{id}` | CommitteeController | rename | required | Yes | committee:manage | SaveCommitteeRequest | ? | 201 | ✓ | `CommitteeController#rename` |
| CMT-005 | Committees | POST | `/committees/{id}/deactivate` | CommitteeController | deactivate | required | Yes | committee:manage | — | ? | 201 | ✓ | `CommitteeController#deactivate` |
| CMT-006 | Committees | POST | `/committees/{id}/members` | CommitteeController | addMember | required | Yes | committee:manage | AddMemberRequest | ? | 201 | ✓ | `CommitteeController#addMember` |
| CMT-007 | Committees | POST | `/committees/{id}/members/{memberId}/end` | CommitteeController | endMembership | required | Yes | committee:manage | — | ? | 201 | ✓ | `CommitteeController#endMembership` |
| CMT-008 | Committees | POST | `/committees/{id}/levels` | CommitteeController | addLevel | required | Yes | committee:manage | CommitteeLevelRequest | ? | 201 | ✓ | `CommitteeController#addLevel` |
| CMT-009 | Committees | POST | `/committees/{id}/levels/{levelId}/deactivate` | CommitteeController | deactivateLevel | required | Yes | committee:manage | — | ? | 201 | ✓ | `CommitteeController#deactivateLevel` |
| CMT-010 | Committees | GET | `/committees/coverage` | CommitteeController | coverage | required | No | committee:view | — | ? | 200 | ✓ | `CommitteeController#coverage` |
| DEL-001 | Delegation | GET | `/delegation-matrix` | DelegationMatrixController | list | required | No | delegation:view | — | ? | 200 | ✓ | `DelegationMatrixController#list` |
| DEL-002 | Delegation | POST | `/delegation-matrix` | DelegationMatrixController | create | required | Yes | delegation:manage | SaveDelegationRuleRequest | ? | 201 | ✓ | `DelegationMatrixController#create` |
| DEL-003 | Delegation | POST | `/delegation-matrix/{id}/close` | DelegationMatrixController | close | required | Yes | delegation:manage | — | ? | 201 | ✓ | `DelegationMatrixController#close` |
| DEL-004 | Delegation | POST | `/delegation-matrix/{id}/activation` | DelegationMatrixController | setActive | required | Yes | delegation:manage | — | ? | 201 | ✓ | `DelegationMatrixController#setActive` |
| DEL-005 | Delegation | POST | `/delegation-matrix/preview` | DelegationMatrixController | preview | required | Yes | delegation:view | — | ? | 201 | ✓ | `DelegationMatrixController#preview` |
| DOC-001 | Documents | POST | `/documents` | DocumentController | upload | required | Yes | document:upload | multipart | ? | 201 | ✓ | `DocumentController#upload` |
| DOC-002 | Documents | GET | `/documents/requests/{requestId}` | DocumentController | listForRequest | required | No | document:download | — | ? | 200 | ✓ | `DocumentController#listForRequest` |
| DOC-003 | Documents | GET | `/documents/{id}` | DocumentController | get | required | No | document:download | — | ? | 200 | ✓ | `DocumentController#get` |
| DOC-004 | Documents | GET | `/documents/{id}/download-url` | DocumentController | downloadUrl | required | No | document:download | — | ? | 200 | ✓ | `DocumentController#downloadUrl` |
| DOC-005 | Documents | GET | `/documents/{id}/scans` | DocumentController | scans | required | No | document:download | — | ? | 200 | ✓ | `DocumentController#scans` |
| DOC-006 | Documents | DELETE | `/documents/{id}` | DocumentController | delete | required | Yes | document:delete | — | ? | 204 | ✓ | `DocumentController#delete` |
| TPL-001 | Email Templates | GET | `/` | EmailTemplateController | list | required | No | notification-template:manage | — | ? | 200 | ✓ | `EmailTemplateController#list` |
| TPL-002 | Email Templates | GET | `/{id}` | EmailTemplateController | get | required | No | notification-template:manage | — | ? | 200 | ✓ | `EmailTemplateController#get` |
| TPL-003 | Email Templates | POST | `/` | EmailTemplateController | create | required | Yes | notification-template:manage | TemplateUpsertRequest | ? | 201 | ✓ | `EmailTemplateController#create` |
| TPL-004 | Email Templates | POST | `/{id}/versions` | EmailTemplateController | newVersion | required | Yes | notification-template:manage | TemplateUpsertRequest | ? | 201 | ✓ | `EmailTemplateController#newVersion` |
| TPL-005 | Email Templates | GET | `/{id}/versions` | EmailTemplateController | history | required | No | notification-template:manage | — | ? | 200 | ✓ | `EmailTemplateController#history` |
| TPL-006 | Email Templates | PUT | `/{id}/active` | EmailTemplateController | setActive | required | Yes | notification-template:manage | — | ? | 200 | ✓ | `EmailTemplateController#setActive` |
| TPL-007 | Email Templates | POST | `/{id}/preview` | EmailTemplateController | preview | required | Yes | notification-template:manage | — | ? | 201 | ✓ | `EmailTemplateController#preview` |
| CFG-001 | Expense Configuration | GET | `/expense-configuration/categories` | ExpenseConfigurationController | categories | required | No | expense-configuration:view | — | ? | 200 | ✓ | `ExpenseConfigurationController#categories` |
| CFG-002 | Expense Configuration | POST | `/expense-configuration/categories` | ExpenseConfigurationController | createCategory | required | Yes | expense-configuration:manage | SaveCategoryRequest | ? | 201 | ✓ | `ExpenseConfigurationController#createCategory` |
| CFG-003 | Expense Configuration | PUT | `/expense-configuration/categories/{id}` | ExpenseConfigurationController | updateCategory | required | Yes | expense-configuration:manage | SaveCategoryRequest | ? | 200 | ✓ | `ExpenseConfigurationController#updateCategory` |
| CFG-004 | Expense Configuration | POST | `/expense-configuration/categories/{id}/activation` | ExpenseConfigurationController | setCategoryActive | required | Yes | expense-configuration:manage | — | ? | 201 | ✓ | `ExpenseConfigurationController#setCategoryActive` |
| CFG-005 | Expense Configuration | POST | `/expense-configuration/categories/{categoryId}/subcategories` | ExpenseConfigurationController | createSubcategory | required | Yes | expense-configuration:manage | SaveSubcategoryRequest | ? | 201 | ✓ | `ExpenseConfigurationController#createSubcategory` |
| CFG-006 | Expense Configuration | PUT | `/expense-configuration/subcategories/{id}` | ExpenseConfigurationController | updateSubcategory | required | Yes | expense-configuration:manage | SaveSubcategoryRequest | ? | 200 | ✓ | `ExpenseConfigurationController#updateSubcategory` |
| CFG-007 | Expense Configuration | POST | `/expense-configuration/subcategories/{id}/activation` | ExpenseConfigurationController | setSubcategoryActive | required | Yes | expense-configuration:manage | — | ? | 201 | ✓ | `ExpenseConfigurationController#setSubcategoryActive` |
| CFG-008 | Expense Configuration | GET | `/expense-configuration/natures` | ExpenseConfigurationController | natures | required | No | expense-configuration:view | — | ? | 200 | ✓ | `ExpenseConfigurationController#natures` |
| CFG-009 | Expense Configuration | POST | `/expense-configuration/natures` | ExpenseConfigurationController | createNature | required | Yes | expense-configuration:manage | SaveLookupRequest | ? | 201 | ✓ | `ExpenseConfigurationController#createNature` |
| CFG-010 | Expense Configuration | POST | `/expense-configuration/natures/{id}/activation` | ExpenseConfigurationController | setNatureActive | required | Yes | expense-configuration:manage | — | ? | 201 | ✓ | `ExpenseConfigurationController#setNatureActive` |
| CFG-011 | Expense Configuration | GET | `/expense-configuration/request-types` | ExpenseConfigurationController | requestTypes | required | No | expense-configuration:view | — | ? | 200 | ✓ | `ExpenseConfigurationController#requestTypes` |
| CFG-012 | Expense Configuration | POST | `/expense-configuration/request-types` | ExpenseConfigurationController | createRequestType | required | Yes | expense-configuration:manage | SaveLookupRequest | ? | 201 | ✓ | `ExpenseConfigurationController#createRequestType` |
| INT-001 | Integrations | GET | `/integrations/providers` | IntegrationController | providers | required | No | integration:view | — | ? | 200 | ✓ | `IntegrationController#providers` |
| INT-002 | Integrations | GET | `/integrations` | IntegrationController | list | required | No | integration:view | — | ? | 200 | ✓ | `IntegrationController#list` |
| INT-003 | Integrations | GET | `/integrations/{id}` | IntegrationController | detail | required | No | integration:view | — | ? | 200 | ✓ | `IntegrationController#detail` |
| INT-004 | Integrations | POST | `/integrations` | IntegrationController | create | required | Yes | integration:manage | CreateIntegrationRequest | ? | 201 | ✓ | `IntegrationController#create` |
| INT-005 | Integrations | PUT | `/integrations/{id}` | IntegrationController | update | required | Yes | integration:manage | UpdateIntegrationRequest | ? | 200 | ✓ | `IntegrationController#update` |
| INT-006 | Integrations | POST | `/integrations/{id}/activate` | IntegrationController | activate | required | Yes | integration:manage | — | ? | 200 | ✓ | `IntegrationController#activate` |
| INT-007 | Integrations | POST | `/integrations/{id}/deactivate` | IntegrationController | deactivate | required | Yes | integration:manage | — | ? | 201 | ✓ | `IntegrationController#deactivate` |
| INT-008 | Integrations | DELETE | `/integrations/{id}` | IntegrationController | archive | required | Yes | integration:manage | — | ? | 204 | ✓ | `IntegrationController#archive` |
| INT-009 | Integrations | POST | `/integrations/{id}/test` | IntegrationController | test | required | Yes | integration:test | — | ? | 200 | ✓ | `IntegrationController#test` |
| INT-010 | Integrations | POST | `/integrations/{id}/executions` | IntegrationController | execute | required | Yes | integration:execute | ExecuteRequest | ? | 200 | ✓ | `IntegrationController#execute` |
| INT-011 | Integrations | GET | `/integrations/{id}/executions` | IntegrationController | history | required | No | integration:view | — | ? | 200 | ✓ | `IntegrationController#history` |
| INT-012 | Integrations | GET | `/integrations/executions/{execId}` | IntegrationController | executionDetail | required | No | integration:view | — | ? | 200 | ✓ | `IntegrationController#executionDetail` |
| INT-013 | Integrations | POST | `/integrations/executions/{execId}/retry` | IntegrationController | retry | required | Yes | integration:execute | — | ? | 201 | ✓ | `IntegrationController#retry` |
| MST-001 | Master Data | GET | `/master-data/currencies` | MasterDataController | currencies | required | No | currency:view | — | ? | 200 | ✓ | `MasterDataController#currencies` |
| MST-002 | Master Data | POST | `/master-data/currencies` | MasterDataController | createCurrency | required | Yes | currency:manage | SaveCurrencyRequest | ? | 201 | ✓ | `MasterDataController#createCurrency` |
| MST-003 | Master Data | PUT | `/master-data/currencies/{id}` | MasterDataController | updateCurrency | required | Yes | currency:manage | SaveCurrencyRequest | ? | 200 | ✓ | `MasterDataController#updateCurrency` |
| MST-004 | Master Data | POST | `/master-data/currencies/{id}/activation` | MasterDataController | setCurrencyActive | required | Yes | currency:manage | — | ? | 201 | ✓ | `MasterDataController#setCurrencyActive` |
| MST-005 | Master Data | GET | `/master-data/exchange-rates` | MasterDataController | exchangeRates | required | No | currency:view | — | ? | 200 | ✓ | `MasterDataController#exchangeRates` |
| MST-006 | Master Data | POST | `/master-data/exchange-rates` | MasterDataController | createRate | required | Yes | currency:manage | SaveExchangeRateRequest | ? | 201 | ✓ | `MasterDataController#createRate` |
| MST-007 | Master Data | POST | `/master-data/exchange-rates/{id}/close` | MasterDataController | closeRate | required | Yes | currency:manage | — | ? | 201 | ✓ | `MasterDataController#closeRate` |
| MST-008 | Master Data | GET | `/master-data/vendors` | MasterDataController | vendors | required | No | vendor:view | — | ? | 200 | ✓ | `MasterDataController#vendors` |
| MST-009 | Master Data | GET | `/master-data/vendors/{id}` | MasterDataController | vendor | required | No | vendor:view | — | ? | 200 | ✓ | `MasterDataController#vendor` |
| MST-010 | Master Data | POST | `/master-data/vendors` | MasterDataController | createVendor | required | Yes | vendor:manage | SaveVendorRequest | ? | 201 | ✓ | `MasterDataController#createVendor` |
| MST-011 | Master Data | PUT | `/master-data/vendors/{id}` | MasterDataController | updateVendor | required | Yes | vendor:manage | SaveVendorRequest | ? | 200 | ✓ | `MasterDataController#updateVendor` |
| MST-012 | Master Data | POST | `/master-data/vendors/{id}/activation` | MasterDataController | setVendorActive | required | Yes | vendor:manage | — | ? | 201 | ✓ | `MasterDataController#setVendorActive` |
| MST-013 | Master Data | GET | `/master-data/tax-authorities` | MasterDataController | taxAuthorities | required | No | currency:view | — | ? | 200 | ✓ | `MasterDataController#taxAuthorities` |
| MST-014 | Master Data | POST | `/master-data/tax-authorities` | MasterDataController | createTaxAuthority | required | Yes | tax-authority:manage | SaveTaxAuthorityRequest | ? | 201 | ✓ | `MasterDataController#createTaxAuthority` |
| MST-015 | Master Data | PUT | `/master-data/tax-authorities/{id}` | MasterDataController | updateTaxAuthority | required | Yes | tax-authority:manage | SaveTaxAuthorityRequest | ? | 200 | ✓ | `MasterDataController#updateTaxAuthority` |
| MST-016 | Master Data | POST | `/master-data/tax-authorities/{id}/activation` | MasterDataController | setTaxAuthorityActive | required | Yes | tax-authority:manage | — | ? | 201 | ✓ | `MasterDataController#setTaxAuthorityActive` |
| EVT-001 | Notification Triggers | GET | `/` | NotificationEventConfigController | list | required | No | notification-template:manage | — | ? | 200 | ✓ | `NotificationEventConfigController#list` |
| EVT-002 | Notification Triggers | PUT | `/{eventCode}` | NotificationEventConfigController | update | required | Yes | notification-template:manage | EventConfigRequest | ? | 200 | ✓ | `NotificationEventConfigController#update` |
| NTF-001 | Notifications | GET | `/` | NotificationInboxController | list | required | No | notification:view | — | ? | 200 | ✓ | `NotificationInboxController#list` |
| NTF-002 | Notifications | GET | `/unread-count` | NotificationInboxController | unreadCount | required | No | notification:view | — | ? | 200 | ✓ | `NotificationInboxController#unreadCount` |
| NTF-003 | Notifications | POST | `/{id}/read` | NotificationInboxController | markRead | required | Yes | notification:view | — | ? | 200 | ✓ | `NotificationInboxController#markRead` |
| NTF-004 | Notifications | POST | `/read-all` | NotificationInboxController | markAllRead | required | Yes | notification:view | — | ? | 201 | ✓ | `NotificationInboxController#markAllRead` |
| ORG-001 | Organization | GET | `/organization/profile` | OrganizationController | getProfile | required | No | organization:view | — | ? | 200 | ✓ | `OrganizationController#getProfile` |
| ORG-002 | Organization | POST | `/organization/profile` | OrganizationController | createProfile | required | Yes | configuration:manage | CreateOrganizationProfileRequest | ? | 201 | ✓ | `OrganizationController#createProfile` |
| ORG-003 | Organization | PATCH | `/organization/profile` | OrganizationController | updateProfile | required | Yes | configuration:manage | UpdateOrganizationProfileRequest | ? | 200 | ✓ | `OrganizationController#updateProfile` |
| ORG-004 | Organization | GET | `/organization/unit-types` | OrganizationController | unitTypes | required | No | organization:view | — | ? | 200 | ✓ | `OrganizationController#unitTypes` |
| ORG-005 | Organization | GET | `/organization/units` | OrganizationController | units | required | No | organization:view | — | ? | 200 | ✓ | `OrganizationController#units` |
| ORG-006 | Organization | GET | `/organization/units/tree` | OrganizationController | tree | required | No | organization:view | — | ? | 200 | ✓ | `OrganizationController#tree` |
| ORG-007 | Organization | POST | `/organization/units` | OrganizationController | createUnit | required | Yes | organization:manage | CreateOrganizationUnitRequest | ? | 201 | ✓ | `OrganizationController#createUnit` |
| ORG-008 | Organization | PUT | `/organization/units/{id}` | OrganizationController | updateUnit | required | Yes | organization:manage | UpdateOrganizationUnitRequest | ? | 200 | ✓ | `OrganizationController#updateUnit` |
| ORG-009 | Organization | POST | `/organization/units/{id}/activation` | OrganizationController | setUnitActive | required | Yes | organization:manage | — | ? | 201 | ✓ | `OrganizationController#setUnitActive` |
| ORG-010 | Organization | GET | `/organization/departments` | OrganizationController | departments | required | No | organization:view | — | ? | 200 | ✓ | `OrganizationController#departments` |
| ORG-011 | Organization | POST | `/organization/departments` | OrganizationController | createDepartment | required | Yes | organization:manage | SaveDepartmentRequest | ? | 201 | ✓ | `OrganizationController#createDepartment` |
| ORG-012 | Organization | PUT | `/organization/departments/{id}` | OrganizationController | updateDepartment | required | Yes | organization:manage | SaveDepartmentRequest | ? | 200 | ✓ | `OrganizationController#updateDepartment` |
| ORG-013 | Organization | POST | `/organization/departments/{id}/activation` | OrganizationController | setDepartmentActive | required | Yes | organization:manage | — | ? | 201 | ✓ | `OrganizationController#setDepartmentActive` |
| ORG-014 | Organization | GET | `/organization/branches` | OrganizationController | branches | required | No | organization:view | — | ? | 200 | ✓ | `OrganizationController#branches` |
| ORG-015 | Organization | POST | `/organization/branches` | OrganizationController | createBranch | required | Yes | organization:manage | SaveBranchRequest | ? | 201 | ✓ | `OrganizationController#createBranch` |
| ORG-016 | Organization | PUT | `/organization/branches/{id}` | OrganizationController | updateBranch | required | Yes | organization:manage | SaveBranchRequest | ? | 200 | ✓ | `OrganizationController#updateBranch` |
| ORG-017 | Organization | POST | `/organization/branches/{id}/activation` | OrganizationController | setBranchActive | required | Yes | organization:manage | — | ? | 201 | ✓ | `OrganizationController#setBranchActive` |
| RPT-001 | Reports & Dashboard | GET | `/reporting/dashboard` | ReportingController | dashboard | required | No | report:view | — | ? | 200 | ✓ | `ReportingController#dashboard` |
| RPT-002 | Reports & Dashboard | GET | `/reporting/widgets` | ReportingController | widgetCatalogue | required | No | dashboard:manage | — | ? | 200 | ✓ | `ReportingController#widgetCatalogue` |
| RPT-003 | Reports & Dashboard | GET | `/reporting/reports` | ReportingController | reportCatalogue | required | No | dashboard:manage | — | ? | 200 | ✓ | `ReportingController#reportCatalogue` |
| RPT-004 | Reports & Dashboard | GET | `/reporting/expense-requests/register` | ReportingController | register | required | No | report:view | — | ? | 200 | ✓ | `ReportingController#register` |
| RPT-005 | Reports & Dashboard | POST | `/reporting/expense-requests/export` | ReportingController | export | required | Yes | report:export | ExportRequest | ? | 201 | ✓ | `ReportingController#export` |
| REQ-001 | Requests (OPEX/CAPEX) | GET | `/expense-requests` | ExpenseRequestController | list | required | No | request:view-own | — | ? | 200 | ✓ | `ExpenseRequestController#list` |
| REQ-002 | Requests (OPEX/CAPEX) | GET | `/expense-requests/{id}` | ExpenseRequestController | get | required | No | request:view-own | — | ? | 200 | ✓ | `ExpenseRequestController#get` |
| REQ-003 | Requests (OPEX/CAPEX) | POST | `/expense-requests` | ExpenseRequestController | create | required | Yes | request:create | CreateRequest | ? | 201 | ✓ | `ExpenseRequestController#create` |
| REQ-004 | Requests (OPEX/CAPEX) | PUT | `/expense-requests/{id}` | ExpenseRequestController | ? | required | Yes | request:edit | — | ? | 200 | ✓ | `ExpenseRequestController#?` |
| REQ-005 | Requests (OPEX/CAPEX) | POST | `/expense-requests/{id}/submit` | ExpenseRequestController | ? | required | Yes | request:submit | — | ? | 201 | ✓ | `ExpenseRequestController#?` |
| REQ-006 | Requests (OPEX/CAPEX) | POST | `/expense-requests/{id}/cancel` | ExpenseRequestController | ? | required | Yes | request:cancel | — | ? | 201 | ✓ | `ExpenseRequestController#?` |
| ROL-001 | Roles & Permissions | GET | `/roles` | RoleController | list | required | No | role:view | — | ? | 200 | ✓ | `RoleController#list` |
| ROL-002 | Roles & Permissions | GET | `/roles/permissions` | RoleController | catalogue | required | No | role:view | — | ? | 200 | ✓ | `RoleController#catalogue` |
| ROL-003 | Roles & Permissions | GET | `/roles/{id}` | RoleController | get | required | No | role:view | — | ? | 200 | ✓ | `RoleController#get` |
| ROL-004 | Roles & Permissions | POST | `/roles` | RoleController | create | required | Yes | role:manage | SaveRoleRequest | ? | 201 | ✓ | `RoleController#create` |
| ROL-005 | Roles & Permissions | PUT | `/roles/{id}` | RoleController | update | required | Yes | role:manage | SaveRoleRequest | ? | 200 | ✓ | `RoleController#update` |
| ROL-006 | Roles & Permissions | POST | `/roles/{id}/activation` | RoleController | setActive | required | Yes | role:manage | — | ? | 201 | ✓ | `RoleController#setActive` |
| ROL-007 | Roles & Permissions | PUT | `/roles/{id}/permissions` | RoleController | setPermissions | required | Yes | role:manage | SetPermissionsRequest | ? | 200 | ✓ | `RoleController#setPermissions` |
| SMT-001 | SMTP | GET | `/` | SmtpConfigurationController | get | required | No | email-configuration:manage | — | ? | 200 | ✓ | `SmtpConfigurationController#get` |
| SMT-002 | SMTP | POST | `/` | SmtpConfigurationController | save | required | Yes | email-configuration:manage | SmtpConfigurationRequest | ? | 201 | ✓ | `SmtpConfigurationController#save` |
| SMT-003 | SMTP | DELETE | `/active` | SmtpConfigurationController | deactivate | required | Yes | email-configuration:manage | — | ? | 204 | ✓ | `SmtpConfigurationController#deactivate` |
| SMT-004 | SMTP | POST | `/test` | SmtpConfigurationController | test | required | Yes | email-configuration:manage | — | ? | 200 | ✓ | `SmtpConfigurationController#test` |
| SYS-001 | System | GET | `/info` | SystemController | info | public | No | — | — | ? | 200 | ✓ | `SystemController#info` |
| USR-001 | Users | GET | `/users` | UserController | list | required | No | user:view | — | ? | 200 | ✓ | `UserController#list` |
| USR-002 | Users | GET | `/users/search` | UserController | search | required | No | user:view | — | ? | 200 | ✓ | `UserController#search` |
| USR-003 | Users | GET | `/users/{id}` | UserController | get | required | No | user:view | — | ? | 200 | ✓ | `UserController#get` |
| USR-004 | Users | POST | `/users` | UserController | create | required | Yes | user:manage | CreateUserRequest | ? | 201 | ✓ | `UserController#create` |
| USR-005 | Users | PUT | `/users/{id}` | UserController | update | required | Yes | user:manage | UpdateUserRequest | ? | 200 | ✓ | `UserController#update` |
| USR-006 | Users | POST | `/users/{id}/activation` | UserController | setActive | required | Yes | user:manage | — | ? | 201 | ✓ | `UserController#setActive` |
| USR-007 | Users | GET | `/users/{id}/roles` | UserController | roles | required | No | user:view | — | ? | 200 | ✓ | `UserController#roles` |
| USR-008 | Users | POST | `/users/{id}/roles` | UserController | assignRole | required | Yes | user-role:assign | AssignRoleRequest | ? | 201 | ✓ | `UserController#assignRole` |
| USR-009 | Users | DELETE | `/users/{id}/roles/{assignmentId}` | UserController | revokeRole | required | Yes | user-role:assign | — | ? | 204 | ✓ | `UserController#revokeRole` |
| USR-010 | Users | POST | `/users/{id}/scopes` | UserController | assignScope | required | Yes | data-scope:manage | AssignScopeRequest | ? | 201 | ✓ | `UserController#assignScope` |
| USR-011 | Users | DELETE | `/users/{id}/scopes/{scopeId}` | UserController | removeScope | required | Yes | data-scope:manage | — | ? | 204 | ✓ | `UserController#removeScope` |
| USR-012 | Users | POST | `/users/{id}/password-reset` | UserController | triggerPasswordReset | required | Yes | user:manage | — | ? | 201 | ✓ | `UserController#triggerPasswordReset` |
| USR-013 | Users | POST | `/users/{id}/unlock` | UserController | unlock | required | Yes | user:manage | — | ? | 201 | ✓ | `UserController#unlock` |
| USR-014 | Users | GET | `/users/{id}/effective-permissions` | UserController | effectivePermissions | required | No | user:view | — | ? | 200 | ✓ | `UserController#effectivePermissions` |
| WF-001 | Workflow Configuration | GET | `/workflows` | WorkflowConfigurationController | list | required | No | workflow:view | — | ? | 200 | ✓ | `WorkflowConfigurationController#list` |
| WF-002 | Workflow Configuration | POST | `/workflows` | WorkflowConfigurationController | create | required | Yes | workflow:manage | SaveDefinitionRequest | ? | 201 | ✓ | `WorkflowConfigurationController#create` |
| WF-003 | Workflow Configuration | PUT | `/workflows/{definitionId}` | WorkflowConfigurationController | update | required | Yes | workflow:manage | SaveDefinitionRequest | ? | 200 | ✓ | `WorkflowConfigurationController#update` |
| WF-004 | Workflow Configuration | POST | `/workflows/{definitionId}/active` | WorkflowConfigurationController | setActive | required | Yes | workflow:manage | — | ? | 201 | ✓ | `WorkflowConfigurationController#setActive` |
| WF-005 | Workflow Configuration | GET | `/workflows/{definitionId}/versions` | WorkflowConfigurationController | versions | required | No | workflow:view | — | ? | 200 | ✓ | `WorkflowConfigurationController#versions` |
| WF-006 | Workflow Configuration | GET | `/workflows/versions/{versionId}` | WorkflowConfigurationController | version | required | No | workflow:view | — | ? | 200 | ✓ | `WorkflowConfigurationController#version` |
| WF-007 | Workflow Configuration | PUT | `/workflows/{definitionId}/draft` | WorkflowConfigurationController | saveDraft | required | Yes | workflow:manage | SaveDraftRequest | ? | 200 | ✓ | `WorkflowConfigurationController#saveDraft` |
| WF-008 | Workflow Configuration | POST | `/workflows/{definitionId}/draft/from-published` | WorkflowConfigurationController | clonePublished | required | Yes | workflow:manage | — | ? | 201 | ✓ | `WorkflowConfigurationController#clonePublished` |
| WF-009 | Workflow Configuration | POST | `/workflows/{definitionId}/draft/from-version/{sourceVersionId}` | WorkflowConfigurationController | rollbackIntoDraft | required | Yes | workflow:manage | — | ? | 201 | ✓ | `WorkflowConfigurationController#rollbackIntoDraft` |
| WF-010 | Workflow Configuration | GET | `/workflows/versions/compare` | WorkflowConfigurationController | compare | required | No | workflow:view | — | ? | 200 | ✓ | `WorkflowConfigurationController#compare` |
| WF-011 | Workflow Configuration | GET | `/workflows/versions/{versionId}/validate` | WorkflowConfigurationController | validate | required | No | workflow:view | — | ? | 200 | ✓ | `WorkflowConfigurationController#validate` |
| WF-012 | Workflow Configuration | POST | `/workflows/versions/{versionId}/publish` | WorkflowConfigurationController | publish | required | Yes | workflow:publish | — | ? | 200 | ✓ | `WorkflowConfigurationController#publish` |
| WF-013 | Workflow Configuration | POST | `/workflows/versions/{versionId}/retire` | WorkflowConfigurationController | retire | required | Yes | workflow:publish | — | ? | 200 | ✓ | `WorkflowConfigurationController#retire` |
