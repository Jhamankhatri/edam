package com.edam.expense;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Expense-request persistence.
 *
 * <p>Every finder takes the institution, and the list queries require a scope argument: the caller's
 * own employee id for "my requests", or {@code null} to mean "all in this institution" for a caller
 * who holds the read-all permission. There is intentionally no unfiltered finder, because an
 * unfiltered list is how a tenant boundary is lost one endpoint at a time.
 */
public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {

	Optional<ExpenseRequest> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<ExpenseRequest> findByInstitutionIdAndRequestNumber(Long institutionId, String requestNumber);

	/**
	 * Paged search constrained by institution, optional requester and optional state.
	 *
	 * @param requesterUsername when non-null, restrict to that requester (My Requests scope)
	 */
	@Query("select r from ExpenseRequest r where r.institutionId = :institutionId "
			+ "and (:requesterUsername is null or r.requesterUsername = :requesterUsername) "
			+ "and (:state is null or r.state = :state) "
			+ "and (:nature is null or r.expenseNature = :nature) "
			+ "and (:departmentCode is null or r.departmentCode = :departmentCode) "
			+ "and (:search is null or lower(r.title) like lower(concat('%', :search, '%')) "
			+ "     or lower(r.requestNumber) like lower(concat('%', :search, '%'))) ")
	Page<ExpenseRequest> search(@Param("institutionId") Long institutionId,
			@Param("requesterUsername") String requesterUsername, @Param("state") RequestState state,
			@Param("nature") com.edam.configplane.ExpenseNature nature,
			@Param("departmentCode") String departmentCode, @Param("search") String search, Pageable pageable);

	/** Requests the caller may act on, by open task assignment. Used by "My Approvals". */
	@Query("select distinct r from ExpenseRequest r join ApprovalTask t on t.request.id = r.id "
			+ "where r.institutionId = :institutionId and t.status = com.edam.approval.ApprovalTaskStatus.PENDING "
			+ "and t.assignedEmployeeId = :employeeId")
	List<ExpenseRequest> findAwaitingAction(@Param("institutionId") Long institutionId,
			@Param("employeeId") String employeeId);

	/** Highest request number suffix issued so far, for gap-free numbering within an institution. */
	@Query("select count(r) from ExpenseRequest r where r.institutionId = :institutionId")
	long countForInstitution(@Param("institutionId") Long institutionId);

}
