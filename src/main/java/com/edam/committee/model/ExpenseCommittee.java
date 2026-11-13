package com.edam.committee.model;

import java.util.ArrayList;
import java.util.List;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * An expense committee (Module F) — the body that approves amounts above the delegation matrix.
 *
 * <h2>Why committees are separate from the matrix</h2>
 * The delegation matrix routes a request to a chain of individuals by classification and amount. A committee
 * is a differently-shaped authority: several people who must collectively approve, some of them mandatory,
 * with a quorum per amount band. Modelling it as another kind of delegate would have lost the "three of five,
 * including the CFO" rule that a bank's charter actually specifies.
 */
@Entity
@Table(name = "expense_committees")
public class ExpenseCommittee extends AuditableEntity {

	@Column(name = "committee_code", nullable = false, length = 64)
	private String committeeCode;

	@Column(name = "committee_name", nullable = false, length = 200)
	private String committeeName;

	@Column(name = "description")
	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@OneToMany(mappedBy = "committee", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("levelNumber ASC")
	private List<CommitteeLevel> levels = new ArrayList<>();

	@OneToMany(mappedBy = "committee", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("effectiveFrom ASC")
	private List<CommitteeMember> members = new ArrayList<>();

	protected ExpenseCommittee() {
	}

	public ExpenseCommittee(String committeeCode, String committeeName, String description) {
		this.committeeCode = committeeCode;
		this.committeeName = committeeName;
		this.description = description;
	}

	public void rename(String committeeName, String description) {
		this.committeeName = committeeName;
		this.description = description;
	}

	public void deactivate() {
		this.active = false;
		this.levels.forEach(CommitteeLevel::deactivate);
	}

	public String getCommitteeCode() {
		return this.committeeCode;
	}

	public String getCommitteeName() {
		return this.committeeName;
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isActive() {
		return this.active;
	}

	public List<CommitteeLevel> getLevels() {
		return this.levels;
	}

	public List<CommitteeMember> getMembers() {
		return this.members;
	}

}
