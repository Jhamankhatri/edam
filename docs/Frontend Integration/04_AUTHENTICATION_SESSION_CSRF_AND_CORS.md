# 04 — Authentication, Session, CSRF & CORS

EDAM uses **server-side HTTP sessions with double-submit CSRF protection**. There is no JWT, no bearer token and no refresh-token endpoint. The browser's cookie jar is the sole credential store.

## 4.1 Overview

```
┌──────────┐  1 GET /api/v1/auth/csrf (public)     ┌─────────┐
│ React    │ ─────────────────────────────────────► │  EDAM   │  Set-Cookie: JSESSIONID=…; HttpOnly; SameSite=Lax
│  app     │ ◄───────────────────────────────────── │ Backend │  Set-Cookie: XSRF-TOKEN=<token>; Path=/
│          │                                       └─────────┘  Body: { headerName:"X-XSRF-TOKEN", parameterName:"_csrf", token:"…" }
│          │
│          │  2 POST /api/v1/auth/login (public)
│          │     headers: { Content-Type: application/json, X-XSRF-TOKEN: <from cookie> }
│          │     body: { username, password }
│          │ ─────────────────────────────────────►            Set-Cookie: JSESSIONID=<new> (migrateSession)
│          │ ◄─────────────────────────────────────            Set-Cookie: XSRF-TOKEN=<rotated>
│          │
│          │  3 GET /api/v1/auth/me → CurrentUserResponse
│          │
│          │  4 Mutations (POST/PUT/PATCH/DELETE)
│          │     always send header X-XSRF-TOKEN: <value from XSRF-TOKEN cookie>
│          │
│          │  5 POST /api/v1/auth/logout
│          │     header X-XSRF-TOKEN required
│          │ ◄─────────────────────────────────────            Session invalidated; cookies cleared
└──────────┘
```

## 4.2 Cookies

| Cookie name | HttpOnly | Secure (prod) | SameSite | Purpose | Frontend visibility |
|---|---|---|---|---|---|
| `JSESSIONID` | **Yes** | Yes (prod profile) | `Lax` | Session identifier. | **Must never be read.** The browser sends it automatically. |
| `XSRF-TOKEN` | No | Yes | `Lax` | Double-submit CSRF token. | **Must read** from `document.cookie` and echo as `X-XSRF-TOKEN` header on mutations. |

Cookie attributes are set in `src/main/java/com/edam/config/SecurityConfig.java`. In production the prod profile forces `Secure; SameSite=Lax` on both cookies; the dev/test profile relaxes `Secure` for HTTP localhost.

## 4.3 CSRF

