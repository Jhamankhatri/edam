package com.edam.committee.model;

import java.time.Instant;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A committee member, optionally an office holder (Module F).
 *
 * <h2>Why the office is a column and not inferred from the user's roles</h2>
 * The charter says "the CFO must sit on every committee". That is a fact about the committee, not about the
 * person's global roles: an acting CFO can chair a committee without holding the CFO role system-wide, and a
 * CFO can be on one committee but not another. Storing it here lets the mandatory-member rule be verified at
 * configuration time, and the database enforces at most one active CEO, CFO and head of administration per
 * committee — a read-then-write check in a service would race.
 */
@Entity
@Table(name = "committee_members")
public class CommitteeMember extends AuditableEntity {

	/** The offices a committee can seat. */
	public enum MemberType {

		/** The chief executive. At most one active per committee. */
		CEO,
		/** The chief financial officer. At most one active per committee. */
		CFO,
		/** The head of administration and general services. At most one active per committee. */
		HEAD_OF_ADMIN_GSD,
		/** An ordinary member. */
		MEMBER

	}

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "committee_id", nullable = false)
	private ExpenseCommittee committee;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	/**
	 * Stored by name, not by ordinal. The database constrains this column to the office vocabulary, and an
	 * ordinal would silently re-point every stored value if the enum were ever reordered or a member type
	 * inserted in the middle — turning a CFO into a CEO without a single row being written.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "member_type", nullable = false, length = 40)
	private MemberType memberType = MemberType.MEMBER;

	@Column(name = "effective_from", nullable = false)
	private Instant effectiveFrom;

	@Column(name = "effective_to")
	private Instant effectiveTo;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected CommitteeMember() {
	}

	public CommitteeMember(ExpenseCommittee committee, UUID userId, MemberType memberType, Instant effectiveFrom) {
		this.committee = committee;
		this.userId = userId;
		this.memberType = memberType;
		this.effectiveFrom = effectiveFrom;
	}

	public void end(Instant when) {
		this.effectiveTo = when;
		this.active = false;
	}

	public ExpenseCommittee getCommittee() {
		return this.committee;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public MemberType getMemberType() {
		return this.memberType;
	}

	public Instant getEffectiveFrom() {
		return this.effectiveFrom;
	}

	public Instant getEffectiveTo() {
		return this.effectiveTo;
	}

	public boolean isActive() {
		return this.active;
	}

}
