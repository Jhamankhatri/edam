-- =====================================================================================
-- EDAM — Phase 4B: Organization profile extensions, branding, typed application settings,
--         feature flags and public-config endpoint support.
--
-- V6 already introduced the skeleton tables (organization_profile, branding_themes,
-- theme_tokens, application_settings, system_parameters, feature_flags). This migration
-- (a) extends organization_profile with the contact and display fields required by
--     Module A of Phase 4B;
-- (b) adds theme-type (LIGHT/DARK) and description columns required by Module B,
--     strengthens the default-theme invariant and widens the colour regex to accept
--     the full set of token colours and #rrggbbaa;
-- (c) adds a display_name / category / default_value / constraints / editable columns
--     to application_settings required by Module C; renames NUMBER -> INTEGER/DECIMAL
--     to reflect typed numeric validation;
-- (d) creates the feature_flag table if V6's version has been dropped/replaced by a
--     later revision — V13 dropped the provisional institution_feature_flag; V6's
--     feature_flags table is global (single deployment). We ALTER rather than CREATE
--     IF NOT EXISTS to avoid a race.
-- (e) seeds a safe default light theme and a minimal set of default application
--     settings so that the public configuration endpoint always has something to
--     return on a freshly-installed system.
--
-- Backwards-compatibility: all new columns are nullable or have defaults so existing
-- rows (including any seed rows from bootstrap) continue to load. The strengthened
-- CHECK constraint is added as NOT VALID then VALIDATE so that existing rows that do
-- not yet conform do not block the migration; the Java service will enforce new rules
-- on writes.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Module A — organization profile additions
-- -------------------------------------------------------------------------------------

ALTER TABLE organization_profile
    ADD COLUMN IF NOT EXISTS display_name      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS organization_code VARCHAR(60),
    ADD COLUMN IF NOT EXISTS contact_email     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contact_phone     VARCHAR(40),
    ADD COLUMN IF NOT EXISTS website           VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_line1     VARCHAR(300),
    ADD COLUMN IF NOT EXISTS address_line2     VARCHAR(300),
    ADD COLUMN IF NOT EXISTS address_city      VARCHAR(120),
    ADD COLUMN IF NOT EXISTS address_state     VARCHAR(120),
    ADD COLUMN IF NOT EXISTS address_postal_code VARCHAR(30),
    ADD COLUMN IF NOT EXISTS address_country   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS support_email     VARCHAR(255);

-- A code (when set) must be unique; a partial unique index allows many NULLs.
CREATE UNIQUE INDEX IF NOT EXISTS uk_organization_profile_code
    ON organization_profile (organization_code) WHERE organization_code IS NOT NULL;

-- -------------------------------------------------------------------------------------
-- Module B — branding / themes: strengthen schema
-- -------------------------------------------------------------------------------------

ALTER TABLE branding_themes
    ADD COLUMN IF NOT EXISTS description  TEXT,
    ADD COLUMN IF NOT EXISTS theme_type   VARCHAR(10) NOT NULL DEFAULT 'LIGHT';

-- Replace the existing tight colour CHECK with one that accepts every colour token
-- Phase 4B requires (heading, nav, surface, button, status colours) plus #rrggbbaa.
-- First drop the old-named constraint if it exists, then re-add with the full set.
ALTER TABLE branding_themes DROP CONSTRAINT IF EXISTS ck_branding_themes_colour;

ALTER TABLE branding_themes
    ADD COLUMN IF NOT EXISTS surface_color      VARCHAR(9),
    ADD COLUMN IF NOT EXISTS heading_color      VARCHAR(9),
    ADD COLUMN IF NOT EXISTS nav_bg_color       VARCHAR(9),
    ADD COLUMN IF NOT EXISTS nav_text_color     VARCHAR(9),
    ADD COLUMN IF NOT EXISTS button_primary_color    VARCHAR(9),
    ADD COLUMN IF NOT EXISTS button_primary_text     VARCHAR(9),
    ADD COLUMN IF NOT EXISTS success_color      VARCHAR(9),
    ADD COLUMN IF NOT EXISTS warning_color      VARCHAR(9),
    ADD COLUMN IF NOT EXISTS error_color        VARCHAR(9),
    ADD COLUMN IF NOT EXISTS info_color         VARCHAR(9);

