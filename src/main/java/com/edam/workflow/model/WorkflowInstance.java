package com.edam.workflow.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A running workflow for one expense request (Module I).
 *
 * <h2>The version pin is the whole point of this table</h2>
 * {@code workflowVersionId} is recorded when the instance starts and is never updated. Every later decision
 * — which step is next, which approvers are required, which transition is legal — is read from
 * <em>that</em> version. Without the pin, publishing a new workflow would change the rules for requests that
 * are already half-approved, which is the single most damaging thing a configurable workflow can do: an
 * approver who acted under one rule set would find their action reinterpreted under another.
 *
 * <h2>One instance per request</h2>
 * Enforced by a unique index on {@code expenseRequestId}. A request cannot be in two approval chains, and a
 * second {@code submit} must not create a parallel instance — that would double the approval burden and
 * could approve the same request twice.
 *
 * <h2>Why this is not an {@code AuditableEntity}</h2>
 * An instance is created once and then only its {@code currentStateId} and {@code completedAt} change, each
 * of which is itself an explicit, audited event in {@code workflow_actions}. Carrying a {@code version}
 * column here would suggest optimistic locking protects the state machine; the real protection is that every
 * transition is written as an action row in the same transaction as the state change.
 */
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "workflow_definition_id", nullable = false, updatable = false)
	private UUID workflowDefinitionId;

	/** Pinned at start. Never re-read as "latest". */
	@Column(name = "workflow_version_id", nullable = false, updatable = false)
	private UUID workflowVersionId;

	@Column(name = "expense_request_id", nullable = false, updatable = false)
	private UUID expenseRequestId;

	@Column(name = "current_state_id")
	private UUID currentStateId;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "started_by", updatable = false)
	private UUID startedBy;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected WorkflowInstance() {
		// JPA
	}

	public WorkflowInstance(UUID workflowDefinitionId, UUID workflowVersionId, UUID expenseRequestId,
			UUID initialStateId, UUID startedBy) {
		this.workflowDefinitionId = workflowDefinitionId;
		this.workflowVersionId = workflowVersionId;
		this.expenseRequestId = expenseRequestId;
		this.currentStateId = initialStateId;
		this.startedAt = Instant.now();
		this.startedBy = startedBy;
	}

	/** True while the request is still being approved. */
	public boolean isRunning() {
		return this.completedAt == null;
	}

	/** Marks the instance finished. Called in the same transaction as the terminal action. */
	public void complete() {
		if (this.completedAt == null) {
			this.completedAt = Instant.now();
		}
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getWorkflowDefinitionId() {
		return this.workflowDefinitionId;
	}

	public UUID getWorkflowVersionId() {
		return this.workflowVersionId;
	}

	public UUID getExpenseRequestId() {
		return this.expenseRequestId;
	}

	public UUID getCurrentStateId() {
		return this.currentStateId;
	}

	public void setCurrentStateId(UUID currentStateId) {
		this.currentStateId = currentStateId;
	}

	public Instant getStartedAt() {
		return this.startedAt;
	}

	public UUID getStartedBy() {
		return this.startedBy;
	}

	public Instant getCompletedAt() {
		return this.completedAt;
	}

}
