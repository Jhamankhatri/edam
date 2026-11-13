-- =====================================================================================
-- EDAM — product catalogue: permissions and system roles (V12)
--
-- This is the one category of "seed" the requirement explicitly permits:
--
--   "Some foundational technical records are different: the permission catalogue, supported
--    internal action codes and required system metadata are part of the product itself. They
--    should be installed through controlled versioned migrations, not treated as mock
--    business data."
--
-- What is installed here:
--   * the permission catalogue (a closed product vocabulary that the Java code enforces),
--   * the system role definitions whose codes the bootstrap and tests depend on.
--
-- What is NOT installed here, deliberately:
--   * any user, password, administrator account,
--   * any department, branch, category, currency, vendor, committee, workflow or limit,
--   * any organization profile.
--
-- Those are the bank's data and are created through the application (or the bootstrap
-- command for the first administrator). A demo category or a placeholder exchange rate in a
-- production migration would be indistinguishable from a real one after installation.
-- =====================================================================================

INSERT INTO permissions (code, module, description) VALUES
    -- Administration and configuration
    ('organization:view', 'organization', 'View organization structure, departments and branches'),
    ('organization:manage', 'organization', 'Create and maintain the organization structure'),
    ('configuration:view', 'configuration', 'View application settings, themes and feature flags'),
    ('configuration:manage', 'configuration', 'Maintain application settings, themes and feature flags'),
    ('system:administer', 'system', 'Platform-level administration of this installation'),
    -- Access management
    ('user:view', 'user', 'View users'),
    ('user:manage', 'user', 'Create, update, deactivate and reset users'),
    ('role:view', 'role', 'View roles and permission assignments'),
    ('role:manage', 'role', 'Create and maintain roles and their permissions'),
    ('user-role:assign', 'user-role', 'Assign and revoke roles for users'),
    ('data-scope:manage', 'data-scope', 'Maintain the organizational data scopes of users'),
    -- Financial master data
    ('currency:view', 'currency', 'View currencies and effective-dated exchange rates'),
    ('currency:manage', 'currency', 'Maintain currencies and effective-dated exchange rates'),
    ('vendor:view', 'vendor', 'View vendors'),
    ('vendor:manage', 'vendor', 'Create and maintain vendors'),
    ('tax-authority:manage', 'tax-authority', 'Maintain tax authorities'),
    -- Expense configuration and delegation
    ('expense-configuration:view', 'expense-configuration', 'View expense categories, subcategories, natures and types'),
    ('expense-configuration:manage', 'expense-configuration', 'Maintain expense categories, subcategories, natures and types'),
    ('delegation:view', 'delegation', 'View the delegation matrix'),
    ('delegation:manage', 'delegation', 'Maintain delegation rules and approver steps'),
    ('approval-limit:view', 'approval-limit', 'View department approval limits'),
    ('approval-limit:manage', 'approval-limit', 'Maintain department approval limits'),
    ('recommending-authority:view', 'recommending-authority', 'View recommending authorities'),
    ('recommending-authority:manage', 'recommending-authority', 'Maintain recommending authorities'),
    ('exceeding-limit:view', 'exceeding-limit', 'View exceeding-limit rules'),
    ('exceeding-limit:manage', 'exceeding-limit', 'Maintain exceeding-limit rules'),
    -- Committees
    ('committee:view', 'committee', 'View expense committees, levels and members'),
    ('committee:manage', 'committee', 'Maintain expense committees, levels and members'),
    -- Requests
    ('request:create', 'request', 'Create an expense request'),
    ('request:view-own', 'request', 'View own expense requests'),
    ('request:view-scope', 'request', 'View expense requests within the caller''s data scope'),
    ('request:view-all', 'request', 'View all expense requests in the organization'),
    ('request:edit', 'request', 'Edit a draft or returned expense request'),
    ('request:submit', 'request', 'Submit an expense request into its workflow'),
    ('request:cancel', 'request', 'Cancel a draft or in-flight expense request'),
    ('request:reverse', 'request', 'Reverse an approved expense request'),
    ('request:export', 'request', 'Export expense request data'),
    ('document:upload', 'document', 'Upload documents to a request'),
    ('document:download', 'document', 'Download request documents'),
    ('document:delete', 'document', 'Delete a request document'),
    -- Approvals
    ('approval:view', 'approval', 'View the approval inbox'),
    ('approval:act', 'approval', 'Approve, reject, revert or forward an assigned task'),
    -- Workflow and rules
    ('workflow:view', 'workflow', 'View workflow definitions and versions'),
    ('workflow:manage', 'workflow', 'Create and edit workflow definitions and draft versions'),
    ('workflow:publish', 'workflow', 'Publish or roll back workflow versions'),
    ('rule:view', 'rule', 'View business rule sets'),
    ('rule:manage', 'rule', 'Maintain business rule sets and draft versions'),
    ('rule:publish', 'rule', 'Publish business rule versions'),
    -- Audit
    ('audit:view', 'audit', 'Search and view audit events'),
    ('audit:export', 'audit', 'Export audit data'),
    -- Notifications and email
    ('notification:view', 'notification', 'View own notifications'),
    ('email-configuration:manage', 'email-configuration', 'Maintain email provider configuration references'),
    ('notification-template:manage', 'notification-template', 'Maintain notification templates'),
    -- Reporting and dashboards
    ('report:view', 'report', 'View reports and dashboards'),
    ('report:export', 'report', 'Export report data'),
    ('dashboard:manage', 'dashboard', 'Maintain dashboard definitions and widgets'),
    -- Integrations
    ('integration:view', 'integration', 'View integration configurations and sync runs'),
    ('integration:manage', 'integration', 'Maintain integration configurations and run synchronisations');

