package com.edam.workflow.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One action taken on a task, with the state it moved the instance from and to (Module I).
 *
 * <h2>Why this table exists in addition to the audit trail</h2>
 * The general audit trail answers "what changed in the system". This table answers the narrower and more
 * frequent question "why is this request at this stage", in the workflow's own vocabulary: which task, which
 * action, by whom, with what remarks, from which state to which. A case-file view can be built from it in
 * one query without interpreting audit JSON.
 *
 * <h2>Append-only by policy</h2>
 * An action is a fact about the past. There is no update or delete path in the engine, and the entity has no
 * mutation methods beyond construction.
 */
@Entity
@Table(name = "workflow_actions")
public class WorkflowAction {

	/** Actions an approver can perform. {@code SUBMIT} is recorded on the instance, not here. */
	public enum ActionCode {

		APPROVE,
		REJECT,
		REVERT,
		FORWARD,
		ESCALATE

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "workflow_task_id", nullable = false, updatable = false)
	private UUID workflowTaskId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_code", nullable = false, updatable = false, length = 40)
	private ActionCode actionCode;

	@Column(name = "performed_by", nullable = false, updatable = false)
	private UUID performedBy;

	@Column(name = "remarks", updatable = false, length = 2000)
	private String remarks;

	@Column(name = "previous_state_id", updatable = false)
	private UUID previousStateId;

	@Column(name = "next_state_id", updatable = false)
	private UUID nextStateId;

	@Column(name = "action_at", nullable = false, updatable = false)
	private Instant actionAt;

	@Column(name = "correlation_id", updatable = false, length = 64)
	private String correlationId;

	protected WorkflowAction() {
		// JPA
	}

	public WorkflowAction(UUID workflowTaskId, ActionCode actionCode, UUID performedBy, String remarks,
			UUID previousStateId, UUID nextStateId, String correlationId) {
		if (remarks != null && remarks.length() > 2000) {
			throw new IllegalArgumentException("Remarks are limited to 2000 characters");
		}
		this.workflowTaskId = workflowTaskId;
		this.actionCode = actionCode;
		this.performedBy = performedBy;
		this.remarks = remarks;
		this.previousStateId = previousStateId;
		this.nextStateId = nextStateId;
		this.actionAt = Instant.now();
		this.correlationId = correlationId;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getWorkflowTaskId() {
		return this.workflowTaskId;
	}

	public ActionCode getActionCode() {
		return this.actionCode;
	}

	public UUID getPerformedBy() {
		return this.performedBy;
	}

	public String getRemarks() {
		return this.remarks;
	}

	public UUID getPreviousStateId() {
		return this.previousStateId;
	}

	public UUID getNextStateId() {
		return this.nextStateId;
	}

	public Instant getActionAt() {
		return this.actionAt;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

}
