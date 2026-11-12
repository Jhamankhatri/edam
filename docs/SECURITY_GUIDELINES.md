# Security Guidelines

> **Read the unresolved decisions in §9 before building any authorization feature.** Several controls that a
> bank will expect — SSO, a role/permission matrix, tenant isolation, data-level masking — are *not*
> approved yet. Guessing them in code is worse than leaving the seam explicit.

## 1. Authentication

### What exists

* A **seam**: `EdamUserDetailsService` (`UserDetailsService`). Any adapter — local database, LDAP, OIDC,
  SAML, or the client's portal — implements it and returns an `EdamPrincipal`.
* A **form-login shell** and a custom `AuthenticationProvider` that verifies credentials through the seam and
  places the `EdamPrincipal` in the security context by construction (not as a post-processing step).
* **Fail-closed behaviour**: with no `EdamUserDetailsService` bean (i.e. any profile that is not `local`),
  the provider rejects every attempt with `security.authentication_rejected reason=no_identity_provider_configured`.
  The application never starts with a default user and never falls back to an auto-generated password.
* **Development fixtures** (`edam.security.dev-users.enabled=true`, `local` profile only): three in-memory
  users. The `admin` fixture belongs to **two** institutions on purpose, to prove the code never assumes a
  single-tenant user. Fixture passwords are hashed with the configured `PasswordEncoder` (BCrypt) and are
  never logged.

### What does not exist

* SSO/AD/OIDC/SAML integration (OD-11).
* Password policy, reset flow, MFA, session registry, "remember me".
* An account lockout policy after repeated failures.

**Do not implement password storage, a login form with self-registration, or a token scheme without an
approved requirement.** The foundation ships the seam, not a product decision.

### Adding an identity adapter

1. Implement `EdamUserDetailsService`, returning a `UserDetails` whose principal is an `EdamPrincipal`
   (wrap a `UserDetails` if the protocol requires it, as `DevUserDetails` does).
2. Populate `institutionRoles` as the **set** of institutions the user may act for, with the roles held in
   each.
3. Populate `permissions` with institution-independent operational permissions (`Permissions.*`).
4. Register the bean; the custom `AuthenticationProvider` picks it up. Never configure a second
   `AuthenticationProvider` that returns a different principal type.
5. Add a contract test proving the principal is an `EdamPrincipal` and that institution membership is a set.

## 2. Authorization

* **Server-side only.** `authorizeHttpRequests` for URL access, `@PreAuthorize` for operations
  (`@EnableMethodSecurity` is enabled). Hiding a button or a menu item in a JSP is **never** an
  authorization control (BRD §13.1); it is a usability decision.
* **Deny by default.** Every request requires authentication except an explicit, reviewed public list
  (`/login`, `/access-denied`, `/error`, static assets, health/info). Adding to that list requires review.
* **Authorities** are namespaced:
  * `PERM_<permission>` — institution-independent operational permissions (`request:create`,
    `approval:act`, `configuration:manage`, `reporting:view`, `audit:view`).
  * `ROLE_<institutionCode>:<role>` — a role held **in a specific institution**, so a role granted for one
    tenant can never satisfy a check for another.
* **Record-level access** (requester ownership, current approval assignment, designated revert recipient,
  department scope) will be enforced in the module services once the workflow exists. It is a service
  responsibility, not a URL rule.
* **Never trust client input for identity or scope.** The actor comes from the security context; the
  institution from `InstitutionContext`; never from a form field or query parameter.

## 3. Institution (tenant) context

`InstitutionContext` is the single seam for "which institution is this request acting for":

| Situation | Behaviour |
|---|---|
| Anonymous | Empty — pages that render without a tenant (login, error) still work |
| One membership | Resolved implicitly |
| Several memberships, none selected | **Fails closed** with `EDAM-2002`; the system refuses to guess |
| Explicit selection | Honoured (session/admin tooling) |

Membership is a `Set<String>`. **A user is never assumed to belong to exactly one institution** (BRD §6).

Isolation is *not yet enforced* at the data layer — there are no tenant-owned tables. See ADR-0002 and §9.

## 4. Transport and browser controls

| Control | Setting | Where |
|---|---|---|
| CSRF | Enabled (session UI); only `/actuator/**` exempt | `SecurityConfig` |
| Session fixation | `migrateSession` | `SecurityConfig` |
| Session cookie | `HttpOnly`, `SameSite=Lax`, `Secure` in `prod`, cookie-only tracking, 30 min | `application*.yml` |
| Content-Security-Policy | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'` | `SecurityConfig` |
| Clickjacking | `X-Frame-Options: DENY` + `frame-ancestors 'none'` | `SecurityConfig` |
| MIME sniffing | `X-Content-Type-Options: nosniff` (container default) | Tomcat |
| Caching of sensitive responses | `Cache-Control: no-store` on API errors | `GlobalExceptionHandler` |
| Reverse proxy | `server.forward-headers-strategy: framework` (`prod`) | `application-prod.yml` |

