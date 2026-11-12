package com.edam.approval;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.audit.Auditor;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.configplane.category.ApproverType;
import com.edam.configplane.category.DelegationMatrixService;
import com.edam.configplane.committee.CommitteeDesignation;
import com.edam.configplane.committee.CommitteeLevel;
import com.edam.configplane.committee.CommitteeLevelRepository;
import com.edam.configplane.committee.CommitteeMember;
import com.edam.configplane.committee.CommitteeMemberRepository;
import com.edam.configplane.authority.RecommendingAuthority;
import com.edam.configplane.authority.RecommendingAuthorityRepository;
import com.edam.expense.ExpenseRequest;
import com.edam.expense.ExpenseRequestRepository;
import com.edam.expense.RequestHistoryEntry;
import com.edam.expense.RequestHistoryRepository;
import com.edam.expense.RequestState;
import com.edam.iam.UserMembership;
import com.edam.iam.UserMembershipRepository;
import com.edam.workflow.WorkflowService;
import com.edam.workflow.WorkflowSteps;
import com.edam.workflow.WorkflowVersion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives a request through its pinned workflow: assignment, action, quorum and completion
 * (BRD §8.3..8.5, §7.4).
 *
 * <h2>What this class is responsible for</h2>
 * <ol>
 *   <li><strong>Assignment.</strong> Turning a workflow step into concrete {@link ApprovalTask} rows:
 *       department-addressed, employee-addressed, office-addressed or committee-addressed.</li>
 *   <li><strong>Action authorization.</strong> Deciding whether <em>this</em> caller may act on
 *       <em>this</em> task right now. Every action re-derives the answer; nothing is cached in the
 *       session.</li>
 *   <li><strong>Quorum.</strong> Counting recorded approvals at the step against the workflow's
 *       quorum and, for the Expense Committee, the configured level's minimum approvals — the stricter
 *       of the two.</li>
 *   <li><strong>Progression.</strong> Advancing to the next step, completing, rejecting or returning
 *       the request, and writing the history entry for each.</li>
 * </ol>
 *
 * <h2>The four checks that must all pass before any action</h2>
 * <ol>
 *   <li>The caller is authenticated and acting for the request's institution (tenant check).</li>
 *   <li>The task is {@code PENDING} — a task already acted on cannot be acted on again, and the
 *       attempt is reported rather than silently ignored (BRD: "duplicate action attempts are
 *       rejected safely", line 1277).</li>
 *   <li>The caller is the assignee: the named employee, a member of the addressed department, the
 *       holder of the addressed office, or a selected member of the addressed committee level.</li>
 *   <li>The action is permitted <em>to that role</em>: the CEO may approve or revert but must not
 *       reject an Expense Committee request (BRD §8.5), and an approver cannot vote twice at one
 *       step.</li>
 * </ol>
 *
 * <h2>What is deliberately not implemented</h2>
 * <ul>
 *   <li><strong>Selective revert to an arbitrary previous approver</strong>: the BRD states
 *       eligibility criteria are an open decision (§7.4). Revert therefore targets the last Phase-1
 *       approver, which is the one behaviour the BRD does specify.</li>
 *   <li><strong>Phase-2 resubmission state machine</strong>: the BRD says the exact target state
 *       machine must be approved (line 837). A returned request is editable and resubmittable; that
 *       is all the current requirements support.</li>
 * </ul>
 */
@Service
public class ApprovalEngine {

	private static final Logger log = LoggerFactory.getLogger(ApprovalEngine.class);

	private final ApprovalTaskRepository tasks;

	private final ExpenseRequestRepository requests;

	private final RequestHistoryRepository history;

	private final WorkflowService workflows;

	private final DelegationMatrixService delegation;

	private final RecommendingAuthorityRepository authorities;

	private final CommitteeMemberRepository committeeMembers;

	private final CommitteeLevelRepository committeeLevels;

	private final UserMembershipRepository memberships;

	private final InstitutionContext institutionContext;

	private final AuditRecorder audit;

	private final Auditor auditor;

	public ApprovalEngine(ApprovalTaskRepository tasks, ExpenseRequestRepository requests,
			RequestHistoryRepository history, WorkflowService workflows, DelegationMatrixService delegation,
			RecommendingAuthorityRepository authorities, CommitteeMemberRepository committeeMembers,
			CommitteeLevelRepository committeeLevels, UserMembershipRepository memberships,
			InstitutionContext institutionContext, AuditRecorder audit, Auditor auditor) {
		this.tasks = tasks;
		this.requests = requests;
		this.history = history;
		this.workflows = workflows;
		this.delegation = delegation;
		this.authorities = authorities;
		this.committeeMembers = committeeMembers;
		this.committeeLevels = committeeLevels;
		this.memberships = memberships;
		this.institutionContext = institutionContext;
		this.audit = audit;
		this.auditor = auditor;
	}

	// ------------------------------------------------------------------
	// Starting a workflow
	// ------------------------------------------------------------------

	/**
	 * Open the first task of a freshly submitted request.
	 *
	 * <p>Called by the submission operation after the version has been pinned. Idempotent in the sense
	 * that it will refuse to open a second task at the same sequence: the database's partial unique
	 * index is the guarantee, and the refusal surfaces as a business error rather than a duplicate
	 * approval later.
	 */
	@Transactional
	public ApprovalTask openFirstTask(ExpenseRequest request) {
		WorkflowVersion version = request.getWorkflowVersion();
		if (version == null) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This request has no pinned workflow version and cannot be routed.");
		}
		WorkflowSteps steps = this.workflows.stepsOf(version);
		return openStep(request, steps.first());
	}

	// ------------------------------------------------------------------
	// Actions
	// ------------------------------------------------------------------

	/** Approve the caller's task. */
	@Transactional
	public ExpenseRequest approve(Long requestId, String comment) {
		return act(requestId, ApprovalTaskStatus.APPROVED, comment, null);
	}

	/** Reject the caller's task. */
	@Transactional
	public ExpenseRequest reject(Long requestId, String comment) {
		return act(requestId, ApprovalTaskStatus.REJECTED, comment, null);
	}

	/** Return the request to the requester (or, for an EC revert, to the last Phase-1 approver). */
	@Transactional
	public ExpenseRequest revert(Long requestId, String comment) {
		return act(requestId, ApprovalTaskStatus.REVERTED, comment, null);
	}

	/** Forward the caller's task to another employee. */
	@Transactional
	public ExpenseRequest forward(Long requestId, String comment, String toEmployeeId) {
		return act(requestId, ApprovalTaskStatus.FORWARDED, comment, toEmployeeId);
	}

	private ExpenseRequest act(Long requestId, ApprovalTaskStatus action, String comment, String forwardTo) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		ExpenseRequest request = this.requests.findByIdAndInstitutionId(requestId, institutionId)
			.orElseThrow(() -> new ResourceNotFoundException("Expense request", requestId));
		ApprovalTask task = this.tasks.findOpenTask(institutionId, requestId)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"This request has no approval task awaiting action."));
		requireComment(action, comment);
		String employeeId = requireEmployeeId();
		requireAssignee(task, employeeId, request);
		requireActionPermittedForRole(task, action, employeeId, institutionId);
		requireNotAlreadyActed(task, employeeId, institutionId);

		Instant now = Instant.now();
		String fromState = request.getState().name();
		switch (action) {
			case APPROVED -> handleApproval(request, task, comment, employeeId, now, fromState);
			case REJECTED -> handleRejection(request, task, comment, employeeId, now, fromState);
			case REVERTED -> handleRevert(request, task, comment, employeeId, now, fromState);
			case FORWARDED -> handleForward(request, task, comment, forwardTo, employeeId, now);
			default -> throw new IllegalStateException("Unsupported action: " + action);
		}
		return request;
	}

	private void handleApproval(ExpenseRequest request, ApprovalTask task, String comment, String employeeId,
			Instant now, String fromState) {
		task.approve(employeeId, this.auditor.username(), comment, now);
		this.audit.record(AuditAction.APPROVE, "ExpenseRequest", request.getId(),
				"Approved at step " + task.getStepCode());
		appendHistory(request, "APPROVE", fromState, request.getState().name(), comment);
		advanceIfSatisfied(request, task);
	}

	private void handleRejection(ExpenseRequest request, ApprovalTask task, String comment, String employeeId,
			Instant now, String fromState) {
		task.reject(employeeId, this.auditor.username(), comment, now);
		request.markRejected(now);
		cancelRemainingTasks(request, now);
		this.audit.record(AuditAction.REJECT, "ExpenseRequest", request.getId(),
				"Rejected at step " + task.getStepCode());
		appendHistory(request, "REJECT", fromState, RequestState.REJECTED.name(), comment);
	}

	private void handleRevert(ExpenseRequest request, ApprovalTask task, String comment, String employeeId,
			Instant now, String fromState) {
		task.revert(employeeId, this.auditor.username(), comment, now);
		request.markReturned(task.getStepCode());
		cancelRemainingTasks(request, now);
		this.audit.record(AuditAction.REVERT, "ExpenseRequest", request.getId(),
				"Reverted at step " + task.getStepCode());
		appendHistory(request, "REVERT", fromState, RequestState.RETURNED.name(), comment);
	}

	private void handleForward(ExpenseRequest request, ApprovalTask task, String comment, String forwardTo,
			String employeeId, Instant now) {
		if (forwardTo == null || forwardTo.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Forwarding requires the employee to forward to.");
		}
		requireActiveMembership(forwardTo);
		if (forwardTo.equals(employeeId)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "You cannot forward a task to yourself.");
		}
		task.forward(employeeId, this.auditor.username(), comment, forwardTo, now);
		// The forwarded task keeps the same step and quorum; the recipient now holds the decision.
		ApprovalTask forwarded = new ApprovalTask(request, task.getStepCode(), task.getStepLabel(),
				task.getRequiredPermission(), task.approverRole(), forwardTo, null, task.getSequenceNumber());
		forwarded.assignInstitution(request.getInstitutionId());
		this.tasks.save(forwarded);
		this.audit.record(AuditAction.FORWARD, "ExpenseRequest", request.getId(),
				"Forwarded at step " + task.getStepCode() + " to " + forwardTo);
		appendHistory(request, "FORWARD", request.getState().name(), request.getState().name(),
				"Forwarded to " + forwardTo + ": " + comment);
	}

	/**
	 * Advance the workflow when the step's quorum is met.
	 *
	 * <p>The quorum is the larger of the workflow step's quorum and, for an Expense Committee step, the
	 * configured level's minimum approvals. Taking the larger means a workflow author cannot lower a
	 * committee rule that the configuration plane already enforces — the two sources of truth can
	 * disagree, and the stricter one wins rather than the more convenient one.
	 */
	private void advanceIfSatisfied(ExpenseRequest request, ApprovalTask completedTask) {
		WorkflowSteps steps = this.workflows.stepsOf(request.getWorkflowVersion());
		WorkflowSteps.Step step = steps.find(completedTask.getStepCode())
			.orElseThrow(() -> new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"The pinned workflow no longer contains step " + completedTask.getStepCode() + "."));
		int required = requiredApprovals(request, step);
		long recorded = this.tasks.countApprovalsAtStep(request.getInstitutionId(), request.getId(),
				step.sequence());
		if (recorded < required) {
			request.markInApproval(step.code());
			return;
		}
		Optional<WorkflowSteps.Step> next = steps.after(step.code());
		if (next.isPresent()) {
			openStep(request, next.get());
			return;
		}
		Instant now = Instant.now();
		request.markApproved(now);
		String fromState = request.getState().name();
		appendHistory(request, "APPROVE_FINAL", fromState, RequestState.APPROVED.name(),
				"All required approvals recorded");
		this.audit.record(AuditAction.APPROVE, "ExpenseRequest", request.getId(), "Request fully approved");
	}

	private int requiredApprovals(ExpenseRequest request, WorkflowSteps.Step step) {
		int required = step.quorum();
		if (step.approver() == ApproverType.EXPENSE_COMMITTEE) {
			Optional<CommitteeLevel> level = resolveCommitteeLevel(request);
			if (level.isPresent()) {
				required = Math.max(required, level.get().getMinimumApprovals());
			}
		}
		return required;
	}

	// ------------------------------------------------------------------
	// Assignment
	// ------------------------------------------------------------------

	private ApprovalTask openStep(ExpenseRequest request, WorkflowSteps.Step step) {
		Long institutionId = request.getInstitutionId();
		ApprovalRole role = ApprovalRole.from(step.approver());
		List<ApprovalTask> opened = new ArrayList<>();
		switch (role) {
			case DEPARTMENT_HEAD -> opened.add(create(request, step, role, null, request.getDepartmentCode()));
			case RECOMMENDING_AUTHORITY -> {
				Set<String> assignees = resolveRecommendingAuthorities(request);
				if (assignees.isEmpty()) {
					throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
							"No active recommending authority is configured for this request, so step '"
									+ step.code() + "' cannot be assigned.");
				}
				for (String employeeId : assignees) {
					opened.add(create(request, step, role, employeeId, null));
				}
			}
			case EXPENSE_COMMITTEE -> {
				CommitteeLevel level = resolveCommitteeLevel(request)
					.orElseThrow(() -> new BusinessException(ErrorCode.PRECONDITION_FAILED,
							"This request routes to the Expense Committee but no committee level covers the "
									+ "amount. Configure a level in the delegation matrix before submitting."));
				if (level.getMembers().isEmpty()) {
					throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
							"The Expense Committee level '" + level.getName() + "' has no members.");
				}
				for (CommitteeMember member : level.getMembers()) {
					if (member.isActive()) {
						opened.add(create(request, step, role, member.getEmployeeId(), null));
					}
				}
			}
			case CEO, CFO, HEAD_OF_ADMIN_GSD -> {
				CommitteeDesignation designation = switch (role) {
					case CEO -> CommitteeDesignation.CEO;
					case CFO -> CommitteeDesignation.CFO;
					default -> CommitteeDesignation.HEAD_OF_ADMIN_GSD;
				};
				String employeeId = this.committeeMembers
					.findFirstByInstitutionIdAndDesignationAndActiveTrue(institutionId, designation)
					.map(CommitteeMember::getEmployeeId)
					.orElseThrow(() -> new BusinessException(ErrorCode.PRECONDITION_FAILED,
							"No active " + designation.displayName()
									+ " is configured, so the step requiring that office cannot be assigned."));
				opened.add(create(request, step, role, employeeId, null));
			}
		}
		if (opened.isEmpty()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"Step '" + step.code() + "' could not be assigned to anyone.");
		}
		request.markInApproval(step.code());
		String fromState = request.getState().name();
		appendHistory(request, "STEP_OPENED", fromState, RequestState.IN_APPROVAL.name(),
				"Step " + step.code() + " opened for " + role);
		return opened.get(0);
	}

	private ApprovalTask create(ExpenseRequest request, WorkflowSteps.Step step, ApprovalRole role,
			String employeeId, String departmentCode) {
		ApprovalTask task = new ApprovalTask(request, step.code(), step.label(), permissionFor(role), role,
				employeeId, departmentCode, step.sequence());
		task.assignInstitution(request.getInstitutionId());
		return this.tasks.save(task);
	}

	/**
	 * Permission a task's assignee must additionally hold. Copied onto the task so history stays
	 * readable, and checked on every action.
	 */
	private static String permissionFor(ApprovalRole role) {
		return switch (role) {
			case CEO -> com.edam.common.security.Permissions.APPROVAL_ACT;
			default -> com.edam.common.security.Permissions.APPROVAL_ACT;
		};
	}

	private Set<String> resolveRecommendingAuthorities(ExpenseRequest request) {
		Long institutionId = request.getInstitutionId();
		Set<String> employeeIds = new LinkedHashSet<>();
		Optional<com.edam.configplane.category.DelegationRule> rule = this.delegation.resolveRule(institutionId,
				request.getExpenseNature(), request.getCategory().getId(),
				request.getSubcategory() == null ? null : request.getSubcategory().getId(),
				request.getAmountInBase());
		rule.ifPresent(found -> found.getRecommendingAuthorities()
			.stream()
			.filter(RecommendingAuthority::isActive)
			.map(RecommendingAuthority::getEmployeeId)
			.forEach(employeeIds::add));
		return employeeIds;
	}

	private Optional<CommitteeLevel> resolveCommitteeLevel(ExpenseRequest request) {
		return this.committeeLevels
			.findByInstitutionIdAndActiveTrueOrderByAmountFromAsc(request.getInstitutionId())
			.stream()
			.filter(level -> level.covers(request.getAmountInBase()))
			.findFirst();
	}

	// ------------------------------------------------------------------
	// Authorization
	// ------------------------------------------------------------------

	private void requireComment(ApprovalTaskStatus action, String comment) {
		boolean requiresComment = action == ApprovalTaskStatus.APPROVED || action == ApprovalTaskStatus.REJECTED
				|| action == ApprovalTaskStatus.REVERTED || action == ApprovalTaskStatus.FORWARDED;
		if (requiresComment && (comment == null || comment.isBlank())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A comment is required for " + action.name().toLowerCase(java.util.Locale.ROOT) + ".");
		}
	}

	private String requireEmployeeId() {
		return this.institutionContext.currentPrincipal()
			.map(EdamPrincipal::employeeId)
			.filter(id -> id != null && !id.isBlank())
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED,
					"Your identity is not linked to an employee record, so approval actions are not available."));
	}

	/**
	 * The caller must be the assignee. Failing this check is reported identically to a missing task,
	 * so probing cannot enumerate assignments it does not hold.
	 */
	private void requireAssignee(ApprovalTask task, String employeeId, ExpenseRequest request) {
		boolean assigned = task.isAssignedTo(employeeId)
				|| task.isAssignedToDepartment(departmentOf(employeeId))
				|| isOfficeHolderOrCommitteeMember(task, employeeId, request);
		if (!assigned) {
			throw new AccessDeniedException("This approval task is not assigned to you.");
		}
	}

	private boolean isOfficeHolderOrCommitteeMember(ApprovalTask task, String employeeId, ExpenseRequest request) {
		if (task.isAssignedTo(employeeId)) {
			return true;
		}
		if (task.getApproverRole() == null) {
			return false;
		}
		ApprovalRole role = task.approverRole();
		if (role == ApprovalRole.EXPENSE_COMMITTEE) {
			// Committee tasks are created per member, so a member sees a task addressed to them.
			// This branch exists for role-addressed tasks created before membership was enumerated.
			return this.committeeMembers.findByInstitutionIdAndEmployeeIdAndActiveTrue(request.getInstitutionId(),
					employeeId).isPresent();
		}
		return false;
	}

	private String departmentOf(String employeeId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.memberships.findByInstitutionIdAndEmployeeId(institutionId, employeeId)
			.map(UserMembership::getDepartmentCode)
			.orElse(null);
	}

	private void requireActiveMembership(String employeeId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		this.memberships.findByInstitutionIdAndEmployeeId(institutionId, employeeId)
			.filter(UserMembership::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
					"The selected employee is not an active member of this institution."));
	}

	/**
	 * Role-specific action restrictions (BRD §8.5): the CEO may approve or revert an Expense Committee
	 * request but must not reject it.
	 */
	private void requireActionPermittedForRole(ApprovalTask task, ApprovalTaskStatus action, String employeeId,
			Long institutionId) {
		ApprovalRole role = task.approverRole();
		if (action == ApprovalTaskStatus.REJECTED && role == ApprovalRole.CEO) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED,
					"The CEO may approve or revert an Expense Committee request but cannot reject it.");
		}
		if (action == ApprovalTaskStatus.REJECTED && role == ApprovalRole.EXPENSE_COMMITTEE) {
			boolean isCeo = this.committeeMembers
				.findByInstitutionIdAndEmployeeIdAndActiveTrue(institutionId, employeeId)
				.map(member -> member.getDesignation() == CommitteeDesignation.CEO)
				.orElse(false);
			if (isCeo) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED,
						"The CEO may approve or revert an Expense Committee request but cannot reject it.");
			}
		}
	}

	private void requireNotAlreadyActed(ApprovalTask task, String employeeId, Long institutionId) {
		if (this.tasks.hasActedAtStep(institutionId, task.getRequest().getId(), task.getSequenceNumber(), employeeId)) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"You have already acted on this request at this step.");
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private void appendHistory(ExpenseRequest request, String action, String fromState, String toState,
			String comment) {
		int sequence = this.history.maxSequence(request.getId()) + 1;
		this.history.save(new RequestHistoryEntry(request.getInstitutionId(), request.getId(), sequence, action,
				fromState, toState, this.auditor.actor(), this.auditor.username(), comment));
	}

	private void cancelRemainingTasks(ExpenseRequest request, Instant when) {
		this.tasks.findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(request.getInstitutionId(), request.getId())
			.stream()
			.filter(ApprovalTask::isPending)
			.forEach(task -> task.cancel(when));
	}

	/** Public read helper used by the inbox and case-file endpoints. */
	@Transactional(readOnly = true)
	public List<ApprovalTask> openTasksOf(Long requestId) {
		Long institutionId = this.institutionContext.requireInstitutionId();
		return this.tasks.findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(institutionId, requestId)
			.stream()
			.filter(ApprovalTask::isPending)
			.toList();
	}

	/** Amount considered for routing: the base-currency figure, so routing is currency-stable. */
	static BigDecimal routingAmount(ExpenseRequest request) {
		return request.getAmountInBase();
	}

}
