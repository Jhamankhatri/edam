package com.edam.expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import com.edam.common.tenant.InstitutionOwnedEntity;
import com.edam.configplane.ExpenseNature;
import com.edam.configplane.category.ExpenseCategory;
import com.edam.configplane.category.ExpenseSubcategory;
import com.edam.workflow.WorkflowDefinition;
import com.edam.workflow.WorkflowVersion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An OPEX or CAPEX expense request (BRD FM-01, FM-04).
 *
 * <h2>What is immutable once submitted</h2>
 * The BRD requires that a request in flight is not altered by later configuration changes (§8.6), so
 * this entity pins what it needs rather than resolving it at read time:
 * <ul>
 *   <li>the <strong>currency rate</strong> used, in {@link RequestCurrencySnapshot} — a later rate
 *       change must not restate an approved amount;</li>
 *   <li>the <strong>workflow version</strong>, in {@link #workflowVersion} — publishing a new version
 *       must not re-route an in-flight request;</li>
 *   <li>the <strong>department code</strong> as text, because the department register is external
 *       (INT-01) and a deactivated department must not break the audit trail.</li>
 * </ul>
 *
 * <h2>Title and description are locked on edit</h2>
 * BRD §8.6 requires the title fields of an existing request to be locked. The mapped columns are
 * therefore {@code updatable = false}; changing them would need an explicit, separately audited
 * operation, which is not exposed.
 *
 * <h2>Money</h2>
 * {@code amount} is in the request currency and {@code amountInBase} in the institution base
 * currency. Both are stored, because reporting totals need the base figure and the requester's intent
 * needs the original. The conversion itself was performed once, at creation, from the rate snapshot.
 *
 * <h2>No approval logic here</h2>
 * This is an aggregate root for the request's own data. Who may act, and in what order, is decided by
 * {@link com.edam.approval.ApprovalEngine} from the pinned workflow version and the delegation matrix.
 */
@Entity
@Table(name = "expense_request")
public class ExpenseRequest extends InstitutionOwnedEntity {

	@NotBlank
	@Column(name = "request_number", nullable = false, length = 40, updatable = false)
	private String requestNumber;

	/** Locked against edit after creation (BRD §8.6). */
	@NotBlank
	@Size(max = 200)
	@Column(name = "title", nullable = false, length = 200, updatable = false)
	private String title;

	@Size(max = 4000)
	@Column(name = "description", length = 4000)
	private String description;

	@NotNull
	@Digits(integer = 17, fraction = 2)
	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@NotBlank
	@Pattern(regexp = "[A-Z]{3}")
	@Column(name = "currency_code", nullable = false, length = 3)
	private String currencyCode;

	@NotNull
	@Digits(integer = 17, fraction = 2)
	@Column(name = "amount_in_base", nullable = false, precision = 19, scale = 2)
	private BigDecimal amountInBase;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "expense_nature", nullable = false, length = 8)
	private ExpenseNature expenseNature;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private ExpenseCategory category;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subcategory_id")
	private ExpenseSubcategory subcategory;

	@NotBlank
	@Column(name = "department_code", nullable = false, length = 64)
	private String departmentCode;

	@Size(max = 64)
	@Column(name = "branch_code", length = 64)
	private String branchCode;

	@NotBlank
	@Column(name = "requester_employee_id", nullable = false, length = 64)
	private String requesterEmployeeId;

	@NotBlank
	@Column(name = "requester_username", nullable = false, length = 64)
	private String requesterUsername;

	@Size(max = 200)
	@Column(name = "requester_display_name", length = 200)
	private String requesterDisplayName;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "state", nullable = false, length = 32)
	private RequestState state = RequestState.DRAFT;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_definition_id")
	private WorkflowDefinition workflowDefinition;

	/** Pinned at submission; never updated afterwards (BRD §8.6). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "workflow_version_id")
	private WorkflowVersion workflowVersion;

	@Size(max = 64)
	@Column(name = "current_step_code", length = 64)
	private String currentStepCode;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "finalized_at")
	private Instant finalizedAt;

	@Size(max = 1000)
	@Column(name = "cancellation_reason", length = 1000)
	private String cancellationReason;

	@OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<RequestCurrencySnapshot> currencySnapshots = new LinkedHashSet<>();

	protected ExpenseRequest() {
		// JPA
	}

	public ExpenseRequest(String requestNumber, String title, BigDecimal amount, String currencyCode,
			BigDecimal amountInBase, ExpenseNature expenseNature, ExpenseCategory category,
			ExpenseSubcategory subcategory, String departmentCode, String requesterEmployeeId,
			String requesterUsername, String requesterDisplayName) {
		this.requestNumber = requestNumber;
		this.title = title;
		this.amount = amount;
		this.currencyCode = currencyCode;
		this.amountInBase = amountInBase;
		this.expenseNature = expenseNature;
		this.category = category;
		this.subcategory = subcategory;
		this.departmentCode = departmentCode;
		this.requesterEmployeeId = requesterEmployeeId;
		this.requesterUsername = requesterUsername;
		this.requesterDisplayName = requesterDisplayName;
	}

	/** Record the exchange rate as it was when the request was created. */
	public void captureRate(RequestCurrencySnapshot snapshot) {
		snapshot.attachTo(this);
		this.currencySnapshots.add(snapshot);
	}

	/**
	 * Update the mutable content of a draft or returned request.
	 *
	 * <p>Title is intentionally absent: it is locked (BRD §8.6). Callers can change the amount,
	 * narrative, classification and department while the request is editable.
	 */
	public void updateContent(String description, BigDecimal amount, String currencyCode, BigDecimal amountInBase,
			ExpenseNature expenseNature, ExpenseCategory category, ExpenseSubcategory subcategory,
			String departmentCode, String branchCode) {
		this.description = description;
		this.amount = amount;
		this.currencyCode = currencyCode;
		this.amountInBase = amountInBase;
		this.expenseNature = expenseNature;
		this.category = category;
		this.subcategory = subcategory;
		this.departmentCode = departmentCode;
		this.branchCode = branchCode;
	}

	/** Pin the workflow version at submission. Called once. */
	public void pinWorkflow(WorkflowDefinition definition, WorkflowVersion version, String firstStepCode) {
		if (this.workflowVersion != null && !this.workflowVersion.getId().equals(version.getId())) {
			throw new IllegalStateException("The workflow version of a submitted request cannot change");
		}
		this.workflowDefinition = definition;
		this.workflowVersion = version;
		this.currentStepCode = firstStepCode;
	}

	public void markSubmitted(Instant when, String firstStepCode) {
		this.state = RequestState.SUBMITTED;
		this.submittedAt = when;
		this.currentStepCode = firstStepCode;
	}

	public void markInApproval(String stepCode) {
		this.state = RequestState.IN_APPROVAL;
		this.currentStepCode = stepCode;
	}

	/** A revert returns the request to the requester for editing (BRD §7.4). */
	public void markReturned(String stepCode) {
		this.state = RequestState.RETURNED;
		this.currentStepCode = stepCode;
	}

	public void markApproved(Instant when) {
		this.state = RequestState.APPROVED;
		this.finalizedAt = when;
		this.currentStepCode = null;
	}

	public void markRejected(Instant when) {
		this.state = RequestState.REJECTED;
		this.finalizedAt = when;
		this.currentStepCode = null;
	}

	public void markCancelled(String reason, Instant when) {
		this.state = RequestState.CANCELLED;
		this.cancellationReason = reason;
		this.finalizedAt = when;
		this.currentStepCode = null;
	}

	public void markReversed(Instant when) {
		this.state = RequestState.REVERSED;
		this.finalizedAt = when;
	}

	public boolean isOwnedBy(String username) {
		return username != null && this.requesterUsername.equalsIgnoreCase(username);
	}

	public String getRequestNumber() {
		return this.requestNumber;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDescription() {
		return this.description;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public String getCurrencyCode() {
		return this.currencyCode;
	}

	public BigDecimal getAmountInBase() {
		return this.amountInBase;
	}

	public ExpenseNature getExpenseNature() {
		return this.expenseNature;
	}

	public ExpenseCategory getCategory() {
		return this.category;
	}

	public ExpenseSubcategory getSubcategory() {
		return this.subcategory;
	}

	public String getDepartmentCode() {
		return this.departmentCode;
	}

	public String getBranchCode() {
		return this.branchCode;
	}

	public String getRequesterEmployeeId() {
		return this.requesterEmployeeId;
	}

	public String getRequesterUsername() {
		return this.requesterUsername;
	}

	public String getRequesterDisplayName() {
		return this.requesterDisplayName;
	}

	public RequestState getState() {
		return this.state;
	}

	public WorkflowDefinition getWorkflowDefinition() {
		return this.workflowDefinition;
	}

	public WorkflowVersion getWorkflowVersion() {
		return this.workflowVersion;
	}

	public String getCurrentStepCode() {
		return this.currentStepCode;
	}

	public Instant getSubmittedAt() {
		return this.submittedAt;
	}

	public Instant getFinalizedAt() {
		return this.finalizedAt;
	}

	public String getCancellationReason() {
		return this.cancellationReason;
	}

	public Set<RequestCurrencySnapshot> getCurrencySnapshots() {
		return Set.copyOf(this.currencySnapshots);
	}

}
