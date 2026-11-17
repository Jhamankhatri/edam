# 09 — Branding, Theme & Configuration APIs

## 9.1 Public configuration (pre-login)
`GET /api/v1/public/configuration` — public. Returns `PublicConfigurationResponse`, a whitelist-only subset containing:
- `applicationName: string`
- `tagline?: string`
- `logoAssetId?: UUID`
- `primaryColor: string` (hex)
- `accentColor?: string`
- `fontFamily?: string`
- `locale: string` (default `en-PK`)
- `loginBackground?: { assetId?: UUID, overlayOpacity?: number }`
No secrets, SMTP hosts, internal settings, user counts or installation state beyond branding are exposed.

## 9.2 Organization profile (branded entity)
`GET/PATCH/POST /api/v1/organization/profile` — permissions `organization:view` (GET) and `configuration:manage` (POST/PATCH). Carries institution name, tax id, address, contact, base currency (ISO-4217 code), locale, timezone.

## 9.3 Themes
Base path `/api/v1/themes` (note: the introspected controller map shows `/themes` under `/api/v1` — see source for exact subpath; controller prefix `/configuration`). Themes map to the `branding_themes` + `theme_tokens` tables.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/themes` | `configuration:view` | List themes with metadata. |
| GET | `/themes/active` | `configuration:view` | Return the currently active theme (full token set) — called after login to apply CSS variables. |
| GET | `/themes/{id}` | `configuration:view` | Get one theme. |
| POST | `/themes` | `configuration:manage` | Create a new theme. |
| PUT | `/themes/{id}` | `configuration:manage` | Update a theme's tokens. |
| POST | `/themes/{id}/activation` | `configuration:manage` | Activate this theme (deactivates the previous one) for the whole institution. |
| POST | `/themes/{id}/default` | `configuration:manage` | Mark as the factory default (used after installation/reset). |

Theme tokens are an enumerated whitelist (colors, radii, font stack, logo asset id, favicon asset id). **Arbitrary CSS/JS is rejected server-side** and must also be dropped by the client for defence in depth.

Logo upload is done via `POST /documents` (or the media-asset flow in §10) with an appropriate document/asset type; the resulting asset id is stored on the theme.

## 9.4 Application settings
Base path `/api/v1/settings` under the configuration controller.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/settings` | `configuration:view` | All settings (grouped). |
| GET | `/settings/categories/{category}` | `configuration:view` | Settings for one category. |
| GET | `/settings/{key}` | `configuration:view` | One setting by key. |
| POST | `/settings` | `configuration:manage` | Create/update a setting (`{key, category, value, type, description?}`). |
| PATCH | `/settings/bulk` | `configuration:manage` | Update many keys at once. |

Setting values are typed (string/number/boolean/JSON) but transported as primitives. The frontend must trust the type returned.

## 9.5 Feature flags
Base path `/api/v1/feature-flags`.

| Method | Path | Permission |
|---|---|---|
| GET | `/feature-flags` | `configuration:view` |
| GET | `/feature-flags/{key}` | `configuration:view` |
| POST | `/feature-flags` | `configuration:manage` |
| POST | `/feature-flags/{key}/activation` | `configuration:manage` (toggle enabled) |

Feature flags gate unfinished frontend features (e.g. CAPEX finance fields). Check the flag before rendering gated UI; even when rendered, the backend enforces rules.

## 9.6 Dark mode
Dark mode is a **client preference** in Phase 4J (no separate per-user server setting). Tokens exist for both palettes; the frontend applies `[data-theme="dark"]` at the `:root` based on `prefers-color-scheme` or a user toggle stored in a non-identifying local flag. The active theme's tokens should be luminosity-shifted on the client if no explicit dark palette is returned. If/when a per-user dark palette is added to the backend, this document will be updated.