-- -------------------------------------------------------------------------------------
-- System roles
--
-- These are capability BUNDLES, not scopes. A role marked is_system_role is protected from
-- deletion, but a bank may still rename it and change its permissions. The names match the
-- administrator personas in the requirement (System Administrator, User Administrator, Role
-- Administrator, Workflow Administrator, Expense Configuration Administrator, Report
-- Administrator) plus the operational roles the approval engine needs.
--
-- No role is granted 'ALL' data scope by itself: scope is assigned per user. That is what the
-- requirement means by "a role is not automatically a global administrator".
-- -------------------------------------------------------------------------------------

INSERT INTO roles (name, code, description, is_system_role) VALUES
    ('System Administrator', 'SYSTEM_ADMINISTRATOR',
     'Platform administration of this installation. Intended for a very small number of users.', TRUE),
    ('User Administrator', 'USER_ADMINISTRATOR', 'User lifecycle: create, update, deactivate, reset', TRUE),
    ('Role Administrator', 'ROLE_ADMINISTRATOR', 'Roles, permissions and role assignment', TRUE),
    ('Workflow Administrator', 'WORKFLOW_ADMINISTRATOR', 'Workflow definitions, versions, publish and rollback', TRUE),
    ('Expense Configuration Administrator', 'EXPENSE_CONFIGURATION_ADMINISTRATOR',
     'Categories, natures, delegation matrix, limits, recommending authorities and committees', TRUE),
    ('Report Administrator', 'REPORT_ADMINISTRATOR', 'Reports, dashboards and exports', TRUE),
    ('Audit Administrator', 'AUDIT_ADMINISTRATOR', 'Audit search and export', TRUE),
    ('Requester', 'REQUESTER', 'Create and track own expense requests', TRUE),
    ('Department Head', 'DEPARTMENT_HEAD', 'Act on department-level approvals', TRUE),
    ('Recommending Authority', 'RECOMMENDING_AUTHORITY', 'Recommend requests in the configured scope', TRUE),
    ('Expense Committee Member', 'EXPENSE_COMMITTEE_MEMBER', 'Act on assigned committee approvals', TRUE),
    ('Chief Executive Officer', 'CEO', 'Required office for configured committee levels', TRUE),
    ('Chief Financial Officer', 'CFO', 'Required office for configured committee levels', TRUE),
    ('Read Only', 'READ_ONLY', 'Read own records only', TRUE);

-- Role -> permission grants.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'organization:view', 'organization:manage', 'configuration:view', 'configuration:manage',
    'system:administer', 'user:view', 'user:manage', 'role:view', 'role:manage', 'user-role:assign',
    'data-scope:manage', 'currency:view', 'currency:manage', 'vendor:view', 'vendor:manage',
    'tax-authority:manage', 'expense-configuration:view', 'expense-configuration:manage', 'delegation:view',
    'delegation:manage', 'approval-limit:view', 'approval-limit:manage', 'recommending-authority:view',
    'recommending-authority:manage', 'exceeding-limit:view', 'exceeding-limit:manage', 'committee:view',
    'committee:manage', 'request:view-all', 'request:export', 'document:download', 'approval:view',
    'workflow:view', 'workflow:manage', 'workflow:publish', 'rule:view', 'rule:manage', 'rule:publish',
    'audit:view', 'audit:export', 'notification:view', 'email-configuration:manage',
    'notification-template:manage', 'report:view', 'report:export', 'dashboard:manage', 'integration:view',
    'integration:manage')
