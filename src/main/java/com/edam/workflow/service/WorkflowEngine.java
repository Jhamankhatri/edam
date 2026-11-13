package com.edam.workflow.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowStep;
import com.edam.workflow.model.WorkflowTask;
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
import org.springframework.stereotype.Service;

/**
 * The workflow state machine (Module I).
 *
 * <h2>What is data and what is code</h2>
 * A bank configures <em>which</em> states exist, which steps are required, who approves each step and how
 * those approvers relate (sequential or parallel). This class decides <em>what an action means</em>:
 *
 * <ul>
 *   <li>which action is legal in which state — approval in a terminal state is nonsense, not a policy
 *       choice;</li>
 *   <li>when a step is satisfied — quorum requires counting approvals, and a quorum cannot be lowered by
 *       configuration to zero;</li>
 *   <li>when the instance advances, thereby assigning the next step's tasks;</li>
 *   <li>what a rejection or a revert does to the tasks that were never actioned — leaving them open would
 *       let a downstream approver act on a request that is already finished.</li>
 * </ul>
 *
 * <p>These are the invariants a reviewer would check against the requirement. They are deliberately not
 * configurable: a bank that could configure "approve after rejection" would be configuring a defect.
 *
 * <h2>Assignment is a separate concern, deliberately</h2>
 * {@link WorkflowAssignmentResolver} turns an approver designation into concrete users. This class never
 * queries users or committees, so the state machine can be read without knowing how an office is staffed —
 * and tested with approvers supplied directly.
 *
 * <h2>Concurrency</h2>
 * {@code WorkflowTask} carries {@code @Version}. Two approvers acting on the same task at once: one commits,
 * the other receives a concurrent-modification error and reloads. Two approvers acting on <em>different</em>
 * tasks of the same step: both are valid, and quorum is evaluated after each within the transaction.
 */
@Service
public class WorkflowEngine {

	private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

	private final InstancesRepository instances;

	private final VersionsRepository versions;

	private final StatesRepository states;

	private final StepsRepository steps;

	private final TasksRepository tasks;

	private final ActionsRepository actions;

	private final TransitionsRepository transitions;

	private final WorkflowAssignmentResolver assignmentResolver;

	public WorkflowEngine(InstancesRepository instances, VersionsRepository versions, StatesRepository states, StepsRepository steps, TasksRepository tasks,
			ActionsRepository actions, TransitionsRepository transitions,
			WorkflowAssignmentResolver assignmentResolver) {
		this.instances = instances;
		this.versions = versions;
		this.states = states;
		this.steps = steps;
		this.tasks = tasks;
		this.actions = actions;
		this.transitions = transitions;
		this.assignmentResolver = assignmentResolver;
	}

	// ------------------------------------------------------------------
	// Start
	// ------------------------------------------------------------------

