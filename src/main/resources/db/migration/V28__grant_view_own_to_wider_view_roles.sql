-- =====================================================================================
-- EDAM — V28: grant request:view-own to roles that already hold wider request:view-*
--
-- WHY THIS MIGRATION EXISTS
--
-- The request list/detail endpoints are gated at the controller by @EdamPermission
-- ("request:view-own") and visibility is enforced in the service layer via isVisible()
-- (author match → department/branch scope → installation scope). Roles that hold
-- request:view-scope or request:view-all were seeded without request:view-own, so the
-- aspect denied them at the controller boundary before the service-level scope filter
-- could ever widen their view. Department heads, recommending authorities, expense
-- committee members, CEO/CFO/Head of Admin GSD, and the report administrator could
-- therefore not open the request register at all despite holding a wider visibility
-- grant.
--
-- SAFETY
--
-- Adding request:view-own does NOT broaden access:
--   * request:view-own alone is the narrowest grant (own requests + service scope filter).
--   * The service layer (ExpenseRequestService.isVisible) already enforces department /
--     branch / installation scopes regardless of which view-* code admitted the caller.
--   * The RequirePermissionAspect checks exact permission membership; it does not imply
--     any privilege hierarchy. We therefore explicitly grant the base code to every role
--     that already holds a wider one. The privilege "hierarchy" is expressed in the
--     service, not the aspect, and we are not changing that (see H04 in the handover).
--
-- IDEMPOTENCY
--
-- INSERT ... ON CONFLICT DO NOTHING means the migration is safe to re-run; a role that
-- already holds request:view-own (e.g. REQUESTER, SYSTEM_ADMINISTRATOR, READ_ONLY) keeps
-- its existing row.
-- =====================================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'request:view-own'
WHERE EXISTS (
    SELECT 1
    FROM role_permissions rp_wide
    JOIN permissions p_wide ON p_wide.id = rp_wide.permission_id
    WHERE rp_wide.role_id = r.id
      AND p_wide.code IN ('request:view-scope', 'request:view-all')
)
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- -------------------------------------------------------------------------------------
-- Self-verification
--
-- After this migration every role that holds request:view-scope or request:view-all must
-- also hold request:view-own. Failing fast here prevents a half-applied seed (e.g. a
-- custom-inserted role in a later migration that forgets the base code) from silently
-- regressing the register view.
-- -------------------------------------------------------------------------------------
DO $$
DECLARE
    missing integer;
BEGIN
    SELECT count(*) INTO missing
    FROM role_permissions rp_wide
    JOIN permissions p_wide ON p_wide.id = rp_wide.permission_id
    LEFT JOIN role_permissions rp_own
           ON rp_own.role_id = rp_wide.role_id
          AND rp_own.permission_id IN (SELECT id FROM permissions WHERE code = 'request:view-own')
    WHERE p_wide.code IN ('request:view-scope', 'request:view-all')
      AND rp_own.role_id IS NULL;

    IF missing <> 0 THEN
        RAISE EXCEPTION 'V28: % role(s) hold a widened request:view permission without request:view-own', missing;
    END IF;
END $$;
