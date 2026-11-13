package com.edam.expenserequest.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * The common request header for both OPEX and CAPEX (Module G).
 *
 * <h2>One header, two detail tables</h2>
 * OPEX and CAPEX share everything that identifies and routes a request — number, classification,
 * department, amount, currency, status — and differ only in the detail they capture. They therefore share
 * this header and each has a single-row detail table. The alternative, two independent request tables,
 * would duplicate every routing and audit column and would make "show me all requests for this
 * department" a union that grows with every future expense type.
 *
 * <h2>Money</h2>
 * Every amount is {@link BigDecimal} and every column is {@code NUMERIC(19,4)}. The base-currency figure
 * and the exchange-rate row used to produce it are both stored, so an approval can be reproduced from
 * its inputs years later even after the rate table has moved on. See OD-P2-01 for the rounding decision
 * that remains open.
 *
 * <h2>Status and the workflow engine</h2>
 * {@code currentStatusCode} is the request's own lifecycle state; the approval path lives in the linked
 * workflow instance. Deliberately kept separate: a request can be RETURNED while its workflow instance is
 * still open, and conflating the two would make "returned to me" impossible to query. The header links to
 * the instance by id so the engine remains the sole owner of routing.
 */
@Entity
@Table(name = "expense_requests")
public class ExpenseRequest extends AuditableEntity {

	/**
	 * The request lifecycle.
	 *
	 * <p>These values are constrained by a CHECK constraint. They are deliberately distinct from the
	 * workflow engine's state vocabulary: a workflow state describes a step, this describes the request
	 * as a business object.
	 */
	public enum Status {

		/** Created but not yet submitted. Only the creator can see or edit it. */
		DRAFT,
		/** Submitted into a workflow instance. */
		SUBMITTED,
		/** In approval. */
		IN_PROGRESS,
		/** Returned to the requester for correction. Editable again by its author. */
		RETURNED,
		/** Approved and closed. Terminal. */
		APPROVED,
		/** Rejected by an approver. Terminal. */
		REJECTED,
		/** Withdrawn by the requester before a decision. Terminal. */
		CANCELLED,
		/** An approved request that was subsequently reversed. Terminal. */
		REVERSED

	}

	@Column(name = "request_number", nullable = false, length = 100, updatable = false)
	private String requestNumber;

	@Column(name = "request_type_id", nullable = false)
	private UUID requestTypeId;

	@Column(name = "expense_category_id", nullable = false)
	private UUID expenseCategoryId;

	@Column(name = "expense_subcategory_id")
	private UUID expenseSubcategoryId;

	@Column(name = "expense_nature_id", nullable = false)
	private UUID expenseNatureId;

	@Column(name = "requested_by", nullable = false, updatable = false)
	private UUID requestedBy;

	@Column(name = "department_id", nullable = false)
	private UUID departmentId;

	@Column(name = "branch_id")
	private UUID branchId;

	@Column(name = "currency_id", nullable = false)
	private UUID currencyId;

	@Column(name = "requested_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal requestedAmount;

	@Column(name = "base_currency_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal baseCurrencyAmount;

	/** The rate row used for the conversion; retained so the figure is reproducible. */
	@Column(name = "exchange_rate_id")
	private UUID exchangeRateId;

	@Column(name = "justification")
	private String justification;

	@Column(name = "current_workflow_instance_id")
	private UUID currentWorkflowInstanceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "current_status_code", nullable = false, length = 60)
	private Status currentStatusCode = Status.DRAFT;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "cancelled_at")
	private Instant cancelledAt;

	@Column(name = "cancelled_by")
	private UUID cancelledBy;

	@Column(name = "cancel_reason", length = 500)
	private String cancelReason;

	// Detail rows are part of the aggregate: a request cannot exist without its typed detail, and the
	// detail cannot exist without the request. Both are cascaded for that reason.

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OpexRequestDetail> opexDetails = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CapexRequestDetail> capexDetails = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExpenseRequestItem> items = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExpenseAllocation> allocations = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExpenseQuotation> quotations = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CapexBudgetSummary> budgetSummaries = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CapexStaDetail> staDetails = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExpensePreviousSummary> previousSummaries = new ArrayList<>();

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ExpenseInvoice> invoices = new ArrayList<>();

	protected ExpenseRequest() {
	}

