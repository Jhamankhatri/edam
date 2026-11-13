package com.edam.committee.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * One amount band of a committee: from {@code minimumAmount} up to {@code maximumAmount} (inclusive of the
 * minimum, exclusive of the maximum, matching the database's {@code [)} range).
 *
 * <p>The currency is explicit rather than inherited. A committee that approves in USD and PKR needs two
 * independent band ladders: a $10,000 approval and a ₨10,000 approval are not the same decision, and treating
 * them as one ladder would either let a small foreign payment through at the wrong level or demand the top
 * level for a trivial one.
 *
 * <p>{@code minimumApprovals} is a floor. The database enforces at least one, so a band configured to require
 * zero approvals cannot be created — a committee level that approves by silence is not an approval.
 */
@Entity
@Table(name = "committee_levels")
public class CommitteeLevel extends AuditableEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "committee_id", nullable = false)
	private ExpenseCommittee committee;

	@Column(name = "level_number", nullable = false)
	private short levelNumber;

	@Column(name = "level_name", nullable = false, length = 200)
	private String levelName;

	@Column(name = "minimum_amount", nullable = false, precision = 19, scale = 4)
	private BigDecimal minimumAmount;

	/** {@code null} means "no upper bound", which is how the top band is expressed. */
	@Column(name = "maximum_amount", precision = 19, scale = 4)
	private BigDecimal maximumAmount;

	@Column(name = "currency_id", nullable = false)
	private UUID currencyId;

	@Column(name = "minimum_approvals", nullable = false)
	private short minimumApprovals;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@OneToMany(mappedBy = "level", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<CommitteeLevelMember> seats = new ArrayList<>();

	protected CommitteeLevel() {
	}

	public CommitteeLevel(ExpenseCommittee committee, short levelNumber, String levelName, BigDecimal minimumAmount,
			BigDecimal maximumAmount, UUID currencyId, short minimumApprovals) {
		this.committee = committee;
		this.levelNumber = levelNumber;
		this.levelName = levelName;
		this.minimumAmount = minimumAmount;
		this.maximumAmount = maximumAmount;
		this.currencyId = currencyId;
		this.minimumApprovals = minimumApprovals;
	}

	public void deactivate() {
		this.active = false;
	}

	/** True when {@code amount} falls in this band, half-open: {@code [minimum, maximum)}. */
	public boolean covers(BigDecimal amount) {
		if (this.maximumAmount == null) {
			return amount.compareTo(this.minimumAmount) >= 0;
		}
		return amount.compareTo(this.minimumAmount) >= 0 && amount.compareTo(this.maximumAmount) < 0;
	}

	public ExpenseCommittee getCommittee() {
		return this.committee;
	}

	public short getLevelNumber() {
		return this.levelNumber;
	}

	public String getLevelName() {
		return this.levelName;
	}

	public BigDecimal getMinimumAmount() {
		return this.minimumAmount;
	}

	public BigDecimal getMaximumAmount() {
		return this.maximumAmount;
	}

	public UUID getCurrencyId() {
		return this.currencyId;
	}

	public short getMinimumApprovals() {
		return this.minimumApprovals;
	}

	public boolean isActive() {
		return this.active;
	}

	public List<CommitteeLevelMember> getSeats() {
		return this.seats;
	}

}
