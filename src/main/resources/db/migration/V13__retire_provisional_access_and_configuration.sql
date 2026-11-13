-- =====================================================================================
-- EDAM — retire the provisional schema from V3/V4 (V13)
--
-- WHY THIS MIGRATION EXISTS
--
-- V3 and V4 were written before the normative data model was agreed. They used BIGINT
-- identity keys, modelled roles as per-institution copies and stored approval tasks directly
-- on the request. The normative model (V5..V12) uses UUID keys, a global role catalogue with
-- organizational data scopes, and a separated workflow engine.
--
-- Leaving both would be actively dangerous, not merely untidy:
--   * two authoritative tables for the same concept (`role` and `roles`) means a permission
--     granted through one is invisible to the other — an access-control bug that silently
--     half-works;
--   * the provisional approval_task table allowed one open task per step, while the engine
--     needs one per assignee, so quorum counting would be wrong;
--   * the provisional tables had no data scope concept at all.
--
-- There is NO data migration, because V3/V4 predate any deployment: they exist only in
-- development databases and CI. This is stated explicitly rather than implied, because on a
-- live installation the same migration would HAVE to carry data across, and a reviewer must
-- be able to see that this one does not.
--
-- Order matters: children first. Dependency-safe ordering is expressed by listing the
-- dependent tables before the tables they reference.
-- =====================================================================================

-- Requests and approvals (V4)
DROP TABLE IF EXISTS approval_task;
DROP TABLE IF EXISTS request_history_entry;
DROP TABLE IF EXISTS document;
DROP TABLE IF EXISTS expense_request_currency_snapshot;
DROP TABLE IF EXISTS expense_request;

-- Workflow placeholders (V4)
DROP TABLE IF EXISTS workflow_version;
DROP TABLE IF EXISTS workflow_definition;

-- Access control (V3)
DROP TABLE IF EXISTS user_membership_role;
DROP TABLE IF EXISTS user_membership;
DROP TABLE IF EXISTS role_permission;
DROP TABLE IF EXISTS role;
DROP TABLE IF EXISTS permission_definition;

-- Configuration plane (V3)
DROP TABLE IF EXISTS delegation_rule_approver;
DROP TABLE IF EXISTS delegation_rule_authority;
DROP TABLE IF EXISTS delegation_rule;
DROP TABLE IF EXISTS committee_level_member;
DROP TABLE IF EXISTS committee_level;
DROP TABLE IF EXISTS committee_member;
DROP TABLE IF EXISTS recommending_authority;
DROP TABLE IF EXISTS exceeding_limit;
DROP TABLE IF EXISTS department_head_limit;
DROP TABLE IF EXISTS currency;
DROP TABLE IF EXISTS expense_subcategory;
DROP TABLE IF EXISTS expense_category;

-- Feature flags and idempotency (V3/V4), replaced by feature_flags and idempotency_records
DROP TABLE IF EXISTS institution_feature_flag;
DROP TABLE IF EXISTS idempotency_record;

-- The provisional `department` table advertised a CAPEX flag and used BIGINT keys; the
-- normative organization module (V6) owns departments now and models them as organization
-- units. `users.department_id` already points at the new table.
DROP TABLE IF EXISTS department;

-- Audit (V3): the append-only table is replaced by audit_events, which carries entity_type,
-- correlation_id and a JSONB before/after pair as required.
--
-- Dropping audit_event also drops its triggers, so its guard function becomes unused. It is
-- dropped WITHOUT CASCADE on purpose: `edam_reject_audit_mutation` guards the replacement
-- audit_events table, and a CASCADE here would silently remove that protection and leave the
-- audit trail mutable — the exact failure this schema exists to prevent.
DROP TABLE IF EXISTS audit_event;
DROP FUNCTION IF EXISTS edam_audit_event_is_append_only();

-- The multi-bank registry and its per-institution settings. The requirement models ONE
-- installation for ONE bank via organization_profile, so these columns too are superseded.
DROP TABLE IF EXISTS institution;

-- V4's seed for the provisional institution is dropped with the table; the permission and
-- role catalogue that matters is installed by V12.
--
-- `edam_reject_audit_mutation` is deliberately NOT dropped: it is the append-only guard
-- installed by V10 and is still attached to audit_events and audit_event_details.

-- -------------------------------------------------------------------------------------
-- Self-verification
--
-- A migration that removes access-control and audit tables should fail loudly if it leaves
-- the replacement protections missing. These two assertions make the append-only triggers a
-- precondition of the migration completing, rather than something a reviewer has to trust.
-- -------------------------------------------------------------------------------------

DO $$
DECLARE
    guard_triggers INTEGER;
BEGIN
    SELECT count(*) INTO guard_triggers
    FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    WHERE NOT t.tgisinternal
      AND c.relname IN ('audit_events', 'audit_event_details');

    IF guard_triggers < 3 THEN
        RAISE EXCEPTION 'audit append-only guards missing after migration (found % of 3)', guard_triggers;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables
               WHERE table_schema = current_schema() AND table_name IN ('role', 'user_membership', 'audit_event')) THEN
        RAISE EXCEPTION 'provisional access-control tables still present after migration';
    END IF;
END $$;
