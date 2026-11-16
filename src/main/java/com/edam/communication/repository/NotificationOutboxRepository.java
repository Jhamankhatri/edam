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
										org.springframework.data.domain.Pageable pageable);

	@Modifying
	@Query("UPDATE NotificationOutbox o SET o.status = :status, o.lockedBy = :worker, o.lockedAt = :now " +
			"WHERE o.id = :id")
	int claim(@Param("id") UUID id,
			  @Param("worker") String workerId,
			  @Param("status") OutboxStatus status,
			  @Param("now") Instant now);

	long countByStatusIn(OutboxStatus... statuses);
	long count();
}
