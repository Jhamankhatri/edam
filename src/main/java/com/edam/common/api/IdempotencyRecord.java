package com.edam.common.api;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A recorded idempotency key for a state-changing request (Module O).
 *
 * <h2>Why this is a table and not a cache entry</h2>
 * Submitting a request and approving one are financial actions. A retried submit must not create a second
 * request, and a retried approval must not approve twice. A cache eviction — by TTL, by memory pressure,
 * by a restart — would silently reopen that exact window, and it would do so at the worst possible moment:
 * under load, when clients retry. The key is therefore durable.
 *
 * <h2>What is stored</h2>
 * A SHA-256 of the caller's key and a fingerprint of the request body, plus the recorded status. The key
 * itself is hashed so that a stolen backup cannot be used to replay a client's requests, and the response
 * body is not stored at all: a replay returns the recorded status and the caller re-reads current state,
 * which is also correct when the resource has changed legitimately since.
 *
 * <h2>State machine</h2>
 * {@code IN_PROGRESS} → {@code COMPLETED} or {@code FAILED}. The in-progress state is what makes a
 * concurrent duplicate detectable: two simultaneous retries cannot both find "no record" and both proceed.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

	/** Lifecycle of one key. */
	public enum Status {

		/** A request is executing under this key; a concurrent duplicate must not start. */
		IN_PROGRESS,
		/** The operation completed; the recorded status is what a replay receives. */
		COMPLETED,
		/** The operation failed; a retry under the same key is allowed once the failure is recorded. */
		FAILED

	}

	@jakarta.persistence.Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	/** Caller-supplied {@code Idempotency-Key}, hashed. Never stored in clear. */
	@Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
	private String keyHash;

	@Column(name = "operation_type", nullable = false, updatable = false, length = 100)
	private String operationType;

	/** SHA-256 of the canonical request body, to detect key reuse with different content. */
	@Column(name = "request_hash", nullable = false, updatable = false, length = 64)
	private String requestHash;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "response_snapshot")
	private String responseSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(name = "processing_status", nullable = false, length = 20)
	private Status status;

	@Column(name = "response_status")
	private Integer responseStatus;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected IdempotencyRecord() {
		// JPA
	}

	public IdempotencyRecord(String keyHash, String operationType, String requestHash, Instant expiresAt) {
		this.keyHash = keyHash;
		this.operationType = operationType;
		this.requestHash = requestHash;
		this.status = Status.IN_PROGRESS;
		this.expiresAt = expiresAt;
		this.createdAt = Instant.now();
	}

	/** Marks the operation complete and records the status a replay should receive. */
	public void complete(int responseStatus) {
		this.status = Status.COMPLETED;
		this.responseStatus = responseStatus;
	}

	/** Marks the operation failed so that the caller may correct the input and retry. */
	public void fail() {
		this.status = Status.FAILED;
	}

	public boolean isCompleted() {
		return this.status == Status.COMPLETED;
	}

	public boolean isInProgress() {
		return this.status == Status.IN_PROGRESS;
	}

	public boolean matchesRequest(String hash) {
		return this.requestHash.equals(hash);
	}

	public UUID getId() {
		return this.id;
	}

	public String getKeyHash() {
		return this.keyHash;
	}

	public String getOperationType() {
		return this.operationType;
	}

	public String getRequestHash() {
		return this.requestHash;
	}

	public String getResponseSnapshot() {
		return this.responseSnapshot;
	}

	public Status getStatus() {
		return this.status;
	}

	public Integer getResponseStatus() {
		return this.responseStatus;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
