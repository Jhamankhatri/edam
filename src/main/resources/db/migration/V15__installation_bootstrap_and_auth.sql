-- =====================================================================================
-- V15 — installation bootstrap state and authentication support (Module C)
--
-- WHY THIS EXISTS
-- The installation must be able to say, authoritatively, whether it has been set up.
-- Without a persisted marker every deployment would rely on "no users exist yet", which is
-- both a racy check and a poor security signal: deleting the last user would silently
-- reopen the bootstrap path.
--
-- WHY A SINGLE-ROW TABLE RATHER THAN A SETTING
-- Convenience settings such as `application_settings` are administrable data. Whether
-- installation is open is a security fact. Keeping it in its own table, guarded by a
-- one-row constraint and not writable by any general settings API, means it cannot be
-- changed by a caller with configuration privileges alone.
-- =====================================================================================

CREATE TABLE installation_state (
    id                    SMALLINT    PRIMARY KEY DEFAULT 1,
    bootstrapped          BOOLEAN     NOT NULL DEFAULT FALSE,
    bootstrapped_at       TIMESTAMPTZ,
    bootstrapped_by       UUID        REFERENCES users (id),
    bootstrap_attempts    INTEGER     NOT NULL DEFAULT 0,
    last_failed_attempt_at TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_installation_state_single_row CHECK (id = 1),
    -- Completion must be accompanied by who did it and when; a flag with no evidence would
    -- make "was this installation set up by an authorised operator" unanswerable.
    CONSTRAINT ck_installation_state_complete CHECK (
        (NOT bootstrapped) OR (bootstrapped_at IS NOT NULL AND bootstrapped_by IS NOT NULL))
);

COMMENT ON TABLE installation_state IS
    'Single-row security fact: whether the one-time first-administrator bootstrap has completed. '
    'Never exposed through general configuration APIs.';

INSERT INTO installation_state (id, bootstrapped) VALUES (1, FALSE);

-- -------------------------------------------------------------------------------------
-- Account lockout state
--
-- Kept on the user row rather than derived from login_attempts on every sign-in, because
-- the lockout decision is on the authentication hot path and must not depend on scanning
-- an append-only evidence table. login_attempts remains the evidence; these columns are
-- the decision.
-- -------------------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until        TIMESTAMPTZ,
    ADD COLUMN password_changed_at TIMESTAMPTZ;

COMMENT ON COLUMN users.failed_login_count IS
    'Consecutive failed sign-ins since the last success. Reset to 0 on success.';
COMMENT ON COLUMN users.locked_until IS
    'Lockout expiry. NULL means not locked. Cleared on a successful sign-in or admin unlock.';
COMMENT ON COLUMN users.password_changed_at IS
    'When the credential was last set. Used to invalidate sessions established before a change.';

-- Supports the "unlock/expired lockouts" operational query without a full scan.
CREATE INDEX ix_users_locked_until ON users (locked_until) WHERE locked_until IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Support indexes for the authentication paths
--
-- The outbox and notification tables arrive in a later phase; these two indexes exist now
-- because reset-token lookup and session expiry sweeping are already implemented.
-- -------------------------------------------------------------------------------------
CREATE INDEX ix_password_reset_tokens_live
    ON password_reset_tokens (expires_at) WHERE used_at IS NULL;

CREATE INDEX ix_user_sessions_live
    ON user_sessions (user_id) WHERE revoked_at IS NULL;
