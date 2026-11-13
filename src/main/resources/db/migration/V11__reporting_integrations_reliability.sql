-- =====================================================================================
-- EDAM — Module M (reporting and dashboards), Module N (external integrations) and
-- Module O (technical reliability) (V11)
--
-- Reporting configuration is DATA; report CALCULATIONS are Java. A report_definition says
-- which curated query to run, which filters it accepts and which permission it needs — it
-- never contains SQL. The requirement is explicit that financial report calculations must
-- use validated backend queries, not arbitrary SQL entered by administrators, so there is
-- deliberately no sql column in this schema.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Module M — reporting and dashboards
-- -------------------------------------------------------------------------------------

CREATE TABLE report_definitions (
    id                     UUID         PRIMARY KEY DEFAULT uuidv7(),
    report_code            VARCHAR(64)  NOT NULL,
    report_name            VARCHAR(200) NOT NULL,
    description            TEXT,
    category               VARCHAR(60)  NOT NULL DEFAULT 'GENERAL',
    configuration          JSONB,
    required_permission_id UUID         REFERENCES permissions (id),
    is_active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(100),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(100),
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_report_definitions_code UNIQUE (report_code)
);

COMMENT ON TABLE report_definitions IS
    'Selects a curated backend report query and its allowed filters. Contains no SQL: a report definition '
    'cannot become a database console.';

CREATE TABLE dashboard_definitions (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    dashboard_code VARCHAR(64)  NOT NULL,
    dashboard_name VARCHAR(200) NOT NULL,
    description    TEXT,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_dashboard_definitions_code UNIQUE (dashboard_code)
);

CREATE UNIQUE INDEX uk_dashboard_definitions_default
    ON dashboard_definitions ((TRUE)) WHERE is_default AND is_active;

CREATE TABLE dashboard_widgets (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    dashboard_id  UUID         NOT NULL REFERENCES dashboard_definitions (id) ON DELETE CASCADE,
    widget_code   VARCHAR(64)  NOT NULL,
    widget_type   VARCHAR(40)  NOT NULL,
    configuration JSONB,
    display_order INTEGER      NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_dashboard_widgets_code UNIQUE (dashboard_id, widget_code),
    CONSTRAINT ck_dashboard_widgets_type CHECK (widget_type IN (
        'KPI_CARD', 'STATUS_BREAKDOWN', 'AMOUNT_BY_CATEGORY', 'TREND', 'RECENT_REQUESTS', 'CUSTOM')),
    CONSTRAINT ck_dashboard_widgets_order CHECK (display_order >= 0)
);

CREATE TABLE user_dashboard_preferences (
    id            UUID        PRIMARY KEY DEFAULT uuidv7(),
    user_id       UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    dashboard_id  UUID        NOT NULL REFERENCES dashboard_definitions (id) ON DELETE CASCADE,
    preferences   JSONB,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_dashboard_preferences UNIQUE (user_id, dashboard_id)
);

-- -------------------------------------------------------------------------------------
-- Module N — external integrations
-- -------------------------------------------------------------------------------------

CREATE TABLE integration_configs (
    id                  UUID         PRIMARY KEY DEFAULT uuidv7(),
    integration_code    VARCHAR(64)  NOT NULL,
    integration_name    VARCHAR(200) NOT NULL,
    integration_type    VARCHAR(40)  NOT NULL,
    endpoint_url        VARCHAR(500),
    authentication_type VARCHAR(40)  NOT NULL DEFAULT 'NONE',
    secret_reference    VARCHAR(300),
    configuration       JSONB,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_integration_configs_code UNIQUE (integration_code),
    CONSTRAINT ck_integration_configs_type CHECK (integration_type IN (
        'HR', 'LDAP', 'ACTIVE_DIRECTORY', 'ERP', 'CORE_BANKING', 'STORAGE', 'EMAIL', 'OTHER')),
    CONSTRAINT ck_integration_configs_auth CHECK (authentication_type IN (
        'NONE', 'BASIC', 'BEARER', 'OAUTH2', 'CLIENT_CERTIFICATE', 'LDAP_BIND')),
    -- Any authentication method other than NONE requires a credential reference; allowing the
    -- row without one produces an integration that fails at the first call, in production.
    CONSTRAINT ck_integration_configs_secret
        CHECK (authentication_type = 'NONE' OR secret_reference IS NOT NULL)
);

