# EDAM Database Data Dictionary

Schema ownership: Flyway forward-only migrations (`src/main/resources/db/migration/V1..V27`). Hibernate `ddl-auto=validate` on every profile. The tables below are the set mapped by JPA `@Table` entities in the Phase 4J baseline at commit `f3a8bc4`.

- **Mapped tables:** 72
- **Migrations:** 28 (V1 → V27, including V4_1)
- **Primary keys:** UUID v7 via `uuid_generate_v7()` (V4_1)
- **Audit columns:** `created_by`, `created_at`, `last_modified_by`, `last_modified_at` on every `Auditable` entity.


## Approval Limits & Delegation

### `delegation_approver_steps` — `DelegationApproverStep`
- First migration referencing: **V7__financial_master_data_and_expense_configuration.sql**
- Source: `com/edam/delegation/model/DelegationApproverStep.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `step_order` | `stepOrder` | short | false |  |
| `approver_type` | `approverType` | ApproverType | false | len=40 |
| `required_role_id` | `requiredRoleId` | UUID | true |  |
| `department_scope` | `departmentScope` | String | true | len=64 |
| `delegation_rule_id` | `delegationRule` | DelegationRule | true | FK → `DelegationRule` |

### `department_approval_limits` — `DepartmentApprovalLimit`
- First migration referencing: **V16__request_numbering_and_limit_support.sql**
- Source: `com/edam/approvallimits/model/DepartmentApprovalLimit.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `department_id` | `departmentId` | UUID | false |  |
| `user_id` | `userId` | UUID | true |  |
| `currency_id` | `currencyId` | UUID | false |  |
| `approval_limit` | `approvalLimit` | BigDecimal | false | prec=19 |
| `effective_from` | `effectiveFrom` | Instant | false |  |
| `effective_to` | `effectiveTo` | Instant | true |  |

### `exceeding_limit_rules` — `ExceedingLimitRule`
- First migration referencing: **V16__request_numbering_and_limit_support.sql**
- Source: `com/edam/approvallimits/model/ExceedingLimitRule.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `nature_id` | `natureId` | UUID | false |  |
| `currency_id` | `currencyId` | UUID | false |  |
| `threshold_amount` | `thresholdAmount` | BigDecimal | false | prec=19 |

### `expense_delegation_rules` — `DelegationRule`
- First migration referencing: **V23__phase4e_admin_configuration.sql**
- Source: `com/edam/delegation/model/DelegationRule.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `category_id` | `categoryId` | UUID | false |  |
| `subcategory_id` | `subcategoryId` | UUID | true |  |
| `nature_id` | `natureId` | UUID | false |  |
| `request_type_id` | `requestTypeId` | UUID | true |  |
| `maximum_amount` | `maximumAmount` | BigDecimal | true | prec=19 |
| `currency_id` | `currencyId` | UUID | false |  |
| `effective_from` | `effectiveFrom` | Instant | false |  |
| `effective_to` | `effectiveTo` | Instant | true |  |

### `recommending_authorities` — `RecommendingAuthority`
- First migration referencing: **V16__request_numbering_and_limit_support.sql**
- Source: `com/edam/approvallimits/model/RecommendingAuthority.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `department_id` | `departmentId` | UUID | false |  |
| `user_id` | `userId` | UUID | false |  |
| `effective_from` | `effectiveFrom` | Instant | false |  |
| `effective_to` | `effectiveTo` | Instant | true |  |

## Audit

### `audit_events` — `AuditEvent`
- First migration referencing: **V10__business_rules_audit_notifications.sql**
- Source: `com/edam/common/audit/AuditEvent.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `entity_type` | `entityType` | String | false | len=100 |
| `entity_id` | `entityId` | String | true | len=100 |
| `action` | `action` | AuditAction | false | len=60 |
| `old_values` | `oldValues` | String | true |  |
| `new_values` | `newValues` | String | true |  |
| `performed_by` | `performedBy` | UUID | true |  |
| `performed_by_username` | `performedByUsername` | String | true | len=100 |
| `ip_address` | `ipAddress` | String | true | len=64 |
| `user_agent` | `userAgent` | String | true | len=500 |
| `correlation_id` | `correlationId` | String | true | len=64 |
| `created_at` | `createdAt` | Instant | false |  |

## Branding & Configuration

### `application_settings` — `ApplicationSetting`
- First migration referencing: **V15__installation_bootstrap_and_auth.sql**
- Source: `com/edam/configuration/model/ApplicationSetting.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `setting_key` | `key` | String | false | len=150 |
| `display_name` | `displayName` | String | true | len=200 |
| `description` | `description` | String | true |  |
| `setting_value` | `valueJson` | String | true |  |
| `default_value` | `defaultValueJson` | String | true |  |
| `created_by` | `createdBy` | String | true | len=100 |

### `branding_themes` — `BrandingTheme`
- First migration referencing: **V20__phase4b_branding_themes_settings_featureflags.sql**
- Source: `com/edam/configuration/model/BrandingTheme.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `theme_name` | `themeName` | String | false | len=100 |
| `description` | `description` | String | true |  |
| `primary_color` | `primaryColor` | String | true | len=9 |
| `secondary_color` | `secondaryColor` | String | true | len=9 |
| `accent_color` | `accentColor` | String | true | len=9 |
| `background_color` | `backgroundColor` | String | true | len=9 |
| `surface_color` | `surfaceColor` | String | true | len=9 |
| `text_color` | `textColor` | String | true | len=9 |
| `heading_color` | `headingColor` | String | true | len=9 |
| `nav_bg_color` | `navBgColor` | String | true | len=9 |
| `nav_text_color` | `navTextColor` | String | true | len=9 |
| `button_primary_color` | `buttonPrimaryColor` | String | true | len=9 |
| `button_primary_text` | `buttonPrimaryText` | String | true | len=9 |
| `success_color` | `successColor` | String | true | len=9 |
| `warning_color` | `warningColor` | String | true | len=9 |
| `error_color` | `errorColor` | String | true | len=9 |
| `info_color` | `infoColor` | String | true | len=9 |

