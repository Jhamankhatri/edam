package com.edam.expenserequest.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.expenserequest.model.ExpenseRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Request headers (Module G).
 *
 * <h2>Scope is a predicate, not a post-filter</h2>
 * Filtering after paging would return short pages whose metadata still counted rows the caller may not
 * see, so the caller's visibility is pushed into SQL. This mirrors {@code UserRepository.searchScoped}.
 *
 * <h2>Why there are two list queries</h2>
 * An optional enum filter expressed as {@code :status is null or r.currentStatusCode = :status} makes the
 * parameter's type ambiguous for the query compiler and produces a runtime error rather than a compile
 * error — the kind of defect that only appears on the one code path a test forgets. Two explicit queries
 * cost a few duplicated lines and cannot fail that way.
 *
 * <h2>Drafts are visible only to their author</h2>
 * A draft is unfinished work, not a business record. {@code :draftStatus} is passed from Java rather than
 * referring to the enum by a fully-qualified name inside the query text, so a rename cannot break the query
 * silently.
 *
 * <h2>Duplicate-submission guard</h2>
 * {@code existsByRequestNumber} answers "is this number taken", but the authoritative guard against a second
 * approval chain is the workflow engine's own check on {@code workflow_instances.expense_request_id}: a
 * request can otherwise be submitted twice under two numbers. See {@code ExpenseRequestService.submit}.
 */
public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, UUID> {

	Optional<ExpenseRequest> findByRequestNumber(String requestNumber);

	boolean existsByRequestNumber(String requestNumber);

	/** Lists visible requests, filtered by department and category, with no status filter. */
	@Query("""
			select r from ExpenseRequest r
			where (:installationWide = true
			       or r.requestedBy = :callerId
			       or (r.currentStatusCode <> :draftStatus
			           and ((:hasDepartments = true and r.departmentId in :departmentIds)
			                or (:hasBranches = true and r.branchId in :branchIds))))
			  and (:departmentId is null or r.departmentId = :departmentId)
			  and (:categoryId is null or r.expenseCategoryId = :categoryId)
			  and ((:mineOnly = true and r.requestedBy = :callerId) or :mineOnly = false)
			  and (:search = ''
			       or lower(r.requestNumber) like lower(concat('%', :search, '%'))
			       or lower(r.justification) like lower(concat('%', :search, '%')))
			""")
	Page<ExpenseRequest> searchScoped(@Param("search") String search,
			@Param("departmentId") UUID departmentId, @Param("categoryId") UUID categoryId,
			@Param("mineOnly") boolean mineOnly, @Param("installationWide") boolean installationWide,
			@Param("callerId") UUID callerId, @Param("draftStatus") ExpenseRequest.Status draftStatus,
			@Param("hasDepartments") boolean hasDepartments, @Param("departmentIds") List<UUID> departmentIds,
			@Param("hasBranches") boolean hasBranches, @Param("branchIds") List<UUID> branchIds,
			Pageable pageable);

	/** Lists visible requests in one status. */
	@Query("""
			select r from ExpenseRequest r
			where (:installationWide = true
			       or r.requestedBy = :callerId
			       or (r.currentStatusCode <> :draftStatus
			           and ((:hasDepartments = true and r.departmentId in :departmentIds)
			                or (:hasBranches = true and r.branchId in :branchIds))))
			  and r.currentStatusCode = :status
			  and (:departmentId is null or r.departmentId = :departmentId)
			  and (:categoryId is null or r.expenseCategoryId = :categoryId)
			  and ((:mineOnly = true and r.requestedBy = :callerId) or :mineOnly = false)
			  and (:search = ''
			       or lower(r.requestNumber) like lower(concat('%', :search, '%'))
			       or lower(r.justification) like lower(concat('%', :search, '%')))
			""")
	Page<ExpenseRequest> searchScopedByStatus(@Param("search") String search,
			@Param("status") ExpenseRequest.Status status, @Param("departmentId") UUID departmentId,
			@Param("categoryId") UUID categoryId, @Param("mineOnly") boolean mineOnly,
			@Param("installationWide") boolean installationWide, @Param("callerId") UUID callerId,
			@Param("draftStatus") ExpenseRequest.Status draftStatus, @Param("hasDepartments") boolean hasDepartments,
			@Param("departmentIds") List<UUID> departmentIds, @Param("hasBranches") boolean hasBranches,
			@Param("branchIds") List<UUID> branchIds, Pageable pageable);

	/** All requests the caller authored, newest first — the "My Requests" register. */
	Page<ExpenseRequest> findByRequestedByOrderByCreatedAtDesc(UUID requestedBy, Pageable pageable);

}
