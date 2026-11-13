# Idempotency Design Note — Phase 4A Step 2

Date: 2026-11-12
Status: Implemented on `feature/phase-4a-step2-idempotency`

This document specifies how EDAM turns unsafe POST/PUT operations into safe, retryable
ones through client-supplied `Idempotency-Key` headers, and records the reasoning behind
every choice in the implementation. It is written for the developer who has to debug a
replay dispute six months from now, not for the first-time reader.

---

## 1. Contract

1. A client may send the header `Idempotency-Key: <string>` on any operation annotated
   `@IdempotentOperation`.
2. The key is a **client-chosen** string, up to 200 printable ASCII characters (no control
   characters, no leading/trailing whitespace).
3. A given key is scoped to the tuple **(actor, operation, key)**. Two different actors
   reusing the same key string never collide. Two different operations (e.g. `POST /requests`
   vs `POST /requests/{id}/submit`) never collide even if the key string matches.
4. When the same key is replayed with the **same request fingerprint** (see §4), the server
   returns the **original response** — same HTTP status, same JSON body — without executing
   the business operation a second time.
5. When the same key is reused with a **different fingerprint**, the server returns
   `400 Bad Request` with error code `EDAM-1004 IDEMPOTENCY_CONFLICT`. The client has a
   programming bug; reusing a key with different payloads is never a safe retry.
6. When a request arrives while a prior request with the same key is still in flight, the
   server returns `409 Precondition Failed` with `EDAM-3003`. The client should retry with
   backoff.
7. If the business operation fails with an exception, the idempotency record is marked
   `FAILED`; the next retry with the same key is permitted to run the operation again.
8. Requests that omit the header behave **exactly** as they did before this feature — no
   idempotency record is created, no replay logic runs.

### Open decision
Retention period is 7 days (constant `IdempotencyService.RETENTION_DAYS`). Bank ops has not
specified a formal retention window; this is flagged for sign-off. Changing it is a one-line
constant plus the scheduled job cadence.

---

## 2. Endpoint scope

Idempotency is applied **only** to operations where duplicate execution would cause a real
business problem (double-creation of a request, double-approval, double-submit). It is
**not** applied to every POST/PUT/PATCH:

| Controller | Method | Operation id | Why |
|---|---|---|---|
| ExpenseRequestController | `POST /api/v1/expense-requests` | `POST /api/v1/expense-requests` | Double-creates a draft (duplicate request numbers). |
| ExpenseRequestController | `PUT /api/v1/expense-requests/{id}` | `PUT /api/v1/expense-requests/{id}` | Overwrites draft fields; retries must not create orphans. |
| ExpenseRequestController | `POST /api/v1/expense-requests/{id}/submit` | `POST /api/v1/expense-requests/{id}/submit` | Submits to workflow; double-submit violates state machine. |
| ExpenseRequestController | `POST /api/v1/expense-requests/{id}/cancel` | `POST /api/v1/expense-requests/{id}/cancel` | State-machine transition; double-cancel must be idempotent. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/approve` | `POST /api/v1/tasks/{taskId}/approve` | Moves workflow; double-approve corrupts state. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/reject` | `POST /api/v1/tasks/{taskId}/reject` | Same. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/revert` | `POST /api/v1/tasks/{taskId}/revert` | Same. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/forward` | `POST /api/v1/tasks/{taskId}/forward` | Same. |

GET/DELETE operations are naturally idempotent or read-only and are deliberately excluded.

---

## 3. Database schema

V18 migration (`V18__idempotency_actor_scope.sql`) amends the `idempotency_records` table:

- Adds `actor_id UUID NOT NULL` (backfilled to `00000000-0000-0000-0000-000000000000` for
  any legacy rows).
- Drops the V11 unique key `(idempotency_key, operation_type)` and replaces it with
  `(idempotency_key, operation_type, actor_id)` — the enforcement tuple.
- Widens `operation_type` from `VARCHAR(100)` to `VARCHAR(120)` so full path-prefixed ids
  (`POST /api/v1/expense-requests/{id}/submit`) fit with margin.
- Adds `response_snapshot JSONB` to store the serialized response body for replay.

The unique constraint is the **only** concurrency-safety guarantee we rely on. Every other
layer (aspect, service) is a fast path; the constraint is what makes two concurrent
identical requests impossible to both pass `Proceed`.

### Record lifecycle

```
                 ┌──────────────┐
   check() --->  │  (no row)    │  -- INSERT IN_PROGRESS -->  Proceed
                 └──────────────┘                                  │
                                                                   ▼
                             ┌──────────────────┐   on success (after TX commit)
                             │   IN_PROGRESS    │ ------------------------------> COMPLETED
                             └──────────────────┘                                  │
                                       │                                           │
                          on exception │                                           │ replay: return
                                       ▼                                           ▼ snapshot
                                    FAILED                                  201 + same body
                                  (cleared on
                                  next check())
```

