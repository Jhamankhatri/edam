# Idempotency Design Note — Phase 4A Step 2

Date: 2026-11-12 (revised 2026-11-14 after review)
Status: Implemented on `feature/phase-4a-step2-idempotency` (PR #12)

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
4. When the same key is replayed with the **same request fingerprint** (see §4) and the
   prior execution `COMPLETED`, the server returns the **original response** — same HTTP
   status, same JSON body — without executing the business operation a second time.
5. When the same key is reused with a **different fingerprint**, the server returns
   **HTTP 409 Conflict** with error code `EDAM-1004 IDEMPOTENCY_CONFLICT`. The client has a
   programming bug; reusing a key with different payloads is never a safe retry.
6. When a request arrives while a prior request with the same key+fingerprint is still in
   flight (state `IN_PROGRESS`, within the grace window), the server returns **HTTP 409
   Conflict** with `EDAM-1005 IDEMPOTENCY_IN_PROGRESS`. The client should retry with
   backoff.
7. When a record is stale (older than the grace window — see §5 "Crash recovery"), the
   server transitions the record to `UNKNOWN` and returns **HTTP 409 Conflict** with
   `EDAM-1006 IDEMPOTENCY_UNKNOWN_OUTCOME`. The client must query the resource out-of-band
   to determine outcome; the server will not execute the operation a second time.
8. If the business operation fails with an exception, the idempotency record is marked
   `FAILED`; the next retry with the same key is permitted to run the operation again.
9. Requests that omit the header behave **exactly** as they did before this feature — no
   idempotency record is created, no replay logic runs.

### HTTP status summary

| Situation | Status | Error code |
|---|---|---|
| New key, first execution | 201 / method-default | — |
| Replay of a COMPLETED record | Original status | — |
| Same key, different fingerprint | 409 Conflict | EDAM-1004 |
| Same key, concurrent duplicate in flight | 409 Conflict | EDAM-1005 |
| Same key, unknown outcome after crash window | 409 Conflict | EDAM-1006 |
| Oversize / malformed key | 400 Bad Request | EDAM-1002 |
| Permission denied on replay | 403 Forbidden | EDAM-2005 |
| Business failure during first execution | Method-specific 4xx/5xx | Method-specific |

All idempotency conflict states use **HTTP 409 Conflict** per RFC 7231 §6.5.8 ("The request
could not be completed due to a conflict with the current state of the target resource").
We deliberately do NOT use 412 Precondition Failed: EDAM-3003 `PRECONDITION_FAILED` is
reserved for optimistic-lock / `If-Match` mismatches (which also map to 409 for backwards
compatibility with existing clients).

### Open decision
- **Retention period**: 7 days (`IdempotencyService.RETENTION`). Bank ops has not specified a
  formal retention window; this is flagged for sign-off. Changing it is a one-line constant
  plus the scheduled job cadence.
- **Stale grace window**: 60 seconds (`IdempotencyService.STALE_IN_PROGRESS_GRACE_DEFAULT`).
  Configurable via constructor overload (not yet exposed as a configuration property; can be
  added via `@ConfigurationProperties` without a code change to the service itself).

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
| ApprovalController | `POST /api/v1/tasks/{taskId}/approve` | `POST /api/v1/approvals/tasks/{taskId}/approve` | Moves workflow; double-approve corrupts state. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/reject` | `POST /api/v1/tasks/{taskId}/reject` | Same. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/revert` | `POST /api/v1/tasks/{taskId}/revert` | Same. |
| ApprovalController | `POST /api/v1/tasks/{taskId}/forward` | `POST /api/v1/tasks/{taskId}/forward` | Same. |

GET/DELETE operations are naturally idempotent or read-only and are deliberately excluded.

---

## 3. Database schema

- **V11** (pre-existing): created `idempotency_records` with UNIQUE `(key, op)`,
  `processing_status IN (IN_PROGRESS, COMPLETED, FAILED)`, nullable `response_snapshot`,
  and an expiry index.
- **V18**: adds `actor_id UUID NOT NULL`, drops V11 unique constraint, adds new UNIQUE
  `(idempotency_key, operation_type, actor_id)`, widens `operation_type` VARCHAR(100→120),
  backfills any existing rows with the nil UUID, and uses `response_snapshot JSONB` to store
  the serialised response body for replay.
- **V19**: adds a fourth state `UNKNOWN` to the processing_status CHECK constraint (see §5
  "Crash recovery").

The unique constraint is the **only** concurrency-safety guarantee we rely on. Every other
layer (aspect, service) is a fast path; the constraint is what makes two concurrent
identical requests impossible to both pass `Proceed`.

### Record lifecycle

```
                 ┌──────────────┐
   check() --->  │  (no row)    │  -- INSERT IN_PROGRESS -->  Proceed
                 └──────────────┘                                  │
                                                                   ▼
                             ┌──────────────┐    afterCommit      ┌───────────┐
                             │ IN_PROGRESS  │ -----------------> │ COMPLETED │ --> Replay
                             └──────────────┘                    └───────────┘
                                       │                           ^
 on exception (REQUIRES_NEW markFailed) │                           │ (afterCommit finalizer
                                       ▼                           │  may not run after crash)
                                    ┌────────┐                      │
                                    │ FAILED │ -- clear+retry ------┘
                                    └────────┘
                                       │
                        stale (>grace) │
                                       ▼
                                   ┌─────────┐  (fail closed; never auto-cleared)
                                   │ UNKNOWN │ -- 409 EDAM-1006 "query the resource"
                                   └─────────┘
```

### State semantics
- **IN_PROGRESS**: The business operation is (believed to be) executing. Concurrent
  duplicates within the grace window get 409 EDAM-1005.
- **COMPLETED**: The business transaction committed and the response snapshot was written.
  Retries replay the stored response without invoking the controller.
- **FAILED**: The business operation threw; the outer TX rolled back. On the next retry the
  FAILED row is deleted and a new IN_PROGRESS row is inserted, so the client can reuse the
  same key after correcting input.
- **UNKNOWN**: The record is past the grace window and the server cannot prove whether the
  business operation committed. The row is **not** deleted and the operation will not be
  executed again. The client receives 409 EDAM-1006 and must query the resource directly.

---

## 4. Request fingerprint

Two retries of "the same logical request" are identified by a fingerprint, not by the key
alone. The key is the client's declaration of intent; the fingerprint is the server's
defence against a buggy client that reuses a key with a different payload.

The fingerprint is `SHA-256` over three components separated by `\0` bytes:

1. **Request line**: `METHOD + " " + URI` (e.g. `POST /api/v1/expense-requests`).
2. **Path variables**, sorted alphabetically by name and serialised as `key=value&` pairs,
   so `/requests/A` and `/requests/B` hash differently even with identical bodies and
   operation ids.
3. **Body**, canonicalised by a dedicated Jackson mapper configured with
   `SORT_PROPERTIES_ALPHABETICALLY` and `ORDER_MAP_ENTRIES_BY_KEYS`, plus a recursive
   `sortKeys` walk that reorders every nested ObjectNode's fields into sorted order. Array
   ordering is preserved (it carries meaning); nulls and primitives are left as-is. The
   body is parsed into a JsonNode tree, the tree is recursively sorted, and the sorted tree
   is re-serialised.

Using a dedicated canonical mapper — not the application's shared JsonMapper — ensures
that global mapper features (NON_NULL inclusion, property naming strategies, custom
serializers) cannot silently change the canonical byte sequence across deployments.

The fingerprint intentionally does **not** include query parameters, headers other than
the idempotency key, or the `Idempotency-Key` value itself. Those do not change what the
operation does.

---

## 5. Crash recovery (the hard part)

### The scenario
1. Idempotency claim INSERT commits as IN_PROGRESS (REQUIRES_NEW, so visible immediately).
2. Controller invokes business service, which opens its own `@Transactional`.
3. Business transaction commits successfully; Spring runs `afterCommit` synchronizations.
4. **The JVM crashes (OOM kill, SIGKILL, power loss, node failure) before the
   `recordSuccess` afterCommit callback persists COMPLETED.**
5. Client retries with the same key.

### What must NOT happen
- The retry must NOT execute the business operation a second time (e.g. create a second
  request, double-approve, double-charge).
- The retry must NOT fabricate a response (we have no snapshot to replay).
- The server must NOT silently eat the retry and return success for an operation that may
  never have happened.

### What we do
The IN_PROGRESS record has a `created_at` timestamp. When a retry arrives for an
IN_PROGRESS record older than `STALE_IN_PROGRESS_GRACE_DEFAULT` (60 seconds):

1. The service atomically marks the record `UNKNOWN` and flushes the update within its own
   REQUIRES_NEW transaction.
2. The retry receives **409 Conflict / EDAM-1006** with a message directing the client to
   query the resource directly.
3. Subsequent retries against the same (now-UNKNOWN) record continue to receive 409 until
   an operator or an out-of-band reconciliation clears it. The record is **never
   automatically deleted**, and the operation is **never re-executed** under the same key.

This is **fail-closed at-most-once** semantics for the crash window, not exactly-once.

### Why this is the right choice for a PR of this scope
A perfectly reliable exactly-once guarantee would require the business service to persist a
durable operation outcome identifier **inside the business transaction itself** (e.g. a
`request_idempotency_key_id` column on `expense_requests`, populated by the service as part
of the same TX that creates the request). That would require invasive changes to every
idempotent operation's service method, entities, and queries, and is outside the scope of
Step 2. The UNKNOWN-state approach:

- **Prevents duplicate execution** (the safety property we care most about).
- **Signals uncertainty honestly** to the client via a distinct error code.
- **Reconciles safely**: an operator (or the client via GET) can determine whether the
  operation committed and, if necessary, manually clean up the UNKNOWN record.
- **Is simple enough to reason about**: there is no timer-based deletion of unknown rows,
  no background resurrection job, no silent "best guess."

The 7-day retention sweep (existing `deleteExpired` scheduled job) does eventually purge
records past `expires_at`, which includes UNKNOWN rows that have aged out; clients that
retry beyond the retention window create a fresh claim. In practice the crash window is
measured in milliseconds (the time between the business TX commit and the afterCommit
callback firing), so the UNKNOWN state will rarely be encountered in production.

### Active-operation protection
While a request is executing (IN_PROGRESS within the grace window), retries get 409
EDAM-1005 "retry after a short delay" and never proceed. The unique constraint on
`(idempotency_key, operation_type, actor_id)` provides an additional backstop: a retry
that somehow bypassed the IN_PROGRESS check would hit a `DataIntegrityViolationException`
on the INSERT and be converted to the same 409 EDAM-1005 response.

---

## 6. HTTP integration

The feature is wired with three cooperating components:

### 6.1 `CachedBodyFilter`
Registered explicitly via a `FilterRegistrationBean` at `HIGHEST_PRECEDENCE + 20` (after
the CorrelationIdFilter at `HIGHEST_PRECEDENCE`, before Spring Security). The filter wraps
POST/PUT/PATCH requests in a `CachedBodyHttpServletRequest` that eagerly buffers the body
on construction so it can be read twice — once by the idempotency aspect for
fingerprinting, then by Spring MVC for argument binding. Spring's built-in
`ContentCachingRequestWrapper` only caches after the first read and is unusable for
pre-controller fingerprinting, which is why we have a custom wrapper.

`RequestFingerprint.readBody` includes a defensive fallback that reads the raw input stream
directly if the request was not wrapped; this is defence-in-depth (e.g. if a future filter
reordering bypasses CachedBodyFilter) and prevents the dangerous case of two different
bodies both hashing to an empty-string fingerprint.

### 6.2 `RequestFingerprint`
Pure function: takes the JsonMapper, HttpServletRequest, and a `Map<String,String>` of
decoded path variables, returns a hex-encoded SHA-256 string. Uses the dedicated canonical
mapper described in §4.

### 6.3 `IdempotencyAspect` (@Around @annotation(IdempotentOperation))
The aspect is the integration seam — it runs around the controller method, which is
exactly where we want the idempotency record to bracket the business operation.

Pseudocode:

```
@Around
Object around(ProceedingJoinPoint pjp) {
  key = request.getHeader("Idempotency-Key");
  if (blank(key)) return pjp.proceed();          // opt-out for legacy clients
  principal = institutionContext.principal();
  fingerprint = RequestFingerprint.forRequest(mapper, request, pathVars);
  decision = idempotency.check(op, principal.userId, key, fingerprint);
                                 // ^ programmatic REQUIRES_NEW via TransactionTemplate
  return switch (decision) {
    case Replay r -> {
        enforceReplayAuthorization(pjp, principal);   // re-check @EdamPermission
        write r.status + r.responseJson to response;
        yield null;                                   // bypass controller
    }
    case Proceed -> {
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            idempotency.markFailed(...);              // REQUIRES_NEW, before rethrow
            throw t;
        }
        idempotency.recordSuccess(op, actor, key,     // afterCommit synchronization
                                  status, snapshot);
        yield result;
    }
  };
}
```

### 6.4 Authorization on replay
The replay path bypasses the controller method entirely, which means it also bypasses the
`@RequirePermission` (actually `@EdamPermission`) check that normally runs via
`RequirePermissionAspect`. We address this by reading the `@EdamPermission` annotation
directly from the controller `Method` in `enforceReplayAuthorization`, and calling
`principal.hasPermission(permission.value())` before serving the replay. If the annotation
is missing (deny-by-default) or the principal no longer holds the permission, the aspect
throws `PERMISSION_DENIED` (HTTP 403) rather than returning the stored response.

Actor-scoped key lookup is the first gate (a different actor never finds the COMPLETED
row); the permission check is the second gate (the same actor whose permissions have been
revoked since the original call cannot retrieve the stored response). Together these
preserve the security properties of the original call.

---

## 7. Transaction boundaries

The key correctness defect in naive idempotency implementations is a COMPLETED record that
outlasts a rolled-back business transaction, producing false replays. We address this with
three rules:

1. **Claim (`check()`) runs in a programmatic `REQUIRES_NEW` transaction** via
   `TransactionTemplate`, so the IN_PROGRESS row commits before the business service's
   `@Transactional` begins. Using programmatic transactions (instead of `@Transactional`
   on the service method) avoids the self-invocation trap where calling an `@Transactional`
   method from another method on the same bean silently joins the outer transaction rather
   than opening a new one.
2. **Success finalisation runs in an `afterCommit` synchronization.**
   `recordSuccess` registers a `TransactionSynchronization.afterCommit` callback that
   writes COMPLETED + responseSnapshot in a new short transaction. If the business TX
   rolls back, `afterCommit` never fires; if the JVM crashes before the callback runs,
   the row is left IN_PROGRESS (handled by the crash-recovery path in §5). If the
   finalizer itself throws, the error is logged and the row stays IN_PROGRESS (so the
   next retry hits UNKNOWN rather than double-executing).
3. **Failure (`markFailed`) uses `@Transactional(REQUIRES_NEW)`** and is invoked from the
   aspect's `catch` block before re-throwing. The outer business transaction is guaranteed
   to roll back, and the FAILED row (in its own transaction) correctly allows a retry.

---

## 8. Concurrency

Between `findByKey(...)` returning empty and `saveAndFlush(new IN_PROGRESS)` there is a
race. Two concurrent identical requests could both see "no row" and both attempt to
insert. The unique constraint is the arbiter: exactly one INSERT wins; the loser hits
`DataIntegrityViolationException`, which the service catches and then re-reads; if the
winner's IN_PROGRESS row is visible it returns 409, otherwise it surfaces
IDEMPOTENCY_IN_PROGRESS.

This is the same pattern used by Stripe's idempotency implementation and is the simplest
correct design; optimistic locking with `@Version` does not protect the initial insert,
and `SELECT … FOR UPDATE` cannot lock a row that does not exist yet.

---

## 9. Client guidance

- Generate the `Idempotency-Key` as a UUID or sufficiently random token (≥32 bits of
  entropy) on the client. Do NOT derive it from user-visible fields.
- Send the **same** key when retrying after a network error, a 5xx response, a timeout, or
  a 409 EDAM-1005 (in-progress). Use exponential backoff starting at ~100 ms.
- Generate a **new** key for each new logical operation — one key per user action.
- When you receive 409 EDAM-1004 (conflicting payload), do NOT retry; fix your code —
  you reused a key with different content.
- When you receive 409 EDAM-1006 (unknown outcome), do NOT automatically retry with the
  same key; issue a GET against the resource to determine whether it was created. If you
  are sure it was not, generate a new key.

---

## 10. What this implementation does and does NOT guarantee

**Guarantees:**
- Retries with the same key+fingerprint after a successful response return the original
  response and do not double-execute.
- Concurrent identical requests execute the business operation at most once.
- A request whose business transaction rolls back never leaves a COMPLETED record.
- Cross-actor key reuse never leaks another user's response.
- Permission revocation before replay is enforced (fail-closed).
- Fingerprinting is order-insensitive for JSON keys, whitespace, and insignificant
  formatting.

**Non-guarantees / known limitations:**
- **Not exactly-once across crashes.** A JVM crash between the business TX commit and the
  afterCommit finaliser leaves the record IN_PROGRESS; after the grace window, retries get
  409 EDAM-1006 and must reconcile out-of-band (§5). The server never double-executes,
  but it cannot in this state prove the original outcome either.
- **Not applied to every unsafe HTTP method.** Only the eight endpoints listed in §2 are
  annotated; adding idempotency to a new endpoint is a one-line `@IdempotentOperation`
  plus an operation string.
- **Replay returns the stored response bytes verbatim.** If a resource is later mutated,
  a replay shows the response as of the original call. Clients that want current state
  must issue a GET.
- **No protection against a malicious client that sends a key without sending the body
  twice.** The server computes fingerprints from what it receives; a client that lies
  about the body still executes once.
- **7-day retention is provisional** pending bank ops sign-off (§1).

---

## 11. Configuration

| Property | Default | Configurable | Notes |
|---|---|---|---|
| Retention | 7 days | Yes (change `IdempotencyService.RETENTION` constant; scheduled job cadence may need adjustment) | Expired records purged by existing `deleteExpired` job |
| Stale grace | 60 seconds | Yes (constructor overload; not yet a `@ConfigurationProperties` knob) | See §5 crash recovery |
| Max key length | 200 chars | Yes (`IdempotencyService.MAX_KEY_LENGTH`) | Safety limit |

---

## 12. Tests

`IdempotencyApiTest` (PostgreSQL Testcontainers, `@SpringBootTest`):

1. First request with a new key → 201, one COMPLETED record.
2. Identical retry → 201, same request id, no new row created (byte-identical replay).
3. Same key + different payload → 409 EDAM-1004.
4. Cross-actor same-key → independent records (no cross-actor replay).
5. Cross-operation same-key → independent (submit succeeds after create).
6. Eight concurrent identical requests → exactly one request row in the DB; other threads
   see either 201-replay or 409.
7. No header → legacy behaviour (two calls → two rows).
8. Failure (negative amount) → same key with valid body proceeds (FAILED state cleared).
9. Oversize key (>200 chars) → 400.
10. JSON property-order insensitivity: two semantically identical bodies with different key
    orderings produce identical canonical strings and fingerprints.
11. Path variables distinguish resources: same body against two different `{id}` values
    produces different fingerprints.
12. Permission revocation before replay → 403 EDAM-2005.
13. Stale IN_PROGRESS (age > grace window) → UNKNOWN, returns 409 EDAM-1006; subsequent
    retries continue to return 409 (no double-execution).
14. Fresh IN_PROGRESS → 409 EDAM-1005.

Tests use real controllers, real repositories, and a real PostgreSQL instance through
Testcontainers; they do not mock the idempotency service.

---

## 13. Changed files (PR #12)

- `src/main/resources/db/migration/V18__idempotency_actor_scope.sql`
- `src/main/resources/db/migration/V19__idempotency_unknown_state.sql`
- `src/main/java/com/edam/common/api/IdempotencyRecord.java` (entity: actorId, complete(snapshot), markUnknown, UNKNOWN state)
- `src/main/java/com/edam/common/api/IdempotencyRecordRepository.java` (actor-scoped findByKey)
- `src/main/java/com/edam/common/api/IdempotencyService.java` (sealed Decision variants, REQUIRES_NEW claim via TransactionTemplate, afterCommit success finaliser, UNKNOWN recovery, 60-second grace, programmatic config)
- `src/main/java/com/edam/common/api/CachedBodyHttpServletRequest.java` (eager body buffering wrapper)
- `src/main/java/com/edam/common/api/CachedBodyFilter.java` (+ FilterRegistrationBean registration)
- `src/main/java/com/edam/common/api/RequestFingerprint.java` (deterministic method+URI+sorted-path-vars+recursively-sorted-JSON fingerprint)
- `src/main/java/com/edam/common/api/IdempotencyAspect.java` (@Around advice, permission re-check on replay)
- `src/main/java/com/edam/common/api/ErrorCode.java` (EDAM-1005 IDEMPOTENCY_IN_PROGRESS, EDAM-1006 IDEMPOTENCY_UNKNOWN_OUTCOME)
- `src/main/java/com/edam/common/exception/GlobalExceptionHandler.java` (all idempotency codes → 409)
- `src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java` (annotations on create/update/submit/cancel)
- `src/main/java/com/edam/workflow/controller/ApprovalController.java` (annotations on approve/reject/revert/forward)
- `src/test/java/com/edam/api/IdempotencyApiTest.java` (14 tests covering the scenarios above)
- `docs/idempotency/IDEMPOTENCY_DESIGN.md` (this document)