COMMENT ON COLUMN integration_configs.secret_reference IS
    'Secret-manager reference only. Storing a client secret here would put a credential in every backup.';

CREATE TABLE integration_sync_runs (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    integration_config_id UUID      NOT NULL REFERENCES integration_configs (id) ON DELETE CASCADE,
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at       TIMESTAMPTZ,
    execution_status   VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    records_processed  INTEGER      NOT NULL DEFAULT 0,
    records_failed     INTEGER      NOT NULL DEFAULT 0,
    error_summary      TEXT,
    triggered_by       UUID         REFERENCES users (id),
    CONSTRAINT ck_integration_sync_runs_status
        CHECK (execution_status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    CONSTRAINT ck_integration_sync_runs_counts CHECK (records_processed >= 0 AND records_failed >= 0)
);

CREATE INDEX ix_integration_sync_runs_config ON integration_sync_runs (integration_config_id, started_at DESC);

CREATE TABLE integration_sync_records (
    id                 UUID         PRIMARY KEY DEFAULT uuidv7(),
    sync_run_id        UUID         NOT NULL REFERENCES integration_sync_runs (id) ON DELETE CASCADE,
    external_reference VARCHAR(200) NOT NULL,
    local_reference    VARCHAR(200),
    sync_status        VARCHAR(20)  NOT NULL,
    error_message      VARCHAR(1000),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_integration_sync_records_status
        CHECK (sync_status IN ('CREATED', 'UPDATED', 'SKIPPED', 'FAILED'))
);

CREATE INDEX ix_integration_sync_records_run ON integration_sync_records (sync_run_id, sync_status);

-- -------------------------------------------------------------------------------------
-- Module O — technical reliability
-- -------------------------------------------------------------------------------------

-- Durable idempotency. `response_snapshot` is deliberately nullable and NOT used to replay a
-- stored body: a replay re-reads current state, because replaying a stale body after the
-- resource changed legitimately would be incorrect.
CREATE TABLE idempotency_records (
    id                UUID         PRIMARY KEY DEFAULT uuidv7(),
    idempotency_key   VARCHAR(200) NOT NULL,
    operation_type    VARCHAR(100) NOT NULL,
    request_hash      VARCHAR(64)  NOT NULL,
    response_snapshot JSONB,
    processing_status VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',
    response_status   INTEGER,
    expires_at        TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_idempotency_records_key UNIQUE (idempotency_key, operation_type),
    CONSTRAINT ck_idempotency_records_status
        CHECK (processing_status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX ix_idempotency_records_expiry ON idempotency_records (expires_at);

COMMENT ON TABLE idempotency_records IS
    'Durable (not cached): an evicted key would silently reopen the double-submit window on financial actions.';

CREATE TABLE scheduled_jobs (
    id              UUID        PRIMARY KEY DEFAULT uuidv7(),
    job_code        VARCHAR(64) NOT NULL,
    job_name        VARCHAR(200) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    configuration   JSONB,
    is_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMPTZ,
    next_run_at     TIMESTAMPTZ,
    last_status     VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_scheduled_jobs_code UNIQUE (job_code),
    -- Only the job catalogue is data. The JOB ITSELF is a Java bean named by job_code; a cron
    -- expression in a table cannot introduce executable behaviour on its own.
    CONSTRAINT ck_scheduled_jobs_status CHECK (last_status IS NULL OR last_status IN ('SUCCEEDED', 'FAILED', 'RUNNING'))
);

CREATE INDEX ix_scheduled_jobs_due ON scheduled_jobs (next_run_at) WHERE is_enabled;

CREATE TABLE system_error_events (
    id            UUID         PRIMARY KEY DEFAULT uuidv7(),
    error_code    VARCHAR(40),
    module        VARCHAR(100),
    correlation_id VARCHAR(64),
    error_summary VARCHAR(1000) NOT NULL,
    error_details JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_system_error_events_time ON system_error_events (created_at DESC);
CREATE INDEX ix_system_error_events_correlation ON system_error_events (correlation_id);

COMMENT ON TABLE system_error_events IS
    'Operational diagnostics. Never stores request bodies or credentials; error_details is redacted before insert.';
