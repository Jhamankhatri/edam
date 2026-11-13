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
 * <h2>Search is authorization-aware</h2>
 * {@code search} is a native query that joins {@code expense_requests} for events whose
 * {@code entity_type = 'ExpenseRequest'}, applying the same visibility rule as the request
 * register: the requester, users whose data scopes include the request's department or branch,
 * and installation-wide users may see the event; all other ExpenseRequest events are filtered
 * out <em>before</em> pagination and {@code count()}. Events for other entity types are visible
 * to any caller holding {@code audit:view}.
 */
public interface AuditRepository extends Repository<AuditEvent, UUID> {

	AuditEvent save(AuditEvent event);

	Optional<AuditEvent> findById(UUID id);

	long count();

	Page<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId,
			Pageable pageable);

	/**
	 * Authorization-bounded audit search.
	 *
	 * <p>The native query is used because JPQL cannot express the conditional left join with a
	 * string-to-UUID cast cleanly across Hibernate versions, and the filter must run at the
	 * database to keep {@code totalElements} honest.
	 *
	 * <p>Parameters {@code deptIds} and {@code branchIds} are required to be non-null (empty
	 * means no department/branch scope). When both are empty and {@code installationWide} is
	 * false, the caller can see only their own requests.
	 */
	@Query(value = """
			select e.* from audit_events e
			left join expense_requests r
			       on e.entity_type = 'ExpenseRequest'
			      and e.entity_id    = r.id::text
			where e.created_at >= :from
			  and e.created_at <  :to
			  and (:action        is null or e.action        = :action)
			  and (:entityType    is null or e.entity_type   = :entityType)
			  and (:entityId      is null or e.entity_id     = :entityId)
			  and (:performedBy   is null or e.performed_by  = :performedBy)
			  and (:correlationId is null or e.correlation_id = :correlationId)
			  and (cast(:search as text) = ''
			       or lower(e.performed_by_username) like lower(concat('%', cast(:search as text), '%')))
			  and (
			        e.entity_type <> 'ExpenseRequest'
			     or r.id is null
			     or :installationWide = true
			     or r.requested_by = :callerId
			     or r.department_id = any (:deptIds)
			     or (r.branch_id is not null and r.branch_id = any (:branchIds))
			  )
			order by e.created_at desc, e.id desc
			""",
			countQuery = """
			select count(*) from audit_events e
			left join expense_requests r
			       on e.entity_type = 'ExpenseRequest'
			      and e.entity_id    = r.id::text
			where e.created_at >= :from
			  and e.created_at <  :to
			  and (:action        is null or e.action        = :action)
			  and (:entityType    is null or e.entity_type   = :entityType)
			  and (:entityId      is null or e.entity_id     = :entityId)
			  and (:performedBy   is null or e.performed_by  = :performedBy)
			  and (:correlationId is null or e.correlation_id = :correlationId)
			  and (cast(:search as text) = ''
			       or lower(e.performed_by_username) like lower(concat('%', cast(:search as text), '%')))
			  and (
			        e.entity_type <> 'ExpenseRequest'
			     or r.id is null
			     or :installationWide = true
			     or r.requested_by = :callerId
			     or r.department_id = any (:deptIds)
			     or (r.branch_id is not null and r.branch_id = any (:branchIds))
			  )
			""",
			nativeQuery = true)
	Page<AuditEvent> search(@Param("from") Instant from,
			@Param("to") Instant to,
			@Param("action") AuditAction action,
			@Param("entityType") String entityType,
			@Param("entityId") String entityId,
			@Param("performedBy") UUID performedBy,
			@Param("correlationId") String correlationId,
			@Param("search") String search,
			@Param("callerId") UUID callerId,
			@Param("installationWide") boolean installationWide,
			@Param("deptIds") UUID[] deptIds,
			@Param("branchIds") UUID[] branchIds,
			Pageable pageable);

	/** Bounded export source; callers cap the page size before streaming. */
	List<AuditEvent> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from,
			Instant to, Pageable pageable);

}
