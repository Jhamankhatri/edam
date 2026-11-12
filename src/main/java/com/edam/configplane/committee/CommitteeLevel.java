package com.edam.configplane.committee;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An Expense Committee approval level (BRD FM-08, §10.3).
 *
 * <h2>Configured, not hardcoded</h2>
 * The BRD describes "Levels 1–3 as descending approval intensity" but explicitly says the exact
 * boundaries and their relationship to configurable levels are <em>not verified</em>. EDAM therefore
 * stores levels as data with their own amount ranges and lets the routing engine select the level
 * that covers an amount — no level numbers, no boundaries and no intensity ordering are assumed in
 * code.
 *
 * <h2>Validation rules from BRD §8.6</h2>
 * <ul>
 *   <li>Minimum approvals cannot exceed the selected member count.</li>
 *   <li>Lower bound must be strictly less than upper bound.</li>
 *   <li>At least one approver must be selected.</li>
 *   <li>A required CEO/CFO must be an assigned member of the level.</li>
 *   <li>At most ten selected EC members (a documented operational limit).</li>
 *   <li>Amount ranges of active levels must not overlap — enforced in the service and asserted by
 *       a database-level exclusion check where expressible.</li>
 * </ul>
 *
 * <p>These are validated in {@code CommitteeService} rather than only by annotations, because they
 * are cross-field and cross-record invariants.
 */
@Entity
@Table(name = "committee_level")
public class CommitteeLevel extends InstitutionOwnedEntity {

	/** BRD §8.6: a new level may select at most ten members. */
	public static final int MAX_SELECTED_MEMBERS = 10;

	@NotBlank
	@Size(max = 120)
	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@NotNull
	@DecimalMin(value = "0.0", message = "must not be negative")
	@Digits(integer = 17, fraction = 2)
	@Column(name = "amount_from", nullable = false, precision = 19, scale = 2)
	private BigDecimal amountFrom;

	/** Exclusive upper bound; {@code null} means "no upper limit". */
	@Digits(integer = 17, fraction = 2)
	@Column(name = "amount_to", precision = 19, scale = 2)
	private BigDecimal amountTo;

	@Min(value = 1, message = "at least one approver is required")
	@Column(name = "minimum_approvals", nullable = false)
	private int minimumApprovals;

	@Min(value = 1, message = "at least one member must be selected")
	@Column(name = "required_member_count", nullable = false)
	private int requiredMemberCount;

	@Column(name = "requires_ceo", nullable = false)
	private boolean requiresCeo;

	@Column(name = "requires_cfo", nullable = false)
	private boolean requiresCfo;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@ManyToMany
	@JoinTable(name = "committee_level_member", joinColumns = @JoinColumn(name = "level_id"),
			inverseJoinColumns = @JoinColumn(name = "member_id"))
	private Set<CommitteeMember> members = new LinkedHashSet<>();

	protected CommitteeLevel() {
		// JPA
	}

	public CommitteeLevel(String name, BigDecimal amountFrom, BigDecimal amountTo, int minimumApprovals,
			int requiredMemberCount, boolean requiresCeo, boolean requiresCfo) {
		this.name = name;
		this.amountFrom = amountFrom;
		this.amountTo = amountTo;
		this.minimumApprovals = minimumApprovals;
		this.requiredMemberCount = requiredMemberCount;
		this.requiresCeo = requiresCeo;
		this.requiresCfo = requiresCfo;
	}

	public void update(String name, BigDecimal amountFrom, BigDecimal amountTo, int minimumApprovals,
			int requiredMemberCount, boolean requiresCeo, boolean requiresCfo) {
		this.name = name;
		this.amountFrom = amountFrom;
		this.amountTo = amountTo;
		this.minimumApprovals = minimumApprovals;
		this.requiredMemberCount = requiredMemberCount;
		this.requiresCeo = requiresCeo;
		this.requiresCfo = requiresCfo;
	}

	public void replaceMembers(Set<CommitteeMember> members) {
		this.members.clear();
		this.members.addAll(members);
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	/** True when the given amount falls inside this level's configured band. */
	public boolean covers(BigDecimal amount) {
		if (amount == null) {
			return false;
		}
		boolean aboveLower = amount.compareTo(this.amountFrom) >= 0;
		boolean belowUpper = this.amountTo == null || amount.compareTo(this.amountTo) < 0;
		return aboveLower && belowUpper;
	}

	public String getName() {
		return this.name;
	}

	public BigDecimal getAmountFrom() {
		return this.amountFrom;
	}

	public BigDecimal getAmountTo() {
		return this.amountTo;
	}

	public int getMinimumApprovals() {
		return this.minimumApprovals;
	}

	public int getRequiredMemberCount() {
		return this.requiredMemberCount;
	}

	public boolean isRequiresCeo() {
		return this.requiresCeo;
	}

	public boolean isRequiresCfo() {
		return this.requiresCfo;
	}

	public boolean isActive() {
		return this.active;
	}

	public Set<CommitteeMember> getMembers() {
		return Set.copyOf(this.members);
	}

}
