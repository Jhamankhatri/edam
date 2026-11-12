package com.edam.configplane.committee;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A member of the Expense Committee pool (BRD FM-08).
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>Duplicate member/designation combinations are prevented; when an inactive match exists the
 *       API directs the administrator to reactivate it instead of creating a duplicate.</li>
 *   <li>Only one <em>active</em> CEO, CFO and Head of Admin &amp; GSD may exist — enforced by
 *       partial unique indexes in the migration, because a service check races.</li>
 * </ul>
 *
 * <p>Membership in the pool is <strong>not</strong> approval authority. A member can only act when
 * an EC level selects them <em>and</em> the workflow assigns them a task (BRD §23.3).
 */
@Entity
@Table(name = "committee_member")
public class CommitteeMember extends InstitutionOwnedEntity {

	@NotBlank
	@Size(max = 64)
	@Column(name = "employee_id", nullable = false, length = 64)
	private String employeeId;

	@NotBlank
	@Size(max = 200)
	@Column(name = "display_name", nullable = false, length = 200)
	private String displayName;

	@Size(max = 120)
	@Column(name = "designation_label", length = 120)
	private String designationLabel;

	@Enumerated(EnumType.STRING)
	@Column(name = "designation", nullable = false, length = 32)
	private CommitteeDesignation designation = CommitteeDesignation.MEMBER;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected CommitteeMember() {
		// JPA
	}

	public CommitteeMember(String employeeId, String displayName, String designationLabel,
			CommitteeDesignation designation) {
		this.employeeId = employeeId;
		this.displayName = displayName;
		this.designationLabel = designationLabel;
		this.designation = designation;
	}

	public void update(String displayName, String designationLabel, CommitteeDesignation designation) {
		this.displayName = displayName;
		this.designationLabel = designationLabel;
		this.designation = designation;
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	public String getEmployeeId() {
		return this.employeeId;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getDesignationLabel() {
		return this.designationLabel;
	}

	public CommitteeDesignation getDesignation() {
		return this.designation;
	}

	public boolean isActive() {
		return this.active;
	}

}
