# Phase 1 — Security Foundation: completion record

**Phase:** 1 of 5 (Security foundation)
**Commits:** `1e0e03a` … through the phase-1 tail on branch `arena/0f75b820-edam`
**CI:** run `43388225875` — `build-and-test`, `war-package-smoke-test`, `prod-profile-smoke-test` all **success**

This document records what Phase 1 actually delivers, what is proven by execution, and what remains
open. It is written against the code, not against the plan.

---

## 1. What changed

| Area | Before Phase 1 | After Phase 1 |
|---|---|---|
| Login API | none (Spring form login only, 302 + HttpOnly cookie) | `POST /api/v1/auth/login`, JSON, session established explicitly |
| CSRF usable by React | no (`HttpOnly` cookie, no repository override) | yes (`CookieCsrfTokenRepository.withHttpOnlyFalse()`) |
| Installation bootstrap | none | one-time token, closes permanently, constant-time comparison |
| Password policy | none | configurable length/composition, personal-data and common-password checks |
| Account lockout | none | 5 failures → 15 min lock, counter committed independently |
| Password reset | none | hashed single-use tokens, enumeration-neutral |
| User administration | none (models + repositories only) | full CRUD, activation, role and data-scope assignment |
| Role administration | none | role CRUD, permission catalogue, permission assignment |
| Privilege-escalation guards | none | three enforced in the service layer |
| CORS | absent entirely | exact-origin, credentialed, configurable |
| Endpoints | 59 | **87** |
| Controllers | 7 | **10** |
| Permission constants with no endpoint | 36 of 58 | **30 of 58** |
| Tests touching a service | **0** | **18 integration + 21 unit** |

---

## 2. Authentication endpoints (final paths)

All bodies and responses use the standard envelope
`{success, data, error{code,message}, fieldErrors, correlationId, path, timestamp}`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | public | JSON credentials → session + `CurrentUserResponse` |
| `POST` | `/api/v1/auth/logout` | public | invalidates the session |
| `GET` | `/api/v1/auth/me` | session | own profile, roles, permissions, data scopes |
| `GET` | `/api/v1/auth/csrf` | public | token, header name and parameter name |
| `POST` | `/api/v1/auth/change-password` | session | requires the current password |
| `POST` | `/api/v1/auth/forgot-password` | public | constant response for every outcome |
| `POST` | `/api/v1/auth/reset-password` | public | redeems a single-use token |
| `GET` | `/api/v1/auth/installation` | public | `{bootstrapped, bootstrappedAt}` |
| `POST` | `/api/v1/auth/bootstrap` | public + `X-Installation-Token` | creates the first administrator |

### User management — `/api/v1/users`

`GET /` (paged, scoped) · `GET /search` · `GET /{id}` · `POST /` · `PUT /{id}` ·
`POST /{id}/activation` · `GET /{id}/roles` · `POST /{id}/roles` · `DELETE /{id}/roles/{assignmentId}` ·
`POST /{id}/scopes` · `DELETE /{id}/scopes/{scopeId}` · `POST /{id}/password-reset`

### Role management — `/api/v1/roles`

`GET /` · `GET /permissions` (read-only catalogue) · `GET /{id}` · `POST /` · `PUT /{id}` ·
`POST /{id}/activation` · `PUT /{id}/permissions`

---

## 3. Security decisions and why

**CSRF is enabled, not disabled.** The API is cookie-authenticated, so a cross-site form post would
otherwise carry the user's session. What changed is the *transport*, not the control: the token moved
from a server-rendered hidden field to a cookie JavaScript can read. `withHttpOnlyFalse()` is the single
call that makes the API usable from a browser — Spring's default marks `XSRF-TOKEN` `HttpOnly`, which
would fail every mutation with 403 while appearing correctly configured.

**Sessions, not tokens.** One bank per deployment, no third-party client, and session invalidation is
immediate and server-side. A JWT would add a revocation problem this product does not need. Inside the
`EdamUserDetailsService` seam, an OIDC adapter replaces local authentication without touching a single
authorization rule.

**Three escalation guards, in the service.** A caller may not grant a role holding permissions they
lack, may not edit an account that outranks them, and may not confer a permission on a role that they do
not hold. Without these, `user:manage` alone would equal every permission in the product.

**No password in any request.** Creating a user issues a credential-setup grant. An administrator-chosen
password is known to two people and typically ends up in a ticket.

**Bootstrap closes permanently.** Empty token means *closed*, not open, so a deployment that forgets the
variable cannot be taken over anonymously. Comparison is over SHA-256 digests because comparing raw
strings leaks the shared-prefix length.

---

## 4. Defects found by the tests

Phase 1 exists to replace "the code looks right" with evidence. Five defects surfaced, four of which
would have been invisible in review:

| # | Defect | Consequence if shipped |
|---|---|---|
| 1 | `invalidateOutstanding` had no `@Modifying` | Hibernate executed an UPDATE as a SELECT; the first superseded reset link failed |
| 2 | **Lockout counter could never increment** | `save()` then `throw` inside one transaction rolls the save back. Brute-force protection was decorative while reading as correct |
| 3 | `@Transactional` on a self-invoked method | No proxy, no transaction — the same class of bug as #2 |
| 4 | Test called `bootstrap` with the wrong arity | (test defect, caught immediately) |
| 5 | `bootstrapped_by` FK violated by a random UUID | The constraint is right: completion must name a real account |

Defect 2 is the most important outcome of this phase. It was found because a test asserted that
`failedLoginCount` actually reached 4 — not that the method existed.

---

## 5. Test evidence (executed, CI run `43388225875`)

| Class | Run | Result |
|---|---|---|
| `identity.AuthenticationIntegrationTest` | 18 | **pass** — real PostgreSQL |
| `identity.PasswordPolicyTest` | 21 (nested) | **pass** |
| `common.*`, `architecture.*`, `web.*` | 44 | **pass** |
| `testsupport.PackagedWarTest` | 3 | skipped (environment-gated) |

`AuthenticationIntegrationTest` asserts behaviour, not presence: lockout after five failures with the
lock persisted, a locked account refusing even the correct password, single-use reset tokens, a new
reset invalidating the previous one, a password change requiring the current one, an inactive account
refused, and — as an executable assertion — that an unknown username and a wrong password produce the
*same* message, so the login form cannot become a user directory.

---

## 6. Open items entering Phase 2

- **No user-facing password-setup email.** `UserCreatedResponse` says plainly that delivery is pending.
  Until the notification module exists, an administrator must convey the setup link out of band.
- **`SYSTEM_ADMINISTER` still has no endpoint.** Deliberate: it is the installation-level role.
- **No audit search API yet** (Phase 4). Audit *writes* from these modules are real.
- **Rate limiting is per-account only.** Per-IP throttling is not implemented; an attacker cycling
  usernames avoids lockout. Recorded as a gap, not silently accepted.
- **`login_attempts` grows without bound.** Retention/purge belongs with the operations work.