	public ExpenseRequest(String requestNumber, UUID requestedBy) {
		this.requestNumber = requestNumber;
		this.requestedBy = requestedBy;
		this.currentStatusCode = Status.DRAFT;
	}

	// ---------------------------------------------------------------------------------------------
	// Lifecycle
	// ---------------------------------------------------------------------------------------------

	/**
	 * True when the request may still be edited by its author.
	 *
	 * <p>A DRAFT was never submitted. A RETURNED request was sent back for correction, which is the whole
	 * point of returning it — if it were not editable, "return" would be indistinguishable from "reject"
	 * and the approver's only option would be to destroy the work.
	 */
	public boolean isEditable() {
		return this.currentStatusCode == Status.DRAFT || this.currentStatusCode == Status.RETURNED;
	}

	/** True when the request may be submitted. */
	public boolean isSubmittable() {
		return isEditable();
	}

	/** True when the request has reached a state from which nothing further happens. */
	public boolean isTerminal() {
		return this.currentStatusCode == Status.APPROVED || this.currentStatusCode == Status.REJECTED
				|| this.currentStatusCode == Status.CANCELLED || this.currentStatusCode == Status.REVERSED;
	}

	/**
	 * Marks the request submitted and links its workflow instance.
	 *
	 * <p>The instance id is stored on the header so subsequent actions can find the workflow without a
	 * reverse lookup, and the engine remains the only component that decides routing.
	 */
	public void markSubmitted(UUID workflowInstanceId, Instant when) {
		if (!isSubmittable()) {
			throw new IllegalStateException("A request in state " + this.currentStatusCode + " cannot be submitted");
		}
		this.currentStatusCode = Status.SUBMITTED;
		this.currentWorkflowInstanceId = workflowInstanceId;
		this.submittedAt = when;
	}

	/** The workflow engine advanced the request into approval. */
	public void markInProgress() {
		if (this.currentStatusCode == Status.SUBMITTED) {
			this.currentStatusCode = Status.IN_PROGRESS;
		}
	}

	/** An approver returned the request for correction. */
	public void markReturned() {
		this.currentStatusCode = Status.RETURNED;
	}

	/** The workflow reached a terminal decision. */
	public void markDecided(Status outcome) {
		if (outcome != Status.APPROVED && outcome != Status.REJECTED && outcome != Status.REVERSED) {
			throw new IllegalArgumentException("Not a decision outcome: " + outcome);
		}
		this.currentStatusCode = outcome;
	}

	/** The requester withdrew the request. Distinct from rejection, which is an approver's decision. */
	public void cancel(UUID actor, String reason, Instant when) {
		if (isTerminal()) {
			throw new IllegalStateException("A request in state " + this.currentStatusCode + " cannot be cancelled");
		}
		this.currentStatusCode = Status.CANCELLED;
		this.cancelledAt = when;
		this.cancelledBy = actor;
		this.cancelReason = reason;
	}

	/** Replaces the typed detail, clearing the other type. An OPEX request cannot carry CAPEX detail. */
	public void replaceDetails(OpexRequestDetail opex, CapexRequestDetail capex) {
		this.opexDetails.clear();
		this.capexDetails.clear();
		if (opex != null) {
			opex.attachTo(this);
			this.opexDetails.add(opex);
		}
		if (capex != null) {
			capex.attachTo(this);
			this.capexDetails.add(capex);
		}
	}

	public void replaceItems(List<ExpenseRequestItem> newItems) {
		this.items.clear();
		newItems.forEach(item -> {
			item.attachTo(this);
			this.items.add(item);
		});
	}

	public void replaceAllocations(List<ExpenseAllocation> newAllocations) {
		this.allocations.clear();
		newAllocations.forEach(allocation -> {
			allocation.attachTo(this);
			this.allocations.add(allocation);
		});
	}

	public void replaceQuotations(List<ExpenseQuotation> newQuotations) {
		this.quotations.clear();
		newQuotations.forEach(quotation -> {
			quotation.attachTo(this);
			this.quotations.add(quotation);
		});
	}

	public void replaceBudgetSummaries(List<CapexBudgetSummary> summaries) {
		this.budgetSummaries.clear();
		summaries.forEach(summary -> {
			summary.attachTo(this);
			this.budgetSummaries.add(summary);
		});
	}

	public void replacePreviousSummaries(List<ExpensePreviousSummary> summaries) {
		this.previousSummaries.clear();
		summaries.forEach(summary -> {
			summary.attachTo(this);
			this.previousSummaries.add(summary);
		});
	}

