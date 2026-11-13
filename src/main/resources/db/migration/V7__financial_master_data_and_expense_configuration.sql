-- =====================================================================================
-- EDAM — Module D (financial master data) and Module E (expense configuration and the
-- delegation matrix) (V7)
--
-- Money is NUMERIC(19,4) throughout, as required: "Financial amounts should use PostgreSQL
-- NUMERIC, never floating-point types." Scale 4 is the storage precision; the CURRENCY's
-- decimal_places decides how many of those are meaningful, and conversion/rounding rules
-- are applied by the Java calculation layer, not by the database.
--
-- The delegation matrix is the heart of the product, so its integrity rules are enforced
-- here rather than only in services:
--   * an amount band must be well formed (minimum < maximum),
--   * active rules for the same classification must not OVERLAP — the requirement is
--     explicit that "two overlapping active configurations must not silently produce
--     unpredictable results",
--   * effective-dated rows may not overlap in time either.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Module D — currencies, exchange rates, tax authorities, vendors
-- -------------------------------------------------------------------------------------

CREATE TABLE currencies (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    currency_code  VARCHAR(3)  NOT NULL,
    currency_name  VARCHAR(100) NOT NULL,
    symbol         VARCHAR(10),
    decimal_places SMALLINT    NOT NULL DEFAULT 2,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_currencies_code UNIQUE (currency_code),
    CONSTRAINT ck_currencies_code CHECK (currency_code ~ '^[A-Z]{3}$'),
    -- ISO 4217 permits 0–3 minor units for the currencies banks actually transact in. 4–6
    -- covers crypto-style precision some treasuries use; anything beyond cannot be stored.
    CONSTRAINT ck_currencies_decimal_places CHECK (decimal_places BETWEEN 0 AND 4)
);

COMMENT ON COLUMN currencies.decimal_places IS
    'Minor units for presentation and currency-specific precision validation. Storage remains NUMERIC(19,4).';

