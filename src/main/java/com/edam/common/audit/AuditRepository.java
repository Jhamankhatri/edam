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

	/**
	 * Newest-first finder for a single entity's audit trail (used by the timeline first/last
	 * event lookups). Page size should be bounded by the caller.
	 */
	Page<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(String entityType, String entityId,
			Pageable pageable);

	/**
	 * Oldest-first keyset fetch for the timeline. Returns at most {@code fetchLimit} audit events
	 * for the given request that are strictly after the cursor key (or from the beginning when
	 * {@code afterTs} is null) and not after {@code boundedAt}. Ordering is
	 * {@code (created_at ASC, msb(id) ASC, lsb(id) ASC)} so tie-breaking matches
	 * {@link java.util.UUID#compareTo} exactly (signed 64-bit halves).
	 *
	 * <p>We use a native query because (a) JPQL keyset pagination with a composite (ts, id)
	 * cursor is awkward to express portably and Hibernate 6 does not reliably bind
	 * {@code (ts,id) > (:afterTs, :afterId)} row-value comparisons on Postgres, and
	 * (b) Postgres' native {@code uuid > uuid} operator orders by unsigned bytes while Java
	 * {@link java.util.UUID#compareTo} orders by signed longs; we therefore cast each UUID
	 * half to {@code bigint} (signed) and compare explicitly.
	 */
	@Query(value = """
			select e.*
			from audit_events e
			where e.entity_type = cast(:entityType as text)
			  and e.entity_id   = cast(:entityId as text)
			  and e.created_at  <= :boundedAt
			  and (   cast(:afterTs as timestamptz) is null
			       or e.created_at > cast(:afterTs as timestamptz)
			       or (e.created_at = cast(:afterTs as timestamptz)
			           and (:afterMsb is null
			                or (   (('x'||substr(replace(e.id::text,'-',''),1,16))::bit(64))::bigint > :afterMsb
			                    or ((('x'||substr(replace(e.id::text,'-',''),1,16))::bit(64))::bigint = :afterMsb
			                        and (('x'||substr(replace(e.id::text,'-',''),17,16))::bit(64))::bigint > :afterLsb)))))
			order by e.created_at asc,
			         (('x'||substr(replace(e.id::text,'-',''),1,16))::bit(64))::bigint asc,
			         (('x'||substr(replace(e.id::text,'-',''),17,16))::bit(64))::bigint asc
			limit :fetchLimit
			""", nativeQuery = true)
	List<AuditEvent> findForEntityAfter(@Param("entityType") String entityType,
			@Param("entityId") String entityId,
			@Param("boundedAt") Instant boundedAt,
			@Param("afterTs") Instant afterTs,
			@Param("afterMsb") Long afterMsb,
			@Param("afterLsb") Long afterLsb,
			@Param("fetchLimit") int fetchLimit);

	/**
	 * Authorization-bounded audit search.
	 *
	 * <p>The native query is used because JPQL cannot express the conditional left join with a
	 * string-to-UUID cast cleanly across Hibernate versions, and the filter must run at the
	 * database to keep {@code totalElements} honest.
	 *
	 * <p>String and enum parameters are cast to {@code text} and arrays to {@code uuid[]} to
	 * keep Postgres from inferring a {@code smallint} or other mismatch against the table's
	 * textual columns when Hibernate binds through native-query type inference.
	 *
	 * <p>Safety policy for orphaned request events: when {@code r.id IS NULL} (the request no
	 * longer exists in {@code expense_requests}), the event is visible <b>only</b> to
	 * installation-wide auditors. This prevents a deleted/stale request from leaking historical
	 * metadata to department-scoped callers.
	 *
	 * <p>DRAFT visibility mirrors {@code ExpenseRequestService.isVisible}: a request whose
	 * {@code current_status_code = 'DRAFT'} is visible only to its requester and to
	 * installation-wide auditors — department/branch scope alone never grants access to another
	 * user's draft, which prevents metadata leakage of unsubmitted filings through the audit
	 * search list, detail endpoint, or {@code totalElements}.
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
			  and (cast(:action as text) is null or e.action         = cast(:action as text))
			  and (cast(:entityType as text) is null or e.entity_type = cast(:entityType as text))
			  and (cast(:entityId as text) is null or e.entity_id     = cast(:entityId as text))
			  and (:performedBy   is null or e.performed_by           = :performedBy)
			  and (cast(:correlationId as text) is null
			       or e.correlation_id = cast(:correlationId as text))
			  and (cast(:search as text) = ''
			       or lower(e.performed_by_username) like lower(concat('%', cast(:search as text), '%')))
			  and (
			        e.entity_type <> 'ExpenseRequest'
			     or (
			          r.id is not null
			          and (
			               cast(:installationWide as boolean) = true
			            or r.requested_by = :callerId
			            or (    r.current_status_code <> cast('DRAFT' as text)
			                and (   r.department_id = any (cast(:deptIds as uuid[]))
			                     or (r.branch_id is not null
			                         and r.branch_id = any (cast(:branchIds as uuid[])))))
			          )
			        )
			     or (r.id is null and cast(:installationWide as boolean) = true)
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
			  and (cast(:action as text) is null or e.action         = cast(:action as text))
			  and (cast(:entityType as text) is null or e.entity_type = cast(:entityType as text))
			  and (cast(:entityId as text) is null or e.entity_id     = cast(:entityId as text))
			  and (:performedBy   is null or e.performed_by           = :performedBy)
			  and (cast(:correlationId as text) is null
			       or e.correlation_id = cast(:correlationId as text))
			  and (cast(:search as text) = ''
			       or lower(e.performed_by_username) like lower(concat('%', cast(:search as text), '%')))
			  and (
			        e.entity_type <> 'ExpenseRequest'
			     or (
			          r.id is not null
			          and (
			               cast(:installationWide as boolean) = true
			            or r.requested_by = :callerId
			            or (    r.current_status_code <> cast('DRAFT' as text)
			                and (   r.department_id = any (cast(:deptIds as uuid[]))
			                     or (r.branch_id is not null
			                         and r.branch_id = any (cast(:branchIds as uuid[])))))
			          )
			        )
			     or (r.id is null and cast(:installationWide as boolean) = true)
			  )
			""",
			nativeQuery = true)
	Page<AuditEvent> search(@Param("from") java.time.Instant from,
			@Param("to") java.time.Instant to,
			@Param("action") String action,
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

}
