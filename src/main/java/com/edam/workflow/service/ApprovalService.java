package com.edam.workflow.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.edam.common.api.ErrorCode;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPrincipal;
import com.edam.communication.model.CommunicationEvent;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestResponse;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.repository.ExpenseRequestRepository;
import com.edam.expenserequest.service.ExpenseRequestService;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import com.edam.workflow.dto.ApprovalDtos.ActionAvailability;
import com.edam.workflow.dto.ApprovalDtos.ActionResponse;
import com.edam.workflow.dto.ApprovalDtos.ApprovalCaseFile;
import com.edam.workflow.dto.ApprovalDtos.ApprovalTaskSummary;
import com.edam.workflow.dto.ApprovalDtos.StepProgress;
import com.edam.workflow.dto.ApprovalDtos.TaskProgress;
import com.edam.workflow.dto.ApprovalDtos.WorkflowEventView;
import com.edam.workflow.model.WorkflowAction;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.model.WorkflowTransition;
import com.edam.workflow.model.WorkflowVersion;
import com.edam.workflow.repository.ActionsRepository;
import com.edam.workflow.repository.InstancesRepository;
import com.edam.workflow.repository.StatesRepository;
import com.edam.workflow.repository.StepsRepository;
import com.edam.workflow.repository.TasksRepository;
import com.edam.workflow.repository.TransitionsRepository;
import com.edam.workflow.repository.VersionsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The approval inbox and the four approval actions (Module I, BRD 7.3 and 8.5).
 *
 * <h2>Assignment is checked here, and a permission is not assignment</h2>
 * BRD 24 states the rule this service exists to enforce: "A valid role alone shall not grant approval
 * authority for a request unless the workflow assigns or permits that user to act."
 *
 * <p>{@code approval:act} is therefore checked at the endpoint, and this service additionally requires an
 * <em>open task addressed to the caller</em>, directly or through a role they currently hold. An
 * administrator holding every permission still cannot approve a task that is not theirs. The two checks are
 * different questions — "may this person approve anything?" and "is this person the approver here?" — and a
 * system that answered only the first would let any approver act on any request.
 *
 * <h2>Why every action is one transaction</h2>
 * An approval mutates a task, may open the next step's tasks, may complete the instance, moves the request's
 * status and writes an action row. A partial application of that set is not a recoverable state: a task
 * marked approved with no next task opened is a request that will never be routed again. The
 * {@code @Transactional} boundary is the correctness argument, not an optimisation.
 *
 * <h2>Stale and duplicate actions</h2>
 * Two controls, because they catch different things. The task's {@code @Version} turns a concurrent second
 * click into a concurrent-modification error. An action on an already-completed task is refused by the
 * engine's own state check, naming the status it found. Neither is a courtesy message: a double action would
 * count one approver twice toward a quorum.
 */
@Service
public class ApprovalService {

	private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

	private final TasksRepository tasks;

	private final InstancesRepository instances;

	private final VersionsRepository versions;

	private final StatesRepository states;

	private final StepsRepository steps;

	private final TransitionsRepository transitions;

	private final ActionsRepository actions;

	private final ExpenseRequestRepository requests;

	private final ExpenseRequestService requestService;

	private final UserRepository users;

	private final WorkflowEngine engine;

	private final AuditRecorder audit;

	private final com.edam.rolemanagement.repository.RoleRepository roles;

	private final com.edam.communication.service.NotificationDispatcher notifications;

	public ApprovalService(TasksRepository tasks, InstancesRepository instances, VersionsRepository versions,
			StatesRepository states, StepsRepository steps, TransitionsRepository transitions,
			ActionsRepository actions, ExpenseRequestRepository requests, ExpenseRequestService requestService,
			UserRepository users, com.edam.rolemanagement.repository.RoleRepository roles, WorkflowEngine engine,
			AuditRecorder audit,
			com.edam.communication.service.NotificationDispatcher notifications) {
		this.tasks = tasks;
		this.instances = instances;
		this.versions = versions;
		this.states = states;
		this.steps = steps;
		this.transitions = transitions;
		this.actions = actions;
		this.requests = requests;
		this.requestService = requestService;
		this.users = users;
		this.engine = engine;
		this.roles = roles;
		this.audit = audit;
		this.notifications = notifications;
	}

