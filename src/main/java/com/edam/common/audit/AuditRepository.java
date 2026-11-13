package com.edam.common.audit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Append-only audit access (Module K).
 *
 * <h2>Why this does not extend {@code JpaRepository}</h2>
 * {@code JpaRepository} would expose {@code saveAndFlush}, {@code delete}, {@code deleteAll} and
 * {@code save} on an existing entity — four ways to corrupt history from application code, guarded
 * only by the database trigger. Narrowing the interface removes them entirely: the audit trail can
 * be inserted and searched, and nothing else. This is the interface-segregation principle applied
 * for a security reason, not a stylistic one.
 *
 * <h2>Search</h2>
 * Every filter is optional except the time window, which is mandatory. An unbounded search over an
 * append-only table that only grows is a denial-of-service waiting to happen, and "everything since
 * the beginning" is never a legitimate compliance question.
 */
public interface AuditRepository extends Repository<AuditEvent, UUID> {

	AuditEvent save(AuditEvent event);

	Optional<AuditEvent> findById(UUID id);

	Page<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId,
			Pageable pageable);

	@Query("""
			select e from AuditEvent e
			where e.createdAt >= :from
			  and e.createdAt < :to
			  and (:action is null or e.action = :action)
			  and (:entityType is null or e.entityType = :entityType)
			  and (:entityId is null or e.entityId = :entityId)
			  and (:performedBy is null or e.performedBy = :performedBy)
			  and (:correlationId is null or e.correlationId = :correlationId)
			  and (:search = '' or lower(e.performedByUsername) like lower(concat('%', :search, '%')))
			order by e.createdAt desc
			""")
	Page<AuditEvent> search(@Param("from") Instant from,
			@Param("to") Instant to,
			@Param("action") AuditAction action,
			@Param("entityType") String entityType,
			@Param("entityId") String entityId,
			@Param("performedBy") UUID performedBy,
			@Param("correlationId") String correlationId,
			@Param("search") String search, Pageable pageable);

	/** Bounded export source; callers cap the page size before streaming. */
	List<AuditEvent> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from,
			Instant to, Pageable pageable);

}