---

## 4. Request fingerprint

Two retries of "the same logical request" are identified by a fingerprint, not by the key
alone. The key is the client's declaration of intent; the fingerprint is the server's
defence against a buggy client that reuses a key with a different payload.

The fingerprint is `SHA-256` over three components separated by `\0` bytes:

1. **Request line**: `METHOD + " " + URI` (e.g. `POST /api/v1/expense-requests`).
2. **Path variables**, sorted alphabetically and serialised as `key=value` pairs (so
   `/requests/{id}` with `id=ABC` differs from `/requests/{id}` with `id=DEF`, even with
   the same body).
3. **Body**, canonicalised through Jackson: `mapper.readTree(bytes).toString()` produces a
   stable JSON serialisation regardless of whitespace or object-key order. If the body
   fails to parse as JSON (rare — all our endpoints accept JSON), the raw bytes are hashed.

The fingerprint intentionally does **not** include query parameters, headers other than
the idempotency key, or the `Idempotency-Key` value itself. Those do not change what the
operation does.

---

## 5. HTTP integration

The feature is wired with three cooperating components:

### 5.1 `CachedBodyFilter` (`@Order(HIGHEST_PRECEDENCE + 20)`)

Spring's built-in `ContentCachingRequestWrapper` only caches the body **after** the input
stream is first read, which is useless for pre-controller fingerprinting. A custom
`CachedBodyHttpServletRequest` eagerly reads the entire body into a byte array in its
constructor, exposes it via `getCachedBody()`, and returns a fresh `InputStream` on every
call so downstream handlers (Jackson, Spring Security) can still read it.

The filter wraps only POST/PUT/PATCH requests and runs after `CorrelationIdFilter` (which
populates MDC for logging) but before Spring Security's filter chain (so authentication
happens on the wrapped request without issues).

### 5.2 `RequestFingerprint` helper

Pure function: takes `HttpServletRequest` + Spring MVC's `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`,
returns a hex-encoded SHA-256 string. It is static-method-only and has no Spring
dependencies beyond the servlet API + Jackson `ObjectMapper`.

### 5.3 `IdempotencyAspect` (`@Around @annotation(IdempotentOperation)`)

The aspect is the integration seam — it runs inside Spring's interceptor chain around the
controller method, which is exactly where we want the idempotency record to bracket the
business operation.

Pseudocode:

```
@Around
Object around(ProceedingJoinPoint pjp) {
  key = request.getHeader("Idempotency-Key");
  if (blank(key)) return pjp.proceed();                   // opt-out for legacy clients
  validateKey(key);
  actor = InstitutionContext.principal().orElseThrow();
  op    = annotation.value();
  fp    = RequestFingerprint.compute(request);

  Decision d = idempotency.check(keyHash, op, actor.userId, fp);
  return switch (d) {
    case Replay r -> { write r.status, r.responseJson to response; yield null; }
    case Proceed  -> {
        try {
            Object result = pjp.proceed();
            // Success snapshot is finalized ONLY after the business transaction
            // commits (see §6), so a rollback cannot leave a COMPLETED record.
            return result;
        } catch (Throwable t) {
            idempotency.markFailed(keyHash, op, actor.userId);
            throw t;
        }
    }
  };
}
```

### 5.4 Authorization on replay

The replay path bypasses the controller method entirely, which means it also bypasses the
`@RequirePermission` check that would normally run on that method. Authorization is still
enforced because:

1. The idempotency lookup is **scoped by `actor_id`**. A different user presenting the same
   key will not find the original COMPLETED row; they get a fresh Proceed decision for
   their own scope, and then proceed through their own permission check.
2. The actor id on the IN_PROGRESS/COMPLETED row is compared with the current principal's
   userId before a replay is served; a tampered row is treated as a 409 rather than
   replayed.

This is "fail closed": replay never leaks data across actors.

---

## 6. Transaction boundaries — the hard part

The central defect in naive idempotency implementations is a record that says COMPLETED
while the business transaction actually rolled back (a late constraint violation, an
`@EventListener` failure, an outer `@Transactional` proxy that rolls back after the advice
returns). If we mark the record COMPLETED inside its own `REQUIRES_NEW` transaction, the
record commits before the business transaction does, and a subsequent rollback of the
business TX leaves the idempotency record lying. Subsequent retries then replay a
response for work that never happened.

**Fix**: the success record is committed only from a `TransactionSynchronization.afterCommit`
hook registered during the Proceed path:

