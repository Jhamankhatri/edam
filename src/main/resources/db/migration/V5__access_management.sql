-- =====================================================================================
-- EDAM — Module C: User, security and access management (V5)
--
-- This migration replaces the provisional access-control model with the normative one.
--
-- Differences from V3 that matter:
--   * UUID primary keys for business entities (was BIGINT identity), so identifiers are
--     globally unique and cannot be enumerated by incrementing a URL.
--   * Roles and permissions are GLOBAL catalogue data, not per-institution copies. A role
--     is a reusable definition ("Expense Configuration Administrator"); what makes it
--     powerful in one institution and not another is the user's ORGANIZATIONAL DATA SCOPE
--     (user_data_scopes), not a duplicate role row per tenant.
--   * user_roles supports an optional expiry, so temporary elevation is expressible and
--     self-expiring rather than relying on someone remembering to revoke it.
--   * Data scopes are a first-class table. The requirement is explicit: "A role is not
--     automatically a global administrator. Permissions and organizational data scopes
--     determine what the user can manage."
--
-- No credential is inserted by this migration. The first administrator is created by a
-- secure bootstrap process (see InstallationBootstrapService); committing a default
-- username and password would put a known credential in source control and in every
-- database restored from a seed.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Users
--
-- `password_hash` is nullable because authentication may be delegated to LDAP/AD/SSO.
-- Requiring it would force a local credential to exist for every user even when the bank
-- uses federated authentication, which is both unnecessary and an extra attack surface.
--
-- `manager_id` is a self-reference used for escalation-by-manager. It is nullable because
-- the top of the organization has no manager; a non-null requirement would force a fake
-- reporting line.
-- -------------------------------------------------------------------------------------

CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    employee_code VARCHAR(50),
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255),
    department_id UUID,
    branch_id     UUID,
    designation   VARCHAR(150),
    manager_id    UUID,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(100),
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_users_manager FOREIGN KEY (manager_id) REFERENCES users (id),
    CONSTRAINT ck_users_not_own_manager CHECK (manager_id IS NULL OR manager_id <> id)
);

-- Department and branch foreign keys are added after those tables exist (V6), because the
-- organization module owns them and creating the constraint here would require this
-- migration to depend on a later one.
CREATE INDEX ix_users_department ON users (department_id);
CREATE INDEX ix_users_branch ON users (branch_id);
CREATE INDEX ix_users_manager ON users (manager_id);
CREATE INDEX ix_users_active ON users (is_active) WHERE is_active;

COMMENT ON COLUMN users.password_hash IS
    'BCrypt hash when local authentication is used. NULL when authentication is delegated to LDAP/AD/SSO.';
COMMENT ON COLUMN users.employee_code IS 'Bank employee code; the identifier the bank quotes in its own systems.';

-- -------------------------------------------------------------------------------------
-- Roles and permissions (global catalogue)
--
-- `is_system_role` protects the roles the product depends on from deletion. It does NOT
-- make them immutable: a bank may rename and re-grant them, because the requirement is a
-- configurable role model, not a fixed one.
--
-- A role confers capability. Where it may be used is decided by the holder's data scopes,
-- which is what stops an Expense Configuration Administrator in one department from
-- reconfiguring categories for the whole bank.
-- -------------------------------------------------------------------------------------

CREATE TABLE roles (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    name           VARCHAR(100) NOT NULL,
    code           VARCHAR(100) NOT NULL,
    description    TEXT,
    is_system_role BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_roles_code UNIQUE (code),
    CONSTRAINT ck_roles_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]{1,99}$')
);

COMMENT ON COLUMN roles.is_system_role IS
    'Product-critical role: it cannot be deleted while it is the only holder of a required capability.';

CREATE TABLE permissions (
    id          UUID         PRIMARY KEY DEFAULT uuidv7(),
    code        VARCHAR(150) NOT NULL,
    module      VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_permissions_code UNIQUE (code),
    CONSTRAINT ck_permissions_code_format CHECK (code ~ '^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$')
);

COMMENT ON TABLE permissions IS
    'Product permission catalogue, installed by versioned migration. Codes are a public contract: never renamed or reused.';

