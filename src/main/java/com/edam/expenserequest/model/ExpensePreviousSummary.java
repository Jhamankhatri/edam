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
 * A prior-period expense summary attached to a request (Module G, OPEX).
 *
 * <p>The BRD requires OPEX requests to carry "previous expense summary" rows so an approver can see what
 * was already spent on this expense before deciding. The rows are recorded as the requester presented
 * them; the system does not recompute history, for the same reason it does not recompute YTD: a figure
 * whose provenance the approver cannot see is worse than no figure.
 */
@Entity
@Table(name = "expense_previous_summaries")
public class ExpensePreviousSummary {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "period", nullable = false, length = 40)
	private String period;

	@Column(name = "previous_amount", precision = 19, scale = 4)
	private BigDecimal previousAmount;

	@Column(name = "approved_amount", precision = 19, scale = 4)
	private BigDecimal approvedAmount;

	@Column(name = "description")
	private String description;

	protected ExpensePreviousSummary() {
	}

	public ExpensePreviousSummary(String period, BigDecimal previousAmount, BigDecimal approvedAmount,
			String description) {
		this.period = period;
		this.previousAmount = previousAmount;
		this.approvedAmount = approvedAmount;
		this.description = description;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public UUID getId() {
		return this.id;
	}

	public String getPeriod() {
		return this.period;
	}

	public BigDecimal getPreviousAmount() {
		return this.previousAmount;
	}

	public BigDecimal getApprovedAmount() {
		return this.approvedAmount;
	}

	public String getDescription() {
		return this.description;
	}

}