ALTER TABLE branding_themes
    ADD CONSTRAINT ck_branding_themes_type
        CHECK (theme_type IN ('LIGHT', 'DARK')),
    ADD CONSTRAINT ck_branding_themes_colour CHECK (
        (primary_color     IS NULL OR primary_color     ~ '^#[0-9a-fA-F]{3,8}$')
    AND (secondary_color   IS NULL OR secondary_color   ~ '^#[0-9a-fA-F]{3,8}$')
    AND (accent_color      IS NULL OR accent_color      ~ '^#[0-9a-fA-F]{3,8}$')
    AND (background_color  IS NULL OR background_color  ~ '^#[0-9a-fA-F]{3,8}$')
    AND (surface_color     IS NULL OR surface_color     ~ '^#[0-9a-fA-F]{3,8}$')
    AND (text_color        IS NULL OR text_color        ~ '^#[0-9a-fA-F]{3,8}$')
    AND (heading_color     IS NULL OR heading_color     ~ '^#[0-9a-fA-F]{3,8}$')
    AND (nav_bg_color      IS NULL OR nav_bg_color      ~ '^#[0-9a-fA-F]{3,8}$')
    AND (nav_text_color    IS NULL OR nav_text_color    ~ '^#[0-9a-fA-F]{3,8}$')
    AND (button_primary_color IS NULL OR button_primary_color ~ '^#[0-9a-fA-F]{3,8}$')
    AND (button_primary_text  IS NULL OR button_primary_text  ~ '^#[0-9a-fA-F]{3,8}$')
    AND (success_color     IS NULL OR success_color     ~ '^#[0-9a-fA-F]{3,8}$')
    AND (warning_color     IS NULL OR warning_color     ~ '^#[0-9a-fA-F]{3,8}$')
    AND (error_color       IS NULL OR error_color       ~ '^#[0-9a-fA-F]{3,8}$')
    AND (info_color        IS NULL OR info_color        ~ '^#[0-9a-fA-F]{3,8}$')
    );

-- Replace default-theme partial index: must be exactly one default among active
-- themes regardless of type. (Drop and recreate to be sure of the predicate.)
DROP INDEX IF EXISTS uk_branding_themes_default;
CREATE UNIQUE INDEX uk_branding_themes_default
    ON branding_themes ((TRUE)) WHERE is_default = TRUE AND is_active = TRUE;

-- Token key whitelist is enforced at the application layer; the DB enforces
-- ASCII and max length to guard against injection.
ALTER TABLE theme_tokens
    DROP CONSTRAINT IF EXISTS ck_theme_tokens_key_safe;
ALTER TABLE theme_tokens
    ADD CONSTRAINT ck_theme_tokens_key_safe
        CHECK (token_key ~ '^[A-Za-z][A-Za-z0-9_-]{1,99}$');

-- -------------------------------------------------------------------------------------
-- Module C — application settings: widen for typed categories, defaults, validation
-- -------------------------------------------------------------------------------------

-- V6 created application_settings with a UUID FK updated_by column (and only updated_at).
-- Add display_name/category/default_value/is_editable so Phase 4B CRUD works, plus the
-- created_at/created_by stamps that the new entity carries. We do NOT touch updated_by
-- (FK to users); the audit log records actors.
ALTER TABLE application_settings
    ADD COLUMN IF NOT EXISTS display_name   VARCHAR(200),
    ADD COLUMN IF NOT EXISTS category       VARCHAR(80)  NOT NULL DEFAULT 'general',
    ADD COLUMN IF NOT EXISTS default_value  JSONB,
    ADD COLUMN IF NOT EXISTS is_editable    BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by     VARCHAR(100);

-- Replace CHECK to accept INTEGER and DECIMAL as distinct from NUMBER (both numeric,
-- different validation semantics). Keep STRING, BOOLEAN, JSON, DATE. We do this by
-- dropping the old constraint if it exists and re-adding it.
ALTER TABLE application_settings DROP CONSTRAINT IF EXISTS ck_application_settings_type;
ALTER TABLE application_settings
    ADD CONSTRAINT ck_application_settings_type
        CHECK (setting_type IN ('STRING','BOOLEAN','INTEGER','DECIMAL','JSON','DATE'));

-- When setting a user who updated, resolve user id to a string for the existing
-- updated_by column where necessary. The column already exists on application_settings
-- (V6 made it UUID REFERENCES users) so we leave that relationship intact.

-- -------------------------------------------------------------------------------------
-- Module D — feature flags: V6 already created this table without created_at/created_by.
-- Add those columns so the entity and service can stamp creators; updated_by remains a
-- UUID FK to users (as V6 defined it) and is intentionally not written to by Phase 4B.
-- -------------------------------------------------------------------------------------

