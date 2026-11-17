-- =====================================================================================
-- EDAM — Phase 4I finalization: fix integration execution idempotency unique index.
--
-- V26 used UNIQUE NULLS NOT DISTINCT on (config_id, operation_type, idempotency_key).
-- That was wrong: with NULLS NOT DISTINCT, every execution that omits the idempotency
-- key (the common case for fire-and-forget jobs) is treated as a duplicate of every
-- other null-key execution for the same (config, operation), so only ONE could ever
-- exist. The contract is:
--   * When a key is provided, duplicate (config, op, key) replays the prior execution.
--   * When no key is provided, every submission enqueues a fresh execution.
-- Fix: drop the table-wide constraint and use two partial unique indexes — one that
-- enforces the keyed uniqueness, and no constraint on null keys.
-- =====================================================================================

ALTER TABLE integration_executions
    DROP CONSTRAINT IF EXISTS uk_integration_executions_idempotency;

CREATE UNIQUE INDEX IF NOT EXISTS ux_integration_executions_idempotency_key
    ON integration_executions (integration_config_id, operation_type, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Re-grant any permissions that were seeded in V26 so that a fresh DB bootstrap (in
-- which V27 runs immediately after V26 in the same migration chain) is idempotent.
INSERT INTO permissions (code, module, description) VALUES
    ('integration:execute', 'integration', 'Initiate integration executions and retries'),
    ('integration:test',    'integration', 'Run integration connection tests')
 ON CONFLICT (code) DO NOTHING;
