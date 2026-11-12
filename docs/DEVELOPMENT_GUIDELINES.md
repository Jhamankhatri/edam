# Development Guidelines

## 1. Toolchain

| Tool | Version | Enforced by |
|---|---|---|
| JDK | 21+ | `maven-enforcer-plugin` (`requireJavaVersion [21,)`) |
| Maven | 3.9+ | `maven-enforcer-plugin` |
| PostgreSQL | 16+ (CI: 18) | Flyway migration tests |
| Docker | any recent | Testcontainers integration tests |

Build with the repository's own Maven wrapper conventions (`mvn`), not an IDE-managed build.

```bash
mvn clean verify        # the definition of "green": compile, all tests, WAR, packaging contract
mvn clean package -DskipTests
java -jar target/edam.war --spring.profiles.active=local
```

## 2. Code standards

### General

* Java 21: records for immutable data, `switch` expressions, text blocks where they help.
* **Constructor injection only.** Field injection (`@Autowired` on a field) and setter injection are
  rejected in review.
* Immutable by default: `final` fields, records for value objects, defensive copies at boundaries.
* No wildcard imports, no unused imports, no commented-out code, no `TODO` without an owner and a decision
  reference (e.g. `// TODO(OD-20): replace with tenant filter`).
* Prefer clarity over cleverness. A method that needs a paragraph to explain its control flow gets split.
* No static mutable state, no singletons beyond Spring beans, no `ThreadLocal` except where a framework
  contract requires it (documented: `InstitutionContext`).

### Layers

| Layer | Package | May do | Must not do |
|---|---|---|---|
| Delivery (web) | `web`, `*.web` | Map HTTP ↔ service calls, build view models, set response status | Contain business rules, touch repositories, build SQL |
| Application service | `<module>` | Enforce business rules, own the transaction boundary, orchestrate repositories | Talk HTTP, read `HttpServletRequest`, format for a view |
| Domain | `<module>` | Encapsulate state transitions and invariants on the aggregate | Depend on Spring Data, HTTP or configuration |
| Persistence | `<module>` | Map tables, issue queries | Contain business logic beyond a query predicate |
| Configuration | `config` | Declare framework beans and wiring | Decide business behaviour |

### Naming

* Classes: `*Controller`, `*Service`, `*Repository`, `*Properties`, `*Summary` (cross-module read model),
  `*View` (view model), `*Exception`, `*Filter`, `*Advice`.
* Methods: verb first (`resolveInstitution`, `applyAction`), boolean methods `is…`/`has…`/`can…`.
* Configuration properties: `edam.<module>.<thing>`; every property documented on the field with Javadoc so
  the metadata is generated.
* Error codes: `EDAM-<family><sequence>` — see `ErrorCode` (1xxx request/validation, 2xxx
  authentication/authorization, 3xxx business rule, 4xxx resource, 5xxx persistence, 9xxx unexpected).
  **A code is a public contract: never rename or reuse one.**

### Javadoc

Write Javadoc where the *why* is not obvious from the signature: the reason a decision was made, the
failure behaviour, the invariants a caller must uphold. Do not restate the method name
(`/** Gets the name. */`). Every type that embodies an architectural decision links to the relevant ADR or
BRD section.

## 3. Transactions

* `@Transactional` on application services, class-level `readOnly = true` for query services, override per
  write method.
* One business operation = one transaction. A request and its children commit or roll back together.
* Never call an external system (email, file store, HTTP) inside a database transaction; use an outbox.
* Never let a lazy association escape the service boundary (`open-in-view: false`). Return a DTO/record.

## 4. Error handling

* Throw a typed exception (`BusinessException`, `ResourceNotFoundException`, `ValidationFailedException`,
  `ConcurrentModificationException`); let `GlobalExceptionHandler` translate it.
* No `catch (Exception e) {}`. If a failure is deliberately contained, catch the narrowest type, log at
  `WARN`/`ERROR` and state in a comment why degradation is correct (see `InstitutionViewAttributesInterceptor`).
* Business messages are user-safe. Internal causes go to the log and behind the correlation ID.
* Never return `null` where a collection is expected; return `List.of()` / `Optional.empty()`.

## 5. Validation

* Bean Validation on request contracts (`@Valid`), never on entities used by the UI directly.
* Cross-field rules (invoice total equals approval amount; lower bound < upper bound; reason required when
  over budget) belong in the service/domain, not in annotations, because they involve multiple fields and
  business meaning.