### `feature_flags` — `FeatureFlag`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/configuration/model/FeatureFlag.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `feature_key` | `featureKey` | String | false | len=100 |
| `display_name` | `displayName` | String | false | len=150 |
| `description` | `description` | String | true |  |
| `configuration` | `configurationJson` | String | true |  |
| `created_by` | `createdBy` | String | true | len=100 |

### `theme_tokens` — `ThemeToken`
- First migration referencing: **V20__phase4b_branding_themes_settings_featureflags.sql**
- Source: `com/edam/configuration/model/ThemeToken.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `token_key` | `tokenKey` | String | false | len=100 |
| `token_value` | `tokenValue` | String | false | len=500 |
| `theme_id` | `theme` | BrandingTheme | true | FK → `BrandingTheme` |

## Business Rules

### `business_rule_sets` — `BusinessRuleSet`
- First migration referencing: **V10__business_rules_audit_notifications.sql**
- Source: `com/edam/businessrules/model/BusinessRuleSet.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `rule_set_code` | `code` | String | false | len=64 |
| `rule_set_name` | `name` | String | false | len=200 |
| `module` | `module` | String | false | len=60 |
| `description` | `description` | String | true |  |

### `business_rule_versions` — `BusinessRuleVersion`
- First migration referencing: **V10__business_rules_audit_notifications.sql**
- Source: `com/edam/businessrules/model/BusinessRuleVersion.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `version_number` | `versionNumber` | int | false |  |
| `effective_from` | `effectiveFrom` | Instant | true |  |
| `effective_to` | `effectiveTo` | Instant | true |  |
| `published_by` | `publishedBy` | UUID | true |  |
| `published_at` | `publishedAt` | Instant | true |  |
| `rule_set_id` | `ruleSet` | BusinessRuleSet | true | FK → `BusinessRuleSet` |

### `business_rules` — `BusinessRule`
- First migration referencing: **V10__business_rules_audit_notifications.sql**
- Source: `com/edam/businessrules/model/BusinessRule.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `rule_code` | `code` | String | false | len=64 |
| `rule_name` | `name` | String | false | len=200 |
| `condition_definition` | `conditionDefinition` | String | false |  |
| `action_definition` | `actionDefinition` | String | false |  |
| `rule_version_id` | `ruleVersion` | BusinessRuleVersion | true | FK → `BusinessRuleVersion` |

## Committees

### `committee_level_members` — `CommitteeLevelMember`
- First migration referencing: **V8__committees_and_workflow_engine.sql**
- Source: `com/edam/committee/model/CommitteeLevelMember.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `is_mandatory` | `mandatory` | boolean | false |  |
| `approval_order` | `approvalOrder` | Short | true |  |
| `committee_level_id` | `level` | CommitteeLevel | true | FK → `CommitteeLevel` |
| `committee_member_id` | `member` | CommitteeMember | true | FK → `CommitteeMember` |

### `committee_levels` — `CommitteeLevel`
- First migration referencing: **V8__committees_and_workflow_engine.sql**
- Source: `com/edam/committee/model/CommitteeLevel.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `level_number` | `levelNumber` | short | false |  |
| `level_name` | `levelName` | String | false | len=200 |
| `minimum_amount` | `minimumAmount` | BigDecimal | false | prec=19 |
| `maximum_amount` | `maximumAmount` | BigDecimal | true | prec=19 |
| `currency_id` | `currencyId` | UUID | false |  |
| `minimum_approvals` | `minimumApprovals` | short | false |  |
| `committee_id` | `committee` | ExpenseCommittee | true | FK → `ExpenseCommittee` |

### `committee_members` — `CommitteeMember`
- First migration referencing: **V8__committees_and_workflow_engine.sql**
- Source: `com/edam/committee/model/CommitteeMember.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `user_id` | `userId` | UUID | false |  |
| `effective_from` | `effectiveFrom` | Instant | false |  |
| `effective_to` | `effectiveTo` | Instant | true |  |
| `committee_id` | `committee` | ExpenseCommittee | true | FK → `ExpenseCommittee` |

### `expense_committees` — `ExpenseCommittee`
- First migration referencing: **V8__committees_and_workflow_engine.sql**
- Source: `com/edam/committee/model/ExpenseCommittee.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `committee_code` | `committeeCode` | String | false | len=64 |
| `committee_name` | `committeeName` | String | false | len=200 |
| `description` | `description` | String | true |  |

## Communication

### `comm_email_template_versions` — `EmailTemplateVersion`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/EmailTemplateVersion.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `template_id` | `templateId` | UUID | false |  |
| `version_number` | `versionNumber` | int | false |  |
| `subject` | `subject` | String | false | len=500 |
| `html_body` | `htmlBody` | String | false |  |
| `text_body` | `textBody` | String | true |  |
| `locale` | `locale` | String | false | len=16 |
| `change_note` | `changeNote` | String | true |  |
| `created_by` | `createdBy` | UUID | true |  |
| `created_at` | `createdAt` | Instant | false |  |

