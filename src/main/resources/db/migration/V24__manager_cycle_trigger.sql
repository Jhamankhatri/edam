-- Phase 4E follow-up: concurrency-safe manager-cycle protection.
--
-- The service walks the manager chain in UserManagementService.validateOrganizationReferences
-- before updating manager_id, which catches simple mistakes. But under concurrent updates
-- two transactions can each read a consistent pre-image, validate independently, and commit
-- a cycle (A -> B and B -> A) — the classic write-skew anomaly. We therefore enforce the
-- invariant in the database, on every row change, using an AFTER-trigger that re-walks the
-- chain from the newly-assigned manager.
--
-- Why AFTER, not BEFORE: a BEFORE trigger runs against the OLD state of the row for all
-- other rows, so if both A and B are updated in the same transaction the second BEFORE
-- trigger sees A.manager_id as NULL rather than B and misses the cycle. An AFTER trigger
-- fires after the row has been mutated for the current transaction, so the chain as seen
-- from the cursor includes both pending changes. If we find a cycle we RAISE EXCEPTION,
-- which aborts the transaction and rolls the change back.

CREATE OR REPLACE FUNCTION fn_user_no_manager_cycle()
RETURNS trigger AS $$
DECLARE
    mgr   uuid;
    seen  uuid[];
BEGIN
    -- manager_id cleared: cannot form a cycle.
    IF NEW.manager_id IS NULL THEN
        RETURN NEW;
    END IF;

    -- Self-loop (redundant with ck_users_not_own_manager from V5 but harmless).
    IF NEW.manager_id = NEW.id THEN
        RAISE EXCEPTION 'A user cannot be their own manager (user_id=%)', NEW.id;
    END IF;

    -- Walk up the chain starting from the newly-assigned manager, collecting every
    -- ancestor. Because the trigger fires AFTER the row is updated for the current
    -- transaction, concurrent-but-not-yet-committed changes to OTHER rows are NOT
    -- visible (Postgres MVCC) — which is exactly what we want: this trigger catches
    -- cycles formed by this transaction alone; cross-transaction cycles are prevented
    -- by the application acquiring a single advisory lock (see
    -- UserManagementService.acquireManagerHierarchyLock) before mutating manager_id.
    seen := ARRAY[NEW.id];
    mgr  := NEW.manager_id;
    LOOP
        IF mgr IS NULL THEN
            RETURN NEW;
        END IF;
        IF mgr = ANY(seen) THEN
            RAISE EXCEPTION 'Manager assignment would create a cycle in the reporting chain (user_id=%)', NEW.id;
        END IF;
        seen := seen || mgr;
        SELECT manager_id INTO mgr FROM users WHERE id = mgr;
    END LOOP;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_user_no_manager_cycle ON users;
CREATE TRIGGER trg_user_no_manager_cycle
    AFTER INSERT OR UPDATE OF manager_id ON users
    FOR EACH ROW
    EXECUTE FUNCTION fn_user_no_manager_cycle();
