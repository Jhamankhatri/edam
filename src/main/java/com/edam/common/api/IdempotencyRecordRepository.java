package com.edam.common.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Durable idempotency storage (Module O).
 *
 * <p>The lookup is keyed on the hashed key <em>and</em> the operation type. Two different operations under
 * the same client-generated key must not collide: a client that reuses one key across a submit and an
 * approve would otherwise receive the submit's status for its approval.
 */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByIdempotencyKeyAndOperationType(String idempotencyKey, String operationType);

	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt < :cutoff")
	int deleteExpired(@Param("cutoff") Instant cutoff);

}
