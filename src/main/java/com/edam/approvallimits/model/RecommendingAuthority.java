package com.edam.approvallimits.model;

import java.time.Instant;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * The person who must recommend a department's expense requests (Module D).
 *
 * <h2>Why this is not just "the department head"</h2>
 * The two are different offices. A department head is an organizational fact stored on the department; a
 * recommending authority is a delegation of *recommendation* that can sit with someone else — a finance
 * business partner, a chief-of-staff — and can differ by expense classification. Conflating them would
 * mean a bank could not separate "who leads this team" from "who must vouch for this spend".
 *
 * <h2>This is the resolution the workflow engine was missing</h2>
 * In Phase 1 the workflow's {@code RECOMMENDING_AUTHORITY} approver type resolved to nobody and logged a
 * debug line. That was an honest placeholder, but it meant a workflow step assigned to a recommending
 * authority could never open a task. With this table mapped and a resolver built on it, that approver
 * type now resolves to real people — or fails loudly when the department has none configured, which is
 * the behaviour a financial control needs.
 *
 * <h2>Overlap</h2>
 * A department may have only one active recommending authority per expense type over any given period; a
 * database exclusion constraint enforces it. Two active authorities would let the engine pick
 * arbitrarily, and both people would believe they were the designated one.
 */
@Entity
@Table(name = "recommending_authorities")
public class RecommendingAuthority extends AuditableEntity {

	@Column(name = "department_id", nullable = false)
	private UUID departmentId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "expense_type", nullable = false, length = 20)
	private DepartmentApprovalLimit.ExpenseType expenseType = DepartmentApprovalLimit.ExpenseType.BOTH;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected RecommendingAuthority() {
	}

	public RecommendingAuthority(UUID departmentId, UUID userId,
			DepartmentApprovalLimit.ExpenseType expenseType, Instant effectiveFrom, Instant effectiveTo) {
		this.departmentId = departmentId;
		this.userId = userId;
		this.expenseType = expenseType;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = effectiveTo;
	}

	/** True when this authority is the one recommending for the given classification at the given instant. */
	public boolean appliesTo(String classification, Instant at) {
		if (!this.active) {
			return false;
		}
		boolean typeMatches = this.expenseType == DepartmentApprovalLimit.ExpenseType.BOTH
				|| this.expenseType.name().equalsIgnoreCase(classification);
		return typeMatches && !this.effectiveFrom.isAfter(at)
				&& (this.effectiveTo == null || this.effectiveTo.isAfter(at));
	}

	public void close(Instant when) {
		this.effectiveTo = when;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void setUser(UUID userId) {
		this.userId = userId;
	}

	public UUID getDepartmentId() {
		return this.departmentId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public DepartmentApprovalLimit.ExpenseType getExpenseType() {
		return this.expenseType;
	}

	public Instant getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public Instant getEffectiveTo() {
		return this.effectiveTo;
	}

	public boolean isActive() {
		return this.active;
	}

}
