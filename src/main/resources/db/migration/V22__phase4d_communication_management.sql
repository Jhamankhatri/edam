-- =====================================================================================
-- EDAM — Phase 4D: Communication management (SMTP, templates, event triggers, notifications).
--
-- One installation, one active SMTP config. Password is stored encrypted (AES/GCM keyed from
-- EDAM_MAIL_MASTER_KEY env var; key never lands in DB). Templates are versioned (snapshot
-- rows). Delivery uses a transactional outbox so a crash between commit and async handoff
-- never loses a notification.
--
-- Tables are prefixed with comm_ because V10 already introduced differently-shaped
-- notification/outbox tables for an earlier, audit-oriented notification model. This module
-- owns its own tables and does not share state with that earlier scaffolding.
-- =====================================================================================

-- (a) SMTP configuration --------------------------------------------------------------
CREATE TABLE comm_smtp_configuration (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    host                 VARCHAR(255) NOT NULL,
    port                 INTEGER      NOT NULL,
    username             VARCHAR(255),
    encrypted_password   TEXT,
    password_version     INTEGER      NOT NULL DEFAULT 0,
    security_mode        VARCHAR(16)  NOT NULL CHECK (security_mode IN ('NONE','STARTTLS','SSL_TLS')),
    from_email           VARCHAR(255) NOT NULL,
    from_display_name    VARCHAR(255),
    reply_to             VARCHAR(255),
    connection_timeout_ms INTEGER     NOT NULL DEFAULT 10000,
    read_timeout_ms      INTEGER      NOT NULL DEFAULT 30000,
    active               BOOLEAN      NOT NULL DEFAULT FALSE,
    last_test_at         TIMESTAMPTZ,
    last_test_success    BOOLEAN,
    last_test_message    TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_comm_smtp_port CHECK (port BETWEEN 1 AND 65535)
);

CREATE UNIQUE INDEX uq_comm_smtp_active_one ON comm_smtp_configuration ((TRUE)) WHERE active;

