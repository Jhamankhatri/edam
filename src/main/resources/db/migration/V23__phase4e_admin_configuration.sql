-- Phase 4E — Administration & Product Configuration Management
--
-- Structural changes supporting the new administrative operations in this phase.
-- All behavioral guarantees (manager-cycle detection, exchange-rate close semantics,
-- delegation overlap, workflow version immutability, etc.) are enforced at the service
-- layer with existing JPA/EXCLUDE constraints and the new endpoints added in this phase.
--
-- This migration is intentionally conservative: it only adds a CHECK constraint that
-- mirrors a rule already enforced by the service (close-after-start for exchange rates).
-- Other Phase 4E additions (new endpoints, service methods, DTOs, audit action) do not
-- require DDL.
--
-- The previously-attempted PL/pgSQL reporting-cycle trigger is deferred: the service
-- layer walks the chain on every manager assignment, and V5 already installs
-- ck_users_not_own_manager for self-loops. A trigger can be added in a follow-up
-- migration after broader concurrency testing.

-- ----------------------------------------------------------------------------
-- Exchange rate end-dating guard
--
--    The effective_to column was previously set updatable=false in JPA only.
--    Phase 4E adds an explicit close endpoint; this CHECK refuses to close a
--    period before its start, matching the service rule.
-- ----------------------------------------------------------------------------

ALTER TABLE exchange_rates
    DROP CONSTRAINT IF EXISTS ck_exchange_rate_close_after_start;
ALTER TABLE exchange_rates
    ADD CONSTRAINT ck_exchange_rate_close_after_start
        CHECK (effective_to IS NULL OR effective_to > effective_from);

-- ----------------------------------------------------------------------------
-- Audit documentation comment
-- ----------------------------------------------------------------------------

COMMENT ON TABLE audit_event IS
    'Append-only audit trail. Action set includes UNLOCK (Phase 4E user-administration '
    'account unlock) and ROLLBACK (Phase 4E workflow rollback-as-new-version).';