```java
// Inside IdempotencyService.recordSuccess(...)
if (TransactionSynchronizationManager.isActualTransactionActive()) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() {
            // Persist COMPLETED + responseSnapshot in a new, short transaction.
            persistCompleted(keyHash, op, actorId, status, responseJson);
        }
    });
} else {
    persistCompleted(...); // controller had no TX (rare); persist immediately.
}
```

The `markFailed` path remains `REQUIRES_NEW`. That is safe: failures are caught inside the
`catch (Throwable)` block of the aspect before re-throw, so the business transaction is
guaranteed to roll back, and the FAILED row (in its own transaction) correctly tells the
next retry "last attempt did not commit, you may try again."

The `IN_PROGRESS` insert is also `REQUIRES_NEW` so the row is visible to concurrent
duplicates immediately, even before the business transaction commits — that is what makes
the unique-constraint guard work (§7).

---

## 7. Concurrency

Between `findByKey(...)` returning empty and `save(new IN_PROGRESS)` there is a race. Two
concurrent identical requests could both see "no row" and both attempt to insert. That is
why the unique constraint `(idempotency_key, operation_type, actor_id)` exists — exactly
one insert wins; the losers hit `DataIntegrityViolationException`, which the service
translates to a short (≈50 ms) spin-retry of `check()`. By then the winner's IN_PROGRESS
row is visible, so the losers get `Decision.DUPLICATE_IN_PROGRESS` → `409 Precondition Failed`.

This is the same pattern used by Stripe's idempotency implementation and is the simplest
correct design; optimistic locking with `@Version` does not protect the initial insert,
and `SELECT … FOR UPDATE` cannot lock a row that does not exist yet.

---

## 8. Client guidance

- Generate the `Idempotency-Key` as a UUID or sufficiently random token (≥32 bits of
  entropy) on the client. Do NOT derive it from user-visible fields (amount, payee) because
  two different requests can legitimately share those.
- Send the **same** key when retrying after a network error, a 5xx response, or a timeout.
- Generate a **new** key for each new logical operation — e.g. each "Submit" button click
  in the UI, not one key per user session.
- When you receive `400 EDAM-1004 IDEMPOTENCY_CONFLICT`, do NOT retry automatically; that
  means your code has a bug (reused a key with a different payload).
- When you receive `409 EDAM-3003 PRECONDITION_FAILED`, retry with exponential backoff
  (starting at 100 ms, up to ~2 seconds).

---

## 9. What this implementation does NOT guarantee

Honest about the edges:

1. **It does not protect against a client that sends the same key from two tabs with
   different bodies and then retries the "losing" body.** That is a client bug; the server
   returns 400 as specified in §1.5.
2. **It does not deduplicate non-idempotency-keyed requests.** Legacy clients that do not
   send the header get the pre-existing behaviour; their retries can still double-create.
   That is intentional — no breaking change.
3. **Replay returns the stored response bytes verbatim.** If a resource is later mutated
   (e.g. an approver forwards the task between the original request and the retry), the
   replay still shows the original response. This matches Stripe/Stripe-like behaviour;
   clients that want current state must issue a GET after the idempotent create.
4. **It does not protect against process crash between inserting IN_PROGRESS and writing
   COMPLETED/FAILED.** Abandoned IN_PROGRESS rows are treated as `409 Precondition Failed`
   until the next request for the same key arrives, at which point the stale IN_PROGRESS
   is cleared if older than a short grace window (configurable; default 60 seconds). In
   practice this is fine because if the server crashed the client will retry on a new TCP
   connection anyway.

---

## 10. Tests

`IdempotencyApiTest` (PostgreSQL Testcontainers, `@SpringBootTest`) exercises the contract
end-to-end:

1. First request with a new key → `201 Created`, one COMPLETED record.
2. Identical retry → `201 Created`, same request id, no new row created.
3. Same key + different payload → `400 Bad Request`, `EDAM-1004`.
4. Same key under a different actor → separate record created (no cross-actor replay).
5. Same key across different operations (`create` then `submit`) → scoped independently,
   both succeed.
6. Eight concurrent identical requests → exactly one request row in the database; other
   threads see either `201` (replay) or `409` (in-progress).
7. No header → normal behaviour (two sequential creates produce two rows).
8. After a failed request (negative amount), same key with valid body → succeeds (FAILED
   state cleared).
9. Key longer than 200 characters → `400 Bad Request`.
10. Authorization marker test documenting that replay never crosses actors (covered
    behaviourally by test 4).

Tests use real controllers, real repositories, and a real PostgreSQL instance through
Testcontainers; they do not mock the idempotency service. A failing test therefore means
the production contract is broken, not a mock expectation.
