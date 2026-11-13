-- =====================================================================================
-- EDAM — Module F (expense committees) and Module I (dynamic workflow engine) (V8)
--
-- The workflow engine is split exactly as the requirement demands:
--   CONFIGURATION: definitions -> versions -> steps -> step approvers / transitions / states
--   EXECUTION:     instances -> tasks -> actions -> events
--
-- The governing rule is enforced structurally: a workflow_instance stores BOTH the
-- definition and the VERSION it started under, and nothing in the engine ever re-reads "the
-- latest version" for a running instance. A new version therefore cannot alter an in-flight
-- request, which is the requirement's explicit workflow rule.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Module F — expense committees
-- -------------------------------------------------------------------------------------

CREATE TABLE expense_committees (
    id             UUID         PRIMARY KEY DEFAULT uuidv7(),
    committee_code VARCHAR(64)  NOT NULL,
    committee_name VARCHAR(200) NOT NULL,
    description    TEXT,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_expense_committees_code UNIQUE (committee_code)
);

CREATE TABLE committee_levels (
    id                 UUID          PRIMARY KEY DEFAULT uuidv7(),
    committee_id       UUID          NOT NULL REFERENCES expense_committees (id) ON DELETE CASCADE,
    level_number       SMALLINT      NOT NULL,
    level_name         VARCHAR(200)  NOT NULL,
    minimum_amount     NUMERIC(19,4) NOT NULL DEFAULT 0,
    maximum_amount     NUMERIC(19,4),
    currency_id        UUID          NOT NULL REFERENCES currencies (id),
    minimum_approvals  SMALLINT      NOT NULL,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         VARCHAR(100),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         VARCHAR(100),
    version            BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_committee_levels_number UNIQUE (committee_id, level_number),
    CONSTRAINT ck_committee_levels_minimum CHECK (minimum_amount >= 0),
    CONSTRAINT ck_committee_levels_band CHECK (maximum_amount IS NULL OR maximum_amount > minimum_amount),
    CONSTRAINT ck_committee_levels_approvals CHECK (minimum_approvals >= 1)
);

-- Two active levels of the same committee must not cover the same amount.
ALTER TABLE committee_levels
    ADD CONSTRAINT ex_committee_levels_no_overlap
    EXCLUDE USING gist (
        committee_id WITH =,
        currency_id WITH =,
        numrange(minimum_amount, COALESCE(maximum_amount, 'infinity'::numeric), '[)') WITH &&
    ) WHERE (is_active);

CREATE TABLE committee_members (
    id             UUID        PRIMARY KEY DEFAULT uuidv7(),
    committee_id   UUID        NOT NULL REFERENCES expense_committees (id) ON DELETE CASCADE,
    user_id        UUID        NOT NULL REFERENCES users (id),
    member_type    VARCHAR(40) NOT NULL DEFAULT 'MEMBER',
    effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to   TIMESTAMPTZ,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(100),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(100),
    version        BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_committee_members_type
        CHECK (member_type IN ('CEO', 'CFO', 'HEAD_OF_ADMIN_GSD', 'MEMBER')),
    CONSTRAINT ck_committee_members_period CHECK (effective_to IS NULL OR effective_to > effective_from)
);

CREATE UNIQUE INDEX uk_committee_members_active ON committee_members (committee_id, user_id) WHERE is_active;

-- "Only one active CEO/CFO/Head of Admin & GSD" per committee. Three partial unique indexes
-- rather than a service check, because a read-then-write check races.
CREATE UNIQUE INDEX uk_committee_members_single_ceo
    ON committee_members (committee_id) WHERE is_active AND member_type = 'CEO';
CREATE UNIQUE INDEX uk_committee_members_single_cfo
    ON committee_members (committee_id) WHERE is_active AND member_type = 'CFO';
CREATE UNIQUE INDEX uk_committee_members_single_hod
    ON committee_members (committee_id) WHERE is_active AND member_type = 'HEAD_OF_ADMIN_GSD';

CREATE TABLE committee_level_members (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    committee_level_id  UUID        NOT NULL REFERENCES committee_levels (id) ON DELETE CASCADE,
    committee_member_id UUID        NOT NULL REFERENCES committee_members (id) ON DELETE CASCADE,
    is_mandatory        BOOLEAN     NOT NULL DEFAULT FALSE,
    approval_order      SMALLINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_committee_level_members UNIQUE (committee_level_id, committee_member_id),
    CONSTRAINT ck_committee_level_members_order CHECK (approval_order IS NULL OR approval_order >= 1)
);

CREATE INDEX ix_committee_level_members_member ON committee_level_members (committee_member_id);

-- -------------------------------------------------------------------------------------
-- Module I — workflow CONFIGURATION
-- -------------------------------------------------------------------------------------

CREATE TABLE workflow_definitions (
    id              UUID         PRIMARY KEY DEFAULT uuidv7(),
    workflow_code   VARCHAR(64)  NOT NULL,
    workflow_name   VARCHAR(200) NOT NULL,
    description     TEXT,
    request_type_id UUID         REFERENCES expense_request_types (id),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_definitions_code UNIQUE (workflow_code)
);

