# Verification Record — Foundation Phase (Phase 0 / PR 1)

This document records what was verified, **how** it was verified, and what remains
unverified. It is deliberately blunt: a claim that cannot be reproduced from this repository
or from CI does not belong in a "done" column.

* Branch under review: `arena/a3508752-edam`
* Scope: platform foundation only. No OPEX/CAPEX, no approval workflow, no business module
  beyond the institution/branding seam described in [ARCHITECTURE.md](ARCHITECTURE.md).
* Environment used for verification: GitHub Actions (`ubuntu-latest`), JDK 21 (Temurin),
  PostgreSQL 18 service container. No local JDK/Maven/PostgreSQL/Docker exists in the
  authoring sandbox, so **CI is the compile and runtime oracle** — every result below is
  taken from an actual green CI run, not from a locally assumed outcome.

---

## 1. Required pre-PR checklist

| # | Check | Result | Evidence |
|---|---|---|---|
| 1 | Clean Maven build | **Pass** | `mvn verify` in the `build-and-test` job; `tools/ci-report.py` fails the job on any `[ERROR]`/`BUILD FAILURE` |
| 2 | Automated tests | **Pass** | 70 tests, 0 failures, 0 errors (3 skipped by design — see §4) |
| 3 | Application startup | **Pass** | `war-package-smoke-test` starts `java -jar target/edam.war` against PostgreSQL 18 and waits for `Started EdamApplication` |
| 4 | PostgreSQL connectivity | **Pass** | Flyway migrates on startup; the seed institution is read and rendered into the login page; `InstitutionMappingIntegrationTest` asserts mapping against a real PostgreSQL |
| 5 | Flyway migration execution | **Pass** | `V1__baseline.sql`, `V2__institution_seed.sql` applied on every smoke run; app fails fast if they do not apply |
| 6 | JSP rendering from the executable WAR | **Pass** | `/login` → 200 with `<form action="/login">`, `name="username"`, `name="password"`, `name="_csrf"`, `<h1>DemoBank</h1>`, theme block; `/access-denied` → 200; authenticated `/dashboard` → 200 rendered through the JSP layout |
| 7 | Secrets / sensitive-log review | **Pass with notes** | see §5 |
| 8 | Dependency / duplication review | **Pass with notes** | see §6 |

---

## 2. Test inventory (what each suite actually protects)

| Suite | Tests | Layer | Protects |
|---|---|---|---|
| `WebLayerSecurityTest` | 12 | `@SpringBootTest` + MockMvc, PostgreSQL | Anonymous access redirects to `/login`; API answers 401 with the `EDAM-2001` envelope and a correlation ID; actuator health is public and exposes no component detail; authenticated API returns application identity; headers (`X-Frame-Options: DENY`, `nosniff`, CSP); correlation ID echo and hostile-input sanitisation; `/error` never redirects; CSRF on POST; no framework strings in an unknown-path response |
| `ViewModelContractTest` | 5 | Full context, real view-controller mappings | The view model the JSPs consume: `institutionTheme` (from DB-backed configuration) and `correlationId` are present on `/login` and `/access-denied`; a redirect target gets no theme |
| `InstitutionMappingIntegrationTest` | 6 | Testcontainers PostgreSQL | Institution code/currency/colour constraints, locale defaults, lookup-by-code behaviour |
| `InstitutionContextTest` | 5 | Unit | Institution scoping rules, including the multi-institution principal case |
| `EdamPrincipalTest` | 7 | Unit | Principal construction, authority derivation, institution membership |
| `CorrelationIdTest` / `CorrelationIdFilterTest` | 11 / 4 | Unit | Header valid/sanitise/generate logic and MDC lifetime |
| `GlobalExceptionHandlerTest` | 10 | Unit | Every error family maps to the right status + stable `EDAM-*` code; no internal detail in payloads; missing static resource is 404 (not 500) with no body; `no-store` |
| `ApiPathsTest` | 7 | Unit | Context-path handling and API-vs-HTML detection |
| `PackagedWarTest` | 3 | Build-output assertion | WAR contains JSPs, `WEB-INF/lib-provided` (Tomcat provided), Jasper, `WarLauncher`; no test jars leak into the artifact |

---

## 3. Packaging contract (executable WAR, not JAR)

Verified against the built artifact inside CI:

* `target/edam.war` contains `WEB-INF/jsp/**` (JSPs are compiled at runtime by Jasper).
* `WEB-INF/lib-provided/` contains the embedded container; `WEB-INF/lib/` contains the
  application libraries. This is the layout required for a WAR that runs **both** via
  `java -jar` and when deployed to an external Tomcat.
* `org/springframework/boot/loader/launch/WarLauncher` is present, so `java -jar edam.war`
  starts the application (proven by the smoke test).
* `jakarta.servlet.jsp.jstl` + `org.glassfish.web:jakarta.servlet.jsp.jstl` present, so
  `<c:out>`, `<c:url>` and `<fmt:…>` resolve at runtime.
