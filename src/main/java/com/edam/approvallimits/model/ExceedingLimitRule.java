package com.edam.approvallimits.model;

import java.math.BigDecimal;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * What happens when a request exceeds a configured threshold (Module D).
 *
 * <h2>Only the threshold is implemented here, not the escalation</h2>
 * The table records that "above this amount, for this nature and classification, this should happen".
 * The *happening* belongs to the workflow engine in Phase 3, because adding a CEO approval step or
 * notifying the CFO is a routing decision, and routing must have exactly one owner.
 *
 * <p>Keeping evaluation and enforcement apart is not an implementation shortcut: it lets an
 * administrator configure and preview a rule now, and lets the routing consequence be added once the
 * engine can express it — without the rule table changing underneath either of them.
 *
 * <p>See OD-P2-03: what each {@link EscalationType} should do beyond refusing the request is not stated
 * in the BRD and is therefore not invented here.
 */
@Entity
@Table(name = "exceeding_limit_rules")
public class ExceedingLimitRule extends AuditableEntity {

	/** The action a rule prescribes when its threshold is crossed. */
	public enum EscalationType {

		/** Recorded only; no routing change. Useful as a reporting marker. */
		NONE,
		/** Route the request to the chief executive in addition to the normal chain. */
		ADD_CEO_APPROVAL,
		/** Inform the chief financial officer without adding an approval step. */
		NOTIFY_CFO,
		/** Refuse the submission outright. */
		BLOCK

	}

	@Enumerated(EnumType.STRING)
	@Column(name = "expense_type", nullable = false, length = 20)
	private DepartmentApprovalLimit.ExpenseType expenseType;

	@Column(name = "nature_id", nullable = false)
	private UUID natureId;

	@Column(name = "currency_id", nullable = false)
	private UUID currencyId;

	@Column(name = "threshold_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal thresholdAmount;

	@Enumerated(EnumType.STRING)
	@Column(name = "escalation_type", nullable = false, length = 40)
	private EscalationType escalationType = EscalationType.NONE;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected ExceedingLimitRule() {
	}

	public ExceedingLimitRule(DepartmentApprovalLimit.ExpenseType expenseType, UUID natureId, UUID currencyId,
			BigDecimal thresholdAmount, EscalationType escalationType) {
		this.expenseType = expenseType;
		this.natureId = natureId;
		this.currencyId = currencyId;
		this.thresholdAmount = thresholdAmount;
		this.escalationType = escalationType;
	}

	/** True when a request of this classification, nature, currency and amount would cross the threshold. */
	public boolean triggers(String classification, UUID nature, UUID currency, BigDecimal amount) {
		if (!this.active || !this.natureId.equals(nature) || !this.currencyId.equals(currency)) {
			return false;
		}
		boolean typeMatches = this.expenseType == DepartmentApprovalLimit.ExpenseType.BOTH
				|| this.expenseType.name().equalsIgnoreCase(classification);
		return typeMatches && amount != null && amount.compareTo(this.thresholdAmount) > 0;
	}

	public void setRule(DepartmentApprovalLimit.ExpenseType expenseType, UUID natureId, UUID currencyId,
			BigDecimal thresholdAmount, EscalationType escalationType) {
		this.expenseType = expenseType;
		this.natureId = natureId;
		this.currencyId = currencyId;
		this.thresholdAmount = thresholdAmount;
		this.escalationType = escalationType;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public DepartmentApprovalLimit.ExpenseType getExpenseType() {
		return this.expenseType;
	}

	public UUID getNatureId() {
		return this.natureId;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public BigDecimal getThresholdAmount() {
		return this.thresholdAmount;
	}

	public EscalationType getEscalationType() {
		return this.escalationType;
	}

	public boolean isActive() {
		return this.active;
	}

}
