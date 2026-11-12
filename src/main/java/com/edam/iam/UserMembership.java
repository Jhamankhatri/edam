package com.edam.iam;

import java.util.LinkedHashSet;
import java.util.Set;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A user's membership of one institution, with the roles held there (BRD IAM-04).
 *
 * <h2>Membership is the unit, not the user</h2>
 * BRD IAM-04 allows a user to belong to several institutions. The same person therefore has one row
 * per institution, and roles are attached to the membership rather than to the user. That is what
 * makes "administrator in bank A, requester in bank B" expressible, and it is the structure the
 * authorisation model needs: {@code EdamPrincipal} is built from these rows.
 *
 * <h2>Master data</h2>
 * {@code departmentCode} and {@code branchCode} are identifiers, not foreign keys, because the
 * source of truth for departments and branches is OD-36/INT-01 (unresolved). They are stored here so
 * that routing, registers and reporting can scope by department without depending on an external
 * system being reachable.
 */
@Entity
@Table(name = "user_membership")
public class UserMembership extends InstitutionOwnedEntity {

	@NotBlank
	@Size(max = 64)
	@Column(name = "employee_id", nullable = false, length = 64)
	private String employeeId;

	@NotBlank
	@Size(max = 64)
	@Column(name = "username", nullable = false, length = 64)
	private String username;

	@Size(max = 200)
	@Column(name = "display_name", length = 200)
	private String displayName;

	@Email
	@Size(max = 200)
	@Column(name = "email", length = 200)
	private String email;

	@Size(max = 64)
	@Column(name = "department_code", length = 64)
	private String departmentCode;

	@Size(max = 64)
	@Column(name = "branch_code", length = 64)
	private String branchCode;

	@Size(max = 120)
	@Column(name = "designation", length = 120)
	private String designation;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "user_membership_role", joinColumns = @JoinColumn(name = "membership_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<Role> roles = new LinkedHashSet<>();

	protected UserMembership() {
		// JPA
	}

	public UserMembership(String employeeId, String username, String displayName, String email,
			String departmentCode, String branchCode, String designation) {
		this.employeeId = employeeId;
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.departmentCode = departmentCode;
		this.branchCode = branchCode;
		this.designation = designation;
	}

	public void update(String displayName, String email, String departmentCode, String branchCode,
			String designation) {
		this.displayName = displayName;
		this.email = email;
		this.departmentCode = departmentCode;
		this.branchCode = branchCode;
		this.designation = designation;
	}

	public void replaceRoles(Set<Role> roles) {
		this.roles.clear();
		this.roles.addAll(roles);
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	/** Permission codes effective in this institution, unioned across the member's roles. */
	public Set<String> permissionCodes() {
		Set<String> codes = new LinkedHashSet<>();
		this.roles.stream().filter(Role::isActive).forEach(role -> codes.addAll(role.permissionCodes()));
		return Set.copyOf(codes);
	}

	/** Role codes held in this institution. */
	public Set<String> roleCodes() {
		return this.roles.stream().filter(Role::isActive).map(Role::getCode)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public String getEmployeeId() {
		return this.employeeId;
	}

	public String getUsername() {
		return this.username;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public String getEmail() {
		return this.email;
	}

	public String getDepartmentCode() {
		return this.departmentCode;
	}

	public String getBranchCode() {
		return this.branchCode;
	}

	public String getDesignation() {
		return this.designation;
	}

	public boolean isActive() {
		return this.active;
	}

	public Set<Role> getRoles() {
		return Set.copyOf(this.roles);
	}

}
