# ADR-0007 — The container error dispatch must never be authorized

* **Status:** Accepted
* **Date:** 2026-11-12
* **Related:** BRD §13.1; [ARCHITECTURE.md](../ARCHITECTURE.md) §3; `ServletFilterConfig`, `SecurityConfig`, `MvcExceptionHandler`

## Context

Spring Boot registers the Spring Security filter chain for **every** dispatcher type by default
(`REQUEST`, `FORWARD`, `ERROR`, `ASYNC`, `INCLUDE`). Two of those defaults are actively harmful for a
server-rendered application:

1. **`FORWARD`** — rendering a JSP is an internal forward from the MVC handler to `/WEB-INF/jsp/*.jsp`.
   When the chain covers forwards, the framework re-authorizes that internal path as if it were a client
   URL. An anonymous visitor to `/login` therefore gets redirected to `/login`, forever.
2. **`ERROR`** — when a view fails to compile, the container performs an error dispatch to `/error`. When
   the chain covers error dispatches, an anonymous visitor's render failure is answered with **302 → /login**.
   A server fault is disguised as an authentication prompt, the real status code is lost, and the failure is
   invisible in monitoring that watches status codes.

Both symptoms were observed in CI on this branch: a 302 instead of the login page before the `FORWARD`
restriction, and a 302 instead of a 500 when a JSP was broken after it.

## Decision

1. Register the security chain for **`REQUEST` only**:
   `spring.security.filter.dispatcher-types: request` in `application.yml`, documented in place.
   The login page, access-denied page, static assets and every JSP forward are plain `REQUEST` handling plus
   an internal forward, so authorization remains complete — the chain still runs for the incoming URL.
2. Because the chain no longer runs on `ERROR` dispatches, register a narrow
   `SecurityContextHolderFilter` for `ERROR` dispatches only (`ServletFilterConfig`). It restores the
   session-backed context so the error page can render the signed-in shell; it makes **no** authorization
   decision.
3. Map the error path to `MvcExceptionHandler`, which renders the safe error page with the **original**
   status code, the correlation ID and no internal detail.
4. Keep `/error` on the public URL list. An error page that demands authentication cannot explain anything.

## Consequences

**Positive**

* Failures surface as failures: a broken view returns 5xx to the browser and to monitoring.
* No redirect loop on the login page.
* Authorization is unchanged for real client requests, because it is applied on `REQUEST` — where URLs are
  actually requested.

**Negative / risks**

* If a future feature relies on an `ASYNC` dispatch (e.g. `DeferredResult`), the chain must be widened
  deliberately, with a test — the current decision is documented so nobody "fixes" it by returning to the
  framework default.
* The error page's authentication state comes from the restored session context, not from an authorization
  pass. That is sufficient for rendering navigation and is the standard container-error behaviour; it does
  not grant access to any resource.

## Verification

* `WebLayerSecurityTest` asserts `/login` returns 200 (not a redirect) and that `/error` renders with a 5xx
  and no `Location` header.
* CI's packaged-WAR smoke test asserts the login page renders and that an unknown path is answered either by
  a redirect to sign-in or a 4xx — never a 5xx caused by a broken error page.