	// ---------------------------------------------------------------------------------------------
	// Inbox
	// ---------------------------------------------------------------------------------------------

	/**
	 * The caller's pending approvals, newest first.
	 *
	 * <p>Includes tasks addressed to a role the caller currently holds. Role membership is resolved here,
	 * once, rather than joined in SQL, because "current" is the identity layer's definition (an expired
	 * grant is not a membership) and duplicating that predicate in a query is how the two drift apart.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ApprovalTaskSummary> inbox(EdamPrincipal caller, Pageable pageable) {
		Set<UUID> roleIds = caller.roleCodes() == null ? Set.of() : currentRoleIds(caller);
		Page<WorkflowTask> page = this.tasks.findInbox(caller.userId(), roleIds, pageable);
		return PageResponse.of(page, this::summarise);
	}

	/** How many tasks await the caller. Used for the badge the requirement's navigation implies. */
	@Transactional(readOnly = true)
	public long inboxCount(EdamPrincipal caller) {
		return this.tasks.countByAssignedUserIdAndStatus(caller.userId(), WorkflowTask.TaskStatus.PENDING);
	}

	private Set<UUID> currentRoleIds(EdamPrincipal caller) {
		// The principal carries role codes, not ids; resolve the ids once for this request.
		Set<String> codes = caller.roleCodes();
		if (codes.isEmpty()) {
			return Set.of();
		}
		Set<UUID> ids = new LinkedHashSet<>();
		for (String code : codes) {
			this.rolesByCode(code).ifPresent(ids::add);
		}
		return ids;
	}

	private Optional<UUID> rolesByCode(String code) {
		return this.roles.findByCode(code).map(role -> role.getId());
	}

	private ApprovalTaskSummary summarise(WorkflowTask task) {
		WorkflowInstance instance = this.instances.findById(task.getWorkflowInstanceId()).orElse(null);
		if (instance == null) {
			// A task whose instance vanished is not a task anyone can act on. Reporting it with nulls would
			// put an unopenable row in the inbox.
			return null;
		}
		ExpenseRequest request = this.requests.findById(instance.getExpenseRequestId()).orElse(null);
		WorkflowStep step = this.steps.findById(task.getWorkflowStepId()).orElse(null);
		return ApprovalTaskSummary.from(task, instance.getExpenseRequestId(),
				request == null ? null : request.getRequestNumber(),
				request == null ? null : request.getCurrentStatusCode().name(),
				request == null ? null : classificationOf(request),
				request == null ? null : request.getDepartmentId(),
				request == null ? null : request.getRequestedAmount(),
				request == null ? null : request.getCurrencyId(),
				step == null ? null : step.getStepCode(), step == null ? null : step.getStepName(),
				step == null ? 0 : step.getStepOrder());
	}

	private String classificationOf(ExpenseRequest request) {
		// The category decides it; a request without one cannot exist (NOT NULL), but a missing category row
		// would otherwise throw here while rendering an inbox, so it degrades to null.
		return this.requestService.classificationOf(request);
	}

	// ---------------------------------------------------------------------------------------------
	// Case file
	// ---------------------------------------------------------------------------------------------

