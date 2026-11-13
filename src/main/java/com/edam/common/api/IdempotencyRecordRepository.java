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

	/**
	 * Looks a key up by its hash and operation.
	 *
	 * <p>Written as an explicit query rather than a derived name because the persisted field is
	 * {@code keyHash} while the column is {@code idempotency_key}; a derived finder would silently try to
	 * resolve the column name as a property and fail at startup.
	 */
	@Query("""
			select r from IdempotencyRecord r
			where r.keyHash = :keyHash
			  and r.operationType = :operationType
			""")
	Optional<IdempotencyRecord> findByKey(String keyHash, String operationType);

	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt < :cutoff")
	int deleteExpired(@Param("cutoff") Instant cutoff);

}