-- (b) Email templates (current) and versions -----------------------------------------
CREATE TABLE comm_email_templates (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    code                 VARCHAR(80)  NOT NULL UNIQUE,
    display_name         VARCHAR(200) NOT NULL,
    description          TEXT,
    event_code           VARCHAR(80)  NOT NULL,
    subject              VARCHAR(500) NOT NULL,
    html_body            TEXT         NOT NULL,
    text_body            TEXT,
    locale               VARCHAR(16)  NOT NULL DEFAULT 'en',
    active               BOOLEAN      NOT NULL DEFAULT FALSE,
    current_version      INTEGER      NOT NULL DEFAULT 1,
    created_by           UUID         REFERENCES users (id),
    updated_by           UUID         REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE comm_email_template_versions (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    template_id          UUID         NOT NULL REFERENCES comm_email_templates (id) ON DELETE CASCADE,
    version_number       INTEGER      NOT NULL,
    subject              VARCHAR(500) NOT NULL,
    html_body            TEXT         NOT NULL,
    text_body            TEXT,
    locale               VARCHAR(16)  NOT NULL,
    change_note          TEXT,
    created_by           UUID         REFERENCES users (id),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_comm_template_version UNIQUE (template_id, version_number)
);

-- (c) Event triggers (administrator-managed channel routing) --------------------------
CREATE TABLE comm_notification_event_configs (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    event_code           VARCHAR(80)  NOT NULL UNIQUE,
    email_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    in_app_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    email_template_id    UUID         REFERENCES comm_email_templates (id),
    recipient_groups     TEXT         NOT NULL DEFAULT '[]',
    active               BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_by           UUID         REFERENCES users (id),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version              BIGINT       NOT NULL DEFAULT 0
);

-- (d) In-app notifications (per-user inbox) ------------------------------------------
CREATE TABLE comm_notifications (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_code           VARCHAR(80)  NOT NULL,
    title                VARCHAR(500) NOT NULL,
    body                 TEXT         NOT NULL,
    payload_json         TEXT,
    is_read              BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_comm_notifications_user_created ON comm_notifications (user_id, created_at DESC);
CREATE INDEX ix_comm_notifications_unread      ON comm_notifications (user_id) WHERE NOT is_read;

-- (e) Notification preferences -------------------------------------------------------
CREATE TABLE comm_notification_preferences (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    user_id              UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    in_app_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    email_digest         VARCHAR(16)  NOT NULL DEFAULT 'IMMEDIATE',
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_comm_notification_prefs_user UNIQUE (user_id),
    CONSTRAINT ck_comm_notification_prefs_digest CHECK (email_digest IN ('IMMEDIATE','DAILY','DISABLED'))
);

CREATE TABLE comm_notification_preference_events (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    preference_id        UUID         NOT NULL REFERENCES comm_notification_preferences (id) ON DELETE CASCADE,
    event_code           VARCHAR(80)  NOT NULL,
    email_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    in_app_enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_comm_prefs_event UNIQUE (preference_id, event_code)
);

-- (f) Notification outbox / delivery log --------------------------------------------
CREATE TABLE comm_notification_outbox (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    idempotency_key      VARCHAR(120) NOT NULL UNIQUE,
    event_code           VARCHAR(80)  NOT NULL,
    channel              VARCHAR(16)  NOT NULL CHECK (channel IN ('EMAIL','IN_APP')),
    user_id              UUID         REFERENCES users (id) ON DELETE CASCADE,
    recipient_email      VARCHAR(255),
    template_version_id  UUID         REFERENCES comm_email_template_versions (id),
    subject              VARCHAR(500),
    body_html            TEXT,
    body_text            TEXT,
    payload_json         TEXT,
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts             INTEGER      NOT NULL DEFAULT 0,
    max_attempts         INTEGER      NOT NULL DEFAULT 5,
    next_attempt_at      TIMESTAMPTZ,
    last_attempt_at      TIMESTAMPTZ,
    last_error           TEXT,
    locked_by            VARCHAR(100),
    locked_at            TIMESTAMPTZ,
    sent_at              TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_comm_outbox_status CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','RETRY_PENDING'))
);

CREATE INDEX ix_comm_outbox_pending ON comm_notification_outbox (next_attempt_at)
    WHERE status IN ('PENDING','RETRY_PENDING');
CREATE INDEX ix_comm_outbox_user_created ON comm_notification_outbox (user_id, created_at DESC);

-- (g) Test email history (separate from outbox) --------------------------------------
CREATE TABLE comm_smtp_test_history (
    id                   UUID         PRIMARY KEY DEFAULT uuidv7(),
    smtp_config_id       UUID         NOT NULL REFERENCES comm_smtp_configuration (id) ON DELETE CASCADE,
    recipient            VARCHAR(255) NOT NULL,
    success              BOOLEAN      NOT NULL,
    diagnostic           TEXT,
    sent_at              TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Seed initial supported event triggers.
INSERT INTO comm_notification_event_configs (event_code, email_enabled, in_app_enabled, recipient_groups) VALUES
    ('EXPENSE_SUBMITTED',     TRUE, TRUE, '["REQUESTER"]'),
    ('APPROVAL_REQUIRED',     TRUE, TRUE, '["ASSIGNED_APPROVER"]'),
    ('EXPENSE_APPROVED',      TRUE, TRUE, '["REQUESTER"]'),
    ('EXPENSE_REJECTED',      TRUE, TRUE, '["REQUESTER"]'),
    ('EXPENSE_RETURNED',      TRUE, TRUE, '["REQUESTER"]'),
    ('EXPENSE_FORWARDED',     TRUE, TRUE, '["REQUESTER","WORKFLOW_PARTICIPANTS"]'),
    ('EXPENSE_FORWARDED_TO',  TRUE, TRUE, '["FORWARDED_RECIPIENT"]'),
    ('EXPENSE_CANCELLED',     TRUE, TRUE, '["WORKFLOW_PARTICIPANTS"]');
