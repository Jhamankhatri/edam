package com.edam.common.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import com.edam.common.exception.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *       returned and the business operation is NOT executed again.</li>
 *   <li>Same key, same actor, different fingerprint: rejected with
 *       {@link ErrorCode#IDEMPOTENCY_CONFLICT} (HTTP 400).</li>
 *   <li>Same key, same actor, {@code IN_PROGRESS}: rejected with {@link ErrorCode#PRECONDITION_FAILED}
 *       (HTTP 409) — the client should retry after a short delay.</li>
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
	 * Stale in-progress records older than this are treated as abandoned (e.g. the JVM crashed
	 * between claim and finalisation) and cleared on the next {@code check()} for that key so a
	 * retry does not dead-end on a permanent 409.
	 */
	private static final Duration STALE_IN_PROGRESS_GRACE = Duration.ofMinutes(5);

	private final IdempotencyRecordRepository records;
	private final TransactionTemplate newTxTemplate;

	public IdempotencyService(IdempotencyRecordRepository records, PlatformTransactionManager txManager) {
		this.records = records;
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
	 * @param operationType stable operation identifier from {@link IdempotentOperation#value()}
	 * @param actorId authenticated user id; must NOT be null
	 * @param key caller-supplied {@code Idempotency-Key} header (trimmed); null/blank ⇒ opt-out
	 * @param fingerprint canonical SHA-256 of the request body + path parameters (see below)
	 */
	@Transactional
	public Decision check(String operationType, UUID actorId, String key, String fingerprint) {
		if (key == null || key.isBlank()) {
			return Proceed.INSTANCE;
		}
		validateKey(key);
		String keyHash = sha256(key);
		Optional<IdempotencyRecord> existing = this.records.findByKey(keyHash, operationType, actorId);
		if (existing.isEmpty()) {
			Instant now = Instant.now();
			try {
				this.records.saveAndFlush(new IdempotencyRecord(keyHash, operationType, actorId, fingerprint,
						now.plus(RETENTION)));
			}
			catch (DataIntegrityViolationException ex) {
				// Lost the race with a concurrent identical request.
				throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
						"An identical request is already being processed. Retry once it completes.");
			}
			return Proceed.INSTANCE;
		}
		IdempotencyRecord record = existing.get();
		if (!record.matchesRequest(fingerprint)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
		}
		return switch (record.getStatus()) {
			case IN_PROGRESS -> {
				Instant abandonedAfter = record.getCreatedAt().plus(STALE_IN_PROGRESS_GRACE);
				if (Instant.now().isAfter(abandonedAfter)) {
					// Server/process crash left a dangling IN_PROGRESS row; clear and retry.
					log.warn("idempotency.stale_in_progress operation={} actor={} keyHash={} clearing",
							operationType, actorId, keyHash);
					this.records.delete(record);
					this.records.flush();
					this.records.saveAndFlush(new IdempotencyRecord(keyHash, operationType, actorId, fingerprint,
							Instant.now().plus(RETENTION)));
					yield Proceed.INSTANCE;
				}
				throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
						"An identical request is already being processed. Retry once it completes.");
			}
			case FAILED -> {
				// Previous attempt failed; allow a retry under the same key+fingerprint by clearing
				// the record. The unique index will be re-claimed by the new save below.
				this.records.delete(record);
				this.records.flush();
				this.records.saveAndFlush(new IdempotencyRecord(keyHash, operationType, actorId, fingerprint,
						Instant.now().plus(RETENTION)));
				yield Proceed.INSTANCE;
			}
			case COMPLETED -> {
				log.info("idempotency.replay operation={} actor={} status={}", operationType, actorId,
						record.getResponseStatus());
				yield new Replay(record.getResponseStatus(), record.getResponseSnapshot());
			}
		};
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
		Runnable finalizer = () -> this.newTxTemplate.executeWithoutResult(status -> {
			this.records.findByKey(keyHash, operationType, actorId).ifPresent(record -> {
				record.complete(httpStatus, responseJson);
				this.records.save(record);
			});
		});
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
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
		this.records.findByKey(sha256(key.trim()), operationType, actorId).ifPresent(record -> {
			record.fail();
			this.records.save(record);
		});
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

	private static String sha256(String value) {
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
