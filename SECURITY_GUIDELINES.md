# Security Guidelines — Authentication, Authorization & Multi-Institution Isolation

## 1. Security Architecture Principles (Phase 1 + BRD Sec 13)

1. **Separation of Concerns**:
   - **Authentication**: Verifies identity (`UserDetailsService` / future SSO adapter) without hardcoding client users or baking client-specific roles into session state.
   - **Institution Context Resolution**: `InstitutionContextFilter` determines the active institution for the request (`X-Institution-Code` header or user's default active membership) and **rejects** any attempt to switch to an institution the user does not hold an `ACTIVE` membership for (`403 Forbidden`).
   - **Authorization**: `InstitutionAuthorizationChecker` (`@edamAuth.hasPermission('...')`) evaluates fine-grained capability permissions strictly within the **currently active institution**, guaranteeing permissions in Institution A never authorize actions in Institution B.
2. **Neutral Session Authorities**: `EdamUserDetailsService` assigns `AUTHENTICATED_USER` at the Spring Security session level rather than hardcoded banking roles (`Finance_Admin`, etc.). All domain permissions come from database-backed `roles` -> `role_permissions` -> `permissions` scoped to `user_institution_memberships`.
3. **Multi-Institution Users**: Users live in `users` decoupled from any single bank; `user_institution_memberships` links a user to one or more active institutions with distinct roles per institution.

---

## 2. Implemented Controls

- **Password Storage**: `BCryptPasswordEncoder` with work factor `12`. Non-local SSO/external accounts (`password_hash IS NULL`) are rejected from local form password login.
- **Session & Cookie Hardening**:
  - `HttpOnly = true`, `SameSite = Lax`, `timeout = 30m` (configurable via `EDAM_SESSION_TIMEOUT`).
  - `Secure = true` enabled automatically in `uat` and `prod` profiles.
  - Session invalidation and `JSESSIONID` deletion on `POST /logout`.
- **CSRF Protection**: Enabled for browser forms (`/login`, `/logout`, future JSP forms); CSRF hidden tokens included in JSP forms.
- **Security Response Headers (`SecurityConfig`)**:
  - `X-Frame-Options: SAMEORIGIN`
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; object-src 'none'; frame-ancestors 'self'`
  - `Referrer-Policy: strict-origin-when-cross-origin`
- **Safe Error Responses (`spring.web.error.*` + Handlers)**:
  - Stack traces, binding error internals, exception class names, and SQL constraint names are never returned to clients.
  - API calls (`/api/**` or `Accept: application/json`) receive `401` (`EDAM-AUTH-401`) or `403` (`EDAM-SEC-403`) JSON `ApiResponse` envelopes; browser navigation receives `/login` redirects or `/access-denied` JSP views.
- **CSS & XSS Hardening**:
  - Institution hex theme colors are validated by `@ValidHexColor` / `HexColorValidator` (`^#[0-9A-Fa-f]{6}$`) before emission in `/assets/theme/institution-theme.css`.
  - JSP views escape dynamic outputs with JSTL `<c:out>` and prohibit inline `<script>` / `<style>`.

---

## 3. Open Security Decisions (`OD-11`, `OD-12`, `OD-19`, `OD-20`)

Before Phase 2/3 production onboarding, security stakeholders must formally sign off on:
1. **Authentication Adapter (`OD-11`)**: Local DB credentials vs corporate Active Directory / OIDC / SAML 2.0 SSO.
2. **Role-to-Permission & Data-Scope Matrix (`OD-12`)**: Complete permission assignments across Requester, Unit Head, Recommending Authority, EC Member, CEO, CFO, Finance, Auditor, and Institution Admin.
3. **Tenant Isolation Model (`OD-20`)**: Confirm whether shared schema (`institution_id` + PostgreSQL RLS), schema-per-tenant, or DB-per-tenant is required.
4. **Password Policy, MFA, Encryption at Rest & Retention (`OD-19`, `OD-14`)**: Numeric targets for lockout thresholds, rotation, field-level PII encryption (vendor CNIC/NTN/STRN), and attachment virus scanning/storage.
