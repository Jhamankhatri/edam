package com.edam.common.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import com.edam.common.exception.BusinessException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the {@code Idempotency-Key} contract for state-changing endpoints (BRD API-04).
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li>No key: the request is processed normally. Idempotency is opt-in so that read paths and
 *       trivial updates are unaffected, while the workflow operations that need it declare it.</li>
 *   <li>New key: processed and recorded.</li>
 *   <li>Same key, same body: the recorded status is returned and the operation is not repeated.</li>
 *   <li>Same key, different body: rejected with {@link ErrorCode#IDEMPOTENCY_CONFLICT}. Silently
 *       replaying an unrelated body under a reused key would be a correctness hazard, and silently
 *       executing it would defeat the point.</li>
 * </ul>
 *
 * <h2>When the key is claimed, and why</h2>
 * The key is claimed <em>before</em> the work runs, not after it succeeds. Recording only on success leaves
 * a window in which two concurrent retries both see "no record" and both execute — precisely the
 * double-submit the mechanism exists to stop. Claiming first makes the unique index the arbiter: the loser
 * of the race is told to retry rather than being allowed to act twice.
 *
 * <p>A failed attempt is then marked {@code FAILED} so the caller may correct the input and retry under the
 * same key. Only a completed operation replays.
 */
@Service
public class IdempotencyService {

	/** How long a key is honoured. Seven days comfortably exceeds any realistic retry window. */
	public static final Duration RETENTION = Duration.ofDays(7);

	private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

	private final IdempotencyRecordRepository records;

	public IdempotencyService(IdempotencyRecordRepository records) {
		this.records = records;
	}

	/** Outcome of the pre-check: either proceed, or replay a previously recorded status. */
	public sealed interface Decision {

		record Proceed() implements Decision {
		}

		record Replay(int status) implements Decision {
		}

	}

	/**
	 * Check a key before executing the operation.
	 *
	 * @param operationType stable operation identifier, e.g. {@code REQUEST_SUBMIT}
	 * @param key caller-supplied key, may be null or blank
	 * @param body canonical request body used for the fingerprint; null is treated as empty
	 */
	@Transactional
	public Decision check(String operationType, String key, String body) {
		if (key == null || key.isBlank()) {
			return new Decision.Proceed();
		}
		String hash = sha256(key.trim());
		String fingerprint = sha256(body == null ? "" : body);
		Optional<IdempotencyRecord> existing = this.records.findByKey(hash, operationType);
		if (existing.isEmpty()) {
			// Claim the key before the work starts. The unique index makes this the winner; a concurrent
			// duplicate loses on insert and is told to retry rather than executing the action twice.
			Instant now = Instant.now();
			try {
				this.records.saveAndFlush(new IdempotencyRecord(hash, operationType, fingerprint, now.plus(RETENTION)));
			}
			catch (org.springframework.dao.DataIntegrityViolationException ex) {
				throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
						"An identical request is already being processed. Retry once it completes.");
			}
			return new Decision.Proceed();
		}
		IdempotencyRecord record = existing.get();
		if (!record.matchesRequest(fingerprint)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
		}
		if (record.isInProgress()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"An identical request is already being processed. Retry once it completes.");
		}
		if (record.getResponseStatus() == null) {
			// A failed attempt is not a replay; let the caller retry under the same key.
			return new Decision.Proceed();
		}
		log.info("idempotency.replay operation={} status={}", operationType, record.getResponseStatus());
		return new Decision.Replay(record.getResponseStatus());
	}

	/** Record the outcome so a retry becomes a replay. No-op without a key. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String operationType, String key, int status) {
		if (key == null || key.isBlank()) {
			return;
		}
		this.records.findByKey(sha256(key.trim()), operationType).ifPresent(record -> {
			record.complete(status);
			this.records.save(record);
		});
	}

	/** Mark a claimed key failed so the caller may correct the input and retry. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(String operationType, String key) {
		if (key == null || key.isBlank()) {
			return;
		}
		this.records.findByKey(sha256(key.trim()), operationType).ifPresent(record -> {
			record.fail();
			this.records.save(record);
		});
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