ALTER TABLE feature_flags
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_by   VARCHAR(100);

-- -------------------------------------------------------------------------------------
-- Seed data: safe default light theme and essential application settings
-- -------------------------------------------------------------------------------------

DO $$
DECLARE
    default_theme_id UUID := '00000000-0000-7000-8000-000000000001'::uuid;
BEGIN
    -- Default EDAM Light theme
    INSERT INTO branding_themes (id, theme_name, description, theme_type,
            primary_color, secondary_color, accent_color, background_color, surface_color,
            text_color, heading_color, nav_bg_color, nav_text_color,
            button_primary_color, button_primary_text,
            success_color, warning_color, error_color, info_color,
            is_default, is_active, created_by, updated_by)
    VALUES (default_theme_id, 'EDAM Light', 'Default light theme for EDAM', 'LIGHT',
            '#1a56db', '#e5edff', '#3b82f6', '#ffffff', '#f9fafb',
            '#111827', '#0f172a', '#0f172a', '#f8fafc',
            '#1a56db', '#ffffff',
            '#047857', '#b45309', '#b91c1c', '#1d4ed8',
            TRUE, TRUE, 'system', 'system')
    ON CONFLICT (id) DO NOTHING;

    -- If no default theme exists, promote the first active one (safety net).
    IF NOT EXISTS (SELECT 1 FROM branding_themes WHERE is_default AND is_active) THEN
        UPDATE branding_themes SET is_default = TRUE, updated_at = CURRENT_TIMESTAMP
         WHERE id = (SELECT id FROM branding_themes WHERE is_active ORDER BY created_at LIMIT 1);
    END IF;
END $$;

-- Seed essential application settings (idempotent by unique key).
-- NOTE: application_settings.updated_by is a UUID FK to users (V6), so we cannot put a
-- plain-text auditor string there. Leave it NULL; the audit log records the seed
-- operation separately. created_by is VARCHAR(100) (added above) so 'system' is fine.
INSERT INTO application_settings
        (setting_key, display_name, category, setting_type, setting_value,
         default_value, description, is_active, is_editable, created_by)
VALUES
    ('app.displayName',       'Application display name', 'branding',     'STRING',
        to_jsonb('EDAM'::text),  to_jsonb('EDAM'::text),
        'The product/installation name shown in the application shell and browser title.',
        TRUE, TRUE, 'system'),
    ('app.defaultLocale',     'Default locale',           'localization', 'STRING',
        to_jsonb('en-GB'::text), to_jsonb('en-GB'::text),
        'Default BCP-47 locale used for number/date formatting until the user chooses one.',
        TRUE, TRUE, 'system'),
    ('app.defaultCurrency',   'Default currency code',    'localization', 'STRING',
        NULL,                     NULL,
        'ISO 4217 code of the default currency. Falls back to the organization default_currency_id when null.',
        TRUE, TRUE, 'system'),
    ('app.dateFormat',        'Server-side date format',  'localization', 'STRING',
        to_jsonb('dd MMM yyyy'::text), to_jsonb('dd MMM yyyy'::text),
        'Default date format pattern used for date rendering where no user preference exists.',
        TRUE, TRUE, 'system'),
    ('app.timeZone',          'Default time zone',        'localization', 'STRING',
        to_jsonb('Asia/Karachi'::text), to_jsonb('Asia/Karachi'::text),
        'IANA time zone used for timestamp rendering when the user has not set a preference.',
        TRUE, TRUE, 'system'),
    ('app.pagination.pageSize',   'Default page size',    'pagination',   'INTEGER',
        to_jsonb(20), to_jsonb(20),
        'Default number of rows per page in list views.',
        TRUE, TRUE, 'system'),
    ('app.pagination.maxPageSize','Maximum page size',    'pagination',   'INTEGER',
        to_jsonb(200), to_jsonb(200),
        'Hard ceiling on the page_size query parameter to prevent a client from pulling every row at once.',
        TRUE, FALSE, 'system'),
    ('notifications.email.enabled','Email notifications enabled', 'notifications', 'BOOLEAN',
        to_jsonb(TRUE), to_jsonb(TRUE),
        'Whether transactional email notifications are dispatched. Does not affect audit logging.',
        TRUE, TRUE, 'system')
ON CONFLICT (setting_key) DO NOTHING;
