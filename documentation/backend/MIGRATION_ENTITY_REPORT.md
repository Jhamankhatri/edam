# Migration and entity report

Measured from the source, not carried over from an earlier document.

| Metric | Count |
|---|---|
| Migrations applied | **17** |
| Tables created across all migrations | **111** |
| Tables dropped by later migrations | **29** |
| **Live tables** | **82** |
| Tables mapped by a JPA entity | **50** |
| Live tables with no entity | **32** |

Every mapped entity's table exists — the reverse check (an entity naming a table no migration creates) returns
zero, which is the failure `ddl-auto: validate` would catch at startup anyway.

## Phase 3 — V17

One forward-only migration. `V8` created the workflow tables; `V17` adds only what the configuration and approval
paths need, and it alters nothing that already held data in a way that would change its meaning.

| Change | Why |
|---|---|
| `workflow_transitions.condition_rule_id` → FK to `business_rules` | It was a bare UUID column with no referential guarantee, so a transition could name a rule that does not exist and fail only when an approver acted |
| `workflow_versions.retired_at`, `retired_by`, `superseded_by_version_id` | A superseded revision must remain readable *and* must record what replaced it; deleting it would break every request pinned to it |
| `ck_workflow_versions_retired` | A retired revision with no retirement timestamp is a state nobody can audit |
| `ck_workflow_versions_supersede_self` | A version cannot claim to supersede itself |
| `ix_workflow_versions_definition` | The version list is read on every configuration screen |
| `ix_workflow_tasks_instance_step_status` | Quorum counting and closing a completed step's siblings both filter by step; the case file reads every task of an instance grouped by step |
| `ix_workflow_tasks_pending_inbox` | Partial index on `PENDING` only — the inbox is the most frequent read in the product, and a partial index stays small however large the task history grows |

Two things the migration deliberately does **not** do, both recorded in its own header: it does not add a
"current version" pointer to `workflow_definitions` (the partial unique index already guarantees exactly one
published revision, and a second source of that truth could disagree with it), and it weakens no CHECK from V8.

**No migration edits an applied one.** Every prior migration is byte-identical to `main`; V17 only appends.

## Workflow tables and their entity coverage

| Table | Entity | Status |
|---|---|---|
| `workflow_definitions` | `WorkflowDefinition` | Mapped |
| `workflow_versions` | `WorkflowVersion` | Mapped |
| `workflow_states` | `WorkflowState` | Mapped |
| `workflow_steps` | `WorkflowStep` | Mapped |
| `workflow_step_approvers` | `WorkflowStepApprover` | Mapped |
| `workflow_transitions` | `WorkflowTransition` | **Mapped in Phase 3** (was unmapped) |
| `workflow_instances` | `WorkflowInstance` | Mapped |
| `workflow_tasks` | `WorkflowTask` | Mapped |
| `workflow_actions` | `WorkflowAction` | Mapped |
| `workflow_events` | — | **Still unmapped** — see below |

### `workflow_events` remains unmapped, deliberately

The table exists and is empty. `workflow_actions` records what an approver *decided*; `workflow_events` was
intended to record everything that happened to an instance, including things no person did — a task expiring, a
notification failing, an escalation firing. Nothing in Phase 1–3 produces those events: there are no timers, no
notifications and no escalation. Writing to it now would mean inventing an event vocabulary before anything
generates events, and the audit trail that matters today (`workflow_actions` plus `audit_events`) is complete.

It is left in place because dropping it would be a schema change with no benefit, and it is called out here so
its emptiness is a recorded decision rather than an oversight.

## Live tables with no entity (32)

These are tables created by earlier migrations for modules that have not been built. They are inert: nothing
reads or writes them, and `ddl-auto: validate` does not complain because validation only checks that mapped
entities have tables, not the reverse.

**V10:** `audit_event_details`, `audit_events`, `business_rule_sets`, `business_rule_versions`, `business_rules`, `email_provider_configs`, `notification_deliveries`, `notification_outbox`, `notification_preferences`, `notification_templates`, `notifications`

**V11:** `dashboard_definitions`, `dashboard_widgets`, `idempotency_records`, `integration_configs`, `integration_sync_records`, `integration_sync_runs`, `report_definitions`, `scheduled_jobs`, `system_error_events`, `user_dashboard_preferences`

**V4_1:** `in`

**V5:** `role_permissions`, `user_sessions`

**V6:** `application_settings`, `branding_themes`, `feature_flags`, `media_assets`, `system_parameters`, `theme_tokens`

**V7:** `time`

**V8:** `workflow_events`

The largest groups are notifications, reporting, integrations and audit-detail tables from `V10`/`V11` — the
modules listed as "not in Phase 1–3 scope" in `PERMISSION_ENDPOINT_MAP.md`. Document management tables arrive with
the Phase 4 upload path.

## Constraints this phase relied on and did not weaken

| Constraint | Where | What it guarantees |
|---|---|---|
| Partial unique index on one `PUBLISHED` version | `V8` | Two live revisions of one workflow cannot coexist |
| `uk_workflow_instances_expense_request` | `V8` | One request cannot have two approval tracks |
| One editable revision per definition | `V3`/`V8` + `VersionsRepository.isEditable` | No race over which draft becomes the next version |
| `workflow_step_approvers` type/match CHECK | `V8` | A designation cannot carry a reference its type does not use |
| `ck_workflow_versions_retired` | `V17` | A retired revision always records when and by whom |
| `workflow_tasks.version` optimistic lock | `V14` | A double-click does not count as two approvals |
