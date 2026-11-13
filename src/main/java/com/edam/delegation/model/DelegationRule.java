package com.edam.delegation.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;
import com.edam.expenseconfiguration.model.ExpenseCategory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * One row of the delegation matrix: for a classification and an amount band, <em>who approves</em>
 * (Module E).
 *
 * <h2>The matrix is the routing rule, and it is data</h2>
 * The requirement calls this configurable approval routing, and it is the single most important piece of
 * configuration in EDAM. It is expressed as rows rather than code so that a bank can change its approval
 * chain without a release — and so that the change is auditable, reviewable and effective-dated.
 *
 * <h2>Effective dating, and why it is not optional</h2>
 * A rule has {@code effectiveFrom} and {@code effectiveTo}. Without them, editing a band would silently
 * change how in-flight requests route, and an auditor could not determine which rule applied when a
 * request was approved last quarter. A request resolves its route once, at submission, and pins it; see
 * {@code WorkflowInstance}.
 *
 * <h2>Overlap is impossible in the database</h2>
 * Migration V7 installs an EXCLUDE constraint over (category, subcategory, nature, request type, currency,
 * amount range) for active rules. Two active rules covering the same amount would make the route depend on
 * row order, so the constraint — not a service check — is what guarantees determinism.
 *
 * <h2>NULL means "any"</h2>
 * A null {@code subcategoryId} or {@code requestTypeId} means the rule applies to every subcategory or
 * request type. That is why the migration uses {@code COALESCE(...)} in the EXCLUDE constraint: PostgreSQL
 * treats NULLs as distinct, and without the coalescing a bank could insert a category-wide rule
 * indefinitely.
 */
@Entity
@Table(name = "expense_delegation_rules")
public class DelegationRule extends AuditableEntity {

	@Column(name = "category_id", nullable = false, updatable = false)
	private UUID categoryId;

	/** Null means "every subcategory of the category". */
	@Column(name = "subcategory_id", updatable = false)
	private UUID subcategoryId;

	@Column(name = "nature_id", nullable = false, updatable = false)
	private UUID natureId;

	/** Null means "every request type". */
	@Column(name = "request_type_id", updatable = false)
	private UUID requestTypeId;

	@Column(name = "minimum_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal minimumAmount = BigDecimal.ZERO;

	/** Null means "no upper bound". */
	@Column(name = "maximum_amount", precision = 19, scale = 4)
	private BigDecimal maximumAmount;

	@Column(name = "currency_id", nullable = false, updatable = false)
	private UUID currencyId;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	/**
	 * The approval chain, in order.
	 *
	 * <p>Ordered by {@code stepOrder} and fetched with the rule because the route is always needed as a
	 * whole: a partial chain would produce a workflow that stops halfway.
	 */
	@OneToMany(mappedBy = "delegationRule", cascade = CascadeType.ALL, orphanRemoval = true,
			fetch = FetchType.EAGER)
	@OrderBy("stepOrder ASC")
	private List<DelegationApproverStep> steps = new ArrayList<>();

	/** True when an amount of the given currency falls in this band at the given instant. */
	public boolean covers(BigDecimal amount, UUID currency, Instant at) {
		if (!this.active || !this.currencyId.equals(currency)) {
			return false;
		}
		// A rule is effective only inside its window. Comparing at both ends matters: a rule whose
		// effectiveTo has passed must not route a new request even though it routed the last one.
		boolean started = !at.isBefore(this.effectiveFrom);
		boolean notEnded = this.effectiveTo == null || at.isBefore(this.effectiveTo);
		if (!started || !notEnded) {
			return false;
		}
		boolean aboveFloor = amount.compareTo(this.minimumAmount) >= 0;
		boolean belowCeiling = this.maximumAmount == null || amount.compareTo(this.maximumAmount) < 0;
		return aboveFloor && belowCeiling;
	}

	/** The category this rule belongs to, for the type check in the schema. Not a mapped association. */
	public UUID getCategoryId() {
		return this.categoryId;
	}

	public void setCategoryId(UUID categoryId) {
		this.categoryId = categoryId;
	}

	public UUID getSubcategoryId() {
		return this.subcategoryId;
	}

	public void setSubcategoryId(UUID subcategoryId) {
		this.subcategoryId = subcategoryId;
	}

	public UUID getNatureId() {
		return this.natureId;
	}

	public void setNatureId(UUID natureId) {
		this.natureId = natureId;
	}

	public UUID getRequestTypeId() {
		return this.requestTypeId;
	}

	public void setRequestTypeId(UUID requestTypeId) {
		this.requestTypeId = requestTypeId;
	}

	public BigDecimal getMinimumAmount() {
		return this.minimumAmount;
	}

	public void setMinimumAmount(BigDecimal minimumAmount) {
		this.minimumAmount = minimumAmount;
	}

	public BigDecimal getMaximumAmount() {
		return this.maximumAmount;
	}

	public void setMaximumAmount(BigDecimal maximumAmount) {
		this.maximumAmount = maximumAmount;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public void setCurrencyId(UUID currencyId) {
		this.currencyId = currencyId;
	}

	public Instant getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public void setEffectiveFrom(Instant effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public Instant getEffectiveTo() {
		return this.effectiveTo;
	}

	public void setEffectiveTo(Instant effectiveTo) {
		this.effectiveTo = effectiveTo;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public List<DelegationApproverStep> getSteps() {
		return this.steps;
	}

	public void setSteps(List<DelegationApproverStep> steps) {
		this.steps = steps;
	}

	/** Convenience for callers that only need the category's type. */
	public static boolean isCapexOnly(ExpenseCategory category) {
		return category.getExpenseType() == ExpenseCategory.ExpenseType.CAPEX;
	}

}
