-- =====================================================================================
-- EDAM — Module J (configurable business rules), Module K (audit) and Module L
-- (notifications and email) (V10)
--
-- THREE SECURITY POSITIONS ARE EXPRESSED HERE, NOT JUST IN JAVA:
--
-- 1. Business rules are DATA INTERPRETED BY A CLOSED ENGINE. condition_definition and
--    action_definition are JSONB, but the Java evaluator accepts only a known set of
--    operators and actions. Nothing in these columns is ever executed as SQL, JavaScript or
--    Java. The requirement is explicit that administrators must not be able to execute
--    arbitrary code from the database.
--
-- 2. AUDIT IS APPEND-ONLY AT THE DATABASE LEVEL. A trigger rejects UPDATE and DELETE, so a
--    privileged application user (or an accidental ORM cascade) cannot rewrite history.
--    "Administrators must not be able to modify or delete audit history" is thus a property
--    of the schema, not a convention in a service.
--
-- 3. SECRETS ARE REFERENCES, NEVER VALUES. email_provider_configs.secret_reference points
--    at a secret manager entry; there is deliberately no password or API-key column to
--    accidentally populate, log or export.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Module J — configurable business rules
-- -------------------------------------------------------------------------------------

CREATE TABLE business_rule_sets (
    id           UUID         PRIMARY KEY DEFAULT uuidv7(),
    rule_set_code VARCHAR(64) NOT NULL,
    rule_set_name VARCHAR(200) NOT NULL,
    module       VARCHAR(60)  NOT NULL,
    description  TEXT,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by   VARCHAR(100),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_rule_sets_code UNIQUE (rule_set_code)
);

