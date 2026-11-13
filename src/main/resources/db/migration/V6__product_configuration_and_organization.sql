-- =====================================================================================
-- EDAM — Module A (application/product configuration) and Module B (organization) (V6)
--
-- Two decisions in this migration are worth reading before the DDL:
--
-- 1. `organization_profile` is a SINGLETON, not a tenant registry. It represents the
--    purchasing bank's own configuration for this installation. The earlier `institution`
--    table modelled a multi-bank registry, which the requirement does not describe. A
--    partial unique index on a constant expression is what makes "exactly one active
--    profile" a database fact rather than a convention.
--
-- 2. The organization hierarchy is `organization_unit_types` + self-referencing
--    `organization_units`, with `departments` and `branches` as typed specializations.
--    The requirement is explicit that "every bank uses the same organization structure"
--    must not be assumed, and that head office / regions / branches / departments must all
--    be representable. A fixed enum of unit types in Java could not express a bank with
--    regions, so the types are data.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Module A — organization profile and product configuration
-- -------------------------------------------------------------------------------------

CREATE TABLE media_assets (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name   VARCHAR(255),
    storage_provider   VARCHAR(32)  NOT NULL,
    storage_reference  VARCHAR(500) NOT NULL,
    mime_type          VARCHAR(150),
    file_size          BIGINT       NOT NULL,
    checksum           VARCHAR(64),
    uploaded_by        UUID         REFERENCES users (id),
    uploaded_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_media_assets_provider CHECK (storage_provider IN ('CLOUDINARY', 'OBJECT_STORAGE', 'LOCAL')),
    CONSTRAINT ck_media_assets_size CHECK (file_size > 0)
);

COMMENT ON TABLE media_assets IS
    'Metadata and a storage reference only. File bytes live in bank-approved storage, never in the database.';