### `comm_email_templates` — `EmailTemplate`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/EmailTemplate.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `code` | `code` | String | false | len=80 |
| `display_name` | `displayName` | String | false | len=200 |
| `description` | `description` | String | true |  |
| `event_code` | `eventCode` | String | false | len=80 |
| `subject` | `subject` | String | false | len=500 |
| `html_body` | `htmlBody` | String | false |  |
| `text_body` | `textBody` | String | true |  |
| `created_by` | `createdBy` | UUID | true |  |
| `updated_by` | `updatedBy` | UUID | true |  |
| `created_at` | `createdAt` | Instant | false |  |
| `updated_at` | `updatedAt` | Instant | false |  |
| `version` | `version` | long | false |  |

### `comm_notification_event_configs` — `NotificationEventConfig`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/NotificationEventConfig.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `event_code` | `eventCode` | String | false | len=80 |
| `email_template_id` | `emailTemplateId` | UUID | true |  |
| `recipient_groups` | `recipientGroups` | String | false |  |
| `updated_by` | `updatedBy` | UUID | true |  |
| `updated_at` | `updatedAt` | Instant | false |  |
| `version` | `version` | long | false |  |

### `comm_notification_outbox` — `NotificationOutbox`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/NotificationOutbox.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `idempotency_key` | `idempotencyKey` | String | false | len=120 |
| `event_code` | `eventCode` | String | false | len=80 |
| `channel` | `channel` | Channel | false | len=16 |
| `user_id` | `userId` | UUID | true |  |
| `recipient_email` | `recipientEmail` | String | true | len=255 |
| `template_version_id` | `templateVersionId` | UUID | true |  |
| `subject` | `subject` | String | true | len=500 |
| `body_html` | `bodyHtml` | String | true |  |
| `body_text` | `bodyText` | String | true |  |
| `payload_json` | `payloadJson` | String | true |  |
| `next_attempt_at` | `nextAttemptAt` | Instant | false |  |
| `last_attempt_at` | `lastAttemptAt` | Instant | true |  |
| `last_error` | `lastError` | String | true |  |
| `locked_by` | `lockedBy` | String | true | len=100 |
| `locked_at` | `lockedAt` | Instant | true |  |
| `sent_at` | `sentAt` | Instant | true |  |
| `created_at` | `createdAt` | Instant | false |  |

### `comm_notification_preference_events` — `NotificationPreferenceEvent`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/NotificationPreferenceEvent.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `preference_id` | `preferenceId` | UUID | false |  |
| `event_code` | `eventCode` | String | false | len=80 |

### `comm_notification_preferences` — `NotificationPreference`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/NotificationPreference.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `user_id` | `userId` | UUID | false |  |
| `updated_at` | `updatedAt` | Instant | false |  |

### `comm_notifications` — `Notification`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/Notification.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `user_id` | `userId` | UUID | false |  |
| `event_code` | `eventCode` | String | false | len=80 |
| `title` | `title` | String | false | len=500 |
| `body` | `body` | String | false |  |
| `payload_json` | `payloadJson` | String | true |  |
| `is_read` | `read` | boolean | false |  |
| `read_at` | `readAt` | Instant | true |  |
| `created_at` | `createdAt` | Instant | false |  |

### `comm_smtp_configuration` — `SmtpConfiguration`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/SmtpConfiguration.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `host` | `host` | String | false | len=255 |
| `port` | `port` | int | false |  |
| `username` | `username` | String | true | len=255 |
| `encrypted_password` | `encryptedPassword` | String | true |  |
| `from_email` | `fromEmail` | String | false | len=255 |
| `from_display_name` | `fromDisplayName` | String | true | len=255 |
| `reply_to` | `replyTo` | String | true | len=255 |
| `last_test_at` | `lastTestAt` | Instant | true |  |
| `last_test_success` | `lastTestSuccess` | Boolean | true |  |
| `last_test_message` | `lastTestMessage` | String | true |  |
| `created_at` | `createdAt` | Instant | false |  |
| `updated_at` | `updatedAt` | Instant | false |  |
| `version` | `version` | long | false |  |

### `comm_smtp_test_history` — `SmtpTestHistory`
- First migration referencing: **V22__phase4d_communication_management.sql**
- Source: `com/edam/communication/model/SmtpTestHistory.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `smtp_config_id` | `smtpConfigId` | UUID | false |  |
| `recipient` | `recipient` | String | false | len=255 |
| `success` | `success` | boolean | false |  |
| `diagnostic` | `diagnostic` | String | true |  |
| `sent_at` | `sentAt` | Instant | false |  |

## Documents

### `doc_scan_events` — `DocumentScanEvent`
- First migration referencing: **V21__phase4c_document_management_cloudinary.sql**
- Source: `com/edam/documents/model/DocumentScanEvent.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `media_asset_id` | `mediaAssetId` | UUID | false |  |
| `scan_status` | `scanStatus` | String | false | len=32 |
| `scanner` | `scanner` | String | false | len=60 |
| `scanner_version` | `scannerVersion` | String | true | len=60 |
| `threat_name` | `threatName` | String | true | len=255 |
| `scan_finished_at` | `scanFinishedAt` | Instant | true |  |
| `detail` | `detail` | String | true |  |

