-- ============================================================================
-- Neutral platform capability permission codes (no client-specific roles/users).
-- Institution-specific roles are configured per institution during provisioning.
-- ============================================================================

INSERT INTO permissions (code, module_code, description)
VALUES
    ('PLATFORM_HEALTH_VIEW', 'PLATFORM', 'View detailed platform operational health status'),
    ('INSTITUTION_CONFIG_VIEW', 'INSTITUTION', 'View active institution branding and configuration'),
    ('INSTITUTION_CONFIG_MANAGE', 'INSTITUTION', 'Manage institution branding, theme, and configurable settings'),
    ('IDENTITY_USER_VIEW', 'IDENTITY', 'View user accounts and institution memberships'),
    ('IDENTITY_USER_MANAGE', 'IDENTITY', 'Manage institution user memberships and role assignments'),
    ('AUDIT_LOG_VIEW', 'AUDIT', 'View platform and configuration audit event logs')
ON CONFLICT (code) DO NOTHING;
