package com.edam.approval.api;

import java.util.List;

import com.edam.approval.ApprovalEngine;
import com.edam.approval.ApprovalTask;
import com.edam.approval.ApprovalTaskRepository;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.common.security.Permissions;
import com.edam.configplane.committee.CommitteeMember;
import com.edam.configplane.committee.CommitteeMemberRepository;
import com.edam.expense.ExpenseRequest;
import com.edam.expense.api.ExpenseRequestController;
import com.edam.expense.api.RequestDtos.ExpenseRequestResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Approval inbox and actions (BRD FM-03, FM-06, §8.4, §8.5).
 *
 * <h2>Why this is a separate controller from requests</h2>
 * Approvers have a different job: they do not browse a register, they work an inbox of assignments.
 * Separating the surface keeps the permission honest — {@code approval:view} reveals assignments,
 * {@code approval:act} permits an action on one — and matches the BRD's navigation ("My Approvals").
 *
 * <h2>Assignment, not permission, decides what appears</h2>
 * The inbox query filters by assignment (named employee, department, or a committee office the caller
 * holds). A user with the approval permission who holds no assignment sees an empty inbox, which is
 * the intended behaviour: the BRD requires approval authority to be an assignment, not a role.
 */
@RestController
@RequestMapping(path = "/api/v1/approvals", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Approvals", description = "Approval inbox and decisions (FM-03, FM-06)")
public class ApprovalController {

	private final ApprovalTaskRepository tasks;

	private final ApprovalEngine engine;

	private final InstitutionContext institutionContext;

	private final CommitteeMemberRepository committeeMembers;

	private final com.edam.iam.UserMembershipRepository memberships;

	public ApprovalController(ApprovalTaskRepository tasks, ApprovalEngine engine,
			InstitutionContext institutionContext, CommitteeMemberRepository committeeMembers,
			com.edam.iam.UserMembershipRepository memberships) {
		this.tasks = tasks;
		this.engine = engine;
		this.institutionContext = institutionContext;
		this.committeeMembers = committeeMembers;
		this.memberships = memberships;
	}

	@GetMapping
	@EdamPermission(Permissions.APPROVAL_VIEW)
	@Operation(summary = "List the caller's pending approvals")
	public ApiResponse<PageResponse<ApprovalInboxItem>> inbox(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
			HttpServletRequest request) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		EdamPrincipal principal = this.institutionContext.currentPrincipal()
			.orElseThrow(() -> new com.edam.common.exception.BusinessException(
					com.edam.common.api.ErrorCode.UNAUTHENTICATED));
		String employeeId = principal.employeeId();
		String departmentCode = departmentOf(institutionId, employeeId);
		List<String> roles = committeeRoles(institutionId, employeeId);
		var pageable = PageQuery.of(page, size, sort).toPageable(Sort.by(Sort.Order.asc("createdAt")));
		var inbox = this.tasks.findInbox(institutionId, employeeId, departmentCode, roles, pageable);
		return ApiResponse.success(PageResponse.of(inbox, task -> {
			ExpenseRequest expenseRequest = task.getRequest();
			return new ApprovalInboxItem(task.getId(), task.getStepCode(), task.getStepLabel(),
					task.getApproverRole(), task.getSequenceNumber(), task.getAssignedEmployeeId(),
					task.getAssignedDepartment(), ExpenseRequestController.toResponse(expenseRequest));
		}), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/{requestId}/approve", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Approve the caller's pending task")
	public ApiResponse<ExpenseRequestResponse> approve(@PathVariable Long requestId,
			@Valid @RequestBody DecisionRequest body, HttpServletRequest request) {
		return ApiResponse.success(ExpenseRequestController.toResponse(this.engine.approve(requestId, body.comment())),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/{requestId}/reject", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Reject the caller's pending task")
	public ApiResponse<ExpenseRequestResponse> reject(@PathVariable Long requestId,
			@Valid @RequestBody DecisionRequest body, HttpServletRequest request) {
		return ApiResponse.success(ExpenseRequestController.toResponse(this.engine.reject(requestId, body.comment())),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/{requestId}/revert", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Return the request to the requester for correction")
	public ApiResponse<ExpenseRequestResponse> revert(@PathVariable Long requestId,
			@Valid @RequestBody DecisionRequest body, HttpServletRequest request) {
		return ApiResponse.success(ExpenseRequestController.toResponse(this.engine.revert(requestId, body.comment())),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/{requestId}/forward", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Forward the caller's pending task to another employee")
	public ApiResponse<ExpenseRequestResponse> forward(@PathVariable Long requestId,
			@Valid @RequestBody ForwardRequest body, HttpServletRequest request) {
		ExpenseRequest forwarded = this.engine.forward(requestId, body.comment(), body.toEmployeeId());
		return ApiResponse.success(ExpenseRequestController.toResponse(forwarded), CorrelationId.current(),
				request.getRequestURI());
	}

	private String departmentOf(Long institutionId, String employeeId) {
		return this.memberships.findByInstitutionIdAndEmployeeId(institutionId, employeeId)
			.map(com.edam.iam.UserMembership::getDepartmentCode)
			.orElse(null);
	}

	/**
	 * Committee offices the caller holds, expressed as the role tokens the inbox query matches.
	 *
	 * <p>Only offices that are not otherwise addressed by employee id are listed: a committee member's
	 * tasks are created per member, so this is a safety net for role-addressed tasks rather than the
	 * primary assignment path.
	 */
	private List<String> committeeRoles(Long institutionId, String employeeId) {
		return this.committeeMembers.findByInstitutionIdAndEmployeeIdAndActiveTrue(institutionId, employeeId)
			.map(member -> switch (member.getDesignation()) {
				case CEO -> List.of("CEO");
				case CFO -> List.of("CFO");
				case HEAD_OF_ADMIN_GSD -> List.of("HEAD_OF_ADMIN_GSD");
				case MEMBER -> List.<String>of();
			})
			.orElse(List.of());
	}

	/** One inbox row: the task plus enough of the request to decide without a second call. */
	public record ApprovalInboxItem(Long taskId, String stepCode, String stepLabel, String approverRole,
			int sequence, String assignedEmployeeId, String assignedDepartment, ExpenseRequestResponse request) {
	}

	/** Remarks are mandatory for every decision (BRD §8.4). */
	public record DecisionRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 2000) String comment) {
	}

	/** Forwarding requires both a recipient and remarks (BRD §8.4). */
	public record ForwardRequest(
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 2000) String comment,
			@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) String toEmployeeId) {
	}

}