CREATE TABLE workflow_versions (
    id                     UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_definition_id UUID        NOT NULL REFERENCES workflow_definitions (id),
    version_number         INTEGER     NOT NULL,
    version_status         VARCHAR(20) NOT NULL,
    effective_from         TIMESTAMPTZ,
    effective_to           TIMESTAMPTZ,
    published_by           UUID        REFERENCES users (id),
    published_at           TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(100),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             VARCHAR(100),
    version                BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_versions_number UNIQUE (workflow_definition_id, version_number),
    CONSTRAINT ck_workflow_versions_status CHECK (version_status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_workflow_versions_number CHECK (version_number >= 1),
    CONSTRAINT ck_workflow_versions_period
        CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from),
    -- A version may only be PUBLISHED if someone published it and said when.
    CONSTRAINT ck_workflow_versions_published
        CHECK (version_status <> 'PUBLISHED' OR (published_at IS NOT NULL AND published_by IS NOT NULL))
);

-- At most one published version per definition: "the current workflow" must have exactly
-- one meaning.
CREATE UNIQUE INDEX uk_workflow_versions_single_published
    ON workflow_versions (workflow_definition_id) WHERE version_status = 'PUBLISHED';

CREATE TABLE workflow_states (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_version_id UUID        NOT NULL REFERENCES workflow_versions (id) ON DELETE CASCADE,
    state_code          VARCHAR(64) NOT NULL,
    state_name          VARCHAR(150) NOT NULL,
    state_type          VARCHAR(20) NOT NULL,
    is_initial          BOOLEAN     NOT NULL DEFAULT FALSE,
    is_terminal         BOOLEAN     NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_workflow_states_code UNIQUE (workflow_version_id, state_code),
    CONSTRAINT ck_workflow_states_type
        CHECK (state_type IN ('INITIAL', 'IN_PROGRESS', 'APPROVED', 'REJECTED', 'CANCELLED', 'REVERSED')),
    CONSTRAINT ck_workflow_states_terminal CHECK (NOT (is_initial AND is_terminal))
);

-- Exactly one initial state per version. Two initial states would make a request's starting
-- point depend on row order.
CREATE UNIQUE INDEX uk_workflow_states_single_initial
    ON workflow_states (workflow_version_id) WHERE is_initial;

CREATE TABLE workflow_steps (
    id                 UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_version_id UUID       NOT NULL REFERENCES workflow_versions (id) ON DELETE CASCADE,
    step_code          VARCHAR(64) NOT NULL,
    step_name          VARCHAR(150) NOT NULL,
    step_order         SMALLINT    NOT NULL,
    approval_mode      VARCHAR(20) NOT NULL DEFAULT 'SEQUENTIAL',
    minimum_approvals  SMALLINT    NOT NULL DEFAULT 1,
    is_mandatory       BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_workflow_steps_code UNIQUE (workflow_version_id, step_code),
    CONSTRAINT uk_workflow_steps_order UNIQUE (workflow_version_id, step_order),
    CONSTRAINT ck_workflow_steps_order CHECK (step_order >= 1),
    CONSTRAINT ck_workflow_steps_mode CHECK (approval_mode IN ('SEQUENTIAL', 'PARALLEL')),
    CONSTRAINT ck_workflow_steps_approvals CHECK (minimum_approvals >= 1)
);

