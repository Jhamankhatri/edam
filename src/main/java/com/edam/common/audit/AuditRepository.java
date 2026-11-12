package com.edam.common.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Audit search (BRD AUD-01).
 *
 * <p>Every finder is institution-scoped except the explicitly platform-level one. There is no
 * finder that returns events across tenants, so a missing {@code where} clause is impossible by
 * construction rather than by discipline.
 */
public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

	Page<AuditEvent> findByInstitutionIdOrderByOccurredAtDesc(Long institutionId, Pageable pageable);

	Page<AuditEvent> findByInstitutionIdAndResourceTypeAndResourceIdOrderByOccurredAtDesc(Long institutionId,
			String resourceType, String resourceId, Pageable pageable);

	@Query("""
			select e from AuditEvent e
			where e.institutionId = :institutionId
			  and (:action is null or e.action = :action)
			  and (:resourceType is null or e.resourceType = :resourceType)
			  and (:actor is null or e.actor = :actor)
			  and e.occurredAt >= :from
			  and e.occurredAt < :to
			order by e.occurredAt desc
			""")
	Page<AuditEvent> search(@Param("institutionId") Long institutionId, @Param("action") AuditAction action,
			@Param("resourceType") String resourceType, @Param("actor") String actor, @Param("from") Instant from,
			@Param("to") Instant to, Pageable pageable);

	/** Bounded export source; callers enforce the row cap before streaming. */
	List<AuditEvent> findByInstitutionIdAndOccurredAtBetweenOrderByOccurredAtDesc(Long institutionId, Instant from,
			Instant to, Pageable pageable);

	long countByInstitutionIdAndOccurredAtGreaterThanEqual(Long institutionId, Instant since);

}