| Item | Value |
|---|---|
| Repository | `CookieCsrfTokenRepository.withHttpOnlyFalse()` |
| Cookie name | `XSRF-TOKEN` |
| Header name | `X-XSRF-TOKEN` |
| Parameter name (form posts) | `_csrf` (not used by the SPA — SPA always uses the header) |
| Required on | All methods **except** GET/HEAD/OPTIONS/TRACE. This includes POST/PUT/PATCH/DELETE **even on public endpoints** that mutate state (e.g. `POST /auth/login`, `POST /auth/bootstrap`, `POST /auth/forgot-password`). |
| Rotation | Rotated on login (because `sessionFixation().migrateSession()` creates a new session) and on logout. The CSRF token returned from `GET /auth/csrf` and from `POST /auth/login` must be re-read from the cookie **after** those calls. |
| Failure response | 403 Forbidden (Spring's default `AccessDeniedHandler` returns JSON via `JsonApiAccessDeniedHandler`). |

Helper:
```ts
function getCsrfTokenFromCookie(): string {
  return document.cookie.split('; ').find(r => r.startsWith('XSRF-TOKEN='))?.split('=')[1] ?? '';
}
```
Call this **immediately before every mutating request** (do not cache it across login/logout).

## 4.4 Exact endpoints

### AUTH-001 `GET /api/v1/auth/csrf` — public
- 200 `{ "headerName": "X-XSRF-TOKEN", "parameterName": "_csrf", "token": "…" }` (`CsrfTokenResponse`)
- Establishes a session and issues both cookies if none exists; idempotent.
- Call once on app boot (before login). The body token is the same value as the cookie; reading the cookie is sufficient — the body exists for clients that cannot read cookies.

### AUTH-002 `POST /api/v1/auth/login` — public
- CSRF **required** (even though it is public).
- Body: `LoginRequest { @NotBlank @Size(max=100) username; @NotBlank @Size(max=200) password }`.
- Success 200 `ApiResponse<CurrentUserResponse>`; session established; CSRF cookie rotated.
- Failure:
  - Invalid credentials (unknown user, wrong password, inactive account): 401 `EDAM-2006 INVALID_CREDENTIALS` (single generic message — no user enumeration).
  - Account temporarily locked after repeated failures: 401 `EDAM-2007 ACCOUNT_LOCKED`.

### AUTH-003 `GET /api/v1/auth/me` — authenticated, no business permission required
- Returns `CurrentUserResponse` (userId, username, employeeCode, displayName, email, designation, departmentId/Name, branchId/Name, roles, permissions, dataScopes, installationScope, mustChangePassword, lastLoginAt, passwordPolicy).
- Call immediately after login and on app boot to rebuild the in-memory auth context.
- If 401, redirect to `/login?reason=expired`.

### AUTH-004 `POST /api/v1/auth/logout` — public (but requires a session)
- CSRF required.
- Body: none.
- Invalidates the session and clears cookies. After logout, call `GET /auth/csrf` again before logging back in (the old XSRF token is invalidated).

### AUTH-005 `POST /api/v1/auth/change-password` — authenticated (any user)
- Body: `ChangePasswordRequest { @NotBlank @Size(max=200) currentPassword; @NotBlank @Size(max=200) newPassword }`.
- `newPassword` must satisfy the policy returned in `CurrentUserResponse.passwordPolicy` (minimum/maximum length, require upper/lower/digit/special).
- Success 200.

### AUTH-006 `POST /api/v1/auth/forgot-password` — public
- Body: `ForgotPasswordRequest { @NotBlank @Email @Size(max=255) email }`.
- Returns a constant `NeutralResponse { message: "If the address is registered, a reset link has been sent." }` regardless of whether the email exists (no account enumeration).
- CSRF required. SMTP must be configured for email delivery; without SMTP the token is only delivered through dev diagnostic channels.

### AUTH-007 `POST /api/v1/auth/reset-password` — public
- Body: `ResetPasswordRequest { @NotBlank @Size(max=400) token; @NotBlank @Size(max=200) newPassword }`.
- Failure 400 with `EDAM-2009 INVALID_RESET_TOKEN` if the token is unknown, used or expired.
- CSRF required.

### AUTH-008 `GET /api/v1/auth/installation` — public
- Returns `InstallationStatusResponse { bootstrapped: boolean; bootstrappedAt: Instant|null }`.
- React uses this to decide between showing `/login` or `/bootstrap` on first load.

### AUTH-009 `POST /api/v1/auth/bootstrap` — public (one-time, only while `bootstrapped === false`)
- CSRF required.
- **Requires header `X-EDAM-Bootstrap-Token: <EDAM_BOOTSTRAP_TOKEN>`** (the token is read from an env var; it travels as a header, not a query param, so it never lands in access logs).
- Body: `BootstrapRequest { username, firstName, lastName, email, employeeCode?, password }` (all strings have `@NotBlank/@Size/@Email` constraints as applicable).
- Success 200 `ApiResponse<BootstrapResponse>`. After success the user is authenticated as the new administrator; CSRF cookie is rotated.
- Failure after installation: 403 `EDAM-2010 INSTALLATION_ALREADY_COMPLETED`.
- After successful bootstrap the operator should unset `EDAM_BOOTSTRAP_TOKEN`.

### SYS-001 `GET /actuator/health` — public
- Returns `{ "status": "UP" }`; usable as a pre-login deployment smoke check. In prod the response contains no component detail (no DB or host information).
- `GET /actuator/info` is also public and returns build/version metadata if configured.

### SYS-002 `GET /api/v1/system/info` — authenticated
- Returns `{ application: "edam", version: string, status: "UP" }` (application metadata). Available after login (deny-by-default — returns 401 anonymously); intended for post-login shell display.


## 4.5 Authenticated GETs
- Send no extra headers beyond what the browser adds (`Cookie: JSESSIONID=…; XSRF-TOKEN=…` is sent automatically because we use `credentials: 'include'`).
- Do not echo `X-XSRF-TOKEN` on GETs (it is ignored but harmless).

## 4.6 Authenticated mutations (POST/PUT/PATCH/DELETE)
- Headers must include `X-XSRF-TOKEN: <value of XSRF-TOKEN cookie>`.
- `Content-Type: application/json` unless the call is `multipart/form-data` (document uploads, see §10).
- On 401: clear local user, redirect to `/login?reason=expired`, re-fetch CSRF before retry.
- On 403 where `error.code === 'EDAM-2002'` (access denied from CSRF filter): re-fetch CSRF (GET `/auth/csrf`) and retry the request **once**.

## 4.7 Session expiration
- The backend returns 401 on any authenticated call once the session times out or is invalidated.
- The frontend must handle this globally (in the HTTP wrapper): clear any in-memory auth, redirect to login, preserve the attempted URL as `?next=…`.
- Do NOT attempt transparent re-login (no refresh tokens exist).

## 4.8 CORS
Configuration lives in `src/main/java/com/edam/config/CorsConfig.java` and is driven by the configuration property `edam.cors.allowed-origins` (comma-separated, exact origin match only — no wildcard `*` with credentials).

| Property | Value |
|---|---|
| Allow credentials | **true** (cookies are sent) |
| Allowed origins | exact-match list from configuration |
| Allowed methods | `GET, POST, PUT, PATCH, DELETE, OPTIONS` |
| Allowed headers | `Content-Type, X-XSRF-TOKEN, X-EDAM-Bootstrap-Token, Idempotency-Key, If-Match` (plus the browser CORS-safelisted headers) |
| Exposed headers | `Location, X-XSRF-TOKEN` (so clients can read CSRF from response header too, though cookie is the canonical source) |
| Preflight max-age | 1 hour |

**Local dev:** Vite `server.proxy` should forward `/api` to `http://localhost:8080`; with a proxy in place the browser is same-origin and CORS does not apply. Without a proxy (e.g. staging), set `edam.cors.allowed-origins=https://app.edam.example.com`.

## 4.9 HTTPS requirements (production)
- Cookies are `Secure` in the prod profile; EDAM must be served over HTTPS (reverse proxy with TLS).
- Set `server.forward-headers-strategy=framework` (already set in `application-prod.yml`) and ensure the proxy sends `X-Forwarded-Proto: https`.

## 4.10 Minimal `api.ts` bootstrap (for reference; see the Frontend Architecture Blueprint for the full client)
```ts
async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  const method = (init.method || "GET").toUpperCase();
  if (method !== "GET" && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    headers.set("X-XSRF-TOKEN", getCsrfTokenFromCookie());
  }
  const res = await fetch(`/api/v1${path}`, { ...init, headers, credentials: "include" });
  const data = await res.json().catch(() => null) as ApiResponse<T> | null;
  if (!res.ok || !data?.success) throw normalizeError(res, data);
  return data.data as T;
}
```
