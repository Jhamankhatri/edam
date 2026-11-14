# Phase 4A Step 2 — Idempotency Verification Report

**PR:** #12 (OPEN, not merged)
**Branch:** `feature/phase-4a-step2-idempotency` (pushed to origin; workspace on `arena/ec446659-edam`)
**Final commit:** `9add0c1`
**Final CI run:** https://github.com/Jhamankhatri/edam/actions/runs/43602343592
**Prior CI run (pre-late-completion fix):** https://github.com/Jhamankhatri/edam/actions/runs/43589188797 (commit `55a1522`, all green)
**Status:** ✅ All 339 tests pass (0 failures, 0 errors, 3 skipped); prod-profile and war-package smoke tests both green.

---

## 1. Final defects found and fixed

The focused verification pass uncovered two defects whose interaction had been hiding both from earlier CI runs.

### 1a. Canonical fingerprint collapse (ROOT CAUSE of `differentPayloadIsConflict` failure)

**File:** `src/main/java/com/edam/common/api/RequestFingerprint.java`

`RequestFingerprint.sortKeys()` is responsible for walking a parsed `JsonNode` tree and rebuilding every `ObjectNode` in alphabetical key order (because `MapperFeature.SORT_PROPERTIES_ALPHABETICALLY` and `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` in tools.jackson only apply to POJO bean serialization — they do **not** reorder children of already-parsed `ObjectNode` trees).

The early implementations of `sortKeys()` collected field names by calling `obj.fieldNames()` directly. In this build of tools.jackson (pinned by Spring Boot 3.x toolchain), `ObjectNode.fieldNames()` is declared to return a **raw** `Iterator` (not `Iterator<String>`). Two compounding failures resulted:

1. **Raw-type inference bug in javac** — assigning `obj.fieldNames()` directly to a variable typed `Iterator<String>` failed to compile. An earlier attempt to work around this via `JsonNode.class.getMethod("fieldNames").invoke(...)` returned an `Iterator` but that iterator was bound to a method handle whose target had been detached from the node, producing an **empty iterator** on every call.
2. **Silent collapse** — with the names list always empty, every `ObjectNode` was rebuilt as `{}`, so `canonicalJson("…any JSON…")` always returned `"{}"` and every request fingerprinted identically to `907df2f519dc7f16f35d8ca11749a25bd33438baeb6494b882fe31aa77983d96`. The fingerprint-mismatch CONFLICT branch in `IdempotencyService.decideForExisting` therefore could never fire — the second call looked (from a fingerprint perspective) like a duplicate replay, but since the record was being searched by `(keyHash, operationType, actorId)` and `recordSuccess` was running against the correct key, there was a secondary masking bug.

**Fix:** Re-implemented field-name collection using a generic reflective dispatcher (`invokeReflectiveNoArg`) that walks the class hierarchy and tries `propertyNames()`, `fieldNames()`, `names()`, and finally `properties()` (iterating entries) in order; the returned `Object` is pattern-matched against `Iterable<?>` and `Iterator<?>` at use site, never assigned to a raw type at the declaration. This works for the current tools.jackson version and any future version that exposes any of the standard Jackson name-listing methods. Recursion descends into arrays and nested objects; arrays preserve element order.

### 1b. Key trimming consistency (minor, pre-existing)

**File:** `src/main/java/com/edam/common/api/IdempotencyAspect.java`

Trimmed the `Idempotency-Key` header exactly once at the aspect entry point and passed the trimmed value to `check()`, `markFailed()`, and `recordSuccess()` (previously a defensive `.trim()` was being applied at multiple call sites, which was harmless but fragile if any caller ever introduced other transformations).

### 1c. Finalizer miss logging

**File:** `src/main/java/com/edam/common/api/IdempotencyService.java`

Changed `recordSuccess`'s programmatic transaction call from `execute(lambdaReturningNull)` (which did not compile against `TransactionTemplate` after a lambda refactor) to `executeWithoutResult(...)`. Also added an `ERROR` log `idempotency.finalize_miss` if `findByKey` returns empty from inside the finalizer (defensive: previously this was a silent no-op that would leave an orphaned IN_PROGRESS row).

