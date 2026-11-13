package com.edam.workflow.model;

import java.time.Instant;
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
 * One approver designation of a workflow step (Module I).
 *
 * <h2>The type-and-reference contract</h2>
 * {@code approverType} decides which reference column is meaningful, and the database CHECK enforces the
 * correspondence. That is worth restating because the failure mode is silent: a {@code ROLE} step that names
 * only a user would resolve to no approvers, the request would sit in {@code IN_APPROVAL} forever, and
 * nothing in the log would say why.
 *
 * <h2>Designations are resolved at submission, not at publish</h2>
 * "Department head" is resolved against the requesting department when the request is submitted, so a
 * change of head between two requests is honoured. Resolving at publish time would freeze the office
 * holders and route a later request to someone who has since left.
 */
@Entity
@Table(name = "workflow_step_approvers")
public class WorkflowStepApprover {

	/** The office or principal that acts at this step. */
	public enum ApproverType {

		/** Any holder of {@code roleId}. */
		ROLE,
		/** A specific user. */
		USER,
		/** The head of the requesting department. */
		DEPARTMENT_HEAD,
		/** A configured recommending authority. */
		RECOMMENDING_AUTHORITY,
		/** The committee level named by {@code committeeLevelId}. */
		EXPENSE_COMMITTEE,
		/** The chief executive office. */
		CEO,
		/** The chief financial office. */
		CFO,
		/** The head of administration. */
		HEAD_OF_ADMIN_GSD,
		/** The requester's line manager. */
		MANAGER

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workflow_step_id", nullable = false, updatable = false)
	private WorkflowStep workflowStep;

	@Enumerated(EnumType.STRING)
	@Column(name = "approver_type", nullable = false, updatable = false, length = 40)
	private ApproverType approverType;

	@Column(name = "role_id", updatable = false)
	private UUID roleId;

	@Column(name = "user_id", updatable = false)
	private UUID userId;

	@Column(name = "department_id", updatable = false)
	private UUID departmentId;

	@Column(name = "committee_level_id", updatable = false)
	private UUID committeeLevelId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected WorkflowStepApprover() {
		// JPA
	}

	public WorkflowStepApprover(WorkflowStep step, ApproverType type, UUID roleId, UUID userId, UUID departmentId,
			UUID committeeLevelId) {
		validate(type, roleId, userId, committeeLevelId);
		this.workflowStep = step;
		this.approverType = type;
		this.roleId = roleId;
		this.userId = userId;
		this.departmentId = departmentId;
		this.committeeLevelId = committeeLevelId;
		this.createdAt = Instant.now();
	}

	/** Mirrors the database CHECK so a misconfigured step fails at construction with a useful message. */
	private static void validate(ApproverType type, UUID roleId, UUID userId, UUID committeeLevelId) {
		boolean valid = switch (type) {
			case ROLE -> roleId != null && userId == null && committeeLevelId == null;
			case USER -> userId != null && roleId == null;
			case EXPENSE_COMMITTEE -> committeeLevelId != null;
			default -> roleId == null && userId == null && committeeLevelId == null;
		};
		if (!valid) {
			throw new IllegalArgumentException("Approver type " + type + " does not match the reference columns supplied");
		}
	}

	public UUID getId() {
		return this.id;
	}

	public WorkflowStep getWorkflowStep() {
		return this.workflowStep;
	}

	public ApproverType getApproverType() {
		return this.approverType;
	}

	public UUID getRoleId() {
		return this.roleId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public UUID getDepartmentId() {
		return this.departmentId;
	}

	public UUID getCommitteeLevelId() {
		return this.committeeLevelId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
