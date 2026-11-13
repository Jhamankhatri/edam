package com.edam.expenserequest.model;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * OPEX-specific detail for one request (Module G).
 *
 * <h2>A recurring expense must state its cadence</h2>
 * The database enforces {@code is_recurring ⇒ recurrence_frequency IS NOT NULL}. A recurring expense
 * with no frequency could not be budgeted for, and the field would exist but never be populated — the
 * kind of silent gap an auditor finds a year later. The constraint is in the schema rather than only in
 * the service so that a future import cannot bypass it.
 *
 * <h2>The historical amounts are recorded, not computed</h2>
 * {@code accrued}, {@code previouslyApproved}, {@code ytd} and {@code mtd} are supplied by the requester.
 * Deriving YTD from other requests would look authoritative while being wrong whenever a request predates
 * this system or an invoice lands in a different period. They are evidence attached to the claim, so the
 * approver sees the same figures the requester saw.
 */
@Entity
@Table(name = "opex_request_details")
public class OpexRequestDetail {

	/** Cadence of a recurring expense. Constrained by a CHECK on the column. */
	public enum RecurrenceFrequency {

		/** Every month. */
		MONTHLY,
		/** Every three months. */
		QUARTERLY,
		/** Twice a year. */
		HALF_YEARLY,
		/** Once a year. */
		ANNUAL

	}

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "is_recurring", nullable = false)
	private boolean recurring;

	/**
	 * Named, not ordinal.
	 *
	 * <p>{@code @Enumerated} defaults to {@code ORDINAL}, which would have stored {@code 0}, {@code 1}, …
	 * in this {@code VARCHAR} column and failed the schema's own CHECK. String mapping also means a future
	 * reordering of the enum cannot silently reinterpret every stored row.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "recurrence_frequency", length = 50)
	private RecurrenceFrequency recurrenceFrequency;

	@Column(name = "non_recurring_justification")
	private String nonRecurringJustification;

	@Column(name = "accrued_amount", precision = 19, scale = 4)
	private BigDecimal accruedAmount;

	@Column(name = "previously_approved_amount", precision = 19, scale = 4)
	private BigDecimal previouslyApprovedAmount;

	@Column(name = "ytd_amount", precision = 19, scale = 4)
	private BigDecimal ytdAmount;

	@Column(name = "mtd_amount", precision = 19, scale = 4)
	private BigDecimal mtdAmount;

	@Column(name = "payment_terms")
	private String paymentTerms;

	@Column(name = "budget_available", precision = 19, scale = 4)
	private BigDecimal budgetAvailable;

	@Column(name = "budget_reference", length = 150)
	private String budgetReference;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected OpexRequestDetail() {
	}

	public OpexRequestDetail(boolean recurring, RecurrenceFrequency frequency) {
		this.recurring = recurring;
		// Silently dropping a frequency supplied for a non-recurring expense would let the form and the
		// stored row disagree; the database CHECK would reject the combination outright, so it is cleared
		// here to keep the object internally consistent before it is ever persisted.
		this.recurrenceFrequency = recurring ? frequency : null;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	public void setJustificationFields(String nonRecurringJustification, String paymentTerms) {
		this.nonRecurringJustification = nonRecurringJustification;
		this.paymentTerms = paymentTerms;
	}

	public void setHistoricalAmounts(BigDecimal accrued, BigDecimal previouslyApproved, BigDecimal ytd,
			BigDecimal mtd) {
		this.accruedAmount = accrued;
		this.previouslyApprovedAmount = previouslyApproved;
		this.ytdAmount = ytd;
		this.mtdAmount = mtd;
	}

	public void setBudget(BigDecimal budgetAvailable, String budgetReference) {
		this.budgetAvailable = budgetAvailable;
		this.budgetReference = budgetReference;
	}

	public UUID getId() {
		return this.id;
	}

	public boolean isRecurring() {
		return this.recurring;
	}

	public RecurrenceFrequency getRecurrenceFrequency() {
		return this.recurrenceFrequency;
	}

	public String getNonRecurringJustification() {
		return this.nonRecurringJustification;
	}

	public BigDecimal getAccruedAmount() {
		return this.accruedAmount;
	}

	public BigDecimal getPreviouslyApprovedAmount() {
		return this.previouslyApprovedAmount;
	}

	public BigDecimal getYtdAmount() {
		return this.ytdAmount;
	}

	public BigDecimal getMtdAmount() {
		return this.mtdAmount;
	}

	public String getPaymentTerms() {
		return this.paymentTerms;
	}

	public BigDecimal getBudgetAvailable() {
		return this.budgetAvailable;
	}

	public String getBudgetReference() {
		return this.budgetReference;
	}

}