CREATE TABLE workflow_step_approvers (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_step_id    UUID        NOT NULL REFERENCES workflow_steps (id) ON DELETE CASCADE,
    approver_type       VARCHAR(40) NOT NULL,
    role_id             UUID        REFERENCES roles (id),
    user_id             UUID        REFERENCES users (id),
    department_id       UUID        REFERENCES departments (id),
    committee_level_id  UUID        REFERENCES committee_levels (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workflow_step_approvers_type CHECK (approver_type IN (
        'ROLE', 'USER', 'DEPARTMENT_HEAD', 'RECOMMENDING_AUTHORITY', 'EXPENSE_COMMITTEE', 'CEO', 'CFO',
        'HEAD_OF_ADMIN_GSD', 'MANAGER')),
    -- Each approver type resolves its target from exactly one reference column. Enforcing the
    -- correspondence in the database prevents a ROLE step that names only a user (which would
    -- resolve to nobody) from ever being published.
    CONSTRAINT ck_workflow_step_approvers_reference CHECK (
        (approver_type = 'ROLE' AND role_id IS NOT NULL AND user_id IS NULL AND committee_level_id IS NULL)
        OR (approver_type = 'USER' AND user_id IS NOT NULL AND role_id IS NULL)
        OR (approver_type = 'EXPENSE_COMMITTEE' AND committee_level_id IS NOT NULL)
        OR (approver_type IN ('DEPARTMENT_HEAD', 'RECOMMENDING_AUTHORITY', 'CEO', 'CFO', 'HEAD_OF_ADMIN_GSD',
            'MANAGER') AND role_id IS NULL AND user_id IS NULL AND committee_level_id IS NULL))
);

CREATE INDEX ix_workflow_step_approvers_step ON workflow_step_approvers (workflow_step_id);

-- Transitions describe how an action moves the instance between states. Keeping them as data
-- means a bank can add a state (for example REVERSED) without a code deployment, while the
-- ENGINE still decides which actions are legal for which role in Java.
CREATE TABLE workflow_transitions (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_version_id UUID        NOT NULL REFERENCES workflow_versions (id) ON DELETE CASCADE,
    from_state_id       UUID        NOT NULL REFERENCES workflow_states (id) ON DELETE CASCADE,
    to_state_id         UUID        REFERENCES workflow_states (id) ON DELETE CASCADE,
    action_code         VARCHAR(40) NOT NULL,
    condition_rule_id   UUID,
    CONSTRAINT uk_workflow_transitions UNIQUE (workflow_version_id, from_state_id, action_code),
    CONSTRAINT ck_workflow_transitions_action CHECK (action_code IN (
        'SUBMIT', 'APPROVE', 'REJECT', 'REVERT', 'FORWARD', 'CANCEL', 'REVERSE', 'ESCALATE'))
);

CREATE INDEX ix_workflow_transitions_from ON workflow_transitions (from_state_id);

-- -------------------------------------------------------------------------------------
-- Module I — workflow EXECUTION
-- -------------------------------------------------------------------------------------

CREATE TABLE workflow_instances (
    id                     UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_definition_id UUID        NOT NULL REFERENCES workflow_definitions (id),
    -- Pinned at start. The application never reads "latest version" for a running instance.
    workflow_version_id    UUID        NOT NULL REFERENCES workflow_versions (id),
    expense_request_id     UUID        NOT NULL,
    current_state_id       UUID        REFERENCES workflow_states (id),
    started_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_by             UUID        REFERENCES users (id),
    completed_at           TIMESTAMPTZ
);

-- One workflow instance per request: a request cannot be in two approval chains at once.
CREATE UNIQUE INDEX uk_workflow_instances_request ON workflow_instances (expense_request_id);
CREATE INDEX ix_workflow_instances_version ON workflow_instances (workflow_version_id);

COMMENT ON COLUMN workflow_instances.workflow_version_id IS
    'Pinned when the instance starts. Publishing a new version must never change an in-flight request.';

CREATE TABLE workflow_tasks (
    id                 UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_instance_id UUID      NOT NULL REFERENCES workflow_instances (id) ON DELETE CASCADE,
    workflow_step_id   UUID        NOT NULL REFERENCES workflow_steps (id),
    assigned_user_id   UUID        REFERENCES users (id),
    assigned_role_id   UUID        REFERENCES roles (id),
    task_status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    assigned_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at             TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    CONSTRAINT ck_workflow_tasks_status CHECK (task_status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'REVERTED', 'FORWARDED', 'CANCELLED', 'SKIPPED', 'ESCALATED')),
    -- A task must be addressed to someone: a user or a role. An unaddressed task can never be
    -- actioned and would silently stall a request.
    CONSTRAINT ck_workflow_tasks_addressed CHECK (assigned_user_id IS NOT NULL OR assigned_role_id IS NOT NULL)
);

CREATE INDEX ix_workflow_tasks_instance ON workflow_tasks (workflow_instance_id, task_status);
CREATE INDEX ix_workflow_tasks_user_inbox ON workflow_tasks (assigned_user_id, task_status, assigned_at DESC);
CREATE INDEX ix_workflow_tasks_role_inbox ON workflow_tasks (assigned_role_id, task_status, assigned_at DESC);

-- One open task per (instance, step, assignee): a retried request cannot create a duplicate
-- assignment that would let one person approve twice.
CREATE UNIQUE INDEX uk_workflow_tasks_open_assignment
    ON workflow_tasks (workflow_instance_id, workflow_step_id, assigned_user_id) NULLS NOT DISTINCT
    WHERE task_status = 'PENDING';

CREATE TABLE workflow_actions (
    id                  UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_task_id    UUID        NOT NULL REFERENCES workflow_tasks (id) ON DELETE CASCADE,
    action_code         VARCHAR(40) NOT NULL,
    performed_by        UUID        NOT NULL REFERENCES users (id),
    remarks             VARCHAR(2000),
    previous_state_id   UUID        REFERENCES workflow_states (id),
    next_state_id       UUID        REFERENCES workflow_states (id),
    action_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id      VARCHAR(64),
    CONSTRAINT ck_workflow_actions_code CHECK (action_code IN (
        'APPROVE', 'REJECT', 'REVERT', 'FORWARD', 'ESCALATE'))
);

CREATE INDEX ix_workflow_actions_task ON workflow_actions (workflow_task_id, action_at);

COMMENT ON TABLE workflow_actions IS
    'Append-only decision record: who acted, on which task, with what remarks, moving which state to which.';

CREATE TABLE workflow_events (
    id                   UUID        PRIMARY KEY DEFAULT uuidv7(),
    workflow_instance_id UUID        NOT NULL REFERENCES workflow_instances (id) ON DELETE CASCADE,
    event_type           VARCHAR(60) NOT NULL,
    event_data           JSONB,
    performed_by         UUID        REFERENCES users (id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_workflow_events_instance ON workflow_events (workflow_instance_id, created_at);
