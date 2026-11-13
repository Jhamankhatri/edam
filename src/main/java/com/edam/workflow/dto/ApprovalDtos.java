package com.edam.workflow.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.workflow.model.WorkflowAction;
import com.edam.workflow.model.WorkflowTask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Approval inbox and action contracts (Module I, BRD 8.5).
 *
 * <h2>Remarks are mandatory on every action, and that is a BRD rule</h2>
 * BRD 8.5 states it plainly: "Approve, reject and revert require remarks; forward requires a selected
 * recipient and remarks." The records below therefore use {@code @NotBlank} on remarks rather than
 * {@code @Size}. A rejection with no reason is unanswerable when the requester asks why, and a forward with
 * no note leaves the delegate guessing what they inherited.
 *
 * <h2>Why there is no {@code status} field anywhere</h2>
 * A client never states the outcome. It names an action; the engine decides what that action means. A
 * request carrying {@code "status": "APPROVED"} would let a requester approve their own expense by typing
 * the right word.
 */
public final class ApprovalDtos {

	private ApprovalDtos() {
	}

	// ---------------------------------------------------------------------------------------------
	// Inbox
	// ---------------------------------------------------------------------------------------------

	/**
	 * One row of the approval inbox.
	 *
	 * <p>Carries enough of the request to decide whether to open it, and deliberately not the whole
	 * aggregate: an inbox of twenty full requests would load twenty sets of child rows.
	 */
	public record ApprovalTaskSummary(UUID taskId, UUID requestId, String requestNumber, String requestStatus,
			String classification, UUID departmentId, BigDecimal requestedAmount, String currencyId,
			String stepCode, String stepName, short stepOrder, String taskStatus, Instant assignedAt,
			Instant dueAt, String assignedVia) {

		public static ApprovalTaskSummary from(WorkflowTask task, UUID requestId, String requestNumber,
				String requestStatus, String classification, UUID departmentId, BigDecimal requestedAmount,
				UUID currencyId, String stepCode, String stepName, short stepOrder) {
			return new ApprovalTaskSummary(task.getId(), requestId, requestNumber, requestStatus, classification,
					departmentId, requestedAmount, currencyId == null ? null : currencyId.toString(), stepCode,
					stepName, stepOrder, task.getStatus().name(), task.getAssignedAt(), task.getDueAt(),
					task.getAssignedUserId() != null ? "DIRECT" : "ROLE");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Case file
	// ---------------------------------------------------------------------------------------------

	/**
	 * The approver's view of a request: the request itself plus the workflow's position and history.
	 *
	 * <p>The request body is not duplicated here. An approver needs the request in full, and the request
	 * endpoint already returns it to anyone who may see it; this record adds the workflow facts that endpoint
	 * does not have.
	 */
	public record ApprovalCaseFile(UUID taskId, UUID workflowInstanceId, UUID workflowVersionId,
			int workflowVersionNumber, String currentStateCode, String currentStateName, String currentStateType,
			UUID requestId, String requestNumber, boolean open, List<ActionAvailability> availableActions,
			List<StepProgress> steps, List<WorkflowEventView> history) {
	}

	/**
	 * Whether one action is available to this caller right now.
	 *
	 * <p>BRD 8.5: "Actions are visible only for a valid pending assignment and current workflow state." The
	 * {@code reason} explains a refusal so the UI does not have to guess, and so a support call ends with an
	 * answer rather than a screenshot.
	 */
	public record ActionAvailability(String action, boolean available, String reason) {
	}

	/** One step of the pinned revision with its progress. */
	public record StepProgress(UUID stepId, String stepCode, String stepName, short stepOrder, String approvalMode,
			short minimumApprovals, long approvedCount, boolean complete, boolean current, List<TaskProgress> tasks) {
	}

	/** One task of a step. */
	public record TaskProgress(UUID taskId, String assignedTo, String status, Instant assignedAt,
			Instant completedAt) {
	}

	/** One entry of the approval history: the decision record, not the general audit trail. */
	public record WorkflowEventView(UUID actionId, UUID taskId, String actionCode, String performedBy,
			String remarks, String previousStateCode, String nextStateCode, Instant actionAt) {

		public static WorkflowEventView from(WorkflowAction action, String performerName, String previousStateCode,
				String nextStateCode) {
			return new WorkflowEventView(action.getId(), action.getWorkflowTaskId(),
					action.getActionCode().name(), performerName, action.getRemarks(), previousStateCode,
					nextStateCode, action.getActionAt());
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	/**
	 * Approves a task.
	 *
	 * <p>Remarks are required by BRD 8.5. There is no {@code actor} field: the actor is the authenticated
	 * caller and is never taken from the body, which is what stops one approver recording an action as
	 * another.
	 */
	public record ApproveRequest(@NotBlank @Size(max = 2000) String remarks) {
	}

	/** Rejects a task. Terminal in the default configuration; a revision may declare otherwise. */
	public record RejectRequest(@NotBlank @Size(max = 2000) String remarks) {
	}

	/** Reverts a task, returning the request for correction. */
	public record RevertRequest(@NotBlank @Size(max = 2000) String remarks) {
	}

	/** Forwards the caller's authority. BRD 8.5: a recipient *and* remarks. */
	public record ForwardRequest(@NotNull UUID recipientUserId, @NotBlank @Size(max = 2000) String remarks) {
	}

	/** The result of an action, as the approver sees it. */
	public record ActionResponse(UUID taskId, String taskStatus, String workflowStateCode,
			String workflowStateType, boolean workflowCompleted, UUID requestId, String requestStatus,
			List<UUID> newTaskIds) {
	}
}
