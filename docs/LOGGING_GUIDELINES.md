# Logging Guidelines

## 1. Facade and implementation

SLF4J is the only logging API in application code. Logback is the implementation
(`logback-spring.xml`). Spring Framework 7 removed the `spring-jcl` shim and depends on
`commons-logging`, which stays on the classpath (it presents the SLF4J API) — that is deliberate and the
`maven-enforcer-plugin` rule documents it. **Never** add `commons-logging` bridges, `log4j` 1.x or a second
binding; the enforcer fails the build if the legacy log4j artifacts appear.

```java
private static final Logger log = LoggerFactory.getLogger(SomeService.class);
```

No `System.out`, no `printStackTrace()`, no logging frameworks obtained through reflection.

## 2. Correlation IDs

Every request owns a correlation ID:

* `CorrelationIdFilter` runs first (highest precedence), accepts `X-Correlation-Id`, `X-Request-Id` or
  `X-Trace-Id` **only** when the value is a well-formed UUID, otherwise generates one.
* The ID is bound to the MDC key `correlationId` and echoed as the `X-Correlation-Id` response header.
* The log pattern prints it on every line, including framework lines:
  `%d{...} %-5level [%thread] [%X{correlationId:-no-correlation-id}] %logger{36} - %msg%n`
* The same ID is returned in the JSON error envelope (`correlationId`) and rendered on the error page
  ("Reference: …"), so a user's screenshot maps to exact log lines.

**A log line without a correlation ID is a defect** unless it is emitted outside a request (startup,
scheduled work) — those log `[no-correlation-id]` by design.

Never accept an arbitrary inbound value: unsanitized headers are a log-injection and disk-fill vector. The
filter's UUID check is the control (see `CorrelationIdFilterTest`).

## 3. Levels

| Level | Use for | Examples |
|---|---|---|
| `ERROR` | The operation failed and a human/alert must look | unexpected exception, persistence failure, error page rendered for a 5xx |
| `WARN` | Expected but noteworthy; no action today | validation failure, access denied, business rule rejection, sanitized correlation ID, branding fallback |
| `INFO` | State changes worth keeping in production | application started, migration applied, access-log line for successful requests |
| `DEBUG` | Diagnostic detail for a developer | resolved institution, query parameters, branch decisions |
| `TRACE` | Extremely detailed, rarely enabled | framework internals |

Rules of thumb:

* `INFO` must stay low-volume. Per-entity-loop logging belongs at `DEBUG`.
* Do not log an exception **and** its stack trace at multiple layers; log it once where it is handled, at a
  level that matches the outcome. `GlobalExceptionHandler` logs 4xx conditions at `WARN` *without* a stack
  trace (they are expected) and 5xx at `ERROR` *with* the stack trace.
* A caught exception that is deliberately not propagated must still be logged, at `WARN` or higher.
* Do not add `if (log.isDebugEnabled())` around a single string; parameterised logging is already free.
  Use the guard only when building the argument is genuinely expensive.

## 4. What must never be logged

| Forbidden | Why |
|---|---|
| Passwords, password hashes, one-time codes | Credential compromise |
| Authentication tokens, session IDs, `JSESSIONID`, API keys, JWTs | Session/credential compromise |
| Full request or response bodies | Unbounded PII/financial-data exposure |
| Bank identifiers: CNIC, NTN, STRN, IBAN/account numbers, card numbers | Regulated personal/financial data |
| Document contents, file bytes, e-mail bodies | Confidentiality |
| SQL with bound parameter values | Contains business data; log the statement shape instead |
| Stack traces sent to a client | Information disclosure (never in an HTTP response) |
| Query strings in access logs | Search terms and identifiers are personal data |
| Secrets from configuration (`spring.datasource.password`, SMTP credentials) | Credential compromise |

The access log intentionally records **method, path (no query string), status and duration** — not headers,
not parameters, not bodies. Path variables are recorded because they are non-sensitive identifiers and make
the log actionable; a path that embeds sensitive data is a design defect.

When a value must be referenced, log a non-reversible identifier (correlation ID, employee ID, request
reference) or a mask (`****1234`). Never log a "redacted" value built by string-replacing a secret — write
the code so the secret never reaches a log call.

## 5. Structured logging

* `local`/`test` (and any deployment without the `prod` profile): human-readable pattern (see §2).
* `prod`: **ECS JSON**, one object per line, selected by `springProfile name="prod"` in `logback-spring.xml`,
  which configures Boot's `StructuredLogEncoder` with `format=ecs`. The same `log.info("...")` calls become
  JSON with `@timestamp`, `log.level`, `log.logger`, `process.thread.name`, `service.name` and the MDC
  entries (including `correlationId`).

> Why not `logging.structured.format.console`? That property only affects appenders Spring Boot creates
> itself. EDAM declares its own console appender, so the property is silently inert — a trap worth knowing
> before changing this file. The `prod-profile-smoke-test` CI job parses the real log to prove the encoder
> is active, so this claim cannot rot unnoticed.
* Prefer parameterised messages with a stable prefix, `area.event key=value ...`, e.g.
  `http.request method=GET path=/dashboard status=200 durationMs=42`. This is greppable in line mode and
  indexable in JSON mode.
* Do not embed JSON in a message. Emit fields via the message pattern or the MDC.

### Configuring levels

Levels are configured per environment through properties — never by editing `logback-spring.xml` for an
incident:

```yaml
logging:
  level:
    root: INFO
    com.edam: INFO
    org.springframework.security: DEBUG   # temporary, never committed
```

`local` sets `com.edam: DEBUG`; `test` sets `root: WARN` to keep CI output readable.

## 6. Access logging

`RequestLoggingFilter` emits exactly one line per request:

```
http.request method=GET path=/dashboard status=200 durationMs=42
```

* 5xx → `ERROR`, 4xx → `WARN`, everything else → `INFO`.
* Static resources (`/css/`, `/js/`, `/images/`, `/favicon.ico`, `/webjars/`) and health probes are skipped.
* Query strings are dropped; control characters are replaced so a crafted path cannot forge log lines.
* The `finally` block guarantees a line even when the handler throws.

## 7. Tracing an incident

1. Take the correlation ID from the user's error page, the JSON envelope, or the `X-Correlation-Id` response
   header in the browser's network tab.
2. `grep <correlation-id> application.log` (or query the log platform).
3. The access-log line gives method/path/status/duration; adjacent lines give the outcome
   (`request.validation_failed`, `business.rule_violation code=… `, `persistence.failure`), and an `ERROR`
   line carries the stack trace.

This flow is the reason the correlation ID is echoed on **every** response, including successful ones.

## 8. Logging review checklist

* [ ] No credential, token, identifier or body in any message (search the diff for `password`, `token`,
      `Authorization`, `cnic`, `ntn`, `body`).
* [ ] Correct level (expected → `WARN`, unexpected → `ERROR`).
* [ ] Exceptions logged once, with the stack trace, where they are handled.
* [ ] Message uses a stable event prefix and parameterised arguments (no string concatenation).
* [ ] Nothing logged inside a tight loop at `INFO`.
* [ ] Client-facing text contains no internal detail (that is the exception handlers' job, not the log's).