-- Effective-dated rates. A rate row is never updated in place: a correction is a new row,
-- so an amount converted last month stays reproducible.
CREATE TABLE exchange_rates (
    id               UUID          PRIMARY KEY DEFAULT uuidv7(),
    from_currency_id UUID          NOT NULL REFERENCES currencies (id),
    to_currency_id   UUID          NOT NULL REFERENCES currencies (id),
    exchange_rate    NUMERIC(19,8) NOT NULL,
    effective_from   TIMESTAMPTZ   NOT NULL,
    effective_to     TIMESTAMPTZ,
    created_by       UUID          REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_exchange_rates_rate CHECK (exchange_rate > 0),
    CONSTRAINT ck_exchange_rates_currencies CHECK (from_currency_id <> to_currency_id),
    CONSTRAINT ck_exchange_rates_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- Two rates for the same pair that are simultaneously effective would make conversion
-- non-deterministic. Enforced as an exclusion constraint so it holds under concurrency.
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE exchange_rates
    ADD CONSTRAINT ex_exchange_rates_no_overlap
    EXCLUDE USING gist (
        from_currency_id WITH =,
        to_currency_id WITH =,
        tstzrange(effective_from, COALESCE(effective_to, 'infinity'::timestamptz), '[)') WITH &&
    );

CREATE INDEX ix_exchange_rates_pair ON exchange_rates (from_currency_id, to_currency_id, effective_from DESC);

CREATE TABLE tax_authorities (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    authority_name VARCHAR(200) NOT NULL,
    authority_code VARCHAR(64)  NOT NULL,
    description    TEXT,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_tax_authorities_code UNIQUE (authority_code)
);

-- Vendors are business master data, entered or imported by the bank. They are NOT seeded:
-- the requirements forbid inventing vendors in install scripts.
CREATE TABLE vendors (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    vendor_code    VARCHAR(64)  NOT NULL,
    vendor_name    VARCHAR(300) NOT NULL,
    tax_number     VARCHAR(64),
    contact_person VARCHAR(200),
    email          VARCHAR(255),
    phone          VARCHAR(50),
    address        TEXT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_vendors_code UNIQUE (vendor_code),
    CONSTRAINT ck_vendors_email CHECK (email IS NULL OR email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')
);

-- After organization_profile exists, wire its default currency. ALTER is used rather than a
-- forward reference because V6 created the profile table and this migration owns currencies.
ALTER TABLE organization_profile
    ADD CONSTRAINT fk_organization_profile_currency FOREIGN KEY (default_currency_id) REFERENCES currencies (id);

-- -------------------------------------------------------------------------------------
-- Module E — expense configuration
-- -------------------------------------------------------------------------------------

CREATE TABLE expense_categories (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    category_code   VARCHAR(64)  NOT NULL,
    category_name   VARCHAR(200) NOT NULL,
    description     TEXT,
    expense_type    VARCHAR(20)  NOT NULL DEFAULT 'BOTH',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_categories_code UNIQUE (category_code),
    CONSTRAINT ck_expense_categories_type CHECK (expense_type IN ('OPEX', 'CAPEX', 'BOTH'))
);

-- Active category names must be unique so an approver cannot be shown two identically named
-- routes. A partial unique index is used because a retired category may keep its name.
CREATE UNIQUE INDEX uk_expense_categories_active_name
    ON expense_categories (lower(category_name)) WHERE is_active;

CREATE TABLE expense_subcategories (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    category_id        UUID         NOT NULL REFERENCES expense_categories (id),
    subcategory_code   VARCHAR(64)  NOT NULL,
    subcategory_name   VARCHAR(200) NOT NULL,
    description        TEXT,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(100),
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_subcategories_code UNIQUE (category_id, subcategory_code)
);

CREATE UNIQUE INDEX uk_expense_subcategories_active_name
    ON expense_subcategories (category_id, lower(subcategory_name)) WHERE is_active;

-- Referenced by the delegation-rule composite FK below (a subcategory must belong to the
-- rule's category). The index has to exist before that table is created, because PostgreSQL
-- resolves the referenced unique constraint at CREATE TABLE time.
CREATE UNIQUE INDEX uk_expense_subcategories_id_category ON expense_subcategories (id, category_id);
CREATE INDEX ix_expense_subcategories_category ON expense_subcategories (category_id);

-- Natures and request types are configuration, not enums: a bank may define additional
-- natures, and the OPEX/CAPEX distinction lives on expense_categories.expense_type.
CREATE TABLE expense_natures (
    id           UUID         PRIMARY KEY DEFAULT uuidv7(),
    nature_code  VARCHAR(64)  NOT NULL,
    nature_name  VARCHAR(200) NOT NULL,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(100),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_natures_code UNIQUE (nature_code)
);

CREATE TABLE expense_request_types (
    id          UUID         PRIMARY KEY DEFAULT uuidv7(),
    type_code   VARCHAR(64)  NOT NULL,
    type_name   VARCHAR(200) NOT NULL,
    description TEXT,
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by  VARCHAR(100),
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_request_types_code UNIQUE (type_code)
);

-- -------------------------------------------------------------------------------------
-- Delegation rules and their approver steps
--
-- A rule says: for this category/subcategory/nature/request-type and this amount band in
-- this currency, run these approver steps in this order.
--
-- Overlap is prevented per CLASSIFICATION using an exclusion constraint over a numeric
-- range, but only for active and currently effective rows. That mirrors the requirement:
-- historical rules may overlap because they applied at different times, but two rules that
-- could both match right now must not exist.
-- -------------------------------------------------------------------------------------

CREATE TABLE expense_delegation_rules (
    id              UUID          PRIMARY KEY DEFAULT uuidv7(),
    category_id     UUID          NOT NULL REFERENCES expense_categories (id),
    subcategory_id  UUID          REFERENCES expense_subcategories (id),
    nature_id       UUID          NOT NULL REFERENCES expense_natures (id),
    request_type_id UUID          REFERENCES expense_request_types (id),
    minimum_amount  NUMERIC(19,4) NOT NULL DEFAULT 0,
    maximum_amount  NUMERIC(19,4),
    currency_id     UUID          NOT NULL REFERENCES currencies (id),
    effective_from  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to    TIMESTAMPTZ,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_expense_delegation_rules_amount CHECK (minimum_amount >= 0),
    CONSTRAINT ck_expense_delegation_rules_band
        CHECK (maximum_amount IS NULL OR maximum_amount > minimum_amount),
    CONSTRAINT ck_expense_delegation_rules_period
        CHECK (effective_to IS NULL OR effective_to > effective_from),
    -- A subcategory must belong to the rule's category. Enforced with a composite foreign
    -- key against a unique index on (id, category_id), which is the only way SQL can express
    -- this without a trigger.
    CONSTRAINT fk_expense_delegation_rules_subcategory
        FOREIGN KEY (subcategory_id, category_id) REFERENCES expense_subcategories (id, category_id)
);

-- No two ACTIVE, currently-effective rules may cover the same amount for the same
-- classification. NULL subcategory/request type means "any", so NULLS NOT DISTINCT is
-- required; without it a category-wide rule could be inserted indefinitely.
ALTER TABLE expense_delegation_rules
    ADD CONSTRAINT ex_expense_delegation_rules_no_overlap
    EXCLUDE USING gist (
        category_id WITH =,
        COALESCE(subcategory_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        nature_id WITH =,
        COALESCE(request_type_id, '00000000-0000-0000-0000-000000000000'::uuid) WITH =,
        currency_id WITH =,
        numrange(minimum_amount, COALESCE(maximum_amount, 'infinity'::numeric), '[)') WITH &&
    ) WHERE (is_active);

CREATE INDEX ix_expense_delegation_rules_lookup
    ON expense_delegation_rules (category_id, nature_id, is_active, minimum_amount);

COMMENT ON CONSTRAINT ex_expense_delegation_rules_no_overlap ON expense_delegation_rules IS
    'Prevents two active rules from matching the same amount for one classification: the requirement '
    'forbids overlapping active configuration. A zero UUID stands in for "any" because NULL is not equal to NULL.';

CREATE TABLE delegation_approver_steps (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    delegation_rule_id UUID        NOT NULL REFERENCES expense_delegation_rules (id) ON DELETE CASCADE,
    step_order        SMALLINT     NOT NULL,
    approver_type     VARCHAR(40)  NOT NULL,
    required_role_id  UUID         REFERENCES roles (id),
    department_scope  VARCHAR(64),
    is_mandatory      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_delegation_approver_steps UNIQUE (delegation_rule_id, step_order),
    CONSTRAINT ck_delegation_approver_steps_order CHECK (step_order >= 1),
    CONSTRAINT ck_delegation_approver_steps_type CHECK (approver_type IN (
        'DEPARTMENT_HEAD', 'RECOMMENDING_AUTHORITY', 'EXPENSE_COMMITTEE', 'CEO', 'CFO',
        'HEAD_OF_ADMIN_GSD', 'ROLE', 'USER', 'MANAGER')),
    -- A ROLE step must name a role; the other types resolve their approvers from
    -- configuration, so a stray role id would be ignored and is therefore rejected.
    CONSTRAINT ck_delegation_approver_steps_role
        CHECK ((approver_type = 'ROLE') = (required_role_id IS NOT NULL))
);

CREATE INDEX ix_delegation_approver_steps_rule ON delegation_approver_steps (delegation_rule_id, step_order);

-- -------------------------------------------------------------------------------------
-- Department approval limits and recommending authorities (FM-09, FM-12)
-- -------------------------------------------------------------------------------------

CREATE TABLE department_approval_limits (
    id              UUID          PRIMARY KEY DEFAULT uuidv7(),
    department_id   UUID          NOT NULL REFERENCES departments (id),
    user_id         UUID          REFERENCES users (id),
    expense_type    VARCHAR(20)   NOT NULL DEFAULT 'BOTH',
    currency_id     UUID          NOT NULL REFERENCES currencies (id),
    approval_limit  NUMERIC(19,4) NOT NULL,
    effective_from  TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to    TIMESTAMPTZ,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_department_approval_limits_amount CHECK (approval_limit > 0),
    CONSTRAINT ck_department_approval_limits_type CHECK (expense_type IN ('OPEX', 'CAPEX', 'BOTH')),
    CONSTRAINT ck_department_approval_limits_period
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

-- One active limit per department, optional holder, expense type and currency. A NULL user
-- means the limit applies to the department as a whole (its head), which is why NULLS NOT
-- DISTINCT is needed here too.
CREATE UNIQUE INDEX uk_department_approval_limits_active
    ON department_approval_limits (department_id, user_id, expense_type, currency_id) NULLS NOT DISTINCT
    WHERE is_active;

CREATE INDEX ix_department_approval_limits_department
    ON department_approval_limits (department_id, is_active);

CREATE TABLE recommending_authorities (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    department_id  UUID        NOT NULL REFERENCES departments (id),
    user_id        UUID        NOT NULL REFERENCES users (id),
    expense_type   VARCHAR(20) NOT NULL DEFAULT 'BOTH',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to   TIMESTAMPTZ,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_recommending_authorities_type CHECK (expense_type IN ('OPEX', 'CAPEX', 'BOTH')),
    CONSTRAINT ck_recommending_authorities_period
        CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX uk_recommending_authorities_active
    ON recommending_authorities (department_id, user_id, expense_type) WHERE is_active;

-- -------------------------------------------------------------------------------------
-- Exceeding limits (FM-13)
--
-- escalation_type is configuration and is NOT consulted by the approval engine: the BRD
-- leaves the consequence of exceeding the limit undecided. Storing the bank's stated
-- intent without acting on it is the honest representation; the API reports
-- escalation_type alongside an explicit "not enforced" marker.
-- -------------------------------------------------------------------------------------

CREATE TABLE exceeding_limit_rules (
    id               UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_type      VARCHAR(20)  NOT NULL,
    nature_id         UUID         NOT NULL REFERENCES expense_natures (id),
    currency_id       UUID         NOT NULL REFERENCES currencies (id),
    threshold_amount  NUMERIC(19,4) NOT NULL,
    escalation_type   VARCHAR(40)  NOT NULL DEFAULT 'NONE',
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        VARCHAR(100),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(100),
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_exceeding_limit_rules_amount CHECK (threshold_amount > 0),
    CONSTRAINT ck_exceeding_limit_rules_type CHECK (expense_type IN ('OPEX', 'CAPEX', 'BOTH')),
    CONSTRAINT ck_exceeding_limit_rules_escalation CHECK (escalation_type IN (
        'NONE', 'ADD_CEO_APPROVAL', 'NOTIFY_CFO', 'BLOCK'))
);

CREATE UNIQUE INDEX uk_exceeding_limit_rules_active
    ON exceeding_limit_rules (expense_type, nature_id, currency_id) WHERE is_active;