* Validate at the boundary and again in the domain where the invariant is genuinely a domain rule. The
  second check protects non-HTTP callers.

## 6. Persistence

See [DATABASE_GUIDELINES.md](DATABASE_GUIDELINES.md). Highlights:

* Flyway migration for every schema change; `ddl-auto: validate` everywhere.
* Entities are never exposed outside their module; return `*Summary`/`*View` records.
* Repositories are injected only into their own module's services.
* `NUMERIC(19,2)` for money, `NUMERIC(19,6)` for rates, `BigDecimal` in Java, never `double`.

## 7. Views (JSP)

* No scriptlets (`<% ... %>`), no business logic, no repository access.
* `<c:out>` for every dynamic value. No inline event handlers or inline `<script>` (CSP).
* Layout via `<%@ include %>` of `_layout/*.jsp`; a page includes `head.jsp`, `theme.jsp`, then content.
* All CSS/JS in `src/main/resources/static`; view-specific behaviour in a small external file.
* Accessibility basics: landmarks, `<label for>`, `alt`, skip link, visible focus, colour contrast.
* A view receives a model, never an entity. If a view needs a lazy association, the service must fetch it.

## 8. Tests

Testing is part of the deliverable, not an afterthought. Choose the cheapest test that can actually fail:

| Kind | Annotation | Use for |
|---|---|---|
| Unit | plain JUnit | Pure logic: correlation IDs, path derivation, validation conversion, principal authorities |
| MVC contract | `@SpringBootTest` + `@AutoConfigureMockMvc` | Security rules, status codes, envelopes, view-model attributes, error pages |
| Integration | `@SpringBootTest` + Testcontainers PostgreSQL | Flyway migrations, JPA mapping/`validate`, constraints, transactions |
| Packaging contract | plain JUnit reading `target/edam.war` | JSP/Jasper/JSTL/lib-provided presence, no test dependencies in the WAR |

Rules:

* A bug fix starts with a test that fails for the right reason (`git stash` the fix to prove it).
* Assert behaviour, not implementation. Do not test getters or framework beans.
* Test the **denial** path: anonymous, wrong permission, wrong tenant, invalid input, concurrent action.
* No `Thread.sleep`, no ordering between tests, no dependency on a developer's local database.
* Test names state the rule: `anonymousApiRequestReceivesJson401Envelope`.
* Keep the suite fast: integration tests share one container (`AbstractPostgreSqlIntegrationTest`).

## 9. Configuration and profiles

* `application.yml` holds cross-environment defaults; `application-<profile>.yml` holds the deltas.
* Everything institution-specific is a configuration property (`edam.institution.*`), never a constant.
* `@ConfigurationProperties` + `@Validated` for typed, validated configuration with documented fields.
* No secrets in any file. No `spring.datasource.password` with a real value. No default credentials in
  `prod`.
* Prefer environment variables in deployment; document every variable in the README.

## 10. Logging

See [LOGGING_GUIDELINES.md](LOGGING_GUIDELINES.md). Minimum bar for a PR: no secrets/PII in messages, correct
level, correlation ID available, exceptions logged once with a stack trace where handled.

## 11. Pull request checklist

**Before pushing**

* [ ] `mvn clean verify` passes locally (or CI proves it if the sandbox cannot — say so explicitly).
* [ ] New behaviour has tests; a bug fix has a regression test.
* [ ] No secrets, no credentials, no sensitive logging.
* [ ] Migrations are additive, reversible in an operational sense, and tested on a clean database.
* [ ] Documentation updated (`README`, relevant `docs/*.md`, ADR for a decision).
* [ ] No new dependency without a stated reason and licence check.
* [ ] Package boundaries respected (no repository imported across modules, no cycle).
* [ ] Error codes stable; no renamed enum constant that clients may depend on.

**Pull request description**

Summary · architecture decisions (link the ADR) · packages/files introduced · dependencies added and why ·
migrations · tests executed and results · known limitations · open decisions · screenshots for UI changes.

**Review and merge**

* Branch protection requires a human review; **the author does not merge their own PR**.
* CI must be green, including the packaged-WAR smoke test that renders the JSPs.
* Squash-merge keeps history readable; the PR description is the durable record.

## 12. Definition of done

A change is done when it is implemented, tested, documented, verified in the packaged WAR when it touches
the view layer, free of secrets and sensitive logging, and consistent with the BRD (or explicitly recorded
as an open decision).