### `media_assets` — `MediaAsset`
- First migration referencing: **V21__phase4c_document_management_cloudinary.sql**
- Source: `com/edam/documents/model/MediaAsset.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `original_file_name` | `originalFileName` | String | false | len=255 |
| `stored_file_name` | `storedFileName` | String | true | len=255 |
| `storage_provider` | `storageProvider` | String | false | len=32 |
| `storage_reference` | `storageReference` | String | false | len=500 |
| `public_id` | `publicId` | String | true | len=500 |
| `asset_id` | `assetId` | String | true | len=100 |
| `secure_url` | `secureUrl` | String | true |  |
| `resource_type` | `resourceType` | String | true | len=32 |
| `file_format` | `fileFormat` | String | true | len=32 |
| `mime_type` | `mimeType` | String | true | len=150 |
| `file_size` | `fileSize` | long | false |  |
| `checksum` | `checksum` | String | true | len=64 |
| `description` | `description` | String | true |  |
| `scanned_at` | `scannedAt` | Instant | true |  |
| `uploaded_by` | `uploadedBy` | UUID | true |  |
| `deleted_at` | `deletedAt` | Instant | true |  |

## Expense Configuration

### `expense_categories` — `ExpenseCategory`
- First migration referencing: **V7__financial_master_data_and_expense_configuration.sql**
- Source: `com/edam/expenseconfiguration/model/ExpenseCategory.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `category_code` | `code` | String | false | len=64 |
| `category_name` | `name` | String | false | len=200 |
| `description` | `description` | String | true |  |

### `expense_natures` — `ExpenseNature`
- First migration referencing: **V7__financial_master_data_and_expense_configuration.sql**
- Source: `com/edam/expenseconfiguration/model/ExpenseNature.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `nature_code` | `code` | String | false | len=64 |
| `nature_name` | `name` | String | false | len=200 |
| `description` | `description` | String | true |  |

### `expense_request_types` — `ExpenseRequestType`
- First migration referencing: **V7__financial_master_data_and_expense_configuration.sql**
- Source: `com/edam/expenseconfiguration/model/ExpenseRequestType.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `type_code` | `code` | String | false | len=64 |
| `type_name` | `name` | String | false | len=200 |
| `description` | `description` | String | true |  |

### `expense_subcategories` — `ExpenseSubcategory`
- First migration referencing: **V7__financial_master_data_and_expense_configuration.sql**
- Source: `com/edam/expenseconfiguration/model/ExpenseSubcategory.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `category_id` | `categoryId` | UUID | false |  |
| `subcategory_code` | `code` | String | false | len=64 |
| `subcategory_name` | `name` | String | false | len=200 |
| `description` | `description` | String | true |  |

## Identity & Access

### `installation_state` — `InstallationState`
- First migration referencing: **V15__installation_bootstrap_and_auth.sql**
- Source: `com/edam/identity/model/InstallationState.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `bootstrapped` | `bootstrapped` | boolean | false |  |
| `bootstrapped_at` | `bootstrappedAt` | Instant | true |  |
| `bootstrapped_by` | `bootstrappedBy` | UUID | true |  |
| `bootstrap_attempts` | `bootstrapAttempts` | int | false |  |
| `last_failed_attempt_at` | `lastFailedAttemptAt` | Instant | true |  |

### `login_attempts` — `LoginAttempt`
- First migration referencing: **V15__installation_bootstrap_and_auth.sql**
- Source: `com/edam/identity/model/LoginAttempt.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `username` | `username` | String | false | len=100 |
| `ip_address` | `ipAddress` | String | true | len=64 |
| `success` | `success` | boolean | false |  |
| `failure_reason` | `failureReason` | String | true | len=200 |
| `attempted_at` | `attemptedAt` | Instant | false |  |

### `password_reset_tokens` — `PasswordResetToken`
- First migration referencing: **V15__installation_bootstrap_and_auth.sql**
- Source: `com/edam/identity/model/PasswordResetToken.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `user_id` | `userId` | UUID | false |  |
| `token_hash` | `tokenHash` | String | false | len=64 |
| `created_at` | `createdAt` | Instant | false |  |
| `expires_at` | `expiresAt` | Instant | false |  |
| `used_at` | `usedAt` | Instant | true |  |

### `permissions` — `Permission`
- First migration referencing: **V11__reporting_integrations_reliability.sql**
- Source: `com/edam/rolemanagement/model/Permission.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `code` | `code` | String | false | len=150 |
| `module` | `module` | String | false | len=100 |
| `description` | `description` | String | true |  |
| `created_at` | `createdAt` | Instant | false |  |

### `roles` — `Role`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/rolemanagement/model/Role.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `name` | `name` | String | false | len=100 |
| `code` | `code` | String | false | len=100 |
| `description` | `description` | String | true |  |
| `is_system_role` | `systemRole` | boolean | false |  |

### `user_data_scopes` — `UserDataScope`
- First migration referencing: **V5__access_management.sql**
- Source: `com/edam/usermanagement/model/UserDataScope.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `user_id` | `userId` | UUID | false |  |
| `scope_reference_id` | `scopeReferenceId` | UUID | true |  |
| `created_at` | `createdAt` | Instant | false |  |
| `created_by` | `createdBy` | String | true | len=100 |

