# ADR-0004 — Authentication seam, no protocol invented

* **Status:** Accepted (seam); **blocking** for the protocol itself (OD-11)
* **Date:** 2026-11-12
* **Related:** BRD §13, §4.2 (Authentication/SSO boundary), OD-11; [SECURITY_GUIDELINES.md](../SECURITY_GUIDELINES.md)

## Context

The reference implementation "relies on Albaraka portal login/session; exact authentication protocol is not
supplied" (BRD §4.2). OD-11 asks the business to choose local authentication, SSO/AD/OIDC/SAML, or a
client-specific adapter. The BRD is explicit that a complete authentication or SSO integration must not be
introduced without an approved requirement and architecture decision.

At the same time, the foundation must be runnable and demonstrable, and every page must be authenticated
(BRD §13.1). Doing nothing is not an option; inventing a protocol is worse than either.

## Decision

1. Define an **authentication seam**: `EdamUserDetailsService extends UserDetailsService`. Implementations
   return a `UserDetails` whose principal is an `EdamPrincipal` (institution membership as a `Set`, roles
   per institution, permissions).
2. Provide a **custom `AuthenticationProvider`** that verifies credentials through the seam and places the
   `EdamPrincipal` in the security context **by construction**. This avoids a subtle class of bug where the
   principal is a framework `User` and every institution lookup silently returns empty.
3. Ship a **form-login shell** (the UI must exist) with CSRF, session fixation protection, cookie-only
   sessions and a 30-minute timeout.
4. Ship **development fixtures only** under `edam.security.dev-users.enabled=true`, which is set in `local`
   alone. Fixture passwords are hashed with BCrypt; they are documented as fixtures, never as secrets.
5. **Fail closed**: with no `EdamUserDetailsService` bean, a provider rejects every attempt and logs one
   actionable warning. There is no default user and no auto-generated password.
6. Record OD-11 as blocking and do not implement password policy, reset, MFA, lockout or token issuance.

## Consequences

**Positive**

* The authorization model (institution-scoped authorities, `@PreAuthorize`, record-level checks later) is
  independent of the authentication mechanism, so choosing SSO later does not touch any controller or
  service.
* A misconfigured deployment fails closed and says so, rather than exposing an application with no identity
  provider.
* Multi-institution membership is exercised from day one by a fixture that belongs to two institutions —
  which is what prevents the "assume one tenant per user" defect from creeping in.

**Negative**

* The foundation's login is not a production identity solution; that is visible and documented rather than
  hidden.
* A future SSO adapter must still map the client's user model onto `EdamPrincipal`, which requires the
  role/permission matrix (OD-12) to be approved first.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Full Spring Security form login with a `users` table | Invents a credential store and password policy that the business has not approved; likely discarded on SSO |
| OIDC/SAML integration now | No approved requirement, no identity provider metadata from any client |
| Disable security in the foundation | Violates BRD §13.1 and would ship an insecure default into later phases |
| Rely on a framework default user | Silent, insecure, and impossible to align with institution membership |
