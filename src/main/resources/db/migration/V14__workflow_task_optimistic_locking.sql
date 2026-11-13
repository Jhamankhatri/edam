-- =====================================================================================
-- V14 — optimistic locking on approval tasks (Module I)
--
-- WHY THIS EXISTS
-- `workflow_tasks` was created in V8 without a version column, but the task entity
-- declares optimistic locking and the engine depends on it: two concurrent approvals of
-- the SAME task must not both be recorded. Without a version, a double-click or a retried
-- request would let one approver be counted twice toward a step's quorum — a single
-- person satisfying a four-eyes rule. That is a governance failure, not a duplicate row.
--
-- WHY A NEW MIGRATION AND NOT AN EDIT TO V8
-- V8 has already been distributed. Editing it in place would change its checksum, and
-- every environment that has run Flyway would then refuse to start with a validation
-- error. An additive ALTER works for both a fresh database and an existing one, and
-- never destroys data.
--
-- The column is added with a DEFAULT and NOT NULL so existing task rows are valid
-- immediately; Hibernate treats a row's version as 0 until the first update.
-- =====================================================================================

ALTER TABLE workflow_tasks
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN workflow_tasks.version IS
    'Optimistic lock for approval actions: a stale write must fail rather than count one approver twice.';