* No `src/test` classes or test-scoped libraries in the WAR.

Rationale and the failed alternative (`spring-boot-starter-tomcat-runtime`, which Boot's
migration guide recommends but which produced an empty `lib-provided` for this build) is
recorded in [adr/0003-executable-war-for-jsp.md](adr/0003-executable-war-for-jsp.md).

---

## 4. What is deliberately *not* covered

| Gap | Why | Consequence |
|---|---|---|
| `PackagedWarTest` skips 3 tests when `target/edam.war` is absent | The test asserts properties of a built artifact; `mvn test` alone has not produced one | Never silent in CI: the `war-package-smoke-test` job asserts the same properties on the real artifact |
| JSP **body** assertions are not in `WebLayerSecurityTest` | Boot's embedded test container does not run Jasper, so a JSP renders as empty | JSP HTML is asserted in the packaged-WAR smoke test instead, against the real artifact |
| No Selenium/browser test | No browser automation in scope for the foundation | Layout regressions are caught by markup assertions, not visual diffing |
| No performance/load test | NFR-01..NFR-12 in the BRD set no numeric targets ([OPEN_DECISIONS.md](OPEN_DECISIONS.md)) | Cannot verify a target that does not exist |
| No penetration test | Out of scope | Security posture is documented, not certified |

---

## 5. Secrets and sensitive logging — what was reviewed

* **No credential literal in `src/main`, `application.yml`, or migrations.** The development
  fixture accounts (`admin`/`admin-change-me`, `approver`, `requester`) exist only under the
  `local` profile and are documented as non-production. They are not production defaults and
  are not enabled by any other profile.
* **CI database credentials** (`edam` / `edam-ci-only`) are throwaway credentials for an
  ephemeral service container. They are not secrets for any real environment.
* **Logging**: `logback-spring.xml` uses the `correlationId` conversion rule; the request log
  records `method`, `path`, `status`, `durationMs` and never query strings, bodies, headers
  other than the correlation ID, or principal credentials. Exception logging records the class
  name and stack trace server-side only; responses carry the `EDAM-*` code and correlation ID.
* **Responses**: no stack traces, SQL, framework messages, or internal class names — asserted
  by `GlobalExceptionHandlerTest`, `WebLayerSecurityTest` and the `*_LEAK` smoke checks.
* **Not yet reviewed by a human security reviewer.** No secret-scanning tool (gitleaks, etc.)
  runs in CI yet; that is a candidate for the next foundation increment.

> Weaker claim, stated explicitly: `application-prod.yml` expects database credentials from the
> environment (`EDAM_DB_URL`, `EDAM_DB_USERNAME`, `EDAM_DB_PASSWORD`) and has no fallback. It
> has **not** been exercised against a real secret manager; that integration is an open decision.

---

## 6. Dependency and duplication review

* Versions are managed by `spring-boot-starter-parent` 4.1.2 except `logstash-logback-encoder`
  (not managed by Boot 4) and the test-container artifacts, which pin their versions.
* `maven-enforcer-plugin` enforces JDK 21+ and Maven 3.9+ and bans duplicate classes.
* No dependency is declared twice with different versions; no `<dependencyManagement>` block is
  used to override a Boot-managed version.
* Known, accepted duplication: both `jakarta.servlet.jsp.jstl` (API) and
  `org.glassfish.web:jakarta.servlet.jsp.jstl` (implementation) are needed; JSTL 3 split them.
* `spring-boot-starter-tomcat` is excluded from `spring-boot-starter-webmvc` and re-declared as
  `provided` — deliberate, explained in ADR-0003. Reviewers should expect this asymmetry.

---

## 7. How to re-run this verification

```bash
# everything CI does for correctness
mvn clean verify

# the packaged-WAR behaviour, requires a reachable PostgreSQL
export EDAM_DB_URL=jdbc:postgresql://localhost:5432/edam
export EDAM_DB_USERNAME=edam
export EDAM_DB_PASSWORD='<your local password>'
java -jar target/edam.war --spring.profiles.active=local --server.port=18080
curl -i http://localhost:18080/login
curl -i http://localhost:18080/favicon.ico          # 200
curl -i http://localhost:18080/css/missing.css      # 404, empty body
curl -i http://localhost:18080/actuator/health      # {"status":"UP"}
```

The GitHub Actions workflow encodes exactly these checks and fails the build if any regresses.

---

## 8. Sign-off status

| Item | Status |
|---|---|
| Build, tests, startup, migrations, JSP rendering | Verified in CI |
| Sensitive-data handling | Reviewed by the author; **independent review outstanding** |
| Dependency hygiene | Reviewed; enforcer rules in place |
| Tenant isolation | **Not implemented** — strategy analysis only, no silent choice ([adr/0002](adr/0002-tenant-isolation-deferred.md)) |
| Authentication against a real IdP | **Not implemented** — seam documented ([adr/0004](adr/0004-authentication-seam.md), OD-11 open) |
| Business modules (OPEX/CAPEX/approval) | **Intentionally absent** — next phase |
