package com.edam.approvallimits.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * How much a department — or a person within it — may approve (Module D).
 *
 * <h2>The nullable {@code userId} is the whole design question</h2>
 * A row with only a {@code departmentId} is a department-wide ceiling. A row that also names a
 * {@code userId} is that person's personal ceiling inside the department. Both are needed: a head of
 * department approves to a figure, and a senior manager inside it may hold a higher personal authority
 * without every manager in the department gaining it.
 *
 * <p>Resolution therefore prefers the more specific rule, and the effective ceiling for a person is the
 * lower of their personal and departmental figures. That second rule is a governance decision, not an
 * arithmetic one — see OD-P2-04. It is implemented conservatively so that a personal limit can tighten
 * authority but never silently widen it.
 *
 * <h2>Effective dating</h2>
 * Limits change at known dates and the change must not restate a past approval. The row carries a period,
 * and a database exclusion constraint prevents two active limits overlapping for the same department,
 * currency, expense type and authority — so "which limit applied" always has exactly one answer.
 */
@Entity
@Table(name = "department_approval_limits")
public class DepartmentApprovalLimit extends AuditableEntity {

	/** Which expense classification a limit governs. */
	public enum ExpenseType {

		/** Operating expenditure only. */
		OPEX,
		/** Capital expenditure only. */
		CAPEX,
		/** Both classifications. */
		BOTH

	}

	@Column(name = "department_id", nullable = false)
	private UUID departmentId;

	/** Null means the limit is department-wide; set means it applies to that person within the department. */
	@Column(name = "user_id")
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "expense_type", nullable = false, length = 20)
	private ExpenseType expenseType = ExpenseType.BOTH;

	@Column(name = "currency_id", nullable = false)
	private UUID currencyId;

	@Column(name = "approval_limit", nullable = false, precision = 19, scale = 4)
	private BigDecimal approvalLimit;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected DepartmentApprovalLimit() {
	}

	public DepartmentApprovalLimit(UUID departmentId, UUID userId, ExpenseType expenseType, UUID currencyId,
			BigDecimal approvalLimit, Instant effectiveFrom, Instant effectiveTo) {
		this.departmentId = departmentId;
		this.userId = userId;
		this.expenseType = expenseType;
		this.currencyId = currencyId;
		this.approvalLimit = approvalLimit;
		this.effectiveFrom = effectiveFrom;
		this.effectiveTo = effectiveTo;
	}

	/** True when this row applies to the given classification, currency and instant. */
	public boolean appliesTo(String classification, UUID currency, Instant at) {
		if (!this.active || !this.currencyId.equals(currency)) {
			return false;
		}
		boolean typeMatches = this.expenseType == ExpenseType.BOTH
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

	public void setLimit(BigDecimal approvalLimit) {
		this.approvalLimit = approvalLimit;
	}

	public UUID getDepartmentId() {
		return this.departmentId;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public ExpenseType getExpenseType() {
		return this.expenseType;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public BigDecimal getApprovalLimit() {
		return this.approvalLimit;
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