### `user_roles` — `UserRole`
- First migration referencing: **V5__access_management.sql**
- Source: `com/edam/rolemanagement/model/UserRole.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `user_id` | `userId` | UUID | false |  |
| `role_id` | `roleId` | UUID | false |  |
| `assigned_by` | `assignedBy` | UUID | true |  |
| `assigned_at` | `assignedAt` | Instant | false |  |
| `expires_at` | `expiresAt` | Instant | true |  |

### `users` — `User`
- First migration referencing: **V10__business_rules_audit_notifications.sql**
- Source: `com/edam/usermanagement/model/User.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `employee_code` | `employeeCode` | String | true | len=50 |
| `first_name` | `firstName` | String | false | len=100 |
| `last_name` | `lastName` | String | false | len=100 |
| `email` | `email` | String | false | len=255 |
| `username` | `username` | String | false | len=100 |
| `password_hash` | `passwordHash` | String | true | len=255 |
| `department_id` | `departmentId` | UUID | true |  |
| `branch_id` | `branchId` | UUID | true |  |
| `designation` | `designation` | String | true | len=150 |
| `manager_id` | `managerId` | UUID | true |  |
| `last_login_at` | `lastLoginAt` | Instant | true |  |
| `failed_login_count` | `failedLoginCount` | int | false |  |
| `locked_until` | `lockedUntil` | Instant | true |  |
| `password_changed_at` | `passwordChangedAt` | Instant | true |  |

## Integrations

### `integration_configs` — `IntegrationConfig`
- First migration referencing: **V11__reporting_integrations_reliability.sql**
- Source: `com/edam/integrations/model/IntegrationConfig.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `integration_code` | `code` | String | false | len=64 |
| `integration_name` | `name` | String | false | len=200 |
| `integration_type` | `providerType` | String | false | len=40 |
| `description` | `description` | String | true | len=1000 |
| `endpoint_url` | `endpointUrl` | String | true | len=500 |
| `secret_reference` | `secretReference` | String | true | len=300 |
| `encrypted_secret` | `encryptedSecret` | String | true |  |
| `configuration` | `configurationJson` | String | true |  |
| `last_test_at` | `lastTestAt` | Instant | true |  |
| `last_test_success` | `lastTestSuccess` | Boolean | true |  |
| `last_test_message` | `lastTestMessage` | String | true | len=500 |
| `last_test_duration_ms` | `lastTestDurationMs` | Integer | true |  |
| `connection_checked_at` | `connectionCheckedAt` | Instant | true |  |
| `created_at` | `createdAt` | Instant | false |  |
| `created_by` | `createdBy` | String | true | len=100 |
| `updated_at` | `updatedAt` | Instant | false |  |
| `updated_by` | `updatedBy` | String | true | len=100 |
| `version` | `version` | long | false |  |

### `integration_executions` — `IntegrationExecution`
- First migration referencing: **V26__phase4h_integration_foundation.sql**
- Source: `com/edam/integrations/model/IntegrationExecution.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `integration_config_id` | `integrationConfigId` | UUID | false |  |
| `operation_type` | `operationType` | String | false | len=60 |
| `idempotency_key` | `idempotencyKey` | String | true | len=200 |
| `external_reference` | `externalReference` | String | true | len=300 |
| `claimed_by` | `claimedBy` | String | true | len=100 |
| `claimed_at` | `claimedAt` | Instant | true |  |
| `claim_expires_at` | `claimExpiresAt` | Instant | true |  |
| `started_at` | `startedAt` | Instant | true |  |
| `completed_at` | `completedAt` | Instant | true |  |
| `next_retry_at` | `nextRetryAt` | Instant | true |  |
| `request_metadata` | `requestMetadata` | String | true |  |
| `response_metadata` | `responseMetadata` | String | true |  |
| `error_code` | `errorCode` | String | true | len=60 |
| `error_message` | `errorMessage` | String | true | len=1000 |
| `triggered_by` | `triggeredBy` | UUID | true |  |
| `created_at` | `createdAt` | Instant | false |  |
| `updated_at` | `updatedAt` | Instant | false |  |
| `version` | `version` | long | false |  |

## Master Data

### `currencies` — `Currency`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/masterdata/model/Currency.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `currency_code` | `code` | String | false | len=3 |
| `currency_name` | `name` | String | false | len=100 |
| `symbol` | `symbol` | String | true | len=10 |

### `exchange_rates` — `ExchangeRate`
- First migration referencing: **V23__phase4e_admin_configuration.sql**
- Source: `com/edam/masterdata/model/ExchangeRate.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `from_currency_id` | `fromCurrencyId` | UUID | false |  |
| `to_currency_id` | `toCurrencyId` | UUID | false |  |
| `exchange_rate` | `rate` | BigDecimal | false | prec=19 |
| `effective_from` | `effectiveFrom` | Instant | false |  |
| `effective_to` | `effectiveTo` | Instant | true |  |
| `created_by` | `createdBy` | UUID | true |  |
| `created_at` | `createdAt` | Instant | false |  |

### `tax_authorities` — `TaxAuthority`
- First migration referencing: **V23__phase4e_admin_configuration.sql**
- Source: `com/edam/masterdata/model/TaxAuthority.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `authority_name` | `name` | String | false | len=200 |
| `authority_code` | `code` | String | false | len=64 |
| `description` | `description` | String | true |  |

### `vendors` — `Vendor`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/masterdata/model/Vendor.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `vendor_code` | `code` | String | false | len=64 |
| `vendor_name` | `name` | String | false | len=300 |
| `tax_number` | `taxNumber` | String | true | len=64 |
| `contact_person` | `contactPerson` | String | true | len=200 |
| `email` | `email` | String | true | len=255 |
| `phone` | `phone` | String | true | len=50 |
| `address` | `address` | String | true |  |

## Organization

