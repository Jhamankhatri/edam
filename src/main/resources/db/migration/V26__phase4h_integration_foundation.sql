-- =====================================================================================
-- EDAM — Phase 4H: Integration Foundation (V26)
--
-- Extends V11's Module N (external integrations):
--   * Replaces the secret_reference placeholder with encrypted-secret-at-rest
--     (AES-256-GCM via IntegrationSecretCrypto, key from environment, not the DB).
--   * Adds connection-status tracking with redacted last-test diagnostics.
--   * Adds soft-delete (archived) and an explicit adapter/provider type registry column.
--   * Adds a robust integration_executions table for PENDING/RUNNING/SUCCEEDED/FAILED/
--     RETRY_PENDING lifecycle with locking, retries and idempotency.
--
-- This migration does NOT create any adapter-specific payload columns: adapters add typed
-- settings via JSONB configuration validated by the adapter at write-time.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1. integration_configs — evolve V11's minimal table into an actively-managed config.
-- -------------------------------------------------------------------------------------

-- V11 created integration_configs without a description column; add it along with all
-- Phase 4H lifecycle columns. The column name matches the entity field.
ALTER TABLE integration_configs
    ADD COLUMN IF NOT EXISTS description    TEXT,
    ADD COLUMN IF NOT EXISTS encrypted_secret TEXT,
    ADD COLUMN IF NOT EXISTS secret_version   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS archived        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS connection_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS last_test_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_test_success BOOLEAN,
    ADD COLUMN IF NOT EXISTS last_test_message VARCHAR(500),
    ADD COLUMN IF NOT EXISTS last_test_duration_ms INTEGER,
    ADD COLUMN IF NOT EXISTS connection_checked_at TIMESTAMPTZ;

-- Drop the V11 "must provide secret_reference if authenticated" constraint; we now store
-- an encrypted blob (or no secret for NONE), and secret_reference is retained for future
-- external secret-manager integration (nullable).
ALTER TABLE integration_configs DROP CONSTRAINT IF EXISTS ck_integration_configs_secret;

-- Drop the V11 authentication-type CHECK to add API_KEY support; re-add with the extended list.
ALTER TABLE integration_configs DROP CONSTRAINT IF EXISTS ck_integration_configs_auth;
ALTER TABLE integration_configs
    ADD CONSTRAINT ck_integration_configs_auth
        CHECK (authentication_type IN ('NONE', 'BASIC', 'BEARER', 'OAUTH2', 'CLIENT_CERTIFICATE', 'LDAP_BIND', 'API_KEY'));

-- Drop the V11 type CHECK to allow adapter-registered types beyond the V11 list; re-add the
-- CHECK once adapters register their types. We deliberately use an open VARCHAR with
-- adapter-level validation so new providers do not require a DDL change.
ALTER TABLE integration_configs DROP CONSTRAINT IF EXISTS ck_integration_configs_type;

ALTER TABLE integration_configs
    ADD CONSTRAINT ck_integration_configs_connection_status
        CHECK (connection_status IN ('UNKNOWN', 'UNCONFIGURED', 'ACTIVE', 'INACTIVE', 'FAILED', 'TESTING')),
    ADD CONSTRAINT ck_integration_configs_duration
        CHECK (last_test_duration_ms IS NULL OR last_test_duration_ms >= 0);

COMMENT ON COLUMN integration_configs.encrypted_secret IS
    'AES-256-GCM ciphertext of the provider credential (Base64: 12-byte IV || 16-byte tag || ct). '
    'Master key lives in EDAM_INTEGRATION_MASTER_KEY, never in the DB or logs.';
COMMENT ON COLUMN integration_configs.secret_version IS
    'Monotonically increasing when credentials are rotated; clients can detect silent rotation failures.';
COMMENT ON COLUMN integration_configs.archived IS
    'Soft-delete flag. Historical execution rows are retained; archived configs cannot be activated or executed.';
COMMENT ON COLUMN integration_configs.last_test_message IS
    'Sanitised diagnostic. The service truncates provider messages and redacts bearer/basic/auth fragments before insert.';

CREATE INDEX IF NOT EXISTS ix_integration_configs_active ON integration_configs (integration_type, is_active) WHERE NOT archived;

-- -------------------------------------------------------------------------------------
-- 2. integration_executions — durable execution / sync tracking with locking + retries.
-- -------------------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS integration_executions (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    integration_config_id UUID        NOT NULL REFERENCES integration_configs (id) ON DELETE RESTRICT,
    operation_type       VARCHAR(60)  NOT NULL,
    idempotency_key      VARCHAR(200),
    external_reference   VARCHAR(300),
    execution_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count        INTEGER      NOT NULL DEFAULT 0,
    max_attempts         INTEGER      NOT NULL DEFAULT 3,
    claimed_by           VARCHAR(100),
    claimed_at           TIMESTAMPTZ,
    claim_expires_at     TIMESTAMPTZ,
    started_at           TIMESTAMPTZ,
    completed_at         TIMESTAMPTZ,
    next_retry_at        TIMESTAMPTZ,
    retryable            BOOLEAN      NOT NULL DEFAULT FALSE,
    request_metadata     JSONB,
    response_metadata    JSONB,
    error_code           VARCHAR(60),
    error_message        VARCHAR(1000),
    triggered_by         UUID         REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_integration_executions_status
        CHECK (execution_status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRY_PENDING', 'CANCELLED')),
    CONSTRAINT ck_integration_executions_attempts
        CHECK (attempt_count >= 0 AND max_attempts >= 1 AND attempt_count <= max_attempts),
    CONSTRAINT uk_integration_executions_idempotency
        UNIQUE NULLS NOT DISTINCT (integration_config_id, operation_type, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_integration_executions_due
    ON integration_executions (next_retry_at)
    WHERE execution_status IN ('PENDING', 'RETRY_PENDING');
CREATE INDEX IF NOT EXISTS ix_integration_executions_claim
    ON integration_executions (claim_expires_at)
    WHERE execution_status = 'RUNNING' AND claim_expires_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_integration_executions_config_time
    ON integration_executions (integration_config_id, created_at DESC);

COMMENT ON TABLE integration_executions IS
    'Durable execution records for integration operations. SELECT ... FOR UPDATE SKIP LOCKED '
    'guarantees at-most-one worker per row; idempotency key is unique per (config, operation).';
COMMENT ON COLUMN integration_executions.request_metadata IS
    'Safe summary of the request. Adapters must strip credentials, auth headers, and account numbers.';
COMMENT ON COLUMN integration_executions.response_metadata IS
    'Sanitised response metadata only. Never stores full payloads or tokens.';
COMMENT ON COLUMN integration_executions.error_message IS
    'Redacted diagnostic; the service strips header/auth/secret fragments before persisting.';

-- -------------------------------------------------------------------------------------
-- 3. Catalogue additions for integration administration and test history viewing.
-- -------------------------------------------------------------------------------------

INSERT INTO permissions (code, module, description) VALUES
    ('integration:execute', 'integration', 'Initiate integration executions and retries'),
    ('integration:test',    'integration', 'Run integration connection tests')
 ON CONFLICT (code) DO NOTHING;

-- Grant the new permissions to the SYSTEM_ADMINISTRATOR role (system role already defined in V12).
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN ('integration:execute', 'integration:test')
WHERE r.code = 'SYSTEM_ADMINISTRATOR'
ON CONFLICT DO NOTHING;