CREATE TABLE role_permissions (
    role_id       UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID        NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

-- -------------------------------------------------------------------------------------
-- User role assignments
--
-- `expires_at` exists so that temporary elevation (a month-end approver, an audit
-- secondment) expires on its own. A nullable column would have made "no expiry" implicit;
-- the constraint below makes an expiry that has already passed impossible to insert by
-- accident, so a mis-keyed date fails loudly rather than granting silently.
-- -------------------------------------------------------------------------------------

CREATE TABLE user_roles (
    id          UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES roles (id),
    assigned_by UUID        REFERENCES users (id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at  TIMESTAMPTZ,
    CONSTRAINT ck_user_roles_expiry CHECK (expires_at IS NULL OR expires_at > assigned_at)
);

-- One live assignment per (user, role). Re-granting after expiry inserts a new row, so the
-- audit trail keeps both the original grant and the regrant.
CREATE UNIQUE INDEX uk_user_roles_live ON user_roles (user_id, role_id)
    WHERE expires_at IS NULL;

CREATE INDEX ix_user_roles_user ON user_roles (user_id);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);
CREATE INDEX ix_user_roles_expiry ON user_roles (expires_at) WHERE expires_at IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Organizational data scopes (the requirement's central access-control idea)
--
-- A scope answers "over which organizational units may this user exercise the permissions
-- their roles grant?". Without it a role is implicitly organization-wide, which is exactly
-- the "role equals global administrator" behaviour the requirement forbids.
--
-- scope_type:
--   ALL        — the whole installation (still distinct from a platform operator role)
--   DEPARTMENT — one or more departments (scope_reference_id -> departments.id)
--   BRANCH     — one or more branches (scope_reference_id -> branches.id)
--   ORGANIZATION_UNIT — any node of the hierarchy (scope_reference_id -> organization_units.id)
--   SELF       — the user's own records only; scope_reference_id is null
--
-- scope_reference_id is UUID and intentionally not a foreign key: it addresses one of three
-- different tables depending on scope_type, and PostgreSQL cannot express a polymorphic
-- foreign key. Referential integrity for scopes is enforced by the service layer and
-- verified by a test that resolves every scope row to its target. A generic
-- `lookup_values`-style reference table would have hidden the relationship instead of
-- documenting it, which the requirements explicitly warn against.
-- -------------------------------------------------------------------------------------

CREATE TABLE user_data_scopes (
    id                 UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    scope_type         VARCHAR(50) NOT NULL,
    scope_reference_id UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(100),
    CONSTRAINT ck_user_data_scopes_type CHECK (scope_type IN ('ALL', 'DEPARTMENT', 'BRANCH', 'ORGANIZATION_UNIT',
        'SELF')),
    CONSTRAINT ck_user_data_scopes_reference CHECK (
        (scope_type IN ('ALL', 'SELF') AND scope_reference_id IS NULL)
        OR (scope_type IN ('DEPARTMENT', 'BRANCH', 'ORGANIZATION_UNIT') AND scope_reference_id IS NOT NULL))
);

CREATE UNIQUE INDEX uk_user_data_scopes_unique
    ON user_data_scopes (user_id, scope_type, scope_reference_id) NULLS NOT DISTINCT;
CREATE INDEX ix_user_data_scopes_user ON user_data_scopes (user_id);

COMMENT ON TABLE user_data_scopes IS
    'Narrows where a role''s permissions apply. A user with no scope row is denied store-level access by the '
    'authorization layer, which is deliberate: an unscoped user is a configuration error, not a superuser.';

-- -------------------------------------------------------------------------------------
-- Sessions, password resets and login attempts
--
-- Tokens are stored ONLY as hashes. A stolen database backup must not yield usable session
-- tokens or reset links; hashing makes the dump inert with respect to authentication.
-- -------------------------------------------------------------------------------------

CREATE TABLE user_sessions (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id            UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    session_token_hash VARCHAR(64)  NOT NULL,
    ip_address         VARCHAR(64),
    user_agent         VARCHAR(500),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at         TIMESTAMPTZ  NOT NULL,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT uk_user_sessions_token UNIQUE (session_token_hash)
);

CREATE INDEX ix_user_sessions_user ON user_sessions (user_id, created_at DESC);
CREATE INDEX ix_user_sessions_expiry ON user_sessions (expires_at);

CREATE TABLE password_reset_tokens (
    id         UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE login_attempts (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    username       VARCHAR(100) NOT NULL,
    ip_address     VARCHAR(64),
    success        BOOLEAN      NOT NULL,
    failure_reason VARCHAR(200),
    attempted_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Supports both the operational question ("is someone being brute-forced right now?") and
-- the compliance question ("show every attempt on this account").
CREATE INDEX ix_login_attempts_username_time ON login_attempts (username, attempted_at DESC);
CREATE INDEX ix_login_attempts_ip_time ON login_attempts (ip_address, attempted_at DESC);
CREATE INDEX ix_login_attempts_failures ON login_attempts (attempted_at DESC) WHERE NOT success;

COMMENT ON TABLE login_attempts IS
    'Append-only authentication evidence. Never stores the presented password, only whether the attempt succeeded.';
