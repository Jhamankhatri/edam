-- Phase 4A Step 2 (follow-up): add UNKNOWN state for crash recovery.
--
-- When the JVM terminates between the business transaction committing and the afterCommit
-- finaliser writing the COMPLETED row, the idempotency record is left IN_PROGRESS even though
-- the business work may have committed. Clearing that row unconditionally would open a
-- duplicate-execution window; blindly replaying a non-existent snapshot is also impossible.
-- Introducing an UNKNOWN state lets the recovery path mark such records explicitly and return
-- 409 Conflict to the retry, which tells the client to query the resource rather than retry
-- blindly. See docs/idempotency/IDEMPOTENCY_DESIGN.md §"Crash recovery".

ALTER TABLE idempotency_records
    DROP CONSTRAINT IF EXISTS idempotency_records_processing_status_check;

ALTER TABLE idempotency_records
    ADD CONSTRAINT idempotency_records_processing_status_check
        CHECK (processing_status IN ('IN_PROGRESS', 'UNKNOWN', 'COMPLETED', 'FAILED'));
