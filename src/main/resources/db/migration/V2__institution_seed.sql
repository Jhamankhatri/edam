-- =====================================================================================
-- EDAM Foundation — reference institution seed (V2)
--
-- Seeds ONE demonstration institution so that a fresh database can render the UI and
-- resolve a tenant. This is reference data, not a client's production configuration:
-- real institutions are created through the administration capability in a later phase.
--
-- The values mirror the local profile defaults so behaviour is identical whether the row
-- exists or the configuration fallback is used.
-- =====================================================================================

INSERT INTO institution (
    code, name, short_name, base_currency,
    primary_color, secondary_color, accent_color,
    language_tag, time_zone,
    department_head_limit, capex_department_restriction_enabled, active,
    created_at, created_by, updated_at, updated_by, version
) VALUES (
    'demo-bank',
    'Demo Bank Limited',
    'DemoBank',
    'PKR',
    '#0f5132',
    '#14532d',
    '#d1a055',
    'en-PK',
    'Asia/Karachi',
    20000.00,
    TRUE,
    TRUE,
    CURRENT_TIMESTAMP,
    'system',
    CURRENT_TIMESTAMP,
    'system',
    0
);
