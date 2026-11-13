package com.edam.workflow.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * One approval step of a published workflow (Module I).
 *
 * <h2>Sequential versus parallel is per step, not per workflow</h2>
 * A bank typically wants parallel collection (a committee voting simultaneously) followed by a sequential
 * sign-off (department head, then CFO). Making the mode a property of the step expresses both in one
 * workflow; making it a property of the workflow would force two workflows that differ in nothing else.
 *
 * <h2>Quorum</h2>
 * {@code minimumApprovals} is the number of approvals required before the step completes. For a
 * {@code PARALLEL} step with several approvers this is a quorum; for {@code SEQUENTIAL} it is normally 1.
 * The engine takes {@code max(step quorum, committee-level minimum)} so that a committee's own quorum
 * cannot be lowered by a carelessly configured step.
 */
@Entity
@Table(name = "workflow_steps")
public class WorkflowStep {

	/** How the step's approvers act relative to each other. */
	public enum ApprovalMode {

		/** One after another; the next is assigned only when the previous completes. */
		SEQUENTIAL,
		/** All at once; the step completes when the quorum is met. */
		PARALLEL

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "workflow_version_id", nullable = false, updatable = false)
	private WorkflowVersion workflowVersion;

	@Column(name = "step_code", nullable = false, updatable = false, length = 64)
	private String stepCode;

	@Column(name = "step_name", nullable = false, updatable = false, length = 150)
	private String stepName;

	@Column(name = "step_order", nullable = false, updatable = false)
	private short stepOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "approval_mode", nullable = false, length = 20)
	private ApprovalMode approvalMode = ApprovalMode.SEQUENTIAL;

	@Column(name = "minimum_approvals", nullable = false)
	private short minimumApprovals = 1;

	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory = true;

	@OneToMany(mappedBy = "workflowStep", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
	private List<WorkflowStepApprover> approvers = new ArrayList<>();

	protected WorkflowStep() {
		// JPA
	}

	public WorkflowStep(WorkflowVersion version, String stepCode, String stepName, short stepOrder,
			ApprovalMode approvalMode, short minimumApprovals) {
		if (stepOrder < 1) {
			throw new IllegalArgumentException("Step order starts at 1");
		}
		if (minimumApprovals < 1) {
			throw new IllegalArgumentException("At least one approval is required");
		}
		this.workflowVersion = version;
		this.stepCode = stepCode;
		this.stepName = stepName;
		this.stepOrder = stepOrder;
		this.approvalMode = approvalMode;
		this.minimumApprovals = minimumApprovals;
	}

	public UUID getId() {
		return this.id;
	}

	public WorkflowVersion getWorkflowVersion() {
		return this.workflowVersion;
	}

	public String getStepCode() {
		return this.stepCode;
	}

	public String getStepName() {
		return this.stepName;
	}

	public short getStepOrder() {
		return this.stepOrder;
	}

	public ApprovalMode getApprovalMode() {
		return this.approvalMode;
	}

	public short getMinimumApprovals() {
		return this.minimumApprovals;
	}

	public boolean isMandatory() {
		return this.mandatory;
	}

	public void setMandatory(boolean mandatory) {
		this.mandatory = mandatory;
	}

	public List<WorkflowStepApprover> getApprovers() {
		return this.approvers;
	}

	public void setApprovers(List<WorkflowStepApprover> approvers) {
		this.approvers = approvers;
	}

}