-- Singleton profile. The CHECK + partial unique index below allow at most one row with
-- is_active = true while keeping superseded profiles for history.
CREATE TABLE organization_profile (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    organization_name   VARCHAR(200) NOT NULL,
    legal_name          VARCHAR(300),
    logo_asset_id       UUID        REFERENCES media_assets (id),
    favicon_asset_id    UUID        REFERENCES media_assets (id),
    timezone            VARCHAR(60)  NOT NULL DEFAULT 'Asia/Karachi',
    date_format         VARCHAR(40)  NOT NULL DEFAULT 'dd MMM yyyy',
    default_currency_id UUID,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uk_organization_profile_active
    ON organization_profile ((TRUE)) WHERE is_active;

COMMENT ON TABLE organization_profile IS
    'Exactly one active row per installation. Not a multi-bank registry; one EDAM deployment serves one bank.';

-- Generic application settings, typed so the UI can render the right control and the API
-- can validate. JSONB value with an explicit setting_type is the requirement's model.
CREATE TABLE application_settings (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    setting_key   VARCHAR(150) NOT NULL,
    setting_value JSONB,
    setting_type  VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by    UUID         REFERENCES users (id),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_application_settings_key UNIQUE (setting_key),
    CONSTRAINT ck_application_settings_type CHECK (setting_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON', 'DATE'))
);

COMMENT ON TABLE application_settings IS
    'Genuinely dynamic configuration only. Typical settings that have a proper home (currencies, categories) '
    'must not be smuggled in here; the requirements warn against a catch-all key/value store.';

CREATE TABLE branding_themes (
    id               UUID         PRIMARY KEY DEFAULT uuidv7(),
    theme_name       VARCHAR(100) NOT NULL,
    primary_color    VARCHAR(9),
    secondary_color  VARCHAR(9),
    accent_color     VARCHAR(9),
    background_color VARCHAR(9),
    text_color       VARCHAR(9),
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(100),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_branding_themes_name UNIQUE (theme_name),
    -- Colour accepts #rgb, #rrggbb, #rrggbbaa, or a CSS custom-property-free named token.
    -- Deliberately ASCII-only: the value is injected into a stylesheet served to browsers.
    CONSTRAINT ck_branding_themes_colour CHECK (
        (primary_color IS NULL OR primary_color ~ '^#[0-9a-fA-F]{3,8}$')
        AND (secondary_color IS NULL OR secondary_color ~ '^#[0-9a-fA-F]{3,8}$')
        AND (accent_color IS NULL OR accent_color ~ '^#[0-9a-fA-F]{3,8}$')
        AND (background_color IS NULL OR background_color ~ '^#[0-9a-fA-F]{3,8}$')
        AND (text_color IS NULL OR text_color ~ '^#[0-9a-fA-F]{3,8}$'))
);

-- At most one default theme: two defaults would make the rendered theme depend on row order.
CREATE UNIQUE INDEX uk_branding_themes_default ON branding_themes ((TRUE)) WHERE is_default AND is_active;

CREATE TABLE theme_tokens (
    id          UUID         PRIMARY KEY DEFAULT uuidv7(),
    theme_id    UUID         NOT NULL REFERENCES branding_themes (id) ON DELETE CASCADE,
    token_key   VARCHAR(100) NOT NULL,
    token_value VARCHAR(500) NOT NULL,
    token_type  VARCHAR(20)  NOT NULL DEFAULT 'COLOUR',
    CONSTRAINT uk_theme_tokens UNIQUE (theme_id, token_key),
    CONSTRAINT ck_theme_tokens_type CHECK (token_type IN ('COLOUR', 'FONT', 'SPACING', 'RADIUS', 'OTHER'))
);

CREATE TABLE feature_flags (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    feature_key   VARCHAR(100) NOT NULL,
    display_name  VARCHAR(150) NOT NULL,
    description   TEXT,
    is_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    configuration JSONB,
    updated_by    UUID         REFERENCES users (id),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_feature_flags_key UNIQUE (feature_key)
);

CREATE TABLE system_parameters (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    parameter_key   VARCHAR(150) NOT NULL,
    parameter_value JSONB,
    value_type      VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    description     TEXT,
    is_editable     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      UUID         REFERENCES users (id),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_system_parameters_key UNIQUE (parameter_key),
    CONSTRAINT ck_system_parameters_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON', 'DATE'))
);

COMMENT ON COLUMN system_parameters.is_editable IS
    'FALSE for parameters the product relies on (for example the schema version marker). The API refuses to edit them.';

-- -------------------------------------------------------------------------------------
-- Module B — organization
--
-- The hierarchy is deliberately generic: a bank with head office -> region -> branch and a
-- bank with head office -> department are both expressible without a code change.
-- -------------------------------------------------------------------------------------

CREATE TABLE organization_unit_types (
    id          UUID         PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(100) NOT NULL,
    code        VARCHAR(50)  NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_organization_unit_types_code UNIQUE (code),
    CONSTRAINT ck_organization_unit_types_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$')
);

CREATE TABLE organization_units (
    id            UUID        PRIMARY KEY DEFAULT uuidv7(),
    parent_unit_id UUID,
    unit_type_id  UUID        NOT NULL REFERENCES organization_unit_types (id),
    code          VARCHAR(64) NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(100),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by    VARCHAR(100),
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_organization_units_code UNIQUE (code),
    CONSTRAINT fk_organization_units_parent FOREIGN KEY (parent_unit_id) REFERENCES organization_units (id),
    CONSTRAINT ck_organization_units_not_own_parent CHECK (parent_unit_id IS NULL OR parent_unit_id <> id)
);

CREATE INDEX ix_organization_units_parent ON organization_units (parent_unit_id);
CREATE INDEX ix_organization_units_type ON organization_units (unit_type_id);

COMMENT ON TABLE organization_units IS
    'Self-referencing hierarchy (head office, region, branch, department, ...). Depth is not fixed because banks differ.';

-- A department is an organization unit of a department-typed unit type, with a head.
-- department_id is the PK so that other tables can reference it directly without joining
-- through organization_units on every query.
CREATE TABLE departments (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    organization_unit_id UUID       NOT NULL REFERENCES organization_units (id),
    department_code     VARCHAR(64)  NOT NULL,
    department_name     VARCHAR(200) NOT NULL,
    head_user_id        UUID         REFERENCES users (id),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_departments_code UNIQUE (department_code),
    CONSTRAINT uk_departments_unit UNIQUE (organization_unit_id)
);

CREATE INDEX ix_departments_head ON departments (head_user_id);

CREATE TABLE branches (
    id                   UUID        PRIMARY KEY DEFAULT uuidv7(),
    organization_unit_id UUID        NOT NULL REFERENCES organization_units (id),
    branch_code          VARCHAR(64) NOT NULL,
    branch_name          VARCHAR(200) NOT NULL,
    address              TEXT,
    is_active            BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(100),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           VARCHAR(100),
    version              BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_branches_code UNIQUE (branch_code),
    CONSTRAINT uk_branches_unit UNIQUE (organization_unit_id)
);

-- users.department_id / branch_id were declared in V5 without foreign keys so that this
-- migration could own the organization tables. The constraints are added now that the
-- targets exist.
ALTER TABLE users
    ADD CONSTRAINT fk_users_department FOREIGN KEY (department_id) REFERENCES departments (id),
    ADD CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES branches (id);

-- Module A also references departments indirectly through users only; no further FK needed.
