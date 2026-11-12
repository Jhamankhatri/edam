package com.edam.common.api;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Durable idempotency key storage. */
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

	Optional<IdempotencyRecord> findByInstitutionIdAndEndpointAndKeyHash(Long institutionId, String endpoint,
			String keyHash);

	@Modifying
	@Query("delete from IdempotencyRecord r where r.expiresAt < :cutoff")
	int deleteExpired(@Param("cutoff") Instant cutoff);

}
