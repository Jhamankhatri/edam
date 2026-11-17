package com.edam.integrations.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.integrations.model.IntegrationConfig;
import com.edam.integrations.model.IntegrationExecution;
import com.edam.integrations.model.IntegrationExecution.ExecutionStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IntegrationExecutionRepository extends JpaRepository<IntegrationExecution, UUID> {

	Optional<IntegrationExecution> findByIdempotencyKeyAndIntegrationConfigIdAndOperationType(
			String idempotencyKey, UUID integrationConfigId, String operationType);

	Page<IntegrationExecution> findByIntegrationConfigIdOrderByCreatedAtDesc(UUID integrationConfigId, Pageable p);

	Page<IntegrationExecution> findByIntegrationConfigIdAndStatusOrderByCreatedAtDesc(
			UUID integrationConfigId, ExecutionStatus status, Pageable p);

	/**
	 * Atomically claim one due execution for a worker. Uses {@code FOR UPDATE SKIP LOCKED} so
	 * concurrent workers cannot grab the same row, and a {@code LIMIT 1} subquery.
	 * Returns the number of rows updated (0 or 1).
	 */
	@Modifying
	@Query(value = """
		WITH due AS (
			SELECT id
			  FROM integration_executions
			 WHERE execution_status IN ('PENDING','RETRY_PENDING')
			   AND (next_retry_at IS NULL OR next_retry_at <= :now)
			 ORDER BY COALESCE(next_retry_at, created_at) ASC
			 LIMIT 1
			   FOR UPDATE SKIP LOCKED
		)
		UPDATE integration_executions e
		   SET execution_status = 'RUNNING',
		       claimed_by = :worker,
		       claimed_at = :now,
		       claim_expires_at = :claimExpires,
		       started_at = COALESCE(e.started_at, :now),
		       attempt_count = e.attempt_count + 1,
		       updated_at = :now
		 FROM due
		 WHERE e.id = due.id""", nativeQuery = true)
	int claimOne(@Param("now") Instant now,
	             @Param("claimExpires") Instant claimExpires,
	             @Param("worker") String worker);

	/** Return the row most recently claimed by this worker (caller uses LIMIT 1). */
	@Query("SELECT e FROM IntegrationExecution e WHERE e.status = 'RUNNING' AND e.claimedBy = :worker ORDER BY e.claimedAt DESC")
	List<IntegrationExecution> findClaimedBy(@Param("worker") String worker, Pageable p);

	/** Release expired claims (return rows whose claim TTL has elapsed back to RETRY_PENDING/PENDING). */
	@Modifying
	@Query("UPDATE IntegrationExecution e SET e.status = CASE WHEN e.retryable = true AND e.attemptCount < e.maxAttempts THEN 'RETRY_PENDING' ELSE 'FAILED' END, "
			+ "e.claimedBy = null, e.claimedAt = null, e.claimExpiresAt = null, e.updatedAt = :now "
			+ "WHERE e.status = 'RUNNING' AND e.claimExpiresAt IS NOT NULL AND e.claimExpiresAt < :now")
	int releaseExpiredClaims(@Param("now") Instant now);

	@Query("SELECT COUNT(e) FROM IntegrationExecution e WHERE e.integrationConfigId = :configId AND e.status IN :statuses")
	long countByConfigAndStatuses(@Param("configId") UUID configId, @Param("statuses") List<ExecutionStatus> statuses);
}
