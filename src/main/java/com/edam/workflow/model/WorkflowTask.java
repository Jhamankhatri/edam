package com.edam.workflow.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One approval to be performed by one approver (Module I).
 *
 * <h2>Why one task per assignee, not one per step</h2>
 * A committee step has several members, each of whom must act independently and be counted separately for
 * quorum. A single "step task" shared by the committee could not record who approved, and would make quorum
 * counting guesswork. The unique index on {@code (instance, step, assignee) WHERE PENDING} is what stops a
 * retried submit from assigning the same person twice and letting them count as two approvals.
 *
 * <h2>Assignment and authorization</h2>
 * A task is addressed either to a user or to a role. Holding {@code approval:act} is <em>not</em> enough to
 * act: the engine also requires that an open task exists for the caller, either directly or through a role
 * they hold. That is the difference between "may approve in general" and "is the approver of this request",
 * and it is why approval authority is never modelled as a bare permission.
 *
 * <h2>{@code @Version} here, unlike the instance</h2>
 * Two approvers clicking at the same moment must not both succeed on the same task. Optimistic locking on
 * the task row turns the second click into a "the record changed, reload" error — which is honest — instead
 * of a silent double-approval.
 */
@Entity
@Table(name = "workflow_tasks")
public class WorkflowTask {

	/** Lifecycle of one approver's task. */
	public enum TaskStatus {

		/** Awaiting action. Included in the approval inbox. */
		PENDING,
		/** The approver approved. */
		APPROVED,
		/** The approver rejected, which ends the request unless the workflow allows otherwise. */
		REJECTED,
		/** The approver returned the request to an earlier stage. */
		REVERTED,
		/** The approver forwarded their authority to another user. */
		FORWARDED,
		/** The request was cancelled or reverted, so this task no longer needs action. */
		CANCELLED,
		/** The step completed without needing this task (quorum already met). */
		SKIPPED,
		/** The step escalated past this task. */
		ESCALATED

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "workflow_instance_id", nullable = false, updatable = false)
	private UUID workflowInstanceId;

	@Column(name = "workflow_step_id", nullable = false, updatable = false)
	private UUID workflowStepId;

	@Column(name = "assigned_user_id", updatable = false)
	private UUID assignedUserId;

	@Column(name = "assigned_role_id", updatable = false)
	private UUID assignedRoleId;

	@Enumerated(EnumType.STRING)
	@Column(name = "task_status", nullable = false, length = 20)
	private TaskStatus status = TaskStatus.PENDING;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

	@Column(name = "due_at")
	private Instant dueAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected WorkflowTask() {
		// JPA
	}

	public WorkflowTask(UUID workflowInstanceId, UUID workflowStepId, UUID assignedUserId, UUID assignedRoleId,
			Instant dueAt) {
		if (assignedUserId == null && assignedRoleId == null) {
			throw new IllegalArgumentException("A task must be addressed to a user or a role");
		}
		if (assignedUserId != null && assignedRoleId != null) {
			throw new IllegalArgumentException("A task is addressed to a user OR a role, not both");
		}
		this.workflowInstanceId = workflowInstanceId;
		this.workflowStepId = workflowStepId;
		this.assignedUserId = assignedUserId;
		this.assignedRoleId = assignedRoleId;
		this.assignedAt = Instant.now();
		this.dueAt = dueAt;
	}

	/** True when this task still needs an action. */
	public boolean isOpen() {
		return this.status == TaskStatus.PENDING;
	}

	/** Records that the task was actioned. */
	public void complete(TaskStatus outcome) {
		if (!isOpen()) {
			// Acting twice on the same task is a defect the caller must see, not something to ignore.
			throw new IllegalStateException("Task " + this.id + " is already " + this.status);
		}
		this.status = outcome;
		this.completedAt = Instant.now();
	}

	/** Cancels the task without an action, used when a request is reverted or cancelled. */
	public void cancel() {
		if (isOpen()) {
			this.status = TaskStatus.CANCELLED;
			this.completedAt = Instant.now();
		}
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getWorkflowInstanceId() {
		return this.workflowInstanceId;
	}

	public UUID getWorkflowStepId() {
		return this.workflowStepId;
	}

	public UUID getAssignedUserId() {
		return this.assignedUserId;
	}

	public UUID getAssignedRoleId() {
		return this.assignedRoleId;
	}

	public TaskStatus getStatus() {
		return this.status;
	}

	public Instant getAssignedAt() {
		return this.assignedAt;
	}

	public Instant getDueAt() {
		return this.dueAt;
	}

	public Instant getCompletedAt() {
		return this.completedAt;
	}

	public long getVersion() {
		return this.version;
	}

}