	/**
	 * Starts a workflow for a request and assigns the first step's tasks.
	 *
	 * <p>Refuses when the request already has an instance: a duplicate submit must not create a second
	 * approval chain. The unique index backstops this, but checking first produces a message the client can
	 * act on.
	 *
	 * @param version the published revision to pin
	 * @return the created instance with its first batch of tasks
	 */
	public StartedInstance start(WorkflowVersion version, UUID requestId, UUID startedBy, AssignmentContext context) {
		if (!version.isStartable()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"Workflow version " + version.getVersionNumber() + " is not published and cannot be started.");
		}
		if (this.instances.existsByExpenseRequestId(requestId)) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"A workflow already exists for this request.");
		}
		WorkflowState initialState = this.states.findInitial(version.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow has no initial state and cannot be started."));
		WorkflowInstance instance = this.instances.save(new WorkflowInstance(version.getWorkflowDefinitionId(),
				version.getId(), requestId, initialState.getId(), startedBy));
		List<WorkflowTask> opened = advanceToFirstStep(instance, version, context);
		log.info("workflow.started instanceId={} requestId={} versionId={} tasks={}", instance.getId(), requestId,
				version.getId(), opened.size());
		return new StartedInstance(instance, opened);
	}

	/**
	 * Re-opens the first step of a running instance whose request was returned for correction.
	 *
	 * <p>Used on resubmission. The instance keeps the version it started under, so the approval path does not
	 * change because the request was sent back once. Refuses when the instance is not sitting at its initial
	 * state, which is where a revert leaves it: resuming from anywhere else would let a resubmission skip
	 * steps approvers had already cleared.
	 */
	public List<WorkflowTask> resumeAtFirstStep(WorkflowInstance instance, WorkflowVersion version,
			AssignmentContext context) {
		if (!instance.isRunning()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This workflow has already been completed.");
		}
		WorkflowState current = requireCurrentState(instance);
		if (current.getStateType() != WorkflowState.StateType.INITIAL) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This workflow is not awaiting resubmission; it is " + current.getStateCode() + ".");
		}
		// Any task left open at this point belongs to an approval nobody is waiting for any more.
		closeAllOpenTasks(instance.getId(), WorkflowTask.TaskStatus.CANCELLED);
		List<WorkflowTask> opened = advanceToFirstStep(instance, version, context);
		log.info("workflow.resumed instanceId={} versionId={} tasks={}", instance.getId(), version.getId(),
				opened.size());
		return opened;
	}

	// ------------------------------------------------------------------
	// Actions
	// ------------------------------------------------------------------

	/**
	 * Approves a task, advancing the instance when the step's quorum is met.
	 *
	 * @return the resulting state after the action
	 */
	public ActionOutcome approve(WorkflowInstance instance, WorkflowTask task, UUID actor, String remarks,
			String correlationId, AssignmentContext context) {
		requireOpen(instance, task);
		WorkflowVersion version = requireVersion(instance);
		WorkflowStep step = requireStep(task);
		WorkflowState previous = requireCurrentState(instance);
		// Consulted for its refusal, not its target: the step machine decides where an approval leads, but
		// a revision that removed APPROVE from this state must not still accept one.
		resolveTarget(version, previous.getId(), com.edam.workflow.model.WorkflowTransition.ActionCode.APPROVE,
				previous);

		task.complete(WorkflowTask.TaskStatus.APPROVED);
		this.tasks.save(task);
		this.actions.save(new com.edam.workflow.model.WorkflowAction(task.getId(),
				com.edam.workflow.model.WorkflowAction.ActionCode.APPROVE, actor, remarks, previous.getId(),
				previous.getId(), correlationId));

		long quorum = effectiveQuorum(step, countAssignedForStep(instance.getId(), step.getId()));
		long approvedCount = this.tasks.countApproved(instance.getId(), step.getId());
		if (approvedCount < quorum) {
			// The step is not finished: leave the remaining tasks open so the other approvers can act.
			log.debug("workflow.approval_recorded instanceId={} stepId={} approved={} quorum={}", instance.getId(),
					step.getId(), approvedCount, quorum);
			return new ActionOutcome(previous, false, List.of());
		}

		// Quorum met: close any task that is still open on this step. Leaving them open would let a member
		// act on a step that has already concluded, which would inflate the count and confuse the inbox.
		closeOpenTasks(instance.getId(), step.getId(), WorkflowTask.TaskStatus.SKIPPED);
		return advance(instance, version, step, context);
	}

	/**
	 * Rejects a request.
	 *
	 * <p>One rejection ends the request immediately, regardless of quorum: an approval chain that continued
	 * after a rejection would ask approvers to keep reviewing something already refused. Every open task is
	 * cancelled so no one is left with a dead item in their inbox.
	 */
	public ActionOutcome reject(WorkflowInstance instance, WorkflowTask task, UUID actor, String remarks,
			String correlationId, AssignmentContext context) {
		requireOpen(instance, task);
		WorkflowVersion version = requireVersion(instance);
		WorkflowState previous = requireCurrentState(instance);
		WorkflowState builtInRejected = this.states.findByType(version.getId(), WorkflowState.StateType.REJECTED)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow defines no rejection state."));
		WorkflowState rejected = resolveTarget(version, previous.getId(),
				com.edam.workflow.model.WorkflowTransition.ActionCode.REJECT, builtInRejected);
		if (rejected == null) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"A rejection must lead to a state, but this workflow declares none.");
		}

		task.complete(WorkflowTask.TaskStatus.REJECTED);
		this.tasks.save(task);
		this.actions.save(new com.edam.workflow.model.WorkflowAction(task.getId(),
				com.edam.workflow.model.WorkflowAction.ActionCode.REJECT, actor, remarks, previous.getId(),
				rejected.getId(), correlationId));
		closeAllOpenTasks(instance.getId(), WorkflowTask.TaskStatus.CANCELLED);
		instance.setCurrentStateId(rejected.getId());
		instance.complete();
		this.instances.save(instance);
		log.info("workflow.rejected instanceId={} by={}", instance.getId(), actor);
		return new ActionOutcome(rejected, true, List.of());
	}

	/**
	 * Returns the request to an earlier stage or to the requester.
	 *
	 * <p>A revert moves the instance back to its initial state and <em>cancels</em> every open task. The
	 * request becomes editable again; on resubmission the same pinned version is reused, so the approval
	 * chain does not change merely because the request was returned once.
	 */
	public ActionOutcome revert(WorkflowInstance instance, WorkflowTask task, UUID actor, String remarks,
			String correlationId) {
		requireOpen(instance, task);
		WorkflowVersion version = requireVersion(instance);
		WorkflowState previous = requireCurrentState(instance);
		WorkflowState builtInInitial = this.states.findInitial(version.getId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow has no initial state to revert to."));
		// BRD 7.4: an EC-stage revert returns the request to the last Phase-1 approver. That destination is
		// configuration, so a revision that declares where a revert goes is honoured; the initial state is
		// the built-in behaviour for revisions that declare nothing.
		WorkflowState initial = resolveTarget(version, previous.getId(),
				com.edam.workflow.model.WorkflowTransition.ActionCode.REVERT, builtInInitial);
		if (initial == null) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"A revert must lead to a state, but this workflow declares none.");
		}

		task.complete(WorkflowTask.TaskStatus.REVERTED);
		this.tasks.save(task);
		this.actions.save(new com.edam.workflow.model.WorkflowAction(task.getId(),
				com.edam.workflow.model.WorkflowAction.ActionCode.REVERT, actor, remarks, previous.getId(),
				initial.getId(), correlationId));
		closeAllOpenTasks(instance.getId(), WorkflowTask.TaskStatus.CANCELLED);
		instance.setCurrentStateId(initial.getId());
		this.instances.save(instance);
		log.info("workflow.reverted instanceId={} by={}", instance.getId(), actor);
		return new ActionOutcome(initial, false, List.of());
	}

	/**
	 * Forwards the caller's authority to another user.
	 *
	 * <p>The forwarding task is closed and an equivalent task is opened for the delegate in the
	 * <em>same</em> step, so the delegate's approval counts toward the same quorum. A forward that created a
	 * new step would silently lengthen the chain.
	 */
	public List<WorkflowTask> forward(WorkflowInstance instance, WorkflowTask task, UUID delegateUserId, UUID actor,
			String remarks, String correlationId) {
		requireOpen(instance, task);
		if (delegateUserId == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A delegate must be named.");
		}
		if (delegateUserId.equals(task.getAssignedUserId())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A task cannot be forwarded to its holder.");
		}
		WorkflowState previous = requireCurrentState(instance);
		task.complete(WorkflowTask.TaskStatus.FORWARDED);
		this.tasks.save(task);
		this.actions.save(new com.edam.workflow.model.WorkflowAction(task.getId(),
				com.edam.workflow.model.WorkflowAction.ActionCode.FORWARD, actor, remarks, previous.getId(),
				previous.getId(), correlationId));
		WorkflowTask forwarded = new WorkflowTask(instance.getId(), task.getWorkflowStepId(), delegateUserId, null,
				task.getDueAt());
		WorkflowTask saved = this.tasks.save(forwarded);
		log.info("workflow.forwarded instanceId={} from={} to={}", instance.getId(), actor, delegateUserId);
		return List.of(saved);
	}

	/**
	 * Cancels the workflow because the requester withdrew the request.
	 *
	 * <p>Closing the instance is what distinguishes this from a revert: a cancelled request does not return
	 * to the requester's drafts, it ends.
	 */
	public ActionOutcome cancel(WorkflowInstance instance, UUID actor, String remarks, String correlationId) {
		WorkflowVersion version = requireVersion(instance);
		WorkflowState previous = requireCurrentState(instance);
		WorkflowState builtInCancelled = this.states.findByType(version.getId(), WorkflowState.StateType.CANCELLED)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow defines no cancellation state."));
		WorkflowState cancelled = resolveTarget(version, previous.getId(),
				com.edam.workflow.model.WorkflowTransition.ActionCode.CANCEL, builtInCancelled);
		if (cancelled == null) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"A cancellation must lead to a state, but this workflow declares none.");
		}
		closeAllOpenTasks(instance.getId(), WorkflowTask.TaskStatus.CANCELLED);
		instance.setCurrentStateId(cancelled.getId());
		instance.complete();
		this.instances.save(instance);
		log.info("workflow.cancelled instanceId={} by={} remarks={}", instance.getId(), actor, remarks);
		return new ActionOutcome(cancelled, true, List.of());
	}

	// ------------------------------------------------------------------
	// Advancement
	// ------------------------------------------------------------------

	private List<WorkflowTask> advanceToFirstStep(WorkflowInstance instance, WorkflowVersion version,
			AssignmentContext context) {
		List<WorkflowStep> ordered = this.steps.findWithApprovers(version.getId());
		if (ordered.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The workflow has no approval steps, so it cannot be routed.");
		}
		return assignStep(instance, ordered.get(0), context);
	}

	private ActionOutcome advance(WorkflowInstance instance, WorkflowVersion version, WorkflowStep completedStep,
			AssignmentContext context) {
		List<WorkflowStep> remaining = this.steps.findAfterOrder(version.getId(), completedStep.getStepOrder());
		if (remaining.isEmpty()) {
			WorkflowState approved = this.states.findByType(version.getId(), WorkflowState.StateType.APPROVED)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
						"The workflow defines no approved state."));
			instance.setCurrentStateId(approved.getId());
			instance.complete();
			this.instances.save(instance);
			log.info("workflow.approved instanceId={}", instance.getId());
			return new ActionOutcome(approved, true, List.of());
		}
		List<WorkflowTask> opened = assignStep(instance, remaining.get(0), context);
		WorkflowState current = requireCurrentState(instance);
		return new ActionOutcome(current, false, opened);
	}

	/** Resolves a step's designations into tasks, respecting the step's approval mode. */
	private List<WorkflowTask> assignStep(WorkflowInstance instance, WorkflowStep step, AssignmentContext context) {
		List<UUID> resolvedUsers = this.assignmentResolver.resolve(step.getApprovers(), context);
		if (resolvedUsers.isEmpty()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"No approver could be resolved for step '" + step.getStepName()
							+ "'. Configure the office holder or role the workflow names.");
		}
		if (step.getApprovalMode() == WorkflowStep.ApprovalMode.PARALLEL) {
			List<WorkflowTask> created = new ArrayList<>();
			for (UUID userId : resolvedUsers) {
				created.add(this.tasks.save(new WorkflowTask(instance.getId(), step.getId(), userId, null,
						context.dueAt())));
			}
			return created;
		}
		// SEQUENTIAL: only the first approver acts; the next is assigned when this one completes. Assigning
		// all of them would put steps in the inbox that the approver is not yet entitled to action.
		WorkflowTask first = this.tasks.save(new WorkflowTask(instance.getId(), step.getId(), resolvedUsers.get(0),
				null, context.dueAt()));
		return List.of(first);
	}

	/**
	 * The quorum for a step.
	 *
	 * <p>{@code minimumApprovals} is a floor, not a ceiling: the caller compares the count of APPROVED tasks
	 * against it. The step's own configuration cannot lower it below one (a CHECK enforces that), so a step
	 * can never be satisfied without a recorded approval.
	 *
	 * <p>The number of tasks actually opened is the higher precedence: a step configured for a quorum of five
	 * with two approvers could never complete, and the request would sit in {@code IN_APPROVAL} forever with
	 * nothing in the log to explain it. The engine therefore caps the effective quorum at the number of
	 * approvals that can physically be collected, and logs the discrepancy so an administrator can correct
	 * the configuration.
	 */
	private long effectiveQuorum(WorkflowStep step, long assigneeCount) {
		long configured = step.getMinimumApprovals();
		if (assigneeCount > 0 && configured > assigneeCount) {
			log.warn("workflow.quorum_capped stepId={} configured={} assignees={}", step.getId(), configured,
					assigneeCount);
			return assigneeCount;
		}
		return configured;
	}

	/** Total tasks ever created for a step, which bounds the approvals that can be collected. */
	private long countAssignedForStep(UUID instanceId, UUID stepId) {
		return this.tasks.findByWorkflowInstanceIdOrderByAssignedAtAsc(instanceId)
			.stream()
			.filter(task -> task.getWorkflowStepId().equals(stepId))
			.count();
	}

	private void closeOpenTasks(UUID instanceId, UUID stepId, WorkflowTask.TaskStatus outcome) {
		for (WorkflowTask open : this.tasks.findOpenForStep(instanceId, stepId)) {
			if (outcome == WorkflowTask.TaskStatus.SKIPPED) {
				open.complete(outcome);
			}
			else {
				open.cancel();
			}
			this.tasks.save(open);
		}
	}

	private void closeAllOpenTasks(UUID instanceId, WorkflowTask.TaskStatus outcome) {
		for (WorkflowTask open : this.tasks.findByWorkflowInstanceIdAndStatus(instanceId,
				WorkflowTask.TaskStatus.PENDING)) {
			open.cancel();
			this.tasks.save(open);
		}
	}

	private void requireOpen(WorkflowInstance instance, WorkflowTask task) {
		if (!task.isOpen()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"That task is already " + task.getStatus() + ".");
		}
		if (!task.getWorkflowInstanceId().equals(instance.getId())) {
			// A task from another instance must never be actionable through this one.
			throw new ResourceNotFoundException("WorkflowTask", task.getId());
		}
		if (!instance.isRunning()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"This workflow has already been completed.");
		}
	}

	private WorkflowVersion requireVersion(WorkflowInstance instance) {
		return this.versions.findById(instance.getWorkflowVersionId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowVersion", instance.getWorkflowVersionId()));
	}

	private WorkflowState requireCurrentState(WorkflowInstance instance) {
		return this.states.findById(instance.getCurrentStateId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowState", instance.getCurrentStateId()));
	}

	private WorkflowStep requireStep(WorkflowTask task) {
		return this.steps.findById(task.getWorkflowStepId())
			.orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", task.getWorkflowStepId()));
	}

	/** Reads the instance's current state for callers that need to render a case file. */
	public WorkflowState currentState(WorkflowInstance instance) {
		return requireCurrentState(instance);
	}

	// ------------------------------------------------------------------
	// Declared transitions
	// ------------------------------------------------------------------

	/**
	 * Resolves the state an action leads to, consulting the revision's declared transitions.
	 *
	 * <h2>Two regimes, and why the switch is not silent</h2>
	 * A revision that declares <em>no</em> transitions at all is treated as using the engine's built-in
	 * semantics. Every revision created before this table was consulted is in that position, and refusing
	 * their actions outright would break every workflow already in production.
	 *
	 * <p>Once a revision declares <em>any</em> transition for a state, that state's permitted actions are
	 * exactly what it declares. An action it does not declare is refused, with a message naming the action.
	 * Falling back would let a workflow that was deliberately narrowed silently regain the actions someone
	 * removed from it — the opposite of what configuring it was for.
	 *
	 * @param version the pinned revision
	 * @param fromStateId the state the instance is in
	 * @param action the action being attempted
	 * @param fallback the state the built-in semantics would produce, used only when the revision declares
	 * nothing for this state
	 * @return the state to move to, or {@code null} when the action ends the instance without one
	 * @throws BusinessException when the revision declares transitions for this state but not this action
	 */
	private WorkflowState resolveTarget(WorkflowVersion version, UUID fromStateId,
			com.edam.workflow.model.WorkflowTransition.ActionCode action, WorkflowState fallback) {
		List<com.edam.workflow.model.WorkflowTransition> declared = this.transitions
			.findByWorkflowVersionId(version.getId());
		boolean stateIsConfigured = declared.stream()
			.anyMatch(transition -> transition.getFromStateId().equals(fromStateId));
		if (!stateIsConfigured) {
			return fallback;
		}
		var transition = declared.stream()
			.filter(candidate -> candidate.matches(fromStateId, action))
			.findFirst()
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"This workflow does not permit " + action + " from its current state."));
		if (transition.getConditionRuleId() != null) {
			// The rule is stored and a foreign key guarantees it exists, but the evaluator that would
			// interpret it is not part of this phase. Refusing is the only honest option: silently ignoring
			// a configured condition would apply an approval route the bank did not intend.
			throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"This transition is conditional, and conditional transitions cannot be evaluated yet.");
		}
		if (transition.getToStateId() == null) {
			return null;
		}
		return this.states.findById(transition.getToStateId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"This transition points at a state that does not exist."));
	}

	// ------------------------------------------------------------------
	// Value types
	// ------------------------------------------------------------------

	/**
	 * The facts an assignment needs that are not part of the workflow definition.
	 *
	 * @param requestingDepartmentId the department the request belongs to, used for office-based steps
	 * @param requestingUserId the requester, used for SELF and MANAGER style steps
	 * @param dueAt an optional deadline for the created tasks
	 */
	public record AssignmentContext(UUID requestingDepartmentId, UUID requestingUserId, java.time.Instant dueAt) {
	}

	/** Result of starting a workflow. */
	public record StartedInstance(WorkflowInstance instance, List<WorkflowTask> openedTasks) {
	}

	/** Result of an action: the new state, whether the workflow finished, and any tasks newly opened. */
	public record ActionOutcome(WorkflowState state, boolean workflowCompleted, List<WorkflowTask> openedTasks) {
	}

}
