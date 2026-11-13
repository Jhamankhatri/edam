package com.edam.expenserequest.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * CAPEX-specific detail for one request (Module G).
 *
 * <h2>Unbudgeted capital spend must say why</h2>
 * The schema requires {@code is_budgeted OR unbudgeted_reason IS NOT NULL}. A capital purchase outside
 * the budget is legitimate — that is why the flag exists — but an unexplained one is not: it is the exact
 * item a board will ask about, and a nullable field would simply be left empty.
 *
 * <h2>Reason length</h2>
 * `unbudgeted_reason` and `over_budget_reason` are {@code VARCHAR(500)}, matching the BRD's stated limit.
 * The limit is enforced on the column, so the API cannot accept text that would be truncated silently on
 * write.
 *
 * <h2>Warranty dates</h2>
 * `warranty_end_date >= warranty_start_date` is a CHECK. A negative warranty period would make the asset
 * register nonsense, and validating it only in the service would leave any import unprotected.
 */
@Entity
@Table(name = "capex_request_details")
public class CapexRequestDetail {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "expense_request_id", nullable = false)
	private ExpenseRequest request;

	@Column(name = "request_type", length = 50)
	private String requestType;

	/** Reference to a STA detail row, set once the rows exist. */
	@Column(name = "sta_id")
	private UUID staId;

	@Column(name = "invoice_total", precision = 19, scale = 4)
	private BigDecimal invoiceTotal;

	@Column(name = "approval_amount", precision = 19, scale = 4)
	private BigDecimal approvalAmount;

	@Column(name = "total_charges", precision = 19, scale = 4)
	private BigDecimal totalCharges;

	@Column(name = "is_budgeted", nullable = false)
	private boolean budgeted = true;

	@Column(name = "budget_amount", precision = 19, scale = 4)
	private BigDecimal budgetAmount;

	@Column(name = "unbudgeted_reason", length = 500)
	private String unbudgetedReason;

	@Column(name = "over_budget_reason", length = 500)
	private String overBudgetReason;

	@Column(name = "classification", length = 100)
	private String classification;

	@Column(name = "warranty_start_date")
	private LocalDate warrantyStartDate;

	@Column(name = "warranty_end_date")
	private LocalDate warrantyEndDate;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	protected CapexRequestDetail() {
	}

	public CapexRequestDetail(boolean budgeted) {
		this.budgeted = budgeted;
	}

	void attachTo(ExpenseRequest request) {
		this.request = request;
	}

	void setStaId(UUID staId) {
		this.staId = staId;
	}

	public void setFinancials(BigDecimal invoiceTotal, BigDecimal totalCharges, BigDecimal approvalAmount) {
		this.invoiceTotal = invoiceTotal;
		this.totalCharges = totalCharges;
		this.approvalAmount = approvalAmount;
	}

	public void setBudget(boolean budgeted, BigDecimal budgetAmount, String unbudgetedReason,
			String overBudgetReason) {
		this.budgeted = budgeted;
		this.budgetAmount = budgetAmount;
		this.unbudgetedReason = unbudgetedReason;
		this.overBudgetReason = overBudgetReason;
	}

	public void setAssetDetails(String requestType, String classification, LocalDate warrantyStart,
			LocalDate warrantyEnd) {
		this.requestType = requestType;
		this.classification = classification;
		this.warrantyStartDate = warrantyStart;
		this.warrantyEndDate = warrantyEnd;
	}

	public UUID getId() {
		return this.id;
	}

	public String getRequestType() {
		return this.requestType;
	}

	public UUID getStaId() {
		return this.staId;
	}

	public BigDecimal getInvoiceTotal() {
		return this.invoiceTotal;
	}

	public BigDecimal getApprovalAmount() {
		return this.approvalAmount;
	}

	public BigDecimal getTotalCharges() {
		return this.totalCharges;
	}

	public boolean isBudgeted() {
		return this.budgeted;
	}

	public BigDecimal getBudgetAmount() {
		return this.budgetAmount;
	}

	public String getUnbudgetedReason() {
		return this.unbudgetedReason;
	}

	public String getOverBudgetReason() {
		return this.overBudgetReason;
	}

	public String getClassification() {
		return this.classification;
	}

	public LocalDate getWarrantyStartDate() {
		return this.warrantyStartDate;
	}

	public LocalDate getWarrantyEndDate() {
		return this.warrantyEndDate;
	}

}
