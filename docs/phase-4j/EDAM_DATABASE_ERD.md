# EDAM Database ERD

High-level entity-relationship diagram generated from JPA `@JoinColumn` relationships. For columns and constraints see `EDAM_DATABASE_DATA_DICTIONARY.md`.

```mermaid
erDiagram
    business_rules }o--|| business_rule_versions : "rule_version_id"
    business_rule_versions }o--|| business_rule_sets : "rule_set_id"
    committee_levels }o--|| expense_committees : "committee_id"
    committee_members }o--|| expense_committees : "committee_id"
    committee_level_members }o--|| committee_levels : "committee_level_id"
    committee_level_members }o--|| committee_members : "committee_member_id"
    theme_tokens }o--|| branding_themes : "theme_id"
    delegation_approver_steps }o--|| expense_delegation_rules : "delegation_rule_id"
    capex_budget_summaries }o--|| expense_requests : "expense_request_id"
    capex_request_details }o--|| expense_requests : "expense_request_id"
    capex_sta_details }o--|| expense_requests : "expense_request_id"
    expense_allocations }o--|| expense_requests : "expense_request_id"
    expense_invoices }o--|| expense_requests : "expense_request_id"
    expense_previous_summaries }o--|| expense_requests : "expense_request_id"
    expense_quotations }o--|| expense_requests : "expense_request_id"
    expense_request_items }o--|| expense_requests : "expense_request_id"
    opex_request_details }o--|| expense_requests : "expense_request_id"
    workflow_states }o--|| workflow_versions : "workflow_version_id"
    workflow_steps }o--|| workflow_versions : "workflow_version_id"
    workflow_step_approvers }o--|| workflow_steps : "workflow_step_id"
    workflow_transitions }o--|| workflow_versions : "workflow_version_id"
    department_approval_limits {
        uuid id PK
    }
    exceeding_limit_rules {
        uuid id PK
    }
    recommending_authorities {
        uuid id PK
    }
    idempotency_records {
        uuid id PK
    }
    audit_events {
        uuid id PK
    }
    comm_email_templates {
        uuid id PK
    }
    comm_email_template_versions {
        uuid id PK
    }
    comm_notifications {
        uuid id PK
    }
    comm_notification_event_configs {
        uuid id PK
    }
    comm_notification_outbox {
        uuid id PK
    }
    comm_notification_preferences {
        uuid id PK
    }
    comm_notification_preference_events {
        uuid id PK
    }
    comm_smtp_configuration {
        uuid id PK
    }
    comm_smtp_test_history {
        uuid id PK
    }
    application_settings {
        uuid id PK
    }
    feature_flags {
        uuid id PK
    }
    doc_scan_events {
        uuid id PK
    }
    media_assets {
        uuid id PK
    }
    expense_categories {
        uuid id PK
    }
    expense_natures {
        uuid id PK
    }
    expense_request_types {
        uuid id PK
    }
    expense_subcategories {
        uuid id PK
    }
    request_documents {
        uuid id PK
    }
    installation_state {
        uuid id PK
    }
    login_attempts {
        uuid id PK
    }
    password_reset_tokens {
        uuid id PK
    }
    integration_configs {
        uuid id PK
    }
    integration_executions {
        uuid id PK
    }
    currencies {
        uuid id PK
    }
    exchange_rates {
        uuid id PK
    }
    tax_authorities {
        uuid id PK
    }
    vendors {
        uuid id PK
    }
    branches {
        uuid id PK
    }
    departments {
        uuid id PK
    }
    organization_profile {
        uuid id PK
    }
    organization_units {
        uuid id PK
    }
    organization_unit_types {
        uuid id PK
    }
    permissions {
        uuid id PK
    }
    roles {
        uuid id PK
    }
    user_roles {
        uuid id PK
    }
    users {
        uuid id PK
    }
    user_data_scopes {
        uuid id PK
    }
    workflow_actions {
        uuid id PK
    }
    workflow_definitions {
        uuid id PK
    }
    workflow_instances {
        uuid id PK
    }
    workflow_tasks {
        uuid id PK
    }
```

## Relationship notes

- Foreign keys are non-nullable UUIDs pointing at `id` of the parent.
- Cascading delete is NOT used for business entities; FK enforcement is `RESTRICT`. Use soft-delete (`active=false`) instead.
- `audit_events` is append-only (trigger prevents UPDATE/DELETE outside the application path; V10/V25).
- `workflow_versions` are immutable once published; definitions keep a full history.
- `integration_configs` and `comm_smtp_configuration` store encrypted ciphertext only.
- `idempotency_records` has a unique constraint `(idempotency_key, actor_id, request_path)` (V27).