`'unsafe-inline'` is allowed for **styles only**, because institution theme tokens are injected as a
`<style>` block. Scripts have no inline exemption: all JavaScript lives in `/js/*.js`.

## 5. Input handling and output encoding

* Bean Validation on every external contract (`@Valid`, `@NotBlank`, `@Pattern`, `@Size`,
  `@DecimalMin`/`@Digits` for money), plus domain validation in services. Client-side validation is
  convenience only.
* JSP output of dynamic values uses `<c:out>` (HTML-escapes) — never `${...}` directly in markup. No
  scriptlets, no `eval`, no `innerHTML` with server data.
* SQL is produced by Spring Data JPA or parameterised queries. String-concatenated SQL is rejected in
  review.
* Redirect targets are server-composed (`redirect:`) with `redirectContextRelative`, never taken from a
  request parameter (open-redirect).
* File uploads are not implemented yet; when they are, the policy must define allowed types, size limits,
  storage location and virus scanning (OD-14).

## 6. Error handling and information disclosure

* API errors: `ApiResponse` with a stable `EDAM-*` code, a safe message, optional field errors and the
  correlation ID. No stack trace, SQL, class name or infrastructure detail.
* HTML errors: `MvcExceptionHandler` renders a status-derived message and the correlation ID; the exception
  object never reaches the template.
* `server.error.include-*` is `never`/`false` for message, stack trace, exception and binding errors.
* `/actuator/health` uses `show-details: never`, so database/infrastructure detail is not published.
* The container error dispatch is **not** authorized (§7 of ARCHITECTURE, ADR-0007): a server fault must be
  answered with a 5xx, never with a redirect to the login page.

## 7. Secrets and configuration

* No secret in the repository: not in `application*.yml`, not in tests, not in documentation. CI uses
  throw-away local passwords for its Postgres service container only.
* `prod` has no default credentials; missing environment variables fail startup.
* Secrets belong in a secret manager or environment injection (`EDAM_DB_PASSWORD`, SMTP credentials later).
* Database credentials, SMTP passwords and API keys must **not** be editable through the UI —
  institution configuration is for branding and business settings, not infrastructure secrets.
* Never log a secret, token, session ID or full identifier (see [LOGGING_GUIDELINES.md](LOGGING_GUIDELINES.md)).

## 8. Dependency and build hygiene

* `maven-enforcer-plugin` requires JDK 21+ and Maven 3.9+, and bans legacy log4j 1.x bindings.
* Versions are managed by `spring-boot-starter-parent` (Boot 4.1.2) — no ad-hoc version pinning of Spring
  components.
* Production dependencies are limited to what the code uses (the build deliberately avoids
  `spring-boot-starter-classic`, whose full optional auto-configuration set fails to start without every
  optional library on the classpath).
* Review new dependencies for licence, maintenance status and transitive surface before adding them.

## 9. Unresolved security decisions (block later phases)

| ID | Decision needed | Impact if guessed |
|---|---|---|
| **OD-11** | Authentication mechanism: local / LDAP / SSO / OIDC / SAML / client adapter | A fabricated protocol will be replaced; credentials handling is regulated |
| **OD-12** | Role → permission → data-scope matrix | The legacy app has no verified matrix; reporting even hard-codes every user as `Finance_Admin` |
| **OD-20** | Tenant isolation model (shared schema vs schema/database per tenant) | Wrong choice is a data breach and a re-platform |
| **OD-04** | CFO action permissions (approve/reject/revert/forward, remarks) | Cannot implement approval actions |
| **OD-10** | Selective-revert eligibility | Cannot implement revert |
| — | Password policy, session timeout, SSO enforcement, MFA | Security review blocker |
| — | Data classification and masking for CNIC/NTN/STRN, contact details | Privacy/compliance breach |
| — | Attachment storage, encryption, retention, virus scanning (OD-14) | Compliance breach |
| — | Audit retention and tamper protection (NFR-05) | Audit finding |

## 10. Security review checklist for a pull request

* [ ] No new public URL; any addition to the public list is justified.
* [ ] Every new operation has a server-side authorization check (`@PreAuthorize` or URL rule).
* [ ] Actor identity comes from the security context; institution from `InstitutionContext`.
* [ ] No tenant-scoped data access without a documented isolation decision (ADR-0002).
* [ ] No secret, credential or sensitive identifier in code, config, tests, logs or comments.
* [ ] JSP output escaped with `<c:out>`; no inline script; CSP unchanged or justified.
* [ ] Errors expose nothing internal; new error code added to `ErrorCode`, not a raw message.
* [ ] CSRF unaffected; state-changing endpoints are `POST`.
* [ ] Tests cover the denial path, not just the happy path.
