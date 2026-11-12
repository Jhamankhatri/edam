# ADR-0003 — Executable WAR packaging (JSP requires it)

* **Status:** Accepted
* **Date:** 2026-11-12
* **Related:** BRD §15.1 (Web UI: JSP server-rendered), §11 of the engineering instructions; [ARCHITECTURE.md](../ARCHITECTURE.md)

## Context

The BRD mandates a JSP/JSTL server-rendered UI. Spring Boot's own documentation states the constraint
plainly: **"JSPs are not supported when using an executable jar."** With Tomcat, JSPs "should work if you use
war packaging"; an executable WAR runs with `java -jar` *and* deploys to a standard container.

JSP rendering additionally requires a JSP engine (Jasper) and a JSTL implementation on the classpath. Tomcat's
embedded default does **not** include Jasper.

Two packaging models exist for WARs:

1. **Fat WAR** — the embedded container inside `WEB-INF/lib`. `java -jar` works; deploying to an external
   Tomcat would have two containers on the classpath.
2. **`provided` container** — the embedded container lands in `WEB-INF/lib-provided`, which the launcher adds
   to the classpath but which is invisible to an external container. Both deployment models work.

## Decision

Produce an **executable WAR** (`<packaging>war</packaging>`), with:

* `EdamApplication extends SpringBootServletInitializer` (required for external-container deployment).
* `spring-boot-starter-webmvc` with `spring-boot-starter-tomcat` **excluded**, plus
  `spring-boot-starter-tomcat` at `provided` scope → packaged into `WEB-INF/lib-provided`.
* `tomcat-embed-jasper` at `provided` scope (the JSP engine).
* JSTL: `jakarta.servlet.jsp.jstl-api` and `org.glassfish.web:jakarta.servlet.jsp.jstl` at `provided` scope.
* JSPs under `src/main/webapp/WEB-INF/jsp` (not addressable by URL; rendered only through MVC).
* `maven-war-plugin` with `failOnMissingWebXml=false` and the main class in the manifest.

The packaging contract is enforced by an automated test (`PackagedWarTest`): the WAR must contain JSPs,
Jasper, JSTL, the PostgreSQL driver, Flyway's PostgreSQL support, the `WarLauncher` and the embedded container,
and must contain **no test-scoped dependency**. CI additionally starts the packaged WAR against PostgreSQL and
performs real HTTP checks on the rendered pages.

## Consequences

**Positive**

* JSPs render in the artifact that is actually deployed, not in a mocked environment.
* The same artifact runs embedded (simplest operations) or in an external Tomcat (client-standard hosting).
* Packaging regressions — a dependency accidentally moving from `provided` to `compile`, JSPs disappearing
  from the archive — fail the build instead of production.

**Negative**

* A WAR is slightly heavier and slower to start than a JAR (acceptable for a Head Office application).
* Spring Boot's documented JSP limitation remains: a custom `error.jsp` cannot replace the default error
  view. This is handled by mapping the container's error dispatch to an MVC error controller
  (`MvcExceptionHandler`) rather than by an `error.jsp`.
* `spring-boot:run` needs `src/main/webapp` as the web root; the standard layout is used so no extra
  configuration is required.

## Alternatives considered

| Alternative | Rejected because |
|---|---|
| Executable JAR | JSPs cannot be rendered at all |
| Thymeleaf/FreeMarker instead of JSP | Contradicts the approved BRD technology decision; would need a change request |
| Deploy to an external Tomcat only | Loses the simple `java -jar` operational path the platform's deployment pipeline is built around |
| Fat WAR (container in `WEB-INF/lib`) | Two containers on the classpath when deployed externally; `provided` gives both models without conflict |

## Verification

`mvn verify` runs `PackagedWarTest`. CI's `war-smoke` job starts `java -jar target/edam.war`, waits for
`Started EdamApplication`, then asserts: health is `UP` without component detail; `/login` renders the
form, the CSRF token and institution branding from configuration; `/dashboard` requires authentication;
`/api/v1/system/info` returns the 401 envelope; an unknown path leaks nothing; a CSRF-protected sign-in with
fixture credentials renders the authenticated shell.
