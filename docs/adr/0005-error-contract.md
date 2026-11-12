# ADR-0005 — Error contract: envelopes for API, a safe page for HTML

* **Status:** Accepted
* **Date:** 2026-11-12
* **Related:** BRD §13, §12.4; [ARCHITECTURE.md](../ARCHITECTURE.md) §3; [LOGGING_GUIDELINES.md](../LOGGING_GUIDELINES.md)

## Context

The application serves two very different clients from one deployment: a browser rendering JSP pages and
(non-browser) API callers expecting JSON. Both need consistent, safe failure behaviour:

* No stack trace, SQL statement, class name or infrastructure detail may reach any client (BRD §13.1).
* Every failure must be traceable from what the user sees to the server log.
* A browser must never be shown a JSON document, and an API client must never be redirected to an HTML login
  page.

## Decision

1. **Two advice types, one policy.**
   * `@RestControllerAdvice GlobalExceptionHandler` → `ApiResponse<T>` envelope for API requests.
   * `@Controller MvcExceptionHandler` → the `error.jsp` page, driven by the container's error dispatch.
2. **Request classification** is centralized in `ApiPaths.wantsApiResponse`: URI prefix `/api`, a JSON
   content type, or an explicit `Accept: application/json` without `text/html`. Form posts (browser) are
   therefore routed to the HTML flow even though a browser's `Accept` header prefers HTML with a JSON
   fallback.
3. **Stable error codes** (`EDAM-1001`, `EDAM-2001`, …) grouped by family are part of the public contract;
   they are never renamed or reused. Humans get a safe message; machines get the code.
4. **Correlation ID everywhere**: in the response header, in the JSON envelope and on the error page
   ("Reference: …"). Support traces an incident from any of the three.
5. **Log levels follow intent**: expected conditions (validation, business rule, access denied, not found)
   at `WARN` without a stack trace; unexpected and persistence failures at `ERROR` with the stack trace.
   The log is the diagnostic record; the response is not.
6. **View attributes must reach every page.** Branding and the correlation ID are contributed by a
   `HandlerInterceptor` (`InstitutionViewAttributesInterceptor`), not by a `@ControllerAdvice`: advice
   `@ModelAttribute` methods run only for handler methods, so pages rendered by a view controller (the login
   and access-denied pages) would render with empty branding while still returning HTTP 200.

## Consequences

**Positive**

* A failure is unambiguous for the caller and complete for the operator, without either leaking into the
  other.
* Error codes let the UI and integrations behave consistently without parsing prose.
* A regression of the "view attributes missing on view-controller pages" defect is caught by
  `ViewModelContractTest`, which asserts the rendered body rather than MVC internals.

**Negative**

* Two translation surfaces must be kept aligned when a new exception type is introduced (checklist item in
  [DEVELOPMENT_GUIDELINES.md](../DEVELOPMENT_GUIDELINES.md)).
* The interceptor runs for API requests too; the branding lookup is skipped for those (`ApiPaths`) so the
  cost is a boolean check.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| `@ControllerAdvice` `@ModelAttribute` for view attributes | Never runs for view-controller pages — the defect that produced a blank login page |
| `@RestControllerAdvice(basePackages=...)` | Scoping excludes handlers that belong to no application package |
| Problem Details (`application/problem+json`) only | The BRD's consumers are not all HTTP-JSON clients; the envelope carries field errors and the correlation ID in one shape |
| Returning entities/messages from controllers and formatting in JSP | Puts error rendering in the view layer and risks leaking internal messages |
| A single advice for both HTML and JSON | HTML needs a view name and status; JSON needs a body. Mixing them produces redirects and JSON fragments in browsers |
