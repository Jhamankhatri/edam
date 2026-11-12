package com.edam.approval;

import java.time.Instant;

import com.edam.common.tenant.InstitutionOwnedEntity;
import com.edam.expense.ExpenseRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One assignment awaiting action (BRD §8.4, §8.5).
 *
 * <h2>Assignment is the authorization, not the permission</h2>
 * BRD §23.3 requires identity + membership + permission + <em>workflow assignment</em>. A task row is
 * that assignment: an approver may act only on a task addressed to them (or to their department, or
 * to their committee office). Holding {@code approval:act} in the institution is necessary but nowhere
 * near sufficient, and {@code ApprovalEngine} enforces both.
 *
 * <h2>Why the step vocabulary is copied onto the task</h2>
 * {@code stepLabel} and {@code requiredPermission} are copied from the pinned workflow version when
 * the task is created. The request's case file must remain readable after the workflow is retired, and
 * a task that rendered its label by joining a mutable definition would change meaning retroactively.
 *
 * <h2>One open task per step</h2>
 * {@code (request_id, sequence_number) WHERE status = 'PENDING'} is unique in the database, so a
 * retried approval cannot open a second task, and a double submit cannot double-approve.
 */
@Entity
@Table(name = "approval_task")
public class ApprovalTask extends InstitutionOwnedEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "request_id", nullable = false)
	private ExpenseRequest request;

	@NotNull
	@Size(max = 64)
	@Column(name = "step_code", nullable = false, length = 64)
	private String stepCode;

	@Size(max = 200)
	@Column(name = "step_label", length = 200)
	private String stepLabel;

	@Size(max = 80)
	@Column(name = "required_permission", length = 80)
	private String requiredPermission;

	@NotNull
	@Size(max = 64)
	@Column(name = "approver_role", nullable = false, length = 64)
	private String approverRole;

	/** Direct assignee; null for role- or department-addressed tasks. */
	@Size(max = 64)
	@Column(name = "assigned_employee_id", length = 64)
	private String assignedEmployeeId;

	/** Department-addressed task (Department Head approval). */
	@Size(max = 64)
	@Column(name = "assigned_department", length = 64)
	private String assignedDepartment;

	@Min(1)
	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private ApprovalTaskStatus status = ApprovalTaskStatus.PENDING;

	@Size(max = 64)
	@Column(name = "acted_by_employee_id", length = 64)
	private String actedByEmployeeId;

	@Size(max = 64)
	@Column(name = "acted_by_username", length = 64)
	private String actedByUsername;

	@Column(name = "acted_at")
	private Instant actedAt;

	@Size(max = 2000)
	@Column(name = "decision_comment", length = 2000)
	private String decisionComment;

	@Size(max = 64)
	@Column(name = "forward_to_employee_id", length = 64)
	private String forwardToEmployeeId;

	protected ApprovalTask() {
		// JPA
	}

	public ApprovalTask(ExpenseRequest request, String stepCode, String stepLabel, String requiredPermission,
			ApprovalRole approverRole, String assignedEmployeeId, String assignedDepartment, int sequenceNumber) {
		this.request = request;
		this.stepCode = stepCode;
		this.stepLabel = stepLabel;
		this.requiredPermission = requiredPermission;
		this.approverRole = approverRole.name();
		this.assignedEmployeeId = assignedEmployeeId;
		this.assignedDepartment = assignedDepartment;
		this.sequenceNumber = sequenceNumber;
	}

	public void approve(String employeeId, String username, String comment, Instant when) {
		requirePending();
		this.status = ApprovalTaskStatus.APPROVED;
		record(employeeId, username, comment, when);
	}

	public void reject(String employeeId, String username, String comment, Instant when) {
		requirePending();
		this.status = ApprovalTaskStatus.REJECTED;
		record(employeeId, username, comment, when);
	}

	public void revert(String employeeId, String username, String comment, Instant when) {
		requirePending();
		this.status = ApprovalTaskStatus.REVERTED;
		record(employeeId, username, comment, when);
	}

	/** Forward to another employee: this task closes and a new one is opened for the recipient. */
	public void forward(String employeeId, String username, String comment, String forwardTo, Instant when) {
		requirePending();
		this.status = ApprovalTaskStatus.FORWARDED;
		this.forwardToEmployeeId = forwardTo;
		record(employeeId, username, comment, when);
	}

	public void cancel(Instant when) {
		if (this.status == ApprovalTaskStatus.PENDING) {
			this.status = ApprovalTaskStatus.CANCELLED;
			this.actedAt = when;
		}
	}

	private void record(String employeeId, String username, String comment, Instant when) {
		this.actedByEmployeeId = employeeId;
		this.actedByUsername = username;
		this.decisionComment = comment;
		this.actedAt = when;
	}

	private void requirePending() {
		if (this.status != ApprovalTaskStatus.PENDING) {
			// Concurrency guard at the object level; the database unique index and the optimistic
			// version are the real controls, this makes the failure a business error rather than a
			// constraint violation.
			throw new IllegalStateException("This task has already been actioned");
		}
	}

	public boolean isPending() {
		return this.status == ApprovalTaskStatus.PENDING;
	}

	/** True when this task is addressed to the given employee. */
	public boolean isAssignedTo(String employeeId) {
		return employeeId != null && employeeId.equals(this.assignedEmployeeId);
	}

	/** True when this task is addressed to the given department. */
	public boolean isAssignedToDepartment(String departmentCode) {
		return departmentCode != null && departmentCode.equals(this.assignedDepartment);
	}

	public ExpenseRequest getRequest() {
		return this.request;
	}

	public String getStepCode() {
		return this.stepCode;
	}

	public String getStepLabel() {
		return this.stepLabel;
	}

	public String getRequiredPermission() {
		return this.requiredPermission;
	}

	public ApprovalRole approverRole() {
		return ApprovalRole.valueOf(this.approverRole);
	}

	public String getApproverRole() {
		return this.approverRole;
	}

	public String getAssignedEmployeeId() {
		return this.assignedEmployeeId;
	}

	public String getAssignedDepartment() {
		return this.assignedDepartment;
	}

	public int getSequenceNumber() {
		return this.sequenceNumber;
	}

	public ApprovalTaskStatus getStatus() {
		return this.status;
	}

	public String getActedByEmployeeId() {
		return this.actedByEmployeeId;
	}

	public String getActedByUsername() {
		return this.actedByUsername;
	}

	public Instant getActedAt() {
		return this.actedAt;
	}

	public String getDecisionComment() {
		return this.decisionComment;
	}

	public String getForwardToEmployeeId() {
		return this.forwardToEmployeeId;
	}

}
