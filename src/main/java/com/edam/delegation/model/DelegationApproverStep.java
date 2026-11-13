package com.edam.delegation.model;

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
 * One approval step of a delegation rule (Module E).
 *
 * <h2>Who approves is a TYPE, not a person</h2>
 * A step names the kind of office that must act — the department head, the recommending authority, the
 * expense committee, the CFO — rather than a user id. That is what makes the matrix survive staff changes:
 * appointing a new department head updates the department, not every rule that routes to it. A named
 * {@code ROLE} step is the escape hatch for a chain defined by a role rather than an office.
 *
 * <h2>Step order is the chain</h2>
 * {@code stepOrder} starts at 1 and increases; the workflow is built by walking the steps in order. A gap
 * is tolerated (the sequence is compared, not enumerated), but two steps cannot share an order — the
 * unique constraint on {@code (rule, stepOrder)} makes the chain unambiguous.
 *
 * <h2>No back-reference to the rule's mutable fields</h2>
 * The association is many-to-one and read-only from here. A step cannot decide its own amount band; it
 * belongs to a rule that does.
 */
@Entity
@Table(name = "delegation_approver_steps")
public class DelegationApproverStep {

	/** The office that acts at a step. The list is closed and mirrored by a CHECK constraint. */
	public enum ApproverType {

		/** The head of the requesting department. */
		DEPARTMENT_HEAD,
		/** A configured recommending authority for the department. */
		RECOMMENDING_AUTHORITY,
		/** The expense committee configured for this level. */
		EXPENSE_COMMITTEE,
		/** The chief executive officer office. */
		CEO,
		/** The chief financial officer office. */
		CFO,
		/** The head of administration (GSD). */
		HEAD_OF_ADMIN_GSD,
		/** Any holder of the named role. */
		ROLE,
		/** A specific user, resolved when the step was configured. */
		USER,
		/** The requester's line manager. */
		MANAGER

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "delegation_rule_id", nullable = false, updatable = false)
	private DelegationRule delegationRule;

	@Column(name = "step_order", nullable = false, updatable = false)
	private short stepOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "approver_type", nullable = false, updatable = false, length = 40)
	private ApproverType approverType;

	/** Only populated for {@link ApproverType#ROLE}; enforced by a CHECK constraint. */
	@Column(name = "required_role_id", updatable = false)
	private UUID requiredRoleId;

	/** Optional narrowing, e.g. a committee level code. */
	@Column(name = "department_scope", length = 64)
	private String departmentScope;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private java.time.Instant createdAt;

	protected DelegationApproverStep() {
		// JPA
	}

	public DelegationApproverStep(DelegationRule delegationRule, short stepOrder, ApproverType approverType,
			UUID requiredRoleId, String departmentScope) {
		if (approverType == ApproverType.ROLE && requiredRoleId == null) {
			throw new IllegalArgumentException("A ROLE approval step must name a role");
		}
		if (approverType != ApproverType.ROLE && requiredRoleId != null) {
			throw new IllegalArgumentException("Only a ROLE approval step may name a role");
		}
		if (stepOrder < 1) {
			throw new IllegalArgumentException("Step order starts at 1");
		}
		this.delegationRule = delegationRule;
		this.stepOrder = stepOrder;
		this.approverType = approverType;
		this.requiredRoleId = requiredRoleId;
		this.departmentScope = departmentScope;
		this.createdAt = java.time.Instant.now();
	}

	public UUID getId() {
		return this.id;
	}

	public DelegationRule getDelegationRule() {
		return this.delegationRule;
	}

	public short getStepOrder() {
		return this.stepOrder;
	}

	public ApproverType getApproverType() {
		return this.approverType;
	}

	public UUID getRequiredRoleId() {
		return this.requiredRoleId;
	}

	public String getDepartmentScope() {
		return this.departmentScope;
	}

	public boolean isMandatory() {
		return this.mandatory;
	}

	public void setMandatory(boolean mandatory) {
		this.mandatory = mandatory;
	}

	public java.time.Instant getCreatedAt() {
		return this.createdAt;
	}

}