	public void replaceInvoices(List<ExpenseInvoice> newInvoices) {
		this.invoices.clear();
		newInvoices.forEach(invoice -> {
			invoice.attachTo(this);
			this.invoices.add(invoice);
		});
	}

	public void replaceStaDetails(List<CapexStaDetail> details) {
		this.staDetails.clear();
		details.forEach(detail -> {
			detail.attachTo(this);
			this.staDetails.add(detail);
		});
	}

	// ---------------------------------------------------------------------------------------------
	// Setters for the header fields (no business decision — plain assignment)
	// ---------------------------------------------------------------------------------------------

	public void setClassification(UUID requestTypeId, UUID categoryId, UUID subcategoryId, UUID natureId) {
		this.requestTypeId = requestTypeId;
		this.expenseCategoryId = categoryId;
		this.expenseSubcategoryId = subcategoryId;
		this.expenseNatureId = natureId;
	}

	public void setPlacement(UUID departmentId, UUID branchId) {
		this.departmentId = departmentId;
		this.branchId = branchId;
	}

	public void setMoney(BigDecimal requestedAmount, UUID currencyId, BigDecimal baseCurrencyAmount,
			UUID exchangeRateId) {
		this.requestedAmount = requestedAmount;
		this.currencyId = currencyId;
		this.baseCurrencyAmount = baseCurrencyAmount;
		this.exchangeRateId = exchangeRateId;
	}

	public void setJustification(String justification) {
		this.justification = justification;
	}

	// ---------------------------------------------------------------------------------------------
	// Accessors
	// ---------------------------------------------------------------------------------------------

	public String getRequestNumber() {
		return this.requestNumber;
	}

	public UUID getRequestTypeId() {
		return this.requestTypeId;
	}

	public UUID getExpenseCategoryId() {
		return this.expenseCategoryId;
	}

	public UUID getExpenseSubcategoryId() {
		return this.expenseSubcategoryId;
	}

	public UUID getExpenseNatureId() {
		return this.expenseNatureId;
	}

	public UUID getRequestedBy() {
		return this.requestedBy;
	}

	public UUID getDepartmentId() {
		return this.departmentId;
	}

	public UUID getBranchId() {
		return this.branchId;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public BigDecimal getRequestedAmount() {
		return this.requestedAmount;
	}

	public BigDecimal getBaseCurrencyAmount() {
		return this.baseCurrencyAmount;
	}

	public UUID getExchangeRateId() {
		return this.exchangeRateId;
	}

	public String getJustification() {
		return this.justification;
	}

	public UUID getCurrentWorkflowInstanceId() {
		return this.currentWorkflowInstanceId;
	}

	public Status getCurrentStatusCode() {
		return this.currentStatusCode;
	}

	public Instant getSubmittedAt() {
		return this.submittedAt;
	}

	public Instant getCancelledAt() {
		return this.cancelledAt;
	}

	public UUID getCancelledBy() {
		return this.cancelledBy;
	}

	public String getCancelReason() {
		return this.cancelReason;
	}

	public List<OpexRequestDetail> getOpexDetails() {
		return this.opexDetails;
	}

	public List<CapexRequestDetail> getCapexDetails() {
		return this.capexDetails;
	}

	public List<ExpenseRequestItem> getItems() {
		return this.items;
	}

	public List<ExpenseAllocation> getAllocations() {
		return this.allocations;
	}

	public List<ExpenseQuotation> getQuotations() {
		return this.quotations;
	}

	public List<CapexBudgetSummary> getBudgetSummaries() {
		return this.budgetSummaries;
	}

	public List<CapexStaDetail> getStaDetails() {
		return this.staDetails;
	}

	public List<ExpensePreviousSummary> getPreviousSummaries() {
		return this.previousSummaries;
	}

	public List<ExpenseInvoice> getInvoices() {
		return this.invoices;
	}

	/** Convenience: the OPEX detail, or null when this is a CAPEX request. */
	public OpexRequestDetail opexDetail() {
		return this.opexDetails.isEmpty() ? null : this.opexDetails.get(0);
	}

	/** Convenience: the CAPEX detail, or null when this is an OPEX request. */
	public CapexRequestDetail capexDetail() {
		return this.capexDetails.isEmpty() ? null : this.capexDetails.get(0);
	}

}