### `branches` — `Branch`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/organization/model/Branch.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `organization_unit_id` | `organizationUnitId` | UUID | false |  |
| `branch_code` | `code` | String | false | len=64 |
| `branch_name` | `name` | String | false | len=200 |
| `address` | `address` | String | true |  |

### `departments` — `Department`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/organization/model/Department.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `organization_unit_id` | `organizationUnitId` | UUID | false |  |
| `department_code` | `code` | String | false | len=64 |
| `department_name` | `name` | String | false | len=200 |
| `head_user_id` | `headUserId` | UUID | true |  |

### `organization_profile` — `OrganizationProfile`
- First migration referencing: **V13__retire_provisional_access_and_configuration.sql**
- Source: `com/edam/organization/model/OrganizationProfile.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `organization_name` | `organizationName` | String | false | len=200 |
| `display_name` | `displayName` | String | true | len=200 |
| `legal_name` | `legalName` | String | true | len=300 |
| `organization_code` | `organizationCode` | String | true | len=60 |
| `contact_email` | `contactEmail` | String | true | len=255 |
| `contact_phone` | `contactPhone` | String | true | len=40 |
| `website` | `website` | String | true | len=255 |
| `address_line1` | `addressLine1` | String | true | len=300 |
| `address_line2` | `addressLine2` | String | true | len=300 |
| `address_city` | `addressCity` | String | true | len=120 |
| `address_state` | `addressState` | String | true | len=120 |
| `address_postal_code` | `addressPostalCode` | String | true | len=30 |
| `address_country` | `addressCountry` | String | true | len=100 |
| `support_email` | `supportEmail` | String | true | len=255 |
| `logo_asset_id` | `logoAssetId` | UUID | true |  |
| `favicon_asset_id` | `faviconAssetId` | UUID | true |  |
| `default_currency_id` | `defaultCurrencyId` | UUID | true |  |

### `organization_unit_types` — `OrganizationUnitType`
- First migration referencing: **V12__permission_catalogue_and_roles.sql**
- Source: `com/edam/organization/model/OrganizationUnitType.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `code` | `code` | String | false | len=50 |
| `name` | `name` | String | false | len=100 |
| `description` | `description` | String | true |  |

### `organization_units` — `OrganizationUnit`
- First migration referencing: **V5__access_management.sql**
- Source: `com/edam/organization/model/OrganizationUnit.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `parent_unit_id` | `parentUnitId` | UUID | true |  |
| `unit_type_id` | `unitTypeId` | UUID | false |  |
| `code` | `code` | String | false | len=64 |
| `name` | `name` | String | false | len=200 |
| `description` | `description` | String | true |  |

## Requests

### `capex_budget_summaries` — `CapexBudgetSummary`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/CapexBudgetSummary.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `budget_reference` | `budgetReference` | String | false | len=150 |
| `allocated_amount` | `allocatedAmount` | BigDecimal | false | prec=19 |
| `consumed_amount` | `consumedAmount` | BigDecimal | false | prec=19 |
| `available_amount` | `availableAmount` | BigDecimal | false | prec=19 |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `capex_request_details` — `CapexRequestDetail`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/CapexRequestDetail.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `request_type` | `requestType` | String | true | len=50 |
| `sta_id` | `staId` | UUID | true |  |
| `invoice_total` | `invoiceTotal` | BigDecimal | true | prec=19 |
| `approval_amount` | `approvalAmount` | BigDecimal | true | prec=19 |
| `total_charges` | `totalCharges` | BigDecimal | true | prec=19 |
| `budget_amount` | `budgetAmount` | BigDecimal | true | prec=19 |
| `unbudgeted_reason` | `unbudgetedReason` | String | true | len=500 |
| `over_budget_reason` | `overBudgetReason` | String | true | len=500 |
| `classification` | `classification` | String | true | len=100 |
| `warranty_start_date` | `warrantyStartDate` | LocalDate | true |  |
| `warranty_end_date` | `warrantyEndDate` | LocalDate | true |  |
| `version` | `version` | long | false |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `capex_sta_details` — `CapexStaDetail`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/CapexStaDetail.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `tax_authority_id` | `taxAuthorityId` | UUID | false |  |
| `sta_reference` | `staReference` | String | true | len=150 |
| `tax_amount` | `taxAmount` | BigDecimal | false | prec=19 |
| `description` | `description` | String | true |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `expense_allocations` — `ExpenseAllocation`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/ExpenseAllocation.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `department_id` | `departmentId` | UUID | false |  |
| `branch_id` | `branchId` | UUID | true |  |
| `cost_center` | `costCenter` | String | true | len=100 |
| `allocation_amount` | `allocationAmount` | BigDecimal | false | prec=19 |
| `allocation_percentage` | `allocationPercentage` | BigDecimal | true | prec=9 |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `expense_invoices` — `ExpenseInvoice`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/ExpenseInvoice.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `invoice_number` | `invoiceNumber` | String | false | len=100 |
| `invoice_date` | `invoiceDate` | LocalDate | false |  |
| `invoice_amount` | `invoiceAmount` | BigDecimal | false | prec=19 |
| `currency_id` | `currencyId` | UUID | false |  |
| `vendor_id` | `vendorId` | UUID | true |  |
| `media_asset_id` | `mediaAssetId` | UUID | true |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `expense_previous_summaries` — `ExpensePreviousSummary`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/ExpensePreviousSummary.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `period` | `period` | String | false | len=40 |
| `previous_amount` | `previousAmount` | BigDecimal | true | prec=19 |
| `approved_amount` | `approvedAmount` | BigDecimal | true | prec=19 |
| `description` | `description` | String | true |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `expense_quotations` — `ExpenseQuotation`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/ExpenseQuotation.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `vendor_id` | `vendorId` | UUID | true |  |
| `quotation_number` | `quotationNumber` | String | false | len=100 |
| `quotation_date` | `quotationDate` | LocalDate | false |  |
| `quotation_amount` | `quotationAmount` | BigDecimal | false | prec=19 |
| `currency_id` | `currencyId` | UUID | false |  |
| `media_asset_id` | `mediaAssetId` | UUID | true |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `expense_request_items` — `ExpenseRequestItem`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/ExpenseRequestItem.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `item_description` | `itemDescription` | String | false | len=500 |
| `quantity` | `quantity` | BigDecimal | false | prec=19 |
| `unit_price` | `unitPrice` | BigDecimal | false | prec=19 |
| `total_amount` | `totalAmount` | BigDecimal | false | prec=19 |
| `warranty_date` | `warrantyDate` | LocalDate | true |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

### `expense_requests` — `ExpenseRequest`
- First migration referencing: **V16__request_numbering_and_limit_support.sql**
- Source: `com/edam/expenserequest/model/ExpenseRequest.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `request_number` | `requestNumber` | String | false | len=100 |
| `request_type_id` | `requestTypeId` | UUID | false |  |
| `expense_category_id` | `expenseCategoryId` | UUID | false |  |
| `expense_subcategory_id` | `expenseSubcategoryId` | UUID | true |  |
| `expense_nature_id` | `expenseNatureId` | UUID | false |  |
| `requested_by` | `requestedBy` | UUID | false |  |
| `department_id` | `departmentId` | UUID | false |  |
| `branch_id` | `branchId` | UUID | true |  |
| `currency_id` | `currencyId` | UUID | false |  |
| `requested_amount` | `requestedAmount` | BigDecimal | false | prec=19 |
| `base_currency_amount` | `baseCurrencyAmount` | BigDecimal | false | prec=19 |
| `exchange_rate_id` | `exchangeRateId` | UUID | true |  |
| `justification` | `justification` | String | true |  |
| `current_workflow_instance_id` | `currentWorkflowInstanceId` | UUID | true |  |
| `submitted_at` | `submittedAt` | Instant | true |  |
| `cancelled_at` | `cancelledAt` | Instant | true |  |
| `cancelled_by` | `cancelledBy` | UUID | true |  |
| `cancel_reason` | `cancelReason` | String | true | len=500 |

