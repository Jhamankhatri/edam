package com.edam.configplane.category;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import com.edam.configplane.ExpenseNature;
import com.edam.configplane.authority.RecommendingAuthority;
import com.edam.configplane.committee.CommitteeLevel;
import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * One row of the Expense Delegation Matrix (BRD FM-07).
 *
 * <h2>What it expresses</h2>
 * For a category/sub-category and expense nature, which recommending authorities and which approver
 * types apply, at which committee level, and within which amount band. This row <em>is</em> the
 * configuration that replaces the legacy hardcoded route.
 *
 * <h2>Approver type</h2>
 * Modelled as an enum of the approver kinds the BRD names (department head, recommending authority,
 * committee, CEO, CFO, HOD). The BRD also requires "at least one approver type" and gives no
 * evidence for institution-defined approver kinds; adding a free-text approver kind would invent a
 * routing capability the product cannot execute.
 *
 * <h2>Validation (BRD §8.6)</h2>
 * Category, nature, at least one recommending authority and at least one approver type are required.
 * Amount limits must be positive with at most two decimals. Active rules for the same
 * category/sub-category/nature must not duplicate, and committee amount ranges must not overlap.
 * The uniqueness is a partial unique index; the overlap rule is validated in the service because it
 * spans rows.
 */
@Entity
@Table(name = "delegation_rule")
public class DelegationRule extends InstitutionOwnedEntity {

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private ExpenseCategory category;

	/** Optional: a rule may apply to the whole category. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subcategory_id")
	private ExpenseSubcategory subcategory;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "expense_nature", nullable = false, length = 8)
	private ExpenseNature expenseNature;

	/** Selected approver kinds. At least one is required. */
	@jakarta.persistence.ElementCollection(fetch = FetchType.EAGER)
	@jakarta.persistence.CollectionTable(name = "delegation_rule_approver",
			joinColumns = @JoinColumn(name = "rule_id"))
	@jakarta.persistence.Column(name = "approver_type", nullable = false, length = 32)
	@Enumerated(EnumType.STRING)
	private Set<ApproverType> approverTypes = new LinkedHashSet<>();

	@ManyToMany(fetch = FetchType.LAZY)
	@JoinTable(name = "delegation_rule_authority", joinColumns = @JoinColumn(name = "rule_id"),
			inverseJoinColumns = @JoinColumn(name = "authority_id"))
	private Set<RecommendingAuthority> recommendingAuthorities = new LinkedHashSet<>();

	/** Optional configured EC level for the rule. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "committee_level_id")
	private CommitteeLevel committeeLevel;

	/** Lower amount bound of the rule, inclusive. */
	@NotNull
	@DecimalMin(value = "0.0", message = "must not be negative")
	@Digits(integer = 17, fraction = 2)
	@Column(name = "amount_from", nullable = false, precision = 19, scale = 2)
	private BigDecimal amountFrom = BigDecimal.ZERO;

	/** Upper amount bound, exclusive; {@code null} means no upper limit. */
	@Digits(integer = 17, fraction = 2)
	@Column(name = "amount_to", precision = 19, scale = 2)
	private BigDecimal amountTo;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected DelegationRule() {
		// JPA
	}

	public DelegationRule(ExpenseCategory category, ExpenseSubcategory subcategory, ExpenseNature expenseNature) {
		this.category = category;
		this.subcategory = subcategory;
		this.expenseNature = expenseNature;
	}

	public void update(ExpenseCategory category, ExpenseSubcategory subcategory, ExpenseNature expenseNature,
			BigDecimal amountFrom, BigDecimal amountTo, CommitteeLevel committeeLevel, Set<ApproverType> approverTypes,
			Set<RecommendingAuthority> authorities) {
		this.category = category;
		this.subcategory = subcategory;
		this.expenseNature = expenseNature;
		this.amountFrom = amountFrom;
		this.amountTo = amountTo;
		this.committeeLevel = committeeLevel;
		this.approverTypes.clear();
		this.approverTypes.addAll(approverTypes);
		this.recommendingAuthorities.clear();
		this.recommendingAuthorities.addAll(authorities);
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	/** True when the amount falls in this rule's band. */
	public boolean covers(BigDecimal amount) {
		if (amount == null) {
			return false;
		}
		boolean aboveLower = amount.compareTo(this.amountFrom) >= 0;
		boolean belowUpper = this.amountTo == null || amount.compareTo(this.amountTo) < 0;
		return aboveLower && belowUpper;
	}

	public ExpenseCategory getCategory() {
		return this.category;
	}

	public ExpenseSubcategory getSubcategory() {
		return this.subcategory;
	}

	public ExpenseNature getExpenseNature() {
		return this.expenseNature;
	}

	public Set<ApproverType> getApproverTypes() {
		return Set.copyOf(this.approverTypes);
	}

	public Set<RecommendingAuthority> getRecommendingAuthorities() {
		return Set.copyOf(this.recommendingAuthorities);
	}

	public CommitteeLevel getCommitteeLevel() {
		return this.committeeLevel;
	}

	public BigDecimal getAmountFrom() {
		return this.amountFrom;
	}

	public BigDecimal getAmountTo() {
		return this.amountTo;
	}

	public boolean isActive() {
		return this.active;
	}

}
