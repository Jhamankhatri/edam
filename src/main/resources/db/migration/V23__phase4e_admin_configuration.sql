-- Phase 4E — Administration & Product Configuration Management
--
-- Structural changes supporting the new administrative operations in this phase.
-- Code-only changes (new endpoints, services, validation) do not require DDL; the
-- additions below cover the small set of persistence adjustments the phase adds.
--
-- All other configuration surfaces (users, roles, departments, branches, categories,
-- committees, approval limits, delegation, workflow definitions/versions, themes,
-- feature flags, SMTP, email templates, notification events, vendors, currencies,
-- exchange rates, tax authorities) were introduced by earlier phases and are reused
-- as-is per the standing "reuse, don't rebuild" rule.

-- ----------------------------------------------------------------------------
-- 1. Manager-cycle safety
--
--    The service already refuses self-manager assignments and walks the chain
--    for transitive cycles. A trigger is belt-and-braces against a future bulk
--    update that bypasses the service, so a reporting loop cannot land in the
--    database even by mistake. Cycles would make escalation unboundable.
--
--    V5 already installs ck_users_not_own_manager (self-loop guard); we keep
--    that constraint and add a transitive-cycle walk.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION fn_user_no_manager_cycle()
RETURNS trigger AS $$
DECLARE
    mgr   uuid;
    seen  uuid[];
BEGIN
    IF NEW.manager_id IS NULL THEN
        RETURN NEW;
    END IF;
    -- Self-loop is already enforced by ck_users_not_own_manager; guard again here
    -- in case that constraint is dropped in some deployment.
    IF NEW.manager_id = NEW.id THEN
        RAISE EXCEPTION 'A user cannot be their own manager (user_id=%)', NEW.id;
    END IF;
    seen := ARRAY[NEW.id];
    mgr := NEW.manager_id;
    LOOP
        IF mgr IS NULL THEN
            EXIT;
        END IF;
        IF mgr = ANY(seen) THEN
            RAISE EXCEPTION 'Manager assignment would create a cycle in the reporting chain (user_id=%)', NEW.id;
        END IF;
        seen := seen || mgr;
        SELECT manager_id INTO mgr FROM users WHERE id = mgr;
    END LOOP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_user_no_manager_cycle ON users;
CREATE TRIGGER trg_user_no_manager_cycle
    BEFORE INSERT OR UPDATE OF manager_id ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_user_no_manager_cycle();

-- ----------------------------------------------------------------------------
-- 2. Exchange rate end-dating
--
--    The effective_to column was previously set updatable=false in JPA only.
--    Phase 4E adds an explicit close endpoint; we add a CHECK constraint that
--    refuses to close a period before its start, matching the service rule.
-- ----------------------------------------------------------------------------

ALTER TABLE exchange_rates
    DROP CONSTRAINT IF EXISTS ck_exchange_rate_close_after_start;
ALTER TABLE exchange_rates
    ADD CONSTRAINT ck_exchange_rate_close_after_start
        CHECK (effective_to IS NULL OR effective_to > effective_from);

-- ----------------------------------------------------------------------------
-- 3. Audit documentation comment
-- ----------------------------------------------------------------------------

COMMENT ON TABLE audit_log IS
    'Append-only audit trail. Action set includes UNLOCK (Phase 4E user-administration '
    'account unlock) and ROLLBACK (Phase 4E workflow rollback-as-new-version).';