### `opex_request_details` — `OpexRequestDetail`
- First migration referencing: **V9__expense_requests.sql**
- Source: `com/edam/expenserequest/model/OpexRequestDetail.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `is_recurring` | `recurring` | boolean | false |  |
| `recurrence_frequency` | `recurrenceFrequency` | RecurrenceFrequency | true | len=50 |
| `non_recurring_justification` | `nonRecurringJustification` | String | true |  |
| `accrued_amount` | `accruedAmount` | BigDecimal | true | prec=19 |
| `previously_approved_amount` | `previouslyApprovedAmount` | BigDecimal | true | prec=19 |
| `ytd_amount` | `ytdAmount` | BigDecimal | true | prec=19 |
| `mtd_amount` | `mtdAmount` | BigDecimal | true | prec=19 |
| `payment_terms` | `paymentTerms` | String | true |  |
| `budget_available` | `budgetAvailable` | BigDecimal | true | prec=19 |
| `budget_reference` | `budgetReference` | String | true | len=150 |
| `version` | `version` | long | false |  |
| `expense_request_id` | `request` | ExpenseRequest | true | FK → `ExpenseRequest` |

## Requests & Documents

### `request_documents` — `RequestDocument`
- First migration referencing: **V16__request_numbering_and_limit_support.sql**
- Source: `com/edam/expenserequest/model/RequestDocument.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `expense_request_id` | `expenseRequestId` | UUID | false |  |
| `media_asset_id` | `mediaAssetId` | UUID | false |  |
| `document_type` | `documentType` | String | false | len=60 |
| `is_mandatory` | `mandatory` | boolean | false |  |
| `required_by_rule` | `requiredByRule` | String | true | len=150 |
| `uploaded_by` | `uploadedBy` | UUID | true |  |
| `description` | `description` | String | true |  |
| `uploaded_at` | `uploadedAt` | Instant | false |  |

## System

### `idempotency_records` — `IdempotencyRecord`
- First migration referencing: **V11__reporting_integrations_reliability.sql**
- Source: `com/edam/common/api/IdempotencyRecord.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `idempotency_key` | `keyHash` | String | false | len=200 |
| `operation_type` | `operationType` | String | false | len=120 |
| `actor_id` | `actorId` | UUID | false |  |
| `request_hash` | `requestHash` | String | false | len=64 |
| `response_snapshot` | `responseSnapshot` | String | true |  |
| `processing_status` | `status` | Status | false | len=20 |
| `response_status` | `responseStatus` | Integer | true |  |
| `expires_at` | `expiresAt` | Instant | false |  |
| `created_at` | `createdAt` | Instant | false |  |

## Workflow

### `workflow_actions` — `WorkflowAction`
- First migration referencing: **V8__committees_and_workflow_engine.sql**
- Source: `com/edam/workflow/model/WorkflowAction.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `workflow_task_id` | `workflowTaskId` | UUID | false |  |
| `action_code` | `actionCode` | ActionCode | false | len=40 |
| `performed_by` | `performedBy` | UUID | false |  |
| `remarks` | `remarks` | String | true | len=2000 |
| `previous_state_id` | `previousStateId` | UUID | true |  |
| `next_state_id` | `nextStateId` | UUID | true |  |
| `action_at` | `actionAt` | Instant | false |  |
| `correlation_id` | `correlationId` | String | true | len=64 |

