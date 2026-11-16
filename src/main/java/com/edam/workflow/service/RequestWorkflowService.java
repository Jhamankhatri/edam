package com.edam.workflow.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.workflow.model.WorkflowInstance;
import com.edam.workflow.model.WorkflowState;
import com.edam.workflow.model.WorkflowTask;
import com.edam.workflow.model.WorkflowVersion;
import com.edam.workflow.repository.InstancesRepository;
import com.edam.workflow.repository.StatesRepository;
import com.edam.workflow.repository.VersionsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The bridge between an expense request and its workflow instance (Module I).
 *
 * <h2>Why this is a separate service</h2>
 * {@code ExpenseRequestService} owns the request; {@code WorkflowEngine} owns the state machine; neither
 * should know the other's vocabulary. This class is the only place that knows both, which keeps the
 * dependency one-way — the workflow package depends on the request model, and the request service depends on
 * this bridge, never on the approval package. Without that split the two would depend on each other and the
 * first change to either would be a cycle.
 *
 * <h2>Propagation: required, and that is the whole rollback argument</h2>
 * Both methods join the caller's transaction. A submission whose workflow cannot start must leave no
 * submitted request behind — not a request marked SUBMITTED with no instance, which would be a request that
 * no approver can ever see and that the requester cannot resubmit because it is no longer editable. Joining
 * the transaction is what makes that impossible rather than merely unlikely.
 *
 * <h2>Resubmission reuses the pinned instance</h2>
 * A reverted request goes back to its author for correction. On resubmission it must return to the
 * <em>same</em> workflow version it started under, not to whichever version is published now — the BRD
 * requires a request to retain the approval path it started with. Starting a second instance is also
 * forbidden by a unique index, so the alternative does not exist.
 */
@Service
public class RequestWorkflowService {

	private static final Logger log = LoggerFactory.getLogger(RequestWorkflowService.class);

	private final WorkflowConfigurationService configuration;

	private final WorkflowEngine engine;

	private final InstancesRepository instances;

	private final VersionsRepository versions;

	private final StatesRepository states;

	public RequestWorkflowService(WorkflowConfigurationService configuration, WorkflowEngine engine,
			InstancesRepository instances, VersionsRepository versions, StatesRepository states) {
		this.configuration = configuration;
		this.engine = engine;
		this.instances = instances;
		this.versions = versions;
		this.states = states;
	}

	/**
	 * Starts (or resumes) the approval chain for a request about to be submitted.
	 *
	 * <h2>Three outcomes, all explicit</h2>
	 * <ol>
	 *   <li><b>First submission</b> — the published revision for the request's type is pinned and a new
	 *       instance opens at its first step.</li>
	 *   <li><b>Resubmission after a revert</b> — the existing instance is reused, keeping its original
	 *       version, and the first step's tasks are opened again.</li>
	 *   <li><b>Anything else</b> — refused. A request already in approval, or already decided, cannot be
	 *       submitted again.</li>
	 * </ol>
	 *
	 * @return the instance that now governs the request
	 * @throws BusinessException when no published workflow exists, when the first step cannot be staffed, or
	 * when the request already has an instance that is not awaiting resubmission
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public WorkflowEngine.StartedInstance startForSubmission(ExpenseRequest request, UUID actor) {
		WorkflowEngine.AssignmentContext context = new WorkflowEngine.AssignmentContext(request.getDepartmentId(),
				request.getRequestedBy(), null);
		Optional<WorkflowInstance> existing = this.instances.findByExpenseRequestId(request.getId());
		if (existing.isPresent()) {
			WorkflowInstance resumed = resume(existing.get(), request, actor, context);
			List<WorkflowTask> opened = this.tasks.findByWorkflowInstanceIdAndStatus(resumed.getId(),
					WorkflowTask.TaskStatus.PENDING);
			return new WorkflowEngine.StartedInstance(resumed, opened);
		}

		WorkflowVersion published = this.configuration.requirePublishedForRequestType(request.getRequestTypeId());
		WorkflowEngine.StartedInstance started = this.engine.start(published, request.getId(), actor, context);
		log.info("request.workflow_started requestId={} instanceId={} version={} tasks={}", request.getId(),
				started.instance().getId(), published.getVersionNumber(), started.openedTasks().size());
		return started;
	}

	/**
	 * Re-opens the first step of an instance whose request was returned for correction.
	 *
	 * <p>Refuses when the instance is not in a state a revert leaves it in: reusing an instance that is
	 * mid-approval would let a resubmission skip steps the approvers have already cleared.
	 */
	private WorkflowInstance resume(WorkflowInstance instance, ExpenseRequest request, UUID actor,
			WorkflowEngine.AssignmentContext context) {
		if (!instance.isRunning()) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This request's workflow has already finished and cannot be resubmitted.");
		}
		WorkflowVersion pinned = this.versions.findById(instance.getWorkflowVersionId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"This request's workflow revision no longer exists."));
		WorkflowState current = instance.getCurrentStateId() == null ? null
				: this.states.findById(instance.getCurrentStateId()).orElse(null);
		if (current == null || current.getStateType() != WorkflowState.StateType.INITIAL) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
					"This request is already in approval and cannot be submitted again.");
		}
		List<WorkflowTask> opened = this.engine.resumeAtFirstStep(instance, pinned, context);
		log.info("request.workflow_resumed requestId={} instanceId={} version={} tasks={}", request.getId(),
				instance.getId(), pinned.getVersionNumber(), opened.size());
		return instance;
	}

	/**
	 * Ends the request's workflow because the requester withdrew it.
	 *
	 * <p>Only when an instance exists and is running. A draft has no workflow, and a request whose workflow
	 * has already been decided is refused by the caller before this is reached.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void cancelForWithdrawal(ExpenseRequest request, UUID actor, String reason) {
		this.instances.findByExpenseRequestId(request.getId()).ifPresent(instance -> {
			if (!instance.isRunning()) {
				return;
			}
			this.engine.cancel(instance, actor, reason, com.edam.common.logging.CorrelationId.current());
			log.info("request.workflow_cancelled requestId={} instanceId={} actor={}", request.getId(),
					instance.getId(), actor);
		});
	}

	/** Where the request sits in its workflow, for the request detail response. */
	@Transactional(readOnly = true)
	public Optional<WorkflowSnapshot> snapshotOf(UUID requestId) {
		return this.instances.findByExpenseRequestId(requestId).map(instance -> {
			WorkflowVersion version = this.versions.findById(instance.getWorkflowVersionId()).orElse(null);
			WorkflowState state = instance.getCurrentStateId() == null ? null
					: this.states.findById(instance.getCurrentStateId()).orElse(null);
			return new WorkflowSnapshot(instance.getId(), instance.getWorkflowVersionId(),
					version == null ? 0 : version.getVersionNumber(), instance.isRunning(),
					state == null ? null : state.getStateCode(), state == null ? null : state.getStateName());
		});
	}

	/** The workflow's position for one request. */
	public record WorkflowSnapshot(UUID workflowInstanceId, UUID workflowVersionId, int versionNumber,
			boolean running, String stateCode, String stateName) {
	}

}
