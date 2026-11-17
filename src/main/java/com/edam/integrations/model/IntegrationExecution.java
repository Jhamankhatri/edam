package com.edam.integrations.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A single execution of an integration operation.
 *
 * <p>Workers claim a PENDING/RETRY_PENDING row with {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * and flip it to RUNNING with a {@code claim_expires_at} TTL; if the worker crashes, the row
 * becomes reclaimable after the TTL. Terminal states (SUCCEEDED/FAILED/CANCELLED) are never
 * overwritten by a late worker.
 */
@Entity
@Table(name = "integration_executions", indexes = {
	@Index(name = "ix_integration_executions_due",   columnList = "next_retry_at"),
	@Index(name = "ix_integration_executions_claim", columnList = "claim_expires_at"),
	@Index(name = "ix_integration_executions_config_time", columnList = "integration_config_id,created_at")
})
public class IntegrationExecution {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "integration_config_id", nullable = false)
	private UUID integrationConfigId;

	@Column(name = "operation_type", nullable = false, length = 60)
	private String operationType;

	@Column(name = "idempotency_key", length = 200)
	private String idempotencyKey;

	@Column(name = "external_reference", length = 300)
	private String externalReference;

	@Enumerated(EnumType.STRING)
	@Column(name = "execution_status", nullable = false, length = 20)
	private ExecutionStatus status = ExecutionStatus.PENDING;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts = 3;

	@Column(name = "claimed_by", length = 100)
	private String claimedBy;

	@Column(name = "claimed_at")
	private Instant claimedAt;

	@Column(name = "claim_expires_at")
	private Instant claimExpiresAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "next_retry_at")
	private Instant nextRetryAt;

	@Column(name = "retryable", nullable = false)
	private boolean retryable = false;

	@Column(name = "request_metadata", columnDefinition = "jsonb")
	private String requestMetadata;

	@Column(name = "response_metadata", columnDefinition = "jsonb")
	private String responseMetadata;

	@Column(name = "error_code", length = 60)
	private String errorCode;

	@Column(name = "error_message", length = 1000)
	private String errorMessage;

	@Column(name = "triggered_by")
	private UUID triggeredBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@PrePersist void onCreate() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		this.updatedAt = now;
	}
	@PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getIntegrationConfigId() { return integrationConfigId; }
	public void setIntegrationConfigId(UUID integrationConfigId) { this.integrationConfigId = integrationConfigId; }
	public String getOperationType() { return operationType; }
	public void setOperationType(String operationType) { this.operationType = operationType; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
	public String getExternalReference() { return externalReference; }
	public void setExternalReference(String externalReference) { this.externalReference = externalReference; }
	public ExecutionStatus getStatus() { return status; }
	public void setStatus(ExecutionStatus status) { this.status = status; }
	public int getAttemptCount() { return attemptCount; }
	public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
	public int getMaxAttempts() { return maxAttempts; }
	public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
	public String getClaimedBy() { return claimedBy; }
	public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
	public Instant getClaimedAt() { return claimedAt; }
	public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }
	public Instant getClaimExpiresAt() { return claimExpiresAt; }
	public void setClaimExpiresAt(Instant claimExpiresAt) { this.claimExpiresAt = claimExpiresAt; }
	public Instant getStartedAt() { return startedAt; }
	public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
	public Instant getCompletedAt() { return completedAt; }
	public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
	public Instant getNextRetryAt() { return nextRetryAt; }
	public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
	public boolean isRetryable() { return retryable; }
	public void setRetryable(boolean retryable) { this.retryable = retryable; }
	public String getRequestMetadata() { return requestMetadata; }
	public void setRequestMetadata(String requestMetadata) { this.requestMetadata = requestMetadata; }
	public String getResponseMetadata() { return responseMetadata; }
	public void setResponseMetadata(String responseMetadata) { this.responseMetadata = responseMetadata; }
	public String getErrorCode() { return errorCode; }
	public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
	public String getErrorMessage() { return errorMessage; }
	public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
	public UUID getTriggeredBy() { return triggeredBy; }
	public void setTriggeredBy(UUID triggeredBy) { this.triggeredBy = triggeredBy; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
	public long getVersion() { return version; }
	public void setVersion(long version) { this.version = version; }

	public enum ExecutionStatus { PENDING, RUNNING, SUCCEEDED, FAILED, RETRY_PENDING, CANCELLED }
}
