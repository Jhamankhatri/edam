package com.edam.configplane.authority;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A recommending authority (BRD FM-11).
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>Title, department and user are required.</li>
 *   <li>Title allows letters, numbers and spaces only, max 200 characters.</li>
 *   <li>Title is <strong>read-only on edit</strong> (BRD §8.6), so it is {@code updatable = false}.</li>
 *   <li>A referenced authority cannot be deleted while the delegation matrix references it; the
 *       service refuses and the foreign key from {@code delegation_rule} is the backstop.</li>
 * </ul>
 *
 * <p>Department and user are stored as stable external codes/identifiers rather than as foreign
 * keys into a master-data table, because the source of truth for users, departments and branches is
 * an explicit open decision (OD-36/INT-01). Persisting an identifier plus a snapshot of the display
 * name keeps historical requests readable even if the master data changes — a deliberate
 * denormalisation required for audit fidelity, not an oversight.
 */
@Entity
@Table(name = "recommending_authority")
public class RecommendingAuthority extends InstitutionOwnedEntity {

	@NotBlank
	@Size(max = 200)
	@Pattern(regexp = "[A-Za-z0-9 ]+", message = "must contain only letters, numbers and spaces")
	@Column(name = "title", nullable = false, updatable = false, length = 200)
	private String title;

	@NotBlank
	@Size(max = 64)
	@Column(name = "department_code", nullable = false, length = 64)
	private String departmentCode;

	@NotBlank
	@Size(max = 64)
	@Column(name = "employee_id", nullable = false, length = 64)
	private String employeeId;

	/** Display name snapshot, so a report does not need live master data. */
	@Size(max = 200)
	@Column(name = "employee_display_name", length = 200)
	private String employeeDisplayName;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected RecommendingAuthority() {
		// JPA
	}

	public RecommendingAuthority(String title, String departmentCode, String employeeId, String employeeDisplayName) {
		this.title = title;
		this.departmentCode = departmentCode;
		this.employeeId = employeeId;
		this.employeeDisplayName = employeeDisplayName;
	}

	/** Title is immutable by design; only the assignment changes. */
	public void reassign(String departmentCode, String employeeId, String employeeDisplayName) {
		this.departmentCode = departmentCode;
		this.employeeId = employeeId;
		this.employeeDisplayName = employeeDisplayName;
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDepartmentCode() {
		return this.departmentCode;
	}

	public String getEmployeeId() {
		return this.employeeId;
	}

	public String getEmployeeDisplayName() {
		return this.employeeDisplayName;
	}

	public boolean isActive() {
		return this.active;
	}

}
