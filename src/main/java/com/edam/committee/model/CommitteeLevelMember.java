package com.edam.committee.model;

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
 * Seats a member on one amount band, optionally mandatorily (Module F).
 *
 * <h2>Why this is not an {@code AuditableEntity}</h2>
 * The table has no update tracking, no version and no audit columns: a seating is created or removed, never
 * edited. Carrying a version column that no query reads would suggest optimistic locking is in play when it is
 * not.
 */
@Entity
@Table(name = "committee_level_members")
public class CommitteeLevelMember {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "committee_level_id", nullable = false)
	private CommitteeLevel level;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "committee_member_id", nullable = false)
	private CommitteeMember member;

	/**
	 * A mandatory member must approve for the band's quorum to count, whatever the number of optional
	 * approvals. This is how "the CFO must sign off" is expressed without hard-coding a role check.
	 */
	@Column(name = "is_mandatory", nullable = false)
	private boolean mandatory;

	@Column(name = "approval_order")
	private Short approvalOrder;

	protected CommitteeLevelMember() {
	}

	public CommitteeLevelMember(CommitteeLevel level, CommitteeMember member, boolean mandatory, Short approvalOrder) {
		this.level = level;
		this.member = member;
		this.mandatory = mandatory;
		this.approvalOrder = approvalOrder;
	}

	public UUID getId() {
		return this.id;
	}

	public CommitteeLevel getLevel() {
		return this.level;
	}

	public CommitteeMember getMember() {
		return this.member;
	}

	public boolean isMandatory() {
		return this.mandatory;
	}

	public Short getApprovalOrder() {
		return this.approvalOrder;
	}

}
