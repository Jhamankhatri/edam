package com.edam.expenserequest.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * A CAPEX budget line the request draws on (Module G).
 *
 * <h2>Three amounts, one identity</h2>
 * The schema stores allocated, consumed and available, and enforces
 * {@code available = allocated − consumed}. Storing a derived value is normally a mistake; here it is
 * deliberate and safe precisely because the identity is enforced — an approver reading "available" cannot
 * be shown a figure that disagrees with the other two. This class recomputes the value rather than
 * accepting it, so the object cannot be constructed inconsistently.
 */
@Entity
@Table(name = "capex_budget_summaries")
public class CapexBudgetSummary {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "budget_reference", nullable = false, length = 150)
	private String budgetReference;

	@Column(name = "allocated_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal allocatedAmount;

	@Column(name = "consumed_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal consumedAmount;

	@Column(name = "available_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal availableAmount;

	protected CapexBudgetSummary() {
	}

	public CapexBudgetSummary(String budgetReference, BigDecimal allocatedAmount, BigDecimal consumedAmount) {
		this.budgetReference = budgetReference;
		this.allocatedAmount = allocatedAmount;
		this.consumedAmount = consumedAmount;
		this.availableAmount = allocatedAmount.subtract(consumedAmount);
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public String getBudgetReference() {
		return this.budgetReference;
	}

	public BigDecimal getAllocatedAmount() {
		return this.allocatedAmount;
	}

	public BigDecimal getConsumedAmount() {
		return this.consumedAmount;
	}

	public BigDecimal getAvailableAmount() {
		return this.availableAmount;
	}

}