WHERE r.code = 'SYSTEM_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'user:view', 'user:manage', 'role:view', 'user-role:assign', 'data-scope:manage')
WHERE r.code = 'USER_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'role:view', 'role:manage', 'user-role:assign', 'data-scope:manage', 'user:view')
WHERE r.code = 'ROLE_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'workflow:view', 'workflow:manage', 'workflow:publish', 'rule:view', 'rule:manage', 'rule:publish',
    'committee:view', 'delegation:view')
WHERE r.code = 'WORKFLOW_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'expense-configuration:view', 'expense-configuration:manage', 'delegation:view', 'delegation:manage',
    'approval-limit:view', 'approval-limit:manage', 'recommending-authority:view',
    'recommending-authority:manage', 'exceeding-limit:view', 'exceeding-limit:manage', 'committee:view',
    'committee:manage', 'currency:view', 'tax-authority:manage', 'vendor:view', 'vendor:manage')
WHERE r.code = 'EXPENSE_CONFIGURATION_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'report:view', 'report:export', 'dashboard:manage', 'request:view-all', 'document:download')
WHERE r.code = 'REPORT_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('audit:view', 'audit:export')
WHERE r.code = 'AUDIT_ADMINISTRATOR';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'request:create', 'request:view-own', 'request:edit', 'request:submit', 'request:cancel',
    'document:upload', 'document:download', 'notification:view')
WHERE r.code = 'REQUESTER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'approval:view', 'approval:act', 'request:view-scope', 'document:download', 'notification:view')
WHERE r.code IN ('DEPARTMENT_HEAD', 'RECOMMENDING_AUTHORITY', 'EXPENSE_COMMITTEE_MEMBER');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'approval:view', 'approval:act', 'request:view-all', 'document:download', 'notification:view')
WHERE r.code IN ('CEO', 'CFO');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('request:view-own', 'notification:view')
WHERE r.code = 'READ_ONLY';

-- -------------------------------------------------------------------------------------
-- Foundation feature flags
--
-- These are switches the CODE reads (FeatureKey in Java). Their existence is part of the
-- product; their VALUE is the bank's decision, so every one starts disabled except where
-- disabling it would itself be unsafe.
-- -------------------------------------------------------------------------------------

INSERT INTO feature_flags (feature_key, display_name, description, is_enabled) VALUES
    ('EMAIL_NOTIFICATIONS', 'Email notifications',
     'Send workflow notifications by email through the configured provider.', FALSE),
    ('REQUIRE_SUPPORTING_DOCUMENTS', 'Supporting documents required',
     'Require at least one supporting document before a request can be submitted.', FALSE),
    ('REQUESTER_CANCELLATION', 'Requester cancellation',
     'Allow a requester to cancel an in-flight request before the first approval is recorded.', FALSE),
    ('CAPEX_DEPARTMENT_RESTRICTION', 'CAPEX department restriction',
     'Only departments marked CAPEX-eligible may raise CAPEX requests.', FALSE),
    ('AUDIT_API_ACCESS', 'Audit API access', 'Expose audit search and export to audit administrators.', TRUE);

-- -------------------------------------------------------------------------------------
-- Organization unit types
--
-- The TYPE VOCABULARY is product metadata (a bank must be able to place its own structure
-- somewhere); the UNITS themselves are the bank's data and are not seeded.
-- -------------------------------------------------------------------------------------

INSERT INTO organization_unit_types (name, code, description) VALUES
    ('Head Office', 'HEAD_OFFICE', 'Top-level organizational unit'),
    ('Region', 'REGION', 'Geographic grouping between head office and branches'),
    ('Zone', 'ZONE', 'Intermediate grouping, where the bank uses one'),
    ('Branch', 'BRANCH', 'Customer-facing or operational branch'),
    ('Department', 'DEPARTMENT', 'Functional department, typically at head office'),
    ('Division', 'DIVISION', 'Sub-unit of a department');
