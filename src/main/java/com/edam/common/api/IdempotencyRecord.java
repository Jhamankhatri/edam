package com.edam.common.api;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A recorded idempotency key for a state-changing request (BRD API-04).
 *
 * <h2>Why the database and not a cache</h2>
 * BRD approval flows are financial transactions: a retried submit must not create a second
 * request, and a retried approval must not approve twice. A cache eviction would silently reopen
 * that window, so the key is durable and scoped to the institution.
 *
 * <h2>What is stored, and what is not</h2>
 * Only a hash of the request body plus the recorded response status and a correlation pointer.
 * Storing the response body would put business data in a technical table with its own retention
 * and access rules; instead a replay returns the recorded status and the caller re-reads current
 * state, which is also correct when the resource has changed legitimately since.
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "institution_id", nullable = false, updatable = false)
	private Long institutionId;

	/** Caller-supplied {@code Idempotency-Key}, hashed so long or sensitive values are not stored. */
	@Column(name = "key_hash", nullable = false, updatable = false, length = 64)
	private String keyHash;

	@Column(name = "endpoint", nullable = false, updatable = false, length = 200)
	private String endpoint;

	/** SHA-256 of the canonical request body, to detect key reuse with different content. */
	@Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
	private String requestFingerprint;

	@Column(name = "response_status", nullable = false)
	private int responseStatus;

	@Column(name = "correlation_id", length = 64)
	private String correlationId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	protected IdempotencyRecord() {
		// JPA
	}

	public IdempotencyRecord(Long institutionId, String keyHash, String endpoint, String requestFingerprint,
			int responseStatus, String correlationId, Instant createdAt, Instant expiresAt) {
		this.institutionId = institutionId;
		this.keyHash = keyHash;
		this.endpoint = endpoint;
		this.requestFingerprint = requestFingerprint;
		this.responseStatus = responseStatus;
		this.correlationId = correlationId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public Long getId() {
		return this.id;
	}

	public Long getInstitutionId() {
		return this.institutionId;
	}

	public String getKeyHash() {
		return this.keyHash;
	}

	public String getEndpoint() {
		return this.endpoint;
	}

	public String getRequestFingerprint() {
		return this.requestFingerprint;
	}

	public int getResponseStatus() {
		return this.responseStatus;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

}
