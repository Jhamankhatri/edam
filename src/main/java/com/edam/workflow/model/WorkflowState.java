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
 * One state of a workflow version (Module I): DRAFT, IN_APPROVAL, APPROVED, REJECTED, RETURNED, CANCELLED,
 * REVERSED.
 *
 * <h2>Why states are rows on the version, not an enum in Java</h2>
 * A bank may need a state the product did not anticipate — a "pending board ratification" step, for
 * instance. Making states data lets that exist without a release. What stays in Java is the
 * <em>meaning</em>: {@code stateType} tells the engine whether a state is initial, in progress, or a
 * terminal outcome, and the engine's decisions are made on the type, never on the code string.
 *
 * <p>That distinction is what keeps the configurability honest: adding a state is configuration, but
 * inventing a new <em>kind</em> of outcome requires code, because only code knows what an outcome implies
 * for a request's totals, audit and reporting.
 *
 * <h2>Exactly one initial state</h2>
 * Enforced by a partial unique index. Two initial states would make a new request's starting point depend
 * on row order.
 */
@Entity
@Table(name = "workflow_states")
public class WorkflowState {

	/** What a state means to the engine. Decisions branch on this, never on {@link #stateCode}. */
	public enum StateType {

		/** Where an instance starts. */
		INITIAL,
		/** Awaiting one or more approvals. */
		IN_PROGRESS,
		/** Successful conclusion. */
		APPROVED,
		/** Unsuccessful conclusion. */
		REJECTED,
		/** Withdrawn by the requester. */
		CANCELLED,
		/** An approved request that was subsequently reversed. */
		REVERSED

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workflow_version_id", nullable = false, updatable = false)
	private WorkflowVersion workflowVersion;

	@Column(name = "state_code", nullable = false, updatable = false, length = 64)
	private String stateCode;

	@Column(name = "state_name", nullable = false, updatable = false, length = 150)
	private String stateName;

	@Enumerated(EnumType.STRING)
	@Column(name = "state_type", nullable = false, updatable = false, length = 20)
	private StateType stateType;

	@Column(name = "is_initial", nullable = false, updatable = false)
	private boolean initial;

	@Column(name = "is_terminal", nullable = false, updatable = false)
	private boolean terminal;

	protected WorkflowState() {
		// JPA
	}

	public WorkflowState(WorkflowVersion version, String stateCode, String stateName, StateType stateType) {
		if (stateType == StateType.INITIAL && stateName == null) {
			throw new IllegalArgumentException("An initial state requires a name");
		}
		this.workflowVersion = version;
		this.stateCode = stateCode;
		this.stateName = stateName;
		this.stateType = stateType;
		this.initial = (stateType == StateType.INITIAL);
		this.terminal = (stateType != StateType.INITIAL && stateType != StateType.IN_PROGRESS);
	}

	public UUID getId() {
		return this.id;
	}

	public WorkflowVersion getWorkflowVersion() {
		return this.workflowVersion;
	}

	public String getStateCode() {
		return this.stateCode;
	}

	public String getStateName() {
		return this.stateName;
	}

	public StateType getStateType() {
		return this.stateType;
	}

	public boolean isInitial() {
		return this.initial;
	}

	public boolean isTerminal() {
		return this.terminal;
	}

}
