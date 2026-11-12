package com.edam.approval;

import java.util.List;
import java.util.Optional;

import com.edam.expense.ExpenseRequest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Approval-task persistence, tenant-scoped. */
public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

	List<ApprovalTask> findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(Long institutionId, Long requestId);

	Optional<ApprovalTask> findByIdAndInstitutionId(Long id, Long institutionId);

	Optional<ApprovalTask> findFirstByInstitutionIdAndRequestIdAndStatusOrderBySequenceNumberAsc(Long institutionId,
			Long requestId, ApprovalTaskStatus status);

	/** The open task of a request, if any. Unique by construction (partial unique index). */
	@Query("select t from ApprovalTask t where t.institutionId = :institutionId and t.request.id = :requestId "
			+ "and t.status = com.edam.approval.ApprovalTaskStatus.PENDING")
	Optional<ApprovalTask> findOpenTask(@Param("institutionId") Long institutionId,
			@Param("requestId") Long requestId);

	/** Approvals already recorded at one step, used for quorum. */
	@Query("select count(t) from ApprovalTask t where t.institutionId = :institutionId "
			+ "and t.request.id = :requestId and t.sequenceNumber = :sequenceNumber "
			+ "and t.status = com.edam.approval.ApprovalTaskStatus.APPROVED")
	long countApprovalsAtStep(@Param("institutionId") Long institutionId, @Param("requestId") Long requestId,
			@Param("sequenceNumber") int sequenceNumber);

	/**
	 * The approval inbox: tasks addressed to the caller directly, to their department, or to a
	 * committee office they hold.
	 *
	 * <p>This is deliberately a query on assignment, not on permission. A user with
	 * {@code approval:act} who is not assigned sees nothing, which is the BRD's requirement.
	 */
	@Query("select t from ApprovalTask t where t.institutionId = :institutionId "
			+ "and t.status = com.edam.approval.ApprovalTaskStatus.PENDING "
			+ "and (t.assignedEmployeeId = :employeeId "
			+ "     or (t.assignedDepartment is not null and t.assignedDepartment = :departmentCode) "
			+ "     or t.approverRole in :roles) "
			+ "order by t.createdAt asc")
	Page<ApprovalTask> findInbox(@Param("institutionId") Long institutionId, @Param("employeeId") String employeeId,
			@Param("departmentCode") String departmentCode, @Param("roles") List<String> roles,
			Pageable pageable);

	@Query("select t from ApprovalTask t where t.institutionId = :institutionId and t.request.id = :requestId "
			+ "and t.status = com.edam.approval.ApprovalTaskStatus.APPROVED and t.sequenceNumber = :sequenceNumber")
	List<ApprovalTask> findApprovedAtStep(@Param("institutionId") Long institutionId,
			@Param("requestId") Long requestId, @Param("sequenceNumber") int sequenceNumber);

	/** True when the employee has already acted on this request at this step (BRD: no double vote). */
	@Query("select (count(t) > 0) from ApprovalTask t where t.institutionId = :institutionId "
			+ "and t.request.id = :requestId and t.sequenceNumber = :sequenceNumber "
			+ "and t.actedByEmployeeId = :employeeId")
	boolean hasActedAtStep(@Param("institutionId") Long institutionId, @Param("requestId") Long requestId,
			@Param("sequenceNumber") int sequenceNumber, @Param("employeeId") String employeeId);

}
