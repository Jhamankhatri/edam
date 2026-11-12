package com.edam.common.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import com.edam.common.exception.BusinessException;
import com.edam.common.logging.CorrelationId;

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
 * <h2>Recording happens after success</h2>
 * A failed attempt is not recorded, so the caller can correct the input and retry with the same
 * key. The record is written in a new transaction so it survives the caller's commit ordering.
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
	 * @param institutionId resolved tenant (never from request input)
	 * @param endpoint stable operation identifier, e.g. {@code POST /api/v1/requests/{id}/submit}
	 * @param key caller-supplied key, may be null or blank
	 * @param body canonical request body used for the fingerprint; null is treated as empty
	 */
	@Transactional(readOnly = true)
	public Decision check(Long institutionId, String endpoint, String key, String body) {
		if (key == null || key.isBlank()) {
			return new Decision.Proceed();
		}
		String hash = sha256(key.trim());
		String fingerprint = sha256(body == null ? "" : body);
		Optional<IdempotencyRecord> existing = this.records.findByInstitutionIdAndEndpointAndKeyHash(institutionId,
				endpoint, hash);
		if (existing.isEmpty()) {
			return new Decision.Proceed();
		}
		IdempotencyRecord record = existing.get();
		if (!record.getRequestFingerprint().equals(fingerprint)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
		}
		log.info("idempotency.replay endpoint={} status={}", endpoint, record.getResponseStatus());
		return new Decision.Replay(record.getResponseStatus());
	}

	/** Record a successful execution so a retry is a replay. No-op without a key. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(Long institutionId, String endpoint, String key, String body, int status) {
		if (key == null || key.isBlank()) {
			return;
		}
		String hash = sha256(key.trim());
		if (this.records.findByInstitutionIdAndEndpointAndKeyHash(institutionId, endpoint, hash).isPresent()) {
			return;
		}
		Instant now = Instant.now();
		this.records.save(new IdempotencyRecord(institutionId, hash, endpoint, sha256(body == null ? "" : body), status,
				CorrelationId.current(), now, now.plus(RETENTION)));
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
