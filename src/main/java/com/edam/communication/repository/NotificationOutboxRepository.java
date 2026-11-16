package com.edam.communication.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.communication.model.NotificationOutbox;
import com.edam.communication.model.OutboxStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

	/**
	 * Claim up to {@code pageable.limit} due rows for the given worker and return them. The
	 * rows are atomically flipped to PROCESSING with the worker id and a lock timestamp using
	 * {@code FOR UPDATE SKIP LOCKED}, so competing workers see disjoint sets.
	 */
	@Query(value = """
		SELECT * FROM comm_notification_outbox
		 WHERE status IN ('PENDING', 'RETRY_PENDING')
		   AND next_attempt_at IS NOT NULL
		   AND next_attempt_at <= :now
		   AND (locked_at IS NULL OR locked_at < :stale)
		 ORDER BY next_attempt_at ASC
		 FOR UPDATE SKIP LOCKED
		""", nativeQuery = true,
		countQuery = "SELECT count(*) FROM comm_notification_outbox WHERE status IN ('PENDING','RETRY_PENDING')")
	List<NotificationOutbox> claimBatch(@Param("now") Instant now,
			@Param("stale") Instant staleBefore,
			Pageable pageable);

	/**
	 * Mark a previously-claimed row as delivered (SENT). Guarded by id + worker id so a stale
	 * lock cannot overwrite another worker's state.
	 */
	@Modifying
	@Query("UPDATE NotificationOutbox o SET o.status = :status, o.sentAt = :sentAt, "
			+ "o.lockedBy = NULL, o.lockedAt = NULL, o.nextAttemptAt = NULL, o.lastError = NULL "
			+ "WHERE o.id = :id AND o.lockedBy = :worker")
	int markSent(@Param("id") UUID id,
			@Param("worker") String workerId,
			@Param("status") OutboxStatus status,
			@Param("sentAt") Instant sentAt);

	/**
	 * Mark a claimed row FAILED or RETRY_PENDING with a safe diagnostic and updated next-retry.
	 * Guarded by id + worker id.
	 */
	@Modifying
	@Query("UPDATE NotificationOutbox o SET o.status = :status, o.lastError = :err, "
			+ "o.lockedBy = NULL, o.lockedAt = NULL, o.nextAttemptAt = :nextAttempt "
			+ "WHERE o.id = :id AND o.lockedBy = :worker")
	int markFailed(@Param("id") UUID id,
			@Param("worker") String workerId,
			@Param("status") OutboxStatus status,
			@Param("err") String lastError,
			@Param("nextAttempt") Instant nextAttempt);

	/** Touch lock timestamp / attempts when a row is grabbed. */
	@Modifying
	@Query("UPDATE NotificationOutbox o SET o.status = :status, o.lockedBy = :worker, o.lockedAt = :now, "
			+ "o.lastAttemptAt = :now, o.attempts = o.attempts + 1 "
			+ "WHERE o.id = :id AND o.lockedBy IS NULL")
	int touchClaim(@Param("id") UUID id,
			@Param("worker") String workerId,
			@Param("status") OutboxStatus status,
			@Param("now") Instant now);

	boolean existsByIdempotencyKey(String key);

	long countByStatusIn(OutboxStatus... statuses);
	long count();
}
