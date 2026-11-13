package com.edam.workflow.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One permitted transition of a workflow revision: "from this state, this action leads there" (Module I).
 *
 * <h2>What belongs in data and what stays in code</h2>
 * V8 created this table with a comment that it was not consulted and that the omission would "mislead
 * whoever configures it next". This class closes that gap for the part that can safely be data: <em>which
 * actions are permitted from which state, and what target state each leads to</em>.
 *
 * <p>What stays in Java is the meaning of each action — that an approval counts toward a quorum, that a
 * rejection closes sibling tasks, that a revert cancels them. A bank may configure that {@code REVERT} is
 * permitted from {@code IN_APPROVAL} and leads back to {@code RETURNED}; it may not configure that a
 * rejection leaves the request open.
 *
 * <h2>A missing transition is a refusal, not a default</h2>
 * If a revision declares no transition for an action from a state, the engine refuses the action. It does
 * <em>not</em> fall back to the built-in target, because a workflow that was deliberately narrowed would
 * silently regain the actions it had removed.
 *
 * <h2>Why the condition is a rule reference and not an expression</h2>
 * {@code conditionRuleId} points at a row in {@code business_rules}. That evaluator is a closed Java
 * interpreter: it understands a fixed set of operators and fields and rejects anything else. Storing an
 * expression string here instead would invite someone to write one that has to be parsed at runtime, which
 * is how a workflow table becomes a scripting engine.
 */
@Entity
@Table(name = "workflow_transitions")
public class WorkflowTransition {

	/** The actions a transition can be declared for. Mirrors the schema's CHECK. */
	public enum ActionCode {

		SUBMIT, APPROVE, REJECT, REVERT, FORWARD, CANCEL, REVERSE, ESCALATE

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workflow_version_id", nullable = false, updatable = false)
	private WorkflowVersion workflowVersion;

	@Column(name = "from_state_id", nullable = false, updatable = false)
	private UUID fromStateId;

	/** Null means the action ends the instance without entering a further state. */
	@Column(name = "to_state_id", updatable = false)
	private UUID toStateId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action_code", nullable = false, updatable = false, length = 40)
	private ActionCode actionCode;

	@Column(name = "condition_rule_id", updatable = false)
	private UUID conditionRuleId;

	protected WorkflowTransition() {
		// JPA
	}

	public WorkflowTransition(WorkflowVersion version, UUID fromStateId, UUID toStateId, ActionCode actionCode,
			UUID conditionRuleId) {
		if (fromStateId == null) {
			throw new IllegalArgumentException("A transition must name the state it leaves");
		}
		if (actionCode == null) {
			throw new IllegalArgumentException("A transition must name its action");
		}
		this.workflowVersion = version;
		this.fromStateId = fromStateId;
		this.toStateId = toStateId;
		this.actionCode = actionCode;
		this.conditionRuleId = conditionRuleId;
	}

	/** True when this transition is the one for the given action leaving the given state. */
	public boolean matches(UUID stateId, ActionCode action) {
		return this.fromStateId.equals(stateId) && this.actionCode == action;
	}

	public UUID getId() {
		return this.id;
	}

	public WorkflowVersion getWorkflowVersion() {
		return this.workflowVersion;
	}

	public UUID getFromStateId() {
		return this.fromStateId;
	}

	public UUID getToStateId() {
		return this.toStateId;
	}

	public ActionCode getActionCode() {
		return this.actionCode;
	}

	public UUID getConditionRuleId() {
		return this.conditionRuleId;
	}

}
