# Logging Guidelines — SLF4J, Logback & Correlation Tracing

## 1. Objectives

EDAM provides traceable, structured, security-safe operational logs suitable for enterprise financial environments (`NFR-12`, BRD Sec 12.4, 13.1).

---

## 2. Correlation ID & MDC Context

- **`CorrelationIdFilter`** runs at `Ordered.HIGHEST_PRECEDENCE` on every HTTP request:
  - Reads incoming `X-Correlation-ID` if valid (`^[A-Za-z0-9._-]{1,64}$`), otherwise generates a UUID.
  - Propagates `X-Correlation-ID` onto the HTTP response header.
  - Populates SLF4J MDC keys:
    - `correlationId`
    - `requestMethod`
    - `requestPath`
  - Logs a single request-completion summary (`HTTP request completed method=... path=... status=... durationMs=...`) and guarantees `MDC.clear()` in `finally`.
- **`InstitutionContextFilter`** enriches MDC during authenticated requests with:
  - `institutionCode`
  - `actor`
  and removes both in `finally`.

---

## 3. Environment Output Formats (`logback-spring.xml`)

| Active Profile | Format | Target Use |
| --- | --- | --- |
| `default`, `dev`, `local`, `test` | Spring Boot console pattern prefixed with `[corr=%X{correlationId:-none} inst=%X{institutionCode:-none} actor=%X{actor:-anonymous}]` | Local developer debugging & test output |
| `uat`, `prod` | Spring Boot `StructuredLogEncoder` (`ecs` JSON format) | Centralized SIEM / log aggregation pipelines (ELK, OpenSearch, Splunk, Datadog) |

> Note: `LOG_CORRELATION_PATTERN` is declared **before** `<include resource="org/springframework/boot/logging/logback/defaults.xml"/>` so Logback evaluates the pattern accurately at startup.

---

## 4. Mandatory Data-Safety & Anti-Injection Rules

1. **Never log secrets or PII**: Passwords, `Authorization` headers, cookies/session IDs, JDBC credentials, CNIC/NTN/STRN tax identifiers, uploaded file contents, and full HTTP request/response bodies must **never** appear in logs.
2. **No SQL logging in UAT/Prod**: `org.hibernate.SQL` remains at `WARN` (never `DEBUG`/`TRACE` with bind parameters in shared environments).
3. **Always sanitize untrusted values**: Pass user-supplied identifiers, paths, and header strings through `LogSanitizer.sanitize(...)` to strip `\r`, `\n`, `\t`, and control characters, preventing CRLF log forging.
4. **Use `LogSanitizer.maskIfSensitive(key, value)`** whenever logging key-value metadata dynamically.
5. **Log exceptions with context once**: Catch and log unexpected exceptions at global boundaries (`GlobalRestExceptionHandler`, `GlobalMvcExceptionHandler`) with `correlationId` in MDC; avoid duplicate catch-log-rethrow clutter in services.