	/**
	 * Everything an approver needs to decide: the request, the workflow's position, and the history.
	 *
	 * <p>Access is limited to the requester, anyone with an assigned task on this instance (open or past),
	 * and callers with an installation-wide data scope. An approver who acted yesterday must still be able to
	 * read the case they acted on; a stranger must not.
	 */
	@Transactional(readOnly = true)
	public ApprovalCaseFile caseFile(UUID taskId, EdamPrincipal caller) {
		WorkflowTask task = this.tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId));
		WorkflowInstance instance = this.instances.findById(task.getWorkflowInstanceId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", task.getWorkflowInstanceId()));
		ExpenseRequest request = this.requests.findById(instance.getExpenseRequestId())
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", instance.getExpenseRequestId()));
		requireCanRead(instance, request, caller);

		WorkflowVersion version = this.versions.findById(instance.getWorkflowVersionId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowVersion", instance.getWorkflowVersionId()));
		WorkflowState current = instance.getCurrentStateId() == null ? null
				: this.states.findById(instance.getCurrentStateId()).orElse(null);

		List<WorkflowTask> allTasks = this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId());
		List<WorkflowStep> orderedSteps = this.steps.findWithApprovers(version.getId());
		Map<UUID, WorkflowState> statesById = this.states.findByWorkflowVersionId(version.getId())
			.stream()
			.collect(Collectors.toMap(WorkflowState::getId, Function.identity()));

		List<UUID> taskIds = allTasks.stream().map(WorkflowTask::getId).toList();
		List<WorkflowAction> history = this.actions.findForInstance(taskIds);
		Map<UUID, UUID> taskToStep = allTasks.stream()
			.collect(Collectors.toMap(WorkflowTask::getId, WorkflowTask::getWorkflowStepId));
		List<WorkflowEventView> historyViews = history.stream()
			.map(action -> WorkflowEventView.from(action, nameOf(action.getPerformedBy()), stateCode(statesById,
					action.getPreviousStateId()), stateCode(statesById, action.getNextStateId())))
			.toList();

		List<StepProgress> progress = new ArrayList<>();
		for (WorkflowStep step : orderedSteps) {
			List<WorkflowTask> stepTasks = allTasks.stream()
				.filter(candidate -> candidate.getWorkflowStepId().equals(step.getId()))
				.toList();
			long approved = stepTasks.stream()
				.filter(candidate -> candidate.getStatus() == WorkflowTask.TaskStatus.APPROVED)
				.count();
			boolean complete = approved >= step.getMinimumApprovals();
			List<TaskProgress> taskViews = stepTasks.stream()
				.map(candidate -> new TaskProgress(candidate.getId(), nameOf(candidate.getAssignedUserId()),
						candidate.getStatus().name(), candidate.getAssignedAt(), candidate.getCompletedAt()))
				.toList();
			progress.add(new StepProgress(step.getId(), step.getStepCode(), step.getStepName(),
					step.getStepOrder(), step.getApprovalMode().name(), step.getMinimumApprovals(), approved,
					complete, task.getWorkflowStepId().equals(step.getId())
							&& task.getStatus() == WorkflowTask.TaskStatus.PENDING,
					taskViews));
		}

		return new ApprovalCaseFile(task.getId(), instance.getId(), version.getId(), version.getVersionNumber(),
				current == null ? null : current.getStateCode(), current == null ? null : current.getStateName(),
				current == null ? null : current.getStateType().name(), request.getId(),
				request.getRequestNumber(), task.isOpen(), availableActions(task, instance, version, caller),
				progress, historyViews);
	}

	/**
	 * Which actions this caller may take right now, and why not when they may not.
	 *
	 * <p>BRD 8.5: "Actions are visible only for a valid pending assignment and current workflow state." The
	 * reasons are returned rather than a bare boolean so the UI can explain itself and a support call can end
	 * in an answer.
	 */
	private List<ActionAvailability> availableActions(WorkflowTask task, WorkflowInstance instance,
			WorkflowVersion version, EdamPrincipal caller) {
		List<ActionAvailability> available = new ArrayList<>();
		boolean assigned = isAssignedToCaller(task, caller);
		boolean open = task.isOpen() && instance.isRunning();
		for (WorkflowTransition.ActionCode action : List.of(WorkflowTransition.ActionCode.APPROVE,
				WorkflowTransition.ActionCode.REJECT, WorkflowTransition.ActionCode.REVERT,
				WorkflowTransition.ActionCode.FORWARD)) {
			if (!open) {
				available.add(new ActionAvailability(action.name(), false,
						"This task is already " + task.getStatus() + "; it cannot be actioned again."));
				continue;
			}
			if (!assigned) {
				available.add(new ActionAvailability(action.name(), false,
						"This task is assigned to someone else. A permission alone does not confer approval "
								+ "authority."));
				continue;
			}
			Optional<String> refusal = refusalFor(version, instance, action);
			available.add(refusal.map(reason -> new ActionAvailability(action.name(), false, reason))
				.orElseGet(() -> new ActionAvailability(action.name(), true, null)));
		}
		return available;
	}

	/** Why the revision would refuse this action, or empty when it permits it. */
	private Optional<String> refusalFor(WorkflowVersion version, WorkflowInstance instance,
			WorkflowTransition.ActionCode action) {
		List<WorkflowTransition> declared = this.transitions.findByWorkflowVersionId(version.getId());
		boolean configured = declared.stream()
			.anyMatch(transition -> transition.getFromStateId().equals(instance.getCurrentStateId()));
		if (!configured) {
			return Optional.empty();
		}
		return declared.stream()
			.filter(transition -> transition.matches(instance.getCurrentStateId(), action))
			.findFirst()
			.<Optional<String>>map(transition -> transition.getConditionRuleId() == null ? Optional.empty()
					: Optional.of("This transition is conditional, and conditional transitions are not "
							+ "evaluated yet."))
			.orElseGet(() -> Optional.of("This workflow does not permit " + action + " from its current state."));
	}

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	/** Approves the caller's task, advancing the workflow when the step's quorum is met. */
	@Transactional
	public ActionResponse approve(UUID taskId, String remarks, EdamPrincipal caller) {
		return act(taskId, remarks, caller, WorkflowTransition.ActionCode.APPROVE);
	}

	/** Rejects the caller's task. Ends the instance unless the revision declares otherwise. */
	@Transactional
	public ActionResponse reject(UUID taskId, String remarks, EdamPrincipal caller) {
		return act(taskId, remarks, caller, WorkflowTransition.ActionCode.REJECT);
	}

	/** Reverts the caller's task, returning the request for correction. */
	@Transactional
	public ActionResponse revert(UUID taskId, String remarks, EdamPrincipal caller) {
		return act(taskId, remarks, caller, WorkflowTransition.ActionCode.REVERT);
	}

	/**
	 * The shared action path.
	 *
	 * <p>Everything that must be true before an action is checked here, in one place, so the four actions
	 * cannot drift apart in what they require. Order matters: the remarks check comes first because it is the
	 * error a client can fix without understanding the workflow, and the assignment check comes last because
	 * it is the security decision.
	 */
	private ActionResponse act(UUID taskId, String remarks, EdamPrincipal caller,
			WorkflowTransition.ActionCode action) {
		if (remarks == null || remarks.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"Remarks are required when you " + action.name().toLowerCase(java.util.Locale.ROOT) + " a task.");
		}
		WorkflowTask task = this.tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId));
		WorkflowInstance instance = this.instances.findById(task.getWorkflowInstanceId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", task.getWorkflowInstanceId()));
		// Assignment before state: a caller who is not the approver should learn that, not be told the task is
		// already closed (which would leak that it was).
		requireAssigned(task, caller);
		ExpenseRequest request = this.requests.findById(instance.getExpenseRequestId())
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", instance.getExpenseRequestId()));

		// Capture a minimal BEFORE snapshot of the fields we will record as changed. Remarks are
		// not part of the prior task state (they are input to this action), so before.remarks is
		// omitted. The engine mutates instance/task state, so this snapshot must be taken before
		// engine calls.
		java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
		before.put("taskStatus", task.getStatus().name());
		before.put("requestStatus", request.getCurrentStatusCode().name());
		if (instance.getCurrentStateId() != null) {
			before.put("workflowStateId", instance.getCurrentStateId());
		}
		before.put("workflowCompleted", instance.getCompletedAt() != null);

		WorkflowEngine.AssignmentContext context = new WorkflowEngine.AssignmentContext(request.getDepartmentId(),
			request.getRequestedBy(), null);
		String correlationId = CorrelationId.current();
		WorkflowEngine.ActionOutcome outcome = switch (action) {
			case APPROVE -> this.engine.approve(instance, task, caller.userId(), remarks.trim(), correlationId,
					context);
			case REJECT -> this.engine.reject(instance, task, caller.userId(), remarks.trim(), correlationId,
					context);
			case REVERT -> this.engine.revert(instance, task, caller.userId(), remarks.trim(), correlationId);
			default -> throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					action + " is not an action this endpoint performs.");
		};

		applyOutcomeToRequest(request, instance, outcome);
		this.requests.save(request);
		// Record the action with redacted remarks and resulting state. Workflow_actions remains the
		// authoritative per-step history; this event is the link from the central audit trail to
		// that history, and is what makes timeline construction for approvals reliable without
		// joining every module. Remarks are passed through recordChange so they are redacted by
		// the existing AuditValueRedactor.
		java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
		after.put("remarks", remarks.trim());
		after.put("taskStatus", task.getStatus().name());
		after.put("requestStatus", request.getCurrentStatusCode().name());
		if (instance.getCurrentStateId() != null) {
			after.put("workflowStateId", instance.getCurrentStateId());
		}
		after.put("workflowCompleted", outcome.workflowCompleted());
		this.audit.recordChange(auditActionFor(action), "WorkflowTask", taskId, before, after);
		log.info("approval.{} taskId={} instanceId={} requestId={} actor={} completed={}", action, taskId,
				instance.getId(), request.getId(), caller.userId(), outcome.workflowCompleted());
		fireActionNotifications(action, request, caller.userId(), remarks, outcome);
		return response(task, outcome, request);
	}

	private void fireActionNotifications(WorkflowTransition.ActionCode action, ExpenseRequest request,
			UUID actorId, String remarks, WorkflowEngine.ActionOutcome outcome) {
		String num = request.getRequestNumber();
		UUID reqId = request.getId();
		String link = "/requests/" + reqId;
		UUID requester = request.getRequestedBy();
		Map<String, String> vars = java.util.Map.of("requestId", num, "remarks", remarks == null ? "" : remarks.trim());
		try {
			switch (action) {
				case APPROVE -> {
					if (outcome.workflowCompleted()) {
						notifySafely(CommunicationEvent.EXPENSE_APPROVED, requester,
								"Expense " + num + " approved",
								"Your expense request " + num + " has been fully approved.", link, reqId, vars);
					}
				}
				case REJECT -> {
					notifySafely(CommunicationEvent.EXPENSE_REJECTED, requester,
							"Expense " + num + " rejected",
							"Your expense request " + num + " was rejected."
								+ (remarks == null ? "" : " Reason: " + remarks.trim()),
							link, reqId, vars);
				}
				case REVERT -> {
					notifySafely(CommunicationEvent.EXPENSE_RETURNED, requester,
							"Expense " + num + " returned for correction",
							"Your expense request " + num + " was returned for correction."
								+ (remarks == null ? "" : " Reason: " + remarks.trim()),
							link, reqId, vars);
				}
			}
			// Notify any newly-assigned approvers (next step) and also the first approver on submit.
			if (outcome.openedTasks() != null) {
				for (WorkflowTask opened : outcome.openedTasks()) {
					if (opened.getAssignedUserId() != null) {
						notifySafely(CommunicationEvent.APPROVAL_REQUIRED, opened.getAssignedUserId(),
								"Approval required: " + num,
								"Expense request " + num + " is awaiting your approval.",
								link, reqId, vars);
					}
				}
			}
		} catch (Exception ex) {
			log.warn("notification.dispatch_failed action={} request={} reason={}", action, reqId, ex.getMessage());
		}
	}

	private void notifySafely(CommunicationEvent event, UUID userId, String title, String body,
			String link, UUID refId, Map<String, String> vars) {
		if (userId == null || this.notifications == null) return;
		try {
			this.notifications.dispatch(event, userId, vars == null ? Map.of() : vars, title, body, link, refId);
		} catch (Exception ex) {
			log.warn("notification.dispatch_failed event={} user={} reason={}", event, userId, ex.getMessage());
		}
	}

	/**
	 * Forwards the caller's task to a named recipient.
	 *
	 * <p>Separate from {@link #act} because it is the one action that opens a task instead of closing the
	 * chain: it closes the caller's task and opens an equivalent one in the same step, so the delegate's
	 * approval counts toward the same quorum. BRD 8.5 requires both a recipient and remarks.
	 */
	@Transactional
	public ActionResponse forward(UUID taskId, UUID recipientUserId, String remarks, EdamPrincipal caller) {
		if (recipientUserId == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A recipient is required to forward a task.");
		}
		if (remarks == null || remarks.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Remarks are required to forward a task.");
		}
		WorkflowTask task = this.tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId));
		WorkflowInstance instance = this.instances.findById(task.getWorkflowInstanceId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", task.getWorkflowInstanceId()));
		requireAssigned(task, caller);
		User recipient = this.users.findById(recipientUserId)
			.orElseThrow(() -> new ResourceNotFoundException("User", recipientUserId));
		if (!recipient.isActive()) {
			// A task forwarded to a deactivated account would sit unactioned forever; the same reasoning the
			// assignment resolver applies to a vacant office applies to a direct forward.
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"That user is deactivated and cannot act on an approval.");
		}
		ExpenseRequest request = this.requests.findById(instance.getExpenseRequestId())
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", instance.getExpenseRequestId()));

		// BEFORE snapshot: the task being forwarded is currently PENDING and assigned to caller;
		// after forward it is closed (FORWARDED) and new tasks are opened for the recipient.
		java.util.Map<String, Object> before = new java.util.LinkedHashMap<>();
		before.put("taskStatus", task.getStatus().name());
		before.put("assignedUserId", task.getAssignedUserId());
		before.put("assignedRoleId", task.getAssignedRoleId());

		List<WorkflowTask> opened = this.engine.forward(instance, task, recipientUserId, caller.userId(),
				remarks.trim(), CorrelationId.current());
		java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
		after.put("remarks", remarks.trim());
		after.put("taskStatus", task.getStatus().name());
		after.put("forwardedToUserId", recipientUserId);
		after.put("openedTaskIds", opened.stream().map(WorkflowTask::getId).toList());
		this.audit.recordChange(AuditAction.FORWARD, "WorkflowTask", taskId, before, after);
		log.info("approval.forward taskId={} to={} actor={}", taskId, recipientUserId, caller.userId());
		String num = request.getRequestNumber();
		notifySafely(CommunicationEvent.EXPENSE_FORWARDED, recipientUserId,
				"Expense " + num + " forwarded to you for approval",
				"Expense request " + num + " has been forwarded to you for approval.",
				"/requests/" + request.getId(), request.getId(),
				java.util.Map.of("requestId", num));
		notifySafely(CommunicationEvent.EXPENSE_FORWARDED, request.getRequestedBy(),
				"Expense " + num + " forwarded",
				"Your expense request " + num + " was forwarded to another approver.",
				"/requests/" + request.getId(), request.getId(),
				java.util.Map.of("requestId", num));
		WorkflowState state = instance.getCurrentStateId() == null ? null
				: this.states.findById(instance.getCurrentStateId()).orElse(null);
		return new ActionResponse(task.getId(), task.getStatus().name(),
				state == null ? null : state.getStateCode(), state == null ? null : state.getStateType().name(),
				false, request.getId(), request.getCurrentStatusCode().name(),
				opened.stream().map(WorkflowTask::getId).toList());
	}

	/**
	 * Projects the engine's outcome onto the request's own status.
	 *
	 * <p>The two state machines are deliberately separate — the workflow's states describe steps, the
	 * request's describe the business object — so one must be translated into the other, and this is the only
	 * place that translation happens.
	 */
	private void applyOutcomeToRequest(ExpenseRequest request, WorkflowInstance instance,
			WorkflowEngine.ActionOutcome outcome) {
		if (outcome.workflowCompleted()) {
			switch (outcome.state().getStateType()) {
				case APPROVED -> request.markDecided(ExpenseRequest.Status.APPROVED);
				case REJECTED -> request.markDecided(ExpenseRequest.Status.REJECTED);
				case CANCELLED -> request.cancel(instance.getStartedBy(), "The workflow was cancelled.",
						java.time.Instant.now());
				case REVERSED -> request.markDecided(ExpenseRequest.Status.REVERSED);
				default -> request.markDecided(ExpenseRequest.Status.REJECTED);
			}
			return;
		}
		switch (outcome.state().getStateType()) {
			case INITIAL -> request.markReturned();
			// The first approval records an action without finishing the step; the request is in approval
			// either way, so this is idempotent after the first transition.
			case IN_PROGRESS -> request.markInProgress();
			default -> request.markInProgress();
		}
	}

	private ActionResponse response(WorkflowTask task, WorkflowEngine.ActionOutcome outcome,
			ExpenseRequest request) {
		return new ActionResponse(task.getId(), task.getStatus().name(), outcome.state().getStateCode(),
				outcome.state().getStateType().name(), outcome.workflowCompleted(), request.getId(),
				request.getCurrentStatusCode().name(),
				outcome.openedTasks().stream().map(WorkflowTask::getId).toList());
	}

	private static AuditAction auditActionFor(WorkflowTransition.ActionCode action) {
		return switch (action) {
			case APPROVE -> AuditAction.APPROVE;
			case REJECT -> AuditAction.REJECT;
			case REVERT -> AuditAction.REVERT;
			case FORWARD -> AuditAction.FORWARD;
			default -> AuditAction.UPDATE;
		};
	}

	// ---------------------------------------------------------------------------------------------
	// Authorization
	// ---------------------------------------------------------------------------------------------

	/**
	 * Requires an open task addressed to the caller.
	 *
	 * <p>This is the check the BRD's section 24 turns on. It is deliberately not satisfied by any permission.
	 */
	private void requireAssigned(WorkflowTask task, EdamPrincipal caller) {
		if (!task.isOpen()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"That task is already " + task.getStatus() + " and cannot be actioned again.");
		}
		if (!isAssignedToCaller(task, caller)) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED,
					"That task is assigned to another approver. Holding an approval permission does not confer "
							+ "authority over a task you were not assigned.");
		}
	}

	/** True when the task names the caller directly, or names a role the caller currently holds. */
	private boolean isAssignedToCaller(WorkflowTask task, EdamPrincipal caller) {
		if (caller.userId().equals(task.getAssignedUserId())) {
			return true;
		}
		if (task.getAssignedRoleId() == null) {
			return false;
		}
		Set<String> codes = caller.roleCodes();
		if (codes == null || codes.isEmpty()) {
			return false;
		}
		for (String code : codes) {
			if (rolesByCode(code).filter(id -> id.equals(task.getAssignedRoleId())).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** Reading a case file: the requester, anyone with a task on it, or an installation-wide scope. */
	private void requireCanRead(WorkflowInstance instance, ExpenseRequest request, EdamPrincipal caller) {
		if (caller.hasInstallationScope() || caller.userId().equals(request.getRequestedBy())) {
			return;
		}
		boolean hasTask = this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instance.getId()).stream()
			.anyMatch(task -> isAssignedToCaller(task, caller));
		if (!hasTask) {
			// 404 rather than 403 for the same reason the request endpoints use it: a task id is not a secret,
			// but confirming which ones exist is information the caller has no business having.
			throw new ResourceNotFoundException("WorkflowTask", instance.getId());
		}
	}

	private String nameOf(UUID userId) {
		if (userId == null) {
			return null;
		}
		return this.users.findById(userId).map(User::displayName).orElse(null);
	}

	private String stateCode(Map<UUID, WorkflowState> statesById, UUID stateId) {
		if (stateId == null) {
			return null;
		}
		WorkflowState state = statesById.get(stateId);
		return state == null ? null : state.getStateCode();
	}

	/** Exposed so the request endpoints can render the pinned workflow without reaching into repositories. */
	@Transactional(readOnly = true)
	public Optional<WorkflowProgress> progressOf(UUID requestId) {
		return this.instances.findByExpenseRequestId(requestId).map(instance -> {
			WorkflowVersion version = this.versions.findById(instance.getWorkflowVersionId()).orElse(null);
			WorkflowState state = instance.getCurrentStateId() == null ? null
					: this.states.findById(instance.getCurrentStateId()).orElse(null);
			return new WorkflowProgress(instance.getId(), instance.getWorkflowVersionId(),
					version == null ? 0 : version.getVersionNumber(), instance.isRunning(),
					state == null ? null : state.getStateCode(), state == null ? null : state.getStateName());
		});
	}

	/** Where a request sits in its pinned workflow. */
	public record WorkflowProgress(UUID instanceId, UUID workflowVersionId, int versionNumber, boolean running,
			String stateCode, String stateName) {
	}

	/**
	 * The request behind an approval task, as the approver may see it.
	 *
	 * <p>Read through the Phase 2 service so the visibility rules are the same ones the request endpoints
	 * apply. An approver assigned to a task on a request outside their data scope would otherwise be unable
	 * to read the thing they are being asked to approve, which is why assignment is added to the Phase 2
	 * rule rather than replacing it.
	 */
	@Transactional(readOnly = true)
	public RequestResponse requestForTask(UUID taskId, EdamPrincipal caller) {
		WorkflowTask task = this.tasks.findById(taskId)
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", taskId));
		WorkflowInstance instance = this.instances.findById(task.getWorkflowInstanceId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", task.getWorkflowInstanceId()));
		ExpenseRequest request = this.requests.findById(instance.getExpenseRequestId())
			.orElseThrow(() -> new ResourceNotFoundException("ExpenseRequest", instance.getExpenseRequestId()));
		requireCanRead(instance, request, caller);
		return this.requestService.getForAssignedApprover(request.getId());
	}

}
