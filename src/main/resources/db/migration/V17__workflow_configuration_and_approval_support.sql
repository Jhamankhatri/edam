-- =====================================================================================
-- V17 — Phase 3 support: workflow configuration and approval execution (Module I)
--
-- WHY THIS MIGRATION EXISTS
-- V8 created the workflow tables but left three gaps that block a real approval
-- implementation. None of them can be closed in application code alone, and none of them
-- can be fixed by editing V8 (that would change its checksum and every environment that
-- has already run Flyway would refuse to start).
--
--   1. `workflow_transitions.condition_rule_id` is an unconstrained UUID. A transition
--      could therefore name a rule id that does not exist, or that belongs to a deleted
--      rule version, and the engine would silently ignore the condition. The column
--      becomes a real foreign key so an invalid configuration cannot be stored.
--
--   2. There was no way to record *why* a version was retired or which version superseded
--      it. Retiring is an auditable configuration act ("who stopped this approval route,
--      and when"), and reconstructing it from audit JSON alone is not queryable. The
--      self-reference also lets the version history read as a chain.
--
--   3. `workflow_instances` had no index on `(expense_request_id)` beyond the unique one —
--      which is fine — but approval-inbox queries filter tasks by instance and status and
--      then need the request's header. A covering index on the task table for the
--      instance+step+status shape the engine actually queries keeps the inbox from
--      degrading as the task table grows. This is a performance index, not a constraint.
--
-- WHAT THIS MIGRATION DELIBERATELY DOES NOT DO
--   * It does not add a "current version" pointer to `workflow_definitions`. A partial
--     unique index already guarantees exactly one PUBLISHED version, and a second source
--     of that truth could disagree with it.
--   * It does not weaken any CHECK. The status/quorum/approver-reference constraints from
--     V8 are what stop an invalid workflow being stored at all, and they stay.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. Transition conditions must reference a real rule definition.
-- -------------------------------------------------------------------------------------

-- `condition_rule_id` was declared without a target because `business_rules` lives in V10,
-- which runs after V8. The constraint can be added now that both tables exist.
ALTER TABLE workflow_transitions
    ADD CONSTRAINT fk_workflow_transitions_condition
    FOREIGN KEY (condition_rule_id) REFERENCES business_rules (id) ON DELETE RESTRICT;

COMMENT ON COLUMN workflow_transitions.condition_rule_id IS
    'Optional business rule that must evaluate true for the transition to be permitted. RESTRICT: a rule that '
    'governs a published transition cannot be deleted out from under it.';

-- -------------------------------------------------------------------------------------
-- 2. Retirement metadata on workflow versions.
-- -------------------------------------------------------------------------------------

ALTER TABLE workflow_versions
    ADD COLUMN retired_by UUID REFERENCES users (id),
    ADD COLUMN retired_at TIMESTAMPTZ,
    ADD COLUMN superseded_by_version_id UUID REFERENCES workflow_versions (id);

COMMENT ON COLUMN workflow_versions.retired_at IS
    'When this revision stopped accepting new instances. In-flight instances keep using it.';

-- A RETIRED version must say who retired it and when. Symmetrical with
-- ck_workflow_versions_published, and for the same reason: an unexplained configuration
-- change is not auditable.
ALTER TABLE workflow_versions
    ADD CONSTRAINT ck_workflow_versions_retired
    CHECK (version_status <> 'RETIRED' OR (retired_at IS NOT NULL AND retired_by IS NOT NULL));

-- A version cannot supersede itself. Trivial to state, and it catches a copy-paste bug in
-- the publish path that would otherwise produce a history nobody can read.
ALTER TABLE workflow_versions
    ADD CONSTRAINT ck_workflow_versions_supersede_self
    CHECK (superseded_by_version_id IS NULL OR superseded_by_version_id <> id);

CREATE INDEX ix_workflow_versions_definition ON workflow_versions (workflow_definition_id, version_number DESC);

-- -------------------------------------------------------------------------------------
-- 3. The shape the approval inbox actually queries.
-- -------------------------------------------------------------------------------------

-- `ix_workflow_tasks_instance` from V8 covers (instance, status). The engine additionally
-- filters by step when counting quorum and when closing a completed step's siblings, and
-- the case-file view reads every task of an instance grouped by step.
CREATE INDEX ix_workflow_tasks_instance_step_status
    ON workflow_tasks (workflow_instance_id, workflow_step_id, task_status);

-- The inbox is "open tasks assigned to me, newest first". V8 has
-- (assigned_user_id, task_status, assigned_at DESC) but not the partial form; a partial
-- index on the only rows the inbox ever reads stays small however large the task history
-- grows.
CREATE INDEX ix_workflow_tasks_pending_inbox
    ON workflow_tasks (assigned_user_id, assigned_at DESC)
    WHERE task_status = 'PENDING';

-- -------------------------------------------------------------------------------------
-- 4. Existing definitions must be readable by code before any workflow can be published.
-- -------------------------------------------------------------------------------------

COMMENT ON TABLE workflow_definitions IS
    'Named approval processes. A definition is bound to a request type and is the stable identity across '
    'revisions; the PUBLISHED version is what routes a request.';