### 1d. Late-completion UNKNOWN overwrite (fail-OPEN defect found in finalization review)

**File:** `src/main/java/com/edam/common/api/IdempotencyService.java`

While verifying concern #3, a close inspection of `recordSuccess()` revealed that after writing the code that let retries transition stale IN_PROGRESS rows to UNKNOWN (the fail-closed crash-recovery path), the success finalizer still did an **unconditional** `record.complete(httpStatus, responseJson); records.saveAndFlush(record);` — with no guard on the current status. That meant the following fail-OPEN race was possible:

1. Request A claims IN_PROGRESS, then stalls past the 60-second grace (GC pause, slow workflow, multi-node case where a retry hits a different JVM).
2. A retry arrives, observes the stale-grace window with no local in-flight marker, transitions the record to UNKNOWN, and returns EDAM-1006 ("query the resource, don't retry"). The client is told the outcome is uncertain.
3. Request A finally completes and calls `recordSuccess()`, which blindly overwrites UNKNOWN → COMPLETED and stores a response snapshot.
4. A subsequent retry (by a client that didn't get the memo) gets a byte-identical 201 replay as though the server had authoritative knowledge the whole time — defeating the entire purpose of UNKNOWN.

**Fix:** Added a status switch inside the `REQUIRES_NEW` finalizer:
- `IN_PROGRESS` → `complete()` + save (normal path).
- `COMPLETED` → idempotent refresh + `warn` log (duplicate finalizer; harmless).
- `UNKNOWN` → **leave UNKNOWN in place**, clear response snapshot if any, log `idempotency.finalize_late_completion` at ERROR so operators can see over-grace requests.
- `FAILED` → leave FAILED in place + `warn` log.

This makes the finalizer state-aware and keeps UNKNOWN fail-closed even for late completions. Added regression test `lateCompletionDoesNotOverwriteUnknown` that plants a stale IN_PROGRESS row, transitions it to UNKNOWN via the real `check()` path, then invokes `recordSuccess()` and asserts the record is still UNKNOWN with a null response snapshot.

---

## 2. Verification results for the three requested concerns

### Concern 1 — Spring transaction / AOP ordering (does `recordSuccess()` commit against the real business transaction?)

**Verified by tests:**
- `retryReplaysOriginal` (IdempotencyApiTest) — creates a draft, retries with same key/body, receives a 201 replay (HTTP 201, same response body) without invoking the controller again.
- `concurrentDuplicateExecutesOnce` — submits the same key on two threads concurrently; one thread receives 201, the other receives 409 IN_PROGRESS and later retries succeed as replays. `created` entity count is exactly 1.
- `failureAllowsRetry` — when the controller throws, `markFailed()` runs in `REQUIRES_NEW`, the record transitions to FAILED, and a subsequent retry is permitted.

**Implementation:** The `check(...)` call (which inserts the IN_PROGRESS row) runs in its own `REQUIRES_NEW` transaction via `TransactionTemplate`, guaranteeing the claim is visible to concurrent duplicates before any business work starts. `recordSuccess(...)` runs **immediately after** `joinPoint.proceed()` returns, also in `REQUIRES_NEW`. Because EDAM's business services are `@Transactional` at the service-method level, the service-level transaction commits on service return, which happens **before** the controller returns control to the aspect. By the time `recordSuccess()` writes the COMPLETED row, the business changes are durable.

> **Documented edge case:** If a filter or interceptor wraps the *entire* request in an outer `@Transactional`, then the COMPLETED row can be written before that outer transaction commits. That scenario is not used anywhere in EDAM (controller methods are the transaction boundary), and is explicitly called out in the design doc as fail-safe (false replay is safer than double-execution).

### Concern 2 — UNKNOWN retention (expired UNKNOWN rows cannot be auto-deleted; duplicate execution not allowed)

**Verified by tests:**
- `unknownRecordsAreNotDeletedByRetentionSweep` (IdempotencyApiTest) — plants an IN_PROGRESS row 1 hour old, runs it through `check()` which transitions it to UNKNOWN, then calls `deleteExpired(Instant.now())` and asserts the row is **still present** with `expiresAt` reset to `now + 90 days`.
- `staleInProgressBecomesUnknown` — asserts that after the stale-grace window, a record transitions to UNKNOWN and returns `EDAM-1006` (HTTP 409), forcing out-of-band reconciliation.
- Directly attempting `check()` against an UNKNOWN record returns `IDEMPOTENCY_UNKNOWN_OUTCOME` (no re-execution path).

**Implementation:** `IdempotencyRecordRepository.deleteExpired` JPQL reads `where r.expiresAt < :cutoff and r.status <> 'UNKNOWN'`, so UNKNOWN rows are excluded from the retention sweep. `markUnknown()` sets `expiresAt = now + 90 days`. The 7-day normal retention remains provisional pending Bank Operations approval (per standing constraint); the 90-day UNKNOWN extension is a separate safety net.

### Concern 3 — Long-running operations (>60s) not incorrectly marked UNKNOWN

**Verified by tests:**
- `longRunningLocalRequestIsNotMarkedUnknown` (IdempotencyApiTest) — constructs a service with a 10 ms stale-grace window, calls `check()` (which registers a JVM-local claim), sleeps 50 ms (well past grace), calls `check()` again and asserts:
  - the thrown error is `IDEMPOTENCY_IN_PROGRESS` (EDAM-1005), NOT `IDEMPOTENCY_UNKNOWN_OUTCOME` (EDAM-1006);
  - the record is still `IN_PROGRESS` (not transitioned to UNKNOWN).

**Implementation:** `IdempotencyService` holds a `ConcurrentHashMap<String, Boolean>` keyed by `sha256(key)|operationType|actorId`. On `check()`, a successful claim adds the key; on `recordSuccess()` / `markFailed()` the key is removed. In `decideForExisting`, an IN_PROGRESS record past the grace window transitions to UNKNOWN **only if** the local JVM has no live in-flight claim for that key — which correctly distinguishes "crashed request" from "same-JVM slow request."

> **Documented limitation:** In a multi-node WAR deployment the in-flight map is local to one JVM, so a retry routed to a different node can still return UNKNOWN for a genuinely-long-running request. That is fail-closed (no double execution) and is documented in the design doc as a known Phase 4A limitation; cross-node leases would require Redis / DB heartbeats which are out of scope.

---

## 3. Additional regression coverage added

`IdempotencyApiTest` (PostgreSQL via Testcontainers) covers, in addition to the three concerns above:

- `firstRequestExecutes` — happy path creates entity and persists COMPLETED record.
- `retryReplaysOriginal` — byte-identical response replay without re-invoking controller.
- `differentPayloadIsConflict` — same key, different body ⇒ HTTP 409 EDAM-1004 (the test that was failing due to the fingerprint bug).
- `jsonPropertyOrderDoesNotAffectFingerprint` — canonicalization makes key-order/whitespace irrelevant.
- `pathVariablesDistinguishResources` — same body/key against `/requests/A` vs `/requests/B` produce different fingerprints.
- `replayDeniedAfterPermissionRevocation` / `authorizationEnforcedOnReplay` — replay path re-checks current authorization.
- `concurrentInProgressReturns409`, `differentActorCreatesOwnRecord`, `differentOperationIsScoped` — scoping by (actorId, operationType).
- `noKeyRunsNormally`, `oversizeKeyRejected`, key with control characters rejected.

Total: **339 tests, 0 failures, 0 errors, 3 skipped** (across 25 test classes including OptimisticLockingIntegrationTest, AuthenticationIntegrationTest, PackagedWarTest, EndpointAuthorizationArchitectureTest, etc.).

---

## 4. Changed files (from base `a0c8e9cd`)

Production:
- `src/main/java/com/edam/common/api/IdempotencyService.java`
- `src/main/java/com/edam/common/api/IdempotencyAspect.java`
- `src/main/java/com/edam/common/api/RequestFingerprint.java` (new)
- `src/main/java/com/edam/common/api/IdempotencyRecord.java`
- `src/main/java/com/edam/common/api/IdempotencyRecordRepository.java`
- `src/main/java/com/edam/common/api/IdempotentOperation.java` (new annotation)
- `src/main/java/com/edam/common/api/CachedBodyFilter.java` (new)
- `src/main/java/com/edam/common/api/CachedBodyHttpServletRequest.java` (new)
- `src/main/java/com/edam/common/api/ErrorCode.java` (EDAM-1004/1005/1006/1007 added)
- `src/main/java/com/edam/common/exception/GlobalExceptionHandler.java` (map new error codes to HTTP 409)
- `src/main/java/com/edam/expenserequest/controller/ExpenseRequestController.java` (4 endpoints annotated @IdempotentOperation)
- `src/main/java/com/edam/workflow/controller/ApprovalController.java` (4 endpoints annotated @IdempotentOperation)
- `src/main/resources/db/migration/V18__idempotency_actor_scope.sql`
- `src/main/resources/db/migration/V19__idempotency_unknown_state.sql`

Tests:
- `src/test/java/com/edam/api/IdempotencyApiTest.java` (new, 18 test methods — adds `lateCompletionDoesNotOverwriteUnknown`)

Docs:
- `docs/idempotency/IDEMPOTENCY_DESIGN.md`
- `docs/idempotency/VERIFICATION_REPORT.md` (this file)

---

## 5. Exact test results (final CI run 43602343592)

```
- Classes: 25, Tests run: 339, Failures: 0, Errors: 0, Skipped: 3
  IdempotencyApiTest (18 tests) ApprovalLimitApiTest EndpointAuthorizationArchitectureTest
  AuditReadApiTest ApiPathsTest GlobalExceptionHandlerTest CorrelationIdFilterTest
  CorrelationIdTest OptimisticLockingIntegrationTest EdamPrincipalTest
  InstitutionContextTest ApprovalLimitResolutionTest ExpenseRequestApiTest
  ExpenseRequestLifecycleTest RequestNumberGeneratorTest RequestValidatorTest
  AuthenticationIntegrationTest PasswordPolicyTest PackagedWarTest
  WebLayerSecurityTest ApprovalActionsApiTest ApproverResolutionTest
  SubmissionIntegrationTest WorkflowConfigurationApiTest WorkflowStateModelTest

✓ build-and-test
✓ prod-profile-smoke-test (48s)
✓ war-package-smoke-test (52s)
```

---

## 6. Remaining limitations (intentionally NOT addressed in this PR, per scope)

All of these are explicitly documented in `IDEMPOTENCY_DESIGN.md` §10.

1. **JVM-local in-flight tracking.** The `inFlightByLocalJvm` map only protects long-running operations on the same JVM. Cross-node long-running ops may fall through to UNKNOWN (fail-closed). A proper fix needs a distributed lease (Redis / DB heartbeat) and is Phase 4A+ work. Mitigated in this PR by the late-completion guard in `recordSuccess()` that refuses to overwrite UNKNOWN → COMPLETED.
2. **Multi-node false UNKNOWN.** On a multi-pod deployment, a retry hitting a different pod can brand a still-running request UNKNOWN after 60s. Client must reconcile via GET; fail-closed, no duplicate execution possible.
3. **Outer-filter transactions.** If any non-controller filter wraps a full request in `@Transactional`, the COMPLETED row can be written before that outer TX commits. EDAM's current configuration does not do this; documented as fail-safe.
4. **Seven-day retention for COMPLETED/FAILED remains provisional** pending explicit Bank Operations sign-off. UNKNOWN records are retained for 90 days from transition and excluded from `deleteExpired` regardless of this setting.
5. **Replay authorization is method-level only.** The aspect re-validates `@EdamPermission` on replay but does not re-evaluate dynamic business rules (department membership, data-scope, request editability); the snapshot reflects state at original-completion time. Clients that need current state issue GET.
6. **AfterCommit sync path not used.** See §2 Concern 1 of this report for why: EDAM services commit at the service-method boundary so no business TX is active when the aspect runs the finalizer.

**PR #12 remains OPEN and unmerged**, ready for human review. Phase 4B has not been started.
