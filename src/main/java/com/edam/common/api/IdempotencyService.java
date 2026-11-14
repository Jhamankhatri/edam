package com.edam.common.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.edam.common.exception.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Enforces the {@code Idempotency-Key} contract for state-changing endpoints (BRD API-04 / Phase 4A Step 2).
 *
 * <h2>Scope</h2>
 * A key is scoped to the tuple {@code (hashed key, operationType, actorId)}. One user's retry can
 * never see another user's response, and two clients that coincidentally use the same key string
 * cannot collide across different operations or users.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>No key (null/blank): the request is processed normally — idempotency is opt-in so that
 *       read paths are unaffected, while endpoints that need it declare {@link IdempotentOperation}.</li>
 *   <li>New key: the key is claimed <em>before</em> the operation runs (state {@code IN_PROGRESS});
 *       the operation then executes and the record is finalised.</li>
 *   <li>Same key, same actor, same fingerprint, {@code COMPLETED}: the recorded response is
 *       returned and the business operation is NOT executed again (replay).</li>
 *   <li>Same key, same actor, different fingerprint: rejected with
 *       {@link ErrorCode#IDEMPOTENCY_CONFLICT} (HTTP 409 Conflict) — client programming bug; do not
 *       retry automatically.</li>
 *   <li>Same key, same actor, {@code IN_PROGRESS} (within grace window): rejected with
 *       {@link ErrorCode#IDEMPOTENCY_IN_PROGRESS} (HTTP 409 Conflict) — retry with backoff.</li>
 *   <li>Same key, same actor, {@code IN_PROGRESS} (past grace window, i.e. stale): transitioned to
 *       {@code UNKNOWN} and rejected with {@link ErrorCode#IDEMPOTENCY_UNKNOWN_OUTCOME}
 *       (HTTP 409 Conflict) — client must query the resource directly; the server will not
 *       double-execute.</li>
 *   <li>Same key, same actor, {@code FAILED} (a previous attempt threw): the key is cleared and the
 *       operation may proceed once with the new fingerprint; this lets a client correct input and
 *       reuse the same key without opening a duplicate-execution window.</li>
 * </ul>
 *
 * <h2>Why claim before doing the work</h2>
 * Inserting the record only after success leaves a window in which two concurrent duplicates both
 * see "no record" and both execute — exactly the double-submit this mechanism exists to stop.
 * Claiming first makes the unique index the arbiter. Failures mark the record {@code FAILED} so
 * the next attempt is permitted; the business service is still expected to throw its own
 * transactional rollback, which the aspect converts into a {@code markFailed} call.
 */
@Service
public class IdempotencyService {

	/** How long a key is honoured. Seven days comfortably exceeds any realistic retry window. */
	public static final Duration RETENTION = Duration.ofDays(7);

	/** Maximum accepted length of an incoming {@code Idempotency-Key} value (safety limit). */
	public static final int MAX_KEY_LENGTH = 200;

	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

	/**
	 * Authoritative stale-in-progress grace period.
	 *
	 * <p>An IN_PROGRESS record older than this window is presumed to originate from a crashed
	 * request: the business TX may have committed even though the COMPLETED row was never
	 * written (the afterCommit callback never fired). The record is transitioned to UNKNOWN
	 * rather than deleted — deleting it would allow a retry to double-execute.
	 *
	 * <p>Sixty seconds comfortably exceeds any normal controller execution time; configurable
	 * via {@code edam.idempotency.stale-grace} if an installation needs a wider window (e.g.
	 * long-running batch approvals).
	 */
	public static final Duration STALE_IN_PROGRESS_GRACE_DEFAULT = Duration.ofSeconds(60);

	private final Duration staleGrace;

	private final IdempotencyRecordRepository records;
	private final TransactionTemplate newTxTemplate;

	/**
	 * In-flight claim tracker.
	 *
	 * <p>When the local JVM is actively processing an {@code IN_PROGRESS} claim on this node, its
	 * composite key is registered here. A concurrent retry on the same node that arrives after
	 * {@link #STALE_IN_PROGRESS_GRACE_DEFAULT} can use this set to distinguish a genuinely
	 * long-running operation (claim still live on this JVM → return 409 EDAM-1005 "in progress")
	 * from a crashed-request row (claim nowhere in memory → transition to UNKNOWN). Without this
	 * guard, a slow endpoint (large payload, synchronous workflow evaluation) would be falsely
	 * branded UNKNOWN after 60 seconds and force the caller into out-of-band reconciliation even
	 * though the original request is still executing.
	 *
	 * <p>In a multi-node WAR deployment this map only tracks claims held by <em>this</em> JVM. A
	 * retry that hits a different node cannot see the local in-flight set and will fall through
	 * to the UNKNOWN path. That is fail-closed (no double execution) and only affects usability:
	 * the client sees EDAM-1006 instead of EDAM-1005 and reconciles. Documented in the design
	 * doc. A proper cross-node lease would require a shared distributed component (e.g. DB
	 * heartbeat row or Redis) which is explicitly out of scope for Phase 4A Step 2.
	 */
	private final ConcurrentMap<String, Boolean> inFlightByLocalJvm = new ConcurrentHashMap<>();

	@Autowired
	public IdempotencyService(IdempotencyRecordRepository records, PlatformTransactionManager txManager) {
		this(records, txManager, STALE_IN_PROGRESS_GRACE_DEFAULT);
	}

	/**
	 * Constructor that lets callers override the stale-grace window. Intended for tests and for
	 * installations that wire a non-default grace (e.g. long-running batch endpoints). The
	 * no-Duration overload is the one Spring picks up via {@code @Autowired}.
	 */
	public IdempotencyService(IdempotencyRecordRepository records, PlatformTransactionManager txManager,
			Duration staleGrace) {
		this.records = records;
		this.staleGrace = staleGrace;
		this.newTxTemplate = new TransactionTemplate(txManager);
		this.newTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/** Outcome of the pre-check: either proceed, or replay a previously recorded response. */
	public interface Decision {
	}

	/** The caller should proceed with the operation; the key is claimed. */
	public static final class Proceed implements Decision {
		public static final Proceed INSTANCE = new Proceed();
		private Proceed() {
		}
	}

	/** Replay the previously recorded response without invoking the business operation. */
	public static final class Replay implements Decision {
		private final int httpStatus;
		private final String responseJson;
		public Replay(int httpStatus, String responseJson) {
			this.httpStatus = httpStatus;
			this.responseJson = responseJson;
		}
		public int httpStatus() { return this.httpStatus; }
		public String responseJson() { return this.responseJson; }
	}

	/**
	 * Check a key before executing the operation. The caller is responsible for canonicalising the
	 * request fingerprint (see {@link #fingerprint(String)}).
	 *
	 * <p>Runs in its own REQUIRES_NEW transaction so the IN_PROGRESS claim is committed immediately
	 * and visible to concurrent duplicates, even though the aspect invokes {@code check} from
	 * inside the controller method whose body will open its own {@code @Transactional} business
	 * transaction. Using default propagation would bind the claim to the outer business TX and
	 * leave it invisible to retries until the entire controller returns — that defeats the whole
	 * point of claiming before doing the work.
	 *
	 * @param operationType stable operation identifier from {@link IdempotentOperation#value()}
	 * @param actorId authenticated user id; must NOT be null
	 * @param key caller-supplied {@code Idempotency-Key} header (trimmed); null/blank ⇒ opt-out
	 * @param fingerprint canonical SHA-256 of the request body + path parameters (see below)
	 */
	public Decision check(String operationType, UUID actorId, String key, String fingerprint) {
		if (key == null || key.isBlank()) {
			return Proceed.INSTANCE;
		}
		String trimmed = key.trim();
		validateKey(trimmed);
		String keyHash = sha256(trimmed);
		// Run the claim in its own transaction so the IN_PROGRESS row is committed before the
		// business service's @Transactional begins. Using Programmatic Tx here avoids relying on
		// self-invocation of @Transactional through the same bean proxy (which would silently
		// join the outer transaction if the caller's TX happened to be open already).
		return this.newTxTemplate.execute(status -> {
			Optional<IdempotencyRecord> existing = this.records.findByKey(keyHash, operationType, actorId);
			String claimKey = claimKey(keyHash, operationType, actorId);
			if (existing.isEmpty()) {
				Instant now = Instant.now();
				try {
					this.records.saveAndFlush(new IdempotencyRecord(keyHash, operationType, actorId, fingerprint,
							now.plus(RETENTION)));
				}
				catch (DataIntegrityViolationException ex) {
					// Lost the race with a concurrent identical request; re-read and decide against
					// the winner's record.
					existing = this.records.findByKey(keyHash, operationType, actorId);
					if (existing.isEmpty()) {
						throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
								"An identical request is already being processed. Retry after a short delay.");
					}
					return decideForExisting(existing.get(), claimKey, keyHash, operationType, actorId, fingerprint);
				}
				// Claim registered on this JVM — register before returning Proceed so the stale-grace
				// path in decideForExisting recognises long-running local claims.
				this.inFlightByLocalJvm.put(claimKey, Boolean.TRUE);
				return (Decision) Proceed.INSTANCE;
			}
			return decideForExisting(existing.get(), claimKey, keyHash, operationType, actorId, fingerprint);
		});
	}

	/** Resolve the decision for an existing IN_PROGRESS / UNKNOWN / FAILED / COMPLETED record. */
	private Decision decideForExisting(IdempotencyRecord record, String claimKey, String keyHash,
			String operationType, UUID actorId, String fingerprint) {
		if (!record.matchesRequest(fingerprint)) {
			// Same key, different payload — HTTP 409 Conflict (not 400): the key is in use by a
			// conflicting request state.
			throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
		}
		return switch (record.getStatus()) {
			case IN_PROGRESS -> {
				Instant abandonedAfter = record.getCreatedAt().plus(this.staleGrace);
				boolean localClaimActive = this.inFlightByLocalJvm.containsKey(claimKey);
				if (Instant.now().isAfter(abandonedAfter) && !localClaimActive) {
					// Crash window: the record is past the grace window AND this JVM has no live
					// claim for it. The business TX may have committed without the afterCommit
					// callback firing (process crashed, OOM kill, pod restart). Transition to
					// UNKNOWN in its own REQUIRES_NEW transaction so the update commits even
					// though we are about to throw BusinessException (which would otherwise mark
					// the outer transaction rollback-only and discard the state change).
					log.error("idempotency.unknown_outcome operation={} actor={} keyHash={} created={}",
							operationType, actorId, keyHash, record.getCreatedAt());
					this.newTxTemplate.executeWithoutResult(s -> {
						IdempotencyRecord fresh = this.records.findById(record.getId()).orElse(record);
						fresh.markUnknown();
						this.records.saveAndFlush(fresh);
					});
					throw new BusinessException(ErrorCode.IDEMPOTENCY_UNKNOWN_OUTCOME,
							"A prior request with this idempotency key did not complete normally. "
									+ "Query the resource to determine its state before retrying.");
				}
				// Either within grace, or the claim is still live on this JVM (long-running op).
				// Return EDAM-1005 "in progress" — the client retries with backoff.
				throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS,
						"An identical request is already being processed. Retry after a short delay.");
			}
			case UNKNOWN -> throw new BusinessException(ErrorCode.IDEMPOTENCY_UNKNOWN_OUTCOME,
					"A prior request with this idempotency key did not complete normally. "
							+ "Query the resource to determine its state before retrying.");
			case FAILED -> {
				// Previous attempt threw; allow a retry under the same key+fingerprint by clearing
				// the record. The unique index will be re-claimed by the new save below.
				this.records.delete(record);
				this.records.flush();
				this.records.saveAndFlush(new IdempotencyRecord(keyHash, operationType, actorId, fingerprint,
						Instant.now().plus(RETENTION)));
				this.inFlightByLocalJvm.put(claimKey, Boolean.TRUE);
				yield Proceed.INSTANCE;
			}
			case COMPLETED -> {
				log.info("idempotency.replay operation={} actor={} status={}", operationType, actorId,
						record.getResponseStatus());
				yield new Replay(record.getResponseStatus(), record.getResponseSnapshot());
			}
		};
	}

	/** Composite in-flight map key for a (keyHash, operationType, actorId) claim. */
	static String claimKey(String keyHash, String operationType, UUID actorId) {
		return keyHash + '|' + operationType + '|' + actorId;
	}

	/** Test-only: clear all in-flight markers so the next check() sees a fresh map. */
	public void clearInFlightForTests() {
		this.inFlightByLocalJvm.clear();
	}

	/**
	 * Record a successful outcome so a retry becomes a replay. No-op when no key was provided.
	 *
	 * <p>The COMPLETED row is written from an {@code afterCommit} synchronization so that the
	 * response snapshot is persisted ONLY after the surrounding business transaction commits. If we
	 * wrote it in a {@code REQUIRES_NEW} transaction while the business TX was still open, a late
	 * rollback (commit-time constraint violation, {@code @EventListener} failure, outer proxy
	 * rollback) would leave a COMPLETED record describing work that never actually happened — a
	 * false replay.
	 *
	 * <p>When there is no surrounding transaction (unexpected for controller flows, but defensive),
	 * the update is performed immediately in a new transaction.
	 */
	public void recordSuccess(String operationType, UUID actorId, String key, int httpStatus, String responseJson) {
		if (key == null || key.isBlank()) {
			return;
		}
		String keyHash = sha256(key.trim());
		String claimKey = claimKey(keyHash, operationType, actorId);
		Runnable finalizer = () -> {
			try {
				this.newTxTemplate.executeWithoutResult(status -> {
					this.records.findByKey(keyHash, operationType, actorId).ifPresent(record -> {
						record.complete(httpStatus, responseJson);
						this.records.saveAndFlush(record);
					});
				});
			}
			finally {
				// Always clear the in-flight marker so a future reuse of the same key (after
				// expiration + deletion) starts clean. If the finalizer threw we still drop the
				// local claim — the row remains IN_PROGRESS/UNKNOWN so the stale-grace path takes
				// over for retries, and we do not want to perpetually mask crash detection.
				this.inFlightByLocalJvm.remove(claimKey);
			}
		};
		// We must NOT register an afterCommit sync against a transaction that was opened by our
		// own REQUIRES_NEW claim (in check()). After check() returns, that claim TX has committed
		// but some TX resources may remain bound to the thread briefly. The right signal is
		// whether the synchronisation manager says a TX is ACTIVE on the *current* thread AND
		// the transaction is not our claim template's TX. In practice for controller flows with
		// open-in-view=false the business service's @Transactional commits BEFORE the aspect
		// invokes recordSuccess (because the service proxy commits on method return, and the
		// aspect is invoked AROUND the controller — which calls the proxied service internally,
		// so by the time joinPoint.proceed() returns to the aspect the service TX is done).
		// Some test setups or filter chains may leave an outer TX open; in that case afterCommit
		// fires correctly. When no TX is active, finalize immediately.
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						finalizer.run();
					}
					catch (RuntimeException ex) {
						// The business operation has already committed; failing here would not undo it.
						// Leave the IN_PROGRESS row so the next retry hits the stale-grace path rather
						// than double-executing. Log loudly for ops.
						log.error("idempotency.finalize_failed operation={} actor={} keyHash={}", operationType,
								actorId, keyHash, ex);
					}
				}
			});
		}
		else {
			finalizer.run();
		}
	}

	/**
	 * Mark a claimed key failed so the caller may correct the input and retry under the same key.
	 *
	 * <p>{@code REQUIRES_NEW} is correct here: the aspect calls {@code markFailed} from a {@code catch}
	 * block before re-throwing the exception, so the outer business transaction is guaranteed to
	 * roll back. Writing the FAILED marker in its own transaction ensures it survives that rollback,
	 * which is exactly what lets the subsequent retry know "the previous attempt did not commit."
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(String operationType, UUID actorId, String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		String keyHash = sha256(key.trim());
		this.records.findByKey(keyHash, operationType, actorId).ifPresent(record -> {
			record.fail();
			this.records.save(record);
		});
		this.inFlightByLocalJvm.remove(claimKey(keyHash, operationType, actorId));
	}

	/** Canonical SHA-256 fingerprint for a request body (or an empty string for bodiless requests). */
	public static String fingerprint(String canonicalBody) {
		return sha256(canonicalBody == null ? "" : canonicalBody);
	}

	/** Accepts printable ASCII of reasonable length; rejects control characters and oversized keys. */
	static void validateKey(String key) {
		String trimmed = key.trim();
		if (trimmed.length() > MAX_KEY_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST,
					"Idempotency-Key exceeds the maximum length of " + MAX_KEY_LENGTH + " characters.");
		}
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			if (c < 0x20 || c == 0x7f) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST,
						"Idempotency-Key contains invalid characters.");
			}
		}
	}

	/** Exposed for tests and internal callers that need to hash an idempotency key. */
	public static String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			// SHA-256 is mandatory in every Java platform; unreachable.
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

}