### `workflow_definitions` — `WorkflowDefinition`
- First migration referencing: **V17__workflow_configuration_and_approval_support.sql**
- Source: `com/edam/workflow/model/WorkflowDefinition.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `workflow_code` | `code` | String | false | len=64 |
| `workflow_name` | `name` | String | false | len=200 |
| `description` | `description` | String | true |  |
| `request_type_id` | `requestTypeId` | UUID | true |  |

### `workflow_instances` — `WorkflowInstance`
- First migration referencing: **V17__workflow_configuration_and_approval_support.sql**
- Source: `com/edam/workflow/model/WorkflowInstance.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `workflow_definition_id` | `workflowDefinitionId` | UUID | false |  |
| `workflow_version_id` | `workflowVersionId` | UUID | false |  |
| `expense_request_id` | `expenseRequestId` | UUID | false |  |
| `current_state_id` | `currentStateId` | UUID | true |  |
| `started_at` | `startedAt` | Instant | false |  |
| `started_by` | `startedBy` | UUID | true |  |
| `completed_at` | `completedAt` | Instant | true |  |

### `workflow_states` — `WorkflowState`
- First migration referencing: **V23__phase4e_admin_configuration.sql**
- Source: `com/edam/workflow/model/WorkflowState.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `state_code` | `stateCode` | String | false | len=64 |
| `state_name` | `stateName` | String | false | len=150 |
| `state_type` | `stateType` | StateType | false | len=20 |
| `is_initial` | `initial` | boolean | false |  |
| `is_terminal` | `terminal` | boolean | false |  |
| `workflow_version_id` | `workflowVersion` | WorkflowVersion | true | FK → `WorkflowVersion` |

### `workflow_step_approvers` — `WorkflowStepApprover`
- First migration referencing: **V8__committees_and_workflow_engine.sql**
- Source: `com/edam/workflow/model/WorkflowStepApprover.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `approver_type` | `approverType` | ApproverType | false | len=40 |
| `role_id` | `roleId` | UUID | true |  |
| `user_id` | `userId` | UUID | true |  |
| `department_id` | `departmentId` | UUID | true |  |
| `committee_level_id` | `committeeLevelId` | UUID | true |  |
| `created_at` | `createdAt` | Instant | false |  |
| `workflow_step_id` | `workflowStep` | WorkflowStep | true | FK → `WorkflowStep` |

### `workflow_steps` — `WorkflowStep`
- First migration referencing: **V23__phase4e_admin_configuration.sql**
- Source: `com/edam/workflow/model/WorkflowStep.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `step_code` | `stepCode` | String | false | len=64 |
| `step_name` | `stepName` | String | false | len=150 |
| `step_order` | `stepOrder` | short | false |  |
| `workflow_version_id` | `workflowVersion` | WorkflowVersion | true | FK → `WorkflowVersion` |

### `workflow_tasks` — `WorkflowTask`
- First migration referencing: **V14__workflow_task_optimistic_locking.sql**
- Source: `com/edam/workflow/model/WorkflowTask.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `workflow_instance_id` | `workflowInstanceId` | UUID | false |  |
| `workflow_step_id` | `workflowStepId` | UUID | false |  |
| `assigned_user_id` | `assignedUserId` | UUID | true |  |
| `assigned_role_id` | `assignedRoleId` | UUID | true |  |
| `assigned_at` | `assignedAt` | Instant | false |  |
| `due_at` | `dueAt` | Instant | true |  |
| `completed_at` | `completedAt` | Instant | true |  |
| `version` | `version` | long | false |  |

### `workflow_transitions` — `WorkflowTransition`
- First migration referencing: **V17__workflow_configuration_and_approval_support.sql**
- Source: `com/edam/workflow/model/WorkflowTransition.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `id` | `id` | UUID | false |  |
| `from_state_id` | `fromStateId` | UUID | false |  |
| `to_state_id` | `toStateId` | UUID | true |  |
| `action_code` | `actionCode` | ActionCode | false | len=40 |
| `condition_rule_id` | `conditionRuleId` | UUID | true |  |
| `workflow_version_id` | `workflowVersion` | WorkflowVersion | true | FK → `WorkflowVersion` |

### `workflow_versions` — `WorkflowVersion`
- First migration referencing: **V17__workflow_configuration_and_approval_support.sql**
- Source: `com/edam/workflow/model/WorkflowVersion.java`

| Column | Field | Java type | Nullable | Notes |
|---|---|---|---|---|
| `workflow_definition_id` | `workflowDefinitionId` | UUID | false |  |
| `version_number` | `versionNumber` | int | false |  |
| `effective_from` | `effectiveFrom` | Instant | true |  |
| `effective_to` | `effectiveTo` | Instant | true |  |
| `published_by` | `publishedBy` | UUID | true |  |
| `published_at` | `publishedAt` | Instant | true |  |
| `retired_by` | `retiredBy` | UUID | true |  |
| `retired_at` | `retiredAt` | Instant | true |  |
| `superseded_by_version_id` | `supersededByVersionId` | UUID | true |  |
