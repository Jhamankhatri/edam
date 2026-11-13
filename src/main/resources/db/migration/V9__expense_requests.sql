-- =====================================================================================
-- EDAM — Module G: expense request management (V9)
--
-- Structure: ONE common request header + typed OPEX / CAPEX detail tables, exactly as the
-- requirement recommends ("This avoids putting dozens of nullable OPEX/CAPEX-specific
-- columns in one giant table").
--
-- Every monetary column is NUMERIC(19,4). Every amount that will be converted stores its
-- base-currency equivalent AND the exchange-rate row used, so a later rate change cannot
-- restate an approved figure.
--
-- Documents are referenced through media_assets; no file content is stored here.
-- =====================================================================================

CREATE TABLE expense_requests (
    id                           UUID          PRIMARY KEY DEFAULT uuidv7(),
    request_number               VARCHAR(100)  NOT NULL,
    request_type_id              UUID          NOT NULL REFERENCES expense_request_types (id),
    expense_category_id          UUID          NOT NULL REFERENCES expense_categories (id),
    expense_subcategory_id       UUID          REFERENCES expense_subcategories (id),
    expense_nature_id            UUID          NOT NULL REFERENCES expense_natures (id),
    requested_by                 UUID          NOT NULL REFERENCES users (id),
    department_id                UUID          NOT NULL REFERENCES departments (id),
    branch_id                    UUID          REFERENCES branches (id),
    currency_id                  UUID          NOT NULL REFERENCES currencies (id),
    requested_amount             NUMERIC(19,4) NOT NULL,
    base_currency_amount         NUMERIC(19,4) NOT NULL,
    exchange_rate_id             UUID          REFERENCES exchange_rates (id),
    justification                TEXT,
    current_workflow_instance_id UUID,
    current_status_code          VARCHAR(60)   NOT NULL DEFAULT 'DRAFT',
    submitted_at                 TIMESTAMPTZ,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                   VARCHAR(100),
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                   VARCHAR(100),
    version                      BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_requests_number UNIQUE (request_number),
    CONSTRAINT ck_expense_requests_amount CHECK (requested_amount > 0),
    CONSTRAINT ck_expense_requests_base_amount CHECK (base_currency_amount >= 0),
    CONSTRAINT ck_expense_requests_status CHECK (current_status_code IN (
        'DRAFT', 'SUBMITTED', 'IN_PROGRESS', 'RETURNED', 'APPROVED', 'REJECTED', 'CANCELLED', 'REVERSED'))
);

-- The request number is the identifier humans quote, so it must be unique installation-wide
-- (not merely per user) or two people will cite "the same" request and mean different ones.
CREATE INDEX ix_expense_requests_requested_by ON expense_requests (requested_by, created_at DESC);
CREATE INDEX ix_expense_requests_department ON expense_requests (department_id, created_at DESC);
CREATE INDEX ix_expense_requests_status ON expense_requests (current_status_code, created_at DESC);
CREATE INDEX ix_expense_requests_category ON expense_requests (expense_category_id);
CREATE INDEX ix_expense_requests_submitted ON expense_requests (submitted_at DESC) WHERE submitted_at IS NOT NULL;

COMMENT ON COLUMN expense_requests.exchange_rate_id IS
    'The effective-dated rate row used for base_currency_amount. Retained so the conversion is reproducible.';

-- -------------------------------------------------------------------------------------
-- OPEX detail (one row per request)
-- -------------------------------------------------------------------------------------

CREATE TABLE opex_request_details (
    id                        UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id        UUID          NOT NULL,
    is_recurring              BOOLEAN       NOT NULL DEFAULT FALSE,
    recurrence_frequency      VARCHAR(50),
    non_recurring_justification TEXT,
    accrued_amount            NUMERIC(19,4),
    previously_approved_amount NUMERIC(19,4),
    ytd_amount                NUMERIC(19,4),
    mtd_amount                NUMERIC(19,4),
    payment_terms             TEXT,
    budget_available          NUMERIC(19,4),
    budget_reference          VARCHAR(150),
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                   BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_opex_request_details_request UNIQUE (expense_request_id),
    CONSTRAINT fk_opex_request_details_request
        FOREIGN KEY (expense_request_id) REFERENCES expense_requests (id) ON DELETE CASCADE,
    CONSTRAINT ck_opex_request_details_recurrence CHECK (
        (is_recurring AND recurrence_frequency IS NOT NULL)
        OR (NOT is_recurring)),
    CONSTRAINT ck_opex_request_details_frequency CHECK (recurrence_frequency IS NULL OR recurrence_frequency IN (
        'MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'ANNUAL'))
);

-- -------------------------------------------------------------------------------------
-- CAPEX detail (one row per request)
-- -------------------------------------------------------------------------------------

CREATE TABLE capex_request_details (
    id                   UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id   UUID          NOT NULL,
    request_type         VARCHAR(50),
    sta_id               UUID,
    invoice_total        NUMERIC(19,4),
    approval_amount      NUMERIC(19,4),
    total_charges        NUMERIC(19,4),
    is_budgeted          BOOLEAN       NOT NULL DEFAULT TRUE,
    budget_amount        NUMERIC(19,4),
    unbudgeted_reason    VARCHAR(500),
    over_budget_reason   VARCHAR(500),
    classification       VARCHAR(100),
    warranty_start_date  DATE,
    warranty_end_date    DATE,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_capex_request_details_request UNIQUE (expense_request_id),
    CONSTRAINT fk_capex_request_details_request
        FOREIGN KEY (expense_request_id) REFERENCES expense_requests (id) ON DELETE CASCADE,
    CONSTRAINT ck_capex_request_details_warranty
        CHECK (warranty_end_date IS NULL OR warranty_start_date IS NULL OR warranty_end_date >= warranty_start_date),
    -- An unbudgeted capital purchase must state why; otherwise the field exists but is never
    -- populated, which is exactly the kind of silent gap an auditor finds later.
    CONSTRAINT ck_capex_request_details_unbudgeted
        CHECK (is_budgeted OR unbudgeted_reason IS NOT NULL)
);

-- -------------------------------------------------------------------------------------
-- Supporting rows
-- -------------------------------------------------------------------------------------

CREATE TABLE expense_request_items (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    item_description   VARCHAR(500)  NOT NULL,
    quantity           NUMERIC(19,4) NOT NULL DEFAULT 1,
    unit_price         NUMERIC(19,4) NOT NULL,
    total_amount       NUMERIC(19,4) NOT NULL,
    warranty_date      DATE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_expense_request_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_expense_request_items_total CHECK (total_amount >= 0),
    -- The stored total must equal quantity x unit price, rounded to 4 dp. Enforced in the
    -- database because a wrong line total would corrupt every downstream aggregate, and a
    -- service-level check would be bypassed by any future import.
    CONSTRAINT ck_expense_request_items_arithmetic
        CHECK (total_amount = round(quantity * unit_price, 4))
);

CREATE INDEX ix_expense_request_items_request ON expense_request_items (expense_request_id);

CREATE TABLE expense_allocations (
    id                    UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id    UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    department_id         UUID          NOT NULL REFERENCES departments (id),
    branch_id             UUID          REFERENCES branches (id),
    cost_center           VARCHAR(100),
    allocation_amount     NUMERIC(19,4) NOT NULL,
    allocation_percentage NUMERIC(9,4),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_expense_allocations_amount CHECK (allocation_amount >= 0),
    CONSTRAINT ck_expense_allocations_percentage
        CHECK (allocation_percentage IS NULL OR (allocation_percentage > 0 AND allocation_percentage <= 100))
);

CREATE INDEX ix_expense_allocations_request ON expense_allocations (expense_request_id);

CREATE TABLE expense_quotations (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    vendor_id          UUID          REFERENCES vendors (id),
    quotation_number   VARCHAR(100)  NOT NULL,
    quotation_date     DATE          NOT NULL,
    quotation_amount   NUMERIC(19,4) NOT NULL,
    currency_id        UUID          NOT NULL REFERENCES currencies (id),
    media_asset_id     UUID          REFERENCES media_assets (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_expense_quotations_amount CHECK (quotation_amount > 0)
);

CREATE INDEX ix_expense_quotations_request ON expense_quotations (expense_request_id);

CREATE TABLE expense_previous_summaries (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    period             VARCHAR(40)   NOT NULL,
    previous_amount    NUMERIC(19,4),
    approved_amount    NUMERIC(19,4),
    description        TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_expense_previous_summaries_request ON expense_previous_summaries (expense_request_id);

CREATE TABLE capex_budget_summaries (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    budget_reference   VARCHAR(150)  NOT NULL,
    allocated_amount   NUMERIC(19,4) NOT NULL,
    consumed_amount    NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_amount   NUMERIC(19,4) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_capex_budget_summaries_amounts CHECK (
        allocated_amount >= 0 AND consumed_amount >= 0 AND available_amount >= 0),
    -- available = allocated - consumed. Storing all three invites drift; requiring the
    -- arithmetic to hold makes an inconsistent row impossible.
    CONSTRAINT ck_capex_budget_summaries_arithmetic
        CHECK (available_amount = allocated_amount - consumed_amount)
);

CREATE INDEX ix_capex_budget_summaries_request ON capex_budget_summaries (expense_request_id);

CREATE TABLE capex_sta_details (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    tax_authority_id   UUID          NOT NULL REFERENCES tax_authorities (id),
    sta_reference      VARCHAR(150),
    tax_amount         NUMERIC(19,4) NOT NULL DEFAULT 0,
    description        TEXT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_capex_sta_details_amount CHECK (tax_amount >= 0)
);

CREATE INDEX ix_capex_sta_details_request ON capex_sta_details (expense_request_id);

CREATE TABLE expense_invoices (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID          NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    invoice_number     VARCHAR(100)  NOT NULL,
    invoice_date       DATE          NOT NULL,
    invoice_amount     NUMERIC(19,4) NOT NULL,
    currency_id        UUID          NOT NULL REFERENCES currencies (id),
    vendor_id          UUID          REFERENCES vendors (id),
    media_asset_id     UUID          REFERENCES media_assets (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_expense_invoices_amount CHECK (invoice_amount > 0)
);

CREATE INDEX ix_expense_invoices_request ON expense_invoices (expense_request_id);

-- -------------------------------------------------------------------------------------
-- Request documents (Module H bridge)
--
-- document_type is data rather than an enum because the BRD requires conditional
-- attachments (tax, GRN, quotation, invoice) that differ per category. is_mandatory records
-- what was required at upload time, so a later configuration change does not rewrite the
-- history of what the approver actually saw.
-- -------------------------------------------------------------------------------------

CREATE TABLE request_documents (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    expense_request_id UUID         NOT NULL REFERENCES expense_requests (id) ON DELETE CASCADE,
    media_asset_id     UUID         NOT NULL REFERENCES media_assets (id),
    document_type      VARCHAR(60)  NOT NULL,
    is_mandatory       BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_by        UUID         REFERENCES users (id),
    uploaded_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_request_documents_type CHECK (document_type IN (
        'SUPPORTING', 'QUOTATION', 'INVOICE', 'TAX', 'GRN', 'WARRANTY', 'BUDGET', 'OTHER'))
);

CREATE INDEX ix_request_documents_request ON request_documents (expense_request_id);
CREATE UNIQUE INDEX uk_request_documents_asset ON request_documents (expense_request_id, media_asset_id);

-- The workflow instance references the request, and the request points at its current
-- instance. Both directions are added now that both tables exist; the request's pointer is
-- nullable because a draft has no instance yet.
ALTER TABLE expense_requests
    ADD CONSTRAINT fk_expense_requests_workflow_instance
        FOREIGN KEY (current_workflow_instance_id) REFERENCES workflow_instances (id);

ALTER TABLE workflow_instances
    ADD CONSTRAINT fk_workflow_instances_request
        FOREIGN KEY (expense_request_id) REFERENCES expense_requests (id);

ALTER TABLE capex_request_details
    ADD CONSTRAINT fk_capex_request_details_sta FOREIGN KEY (sta_id) REFERENCES capex_sta_details (id);