CREATE TABLE business_rule_versions (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    rule_set_id    UUID        NOT NULL REFERENCES business_rule_sets (id) ON DELETE CASCADE,
    version_number INTEGER     NOT NULL,
    version_status VARCHAR(20) NOT NULL,
    effective_from TIMESTAMPTZ,
    effective_to   TIMESTAMPTZ,
    published_by   UUID        REFERENCES users (id),
    published_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_rule_versions_number UNIQUE (rule_set_id, version_number),
    CONSTRAINT ck_business_rule_versions_status CHECK (version_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_business_rule_versions_number CHECK (version_number >= 1),
    CONSTRAINT ck_business_rule_versions_period
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX uk_business_rule_versions_single_published
    ON business_rule_versions (rule_set_id) WHERE version_status = 'PUBLISHED';

CREATE TABLE business_rules (
    id                    UUID         PRIMARY KEY DEFAULT uuidv7(),
    rule_version_id       UUID         NOT NULL REFERENCES business_rule_versions (id) ON DELETE CASCADE,
    rule_code             VARCHAR(64)  NOT NULL,
    rule_name             VARCHAR(200) NOT NULL,
    condition_definition  JSONB        NOT NULL,
    action_definition     JSONB        NOT NULL,
    priority              INTEGER      NOT NULL DEFAULT 100,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version               BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_business_rules_code UNIQUE (rule_version_id, rule_code),
    CONSTRAINT ck_business_rules_priority CHECK (priority >= 0),
    -- The condition must be a JSON object; an array or scalar would be silently ignored by
    -- the evaluator, which is worse than rejecting it here.
    CONSTRAINT ck_business_rules_condition_object CHECK (jsonb_typeof(condition_definition) = 'object'),
    CONSTRAINT ck_business_rules_action_object CHECK (jsonb_typeof(action_definition) = 'object')
);

CREATE INDEX ix_business_rules_version ON business_rules (rule_version_id, priority);

COMMENT ON TABLE business_rules IS
    'Declarative conditions interpreted by a closed Java evaluator. Never executed as code; arbitrary SQL/JS is '
    'not representable. The engine rejects unknown operators and fields rather than guessing.';

-- -------------------------------------------------------------------------------------
-- Module K — audit and compliance (append-only)
-- -------------------------------------------------------------------------------------

CREATE TABLE audit_events (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    entity_type    VARCHAR(100) NOT NULL,
    entity_id      VARCHAR(100),
    action         VARCHAR(60)  NOT NULL,
    old_values     JSONB,
    new_values     JSONB,
    performed_by   UUID         REFERENCES users (id),
    performed_by_username VARCHAR(100),
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(500),
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_events_performed_by ON audit_events (performed_by, created_at DESC);
CREATE INDEX ix_audit_events_time ON audit_events (created_at DESC);
CREATE INDEX ix_audit_events_correlation ON audit_events (correlation_id);

COMMENT ON TABLE audit_events IS
    'Append-only. UPDATE and DELETE are rejected by trigger. Sensitive values (passwords, tokens, secrets) are '
    'redacted before insert by the application, and the trigger below makes later tampering impossible.';

CREATE TABLE audit_event_details (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    audit_event_id UUID         NOT NULL REFERENCES audit_events (id) ON DELETE CASCADE,
    field_name     VARCHAR(200) NOT NULL,
    old_value      TEXT,
    new_value      TEXT
);

CREATE INDEX ix_audit_event_details_event ON audit_event_details (audit_event_id);

-- Append-only enforcement. One function serves both triggers; the message names the
-- operation so an operator reading the log knows whether an UPDATE or a DELETE was attempted.
CREATE OR REPLACE FUNCTION edam_reject_audit_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION edam_reject_audit_mutation();

CREATE TRIGGER trg_audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION edam_reject_audit_mutation();

CREATE TRIGGER trg_audit_event_details_no_update
    BEFORE UPDATE ON audit_event_details
    FOR EACH ROW EXECUTE FUNCTION edam_reject_audit_mutation();

CREATE TRIGGER trg_audit_event_details_no_delete
    BEFORE DELETE ON audit_event_details
    FOR EACH ROW EXECUTE FUNCTION edam_reject_audit_mutation();

-- A detailed audit row is a child of its event. Because the parent can never be deleted, the
-- ON DELETE CASCADE above can never fire, so blocking direct child deletes is safe and makes
-- the whole audit module append-only rather than only its header table.

-- -------------------------------------------------------------------------------------
-- Module L — notifications and email
-- -------------------------------------------------------------------------------------

CREATE TABLE notification_templates (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    template_code   VARCHAR(64)  NOT NULL,
    template_name   VARCHAR(200) NOT NULL,
    channel         VARCHAR(20)  NOT NULL,
    subject_template VARCHAR(500),
    body_template   TEXT         NOT NULL,
    template_status VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    version_number  INTEGER      NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_notification_templates_code_version UNIQUE (template_code, channel, version_number),
    CONSTRAINT ck_notification_templates_channel CHECK (channel IN ('EMAIL', 'IN_APP', 'SMS')),
    CONSTRAINT ck_notification_templates_status CHECK (template_status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
);

-- One published template per code and channel: two would make the rendered email depend on
-- row order.
CREATE UNIQUE INDEX uk_notification_templates_published
    ON notification_templates (template_code, channel) WHERE template_status = 'PUBLISHED';

CREATE TABLE notification_preferences (
    id                UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    notification_type VARCHAR(60) NOT NULL,
    channel           VARCHAR(20) NOT NULL,
    is_enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_preferences UNIQUE (user_id, notification_type, channel),
    CONSTRAINT ck_notification_preferences_channel CHECK (channel IN ('EMAIL', 'IN_APP', 'SMS'))
);

CREATE TABLE notifications (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    notification_type VARCHAR(60)  NOT NULL,
    title             VARCHAR(300) NOT NULL,
    message           TEXT,
    reference_type    VARCHAR(60),
    reference_id      UUID,
    read_at           TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_notifications_reference ON notifications (reference_type, reference_id);

CREATE TABLE notification_deliveries (
    id                 UUID        PRIMARY KEY DEFAULT uuidv7(),
    notification_id    UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    channel            VARCHAR(20) NOT NULL,
    delivery_status    VARCHAR(20) NOT NULL,
    provider_reference VARCHAR(200),
    error_message      VARCHAR(1000),
    sent_at            TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_notification_deliveries_channel CHECK (channel IN ('EMAIL', 'IN_APP', 'SMS')),
    CONSTRAINT ck_notification_deliveries_status
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'BOUNCED'))
);

CREATE INDEX ix_notification_deliveries_notification ON notification_deliveries (notification_id);

-- Transactional outbox. Notifications are written here inside the business transaction and
-- dispatched afterwards, so a failed mail server cannot roll back an approval and a rolled
-- back approval cannot send an email.
CREATE TABLE notification_outbox (
    id                UUID        PRIMARY KEY DEFAULT uuidv7(),
    event_type        VARCHAR(60) NOT NULL,
    payload           JSONB       NOT NULL,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count       INTEGER     NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMPTZ,
    last_error        VARCHAR(1000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at      TIMESTAMPTZ,
    CONSTRAINT ck_notification_outbox_status
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED', 'DEAD')),
    CONSTRAINT ck_notification_outbox_retry CHECK (retry_count >= 0)
);

-- The dispatcher polls due work; this index is what keeps that poll an index scan.
CREATE INDEX ix_notification_outbox_due
    ON notification_outbox (next_retry_at, created_at) WHERE processing_status IN ('PENDING', 'FAILED');

COMMENT ON TABLE notification_outbox IS
    'Transactional outbox: written in the same transaction as the business change, delivered asynchronously.';

CREATE TABLE email_provider_configs (
    id               UUID         PRIMARY KEY DEFAULT uuidv7(),
    provider_name    VARCHAR(100) NOT NULL,
    host             VARCHAR(255) NOT NULL,
    port             INTEGER      NOT NULL,
    username         VARCHAR(255),
    sender_email     VARCHAR(255) NOT NULL,
    -- A REFERENCE to a secret-manager entry. There is no secret column by design: a column
    -- would inevitably be populated, logged, exported or committed.
    secret_reference VARCHAR(300) NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    configuration    JSONB,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       VARCHAR(100),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       VARCHAR(100),
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_email_provider_configs_name UNIQUE (provider_name),
    CONSTRAINT ck_email_provider_configs_port CHECK (port BETWEEN 1 AND 65535),
    CONSTRAINT ck_email_provider_configs_sender
        CHECK (sender_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$')
);

-- At most one active provider: two would make the sender depend on row order.
CREATE UNIQUE INDEX uk_email_provider_configs_active ON email_provider_configs ((TRUE)) WHERE is_active;

COMMENT ON COLUMN email_provider_configs.secret_reference IS
    'Identifier of the credential in the bank-approved secret manager (for example vault:edam/smtp). '
    'The API never returns a credential and never accepts one in clear text.';
