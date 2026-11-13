package com.edam.usermanagement.model;

import java.time.Instant;
import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A bank employee who can sign in to EDAM (Module C).
 *
 * <h2>passwordHash is nullable, deliberately</h2>
 * Not every deployment authenticates locally. A bank that federates to Active Directory or an OIDC
 * provider should not have a local password hash stored for each user at all — an unused hash is pure
 * attack surface. The column is therefore nullable and the identity adapter decides whether it is
 * consulted.
 *
 * <h2>Deactivation, not deletion</h2>
 * A user who leaves cannot be deleted, because that user is referenced by every request, approval and
 * audit record they ever touched. Setting {@code active = false} keeps attribution intact while making
 * sign-in impossible, which is both the audit requirement and the correct referential behaviour.
 *
 * <h2>Organizational placement</h2>
 * {@code departmentId} and {@code branchId} place the user for approval routing. They are not an
 * authorization mechanism: authority comes from
 * {@link com.edam.common.security.DataScope} rows, so moving someone between departments does not
 * silently widen what they can approve.
 *
 * <h2>managerId is a plain id, not a {@code @ManyToOne}</h2>
 * A self-referencing association would make every user load pull a manager, whose manager would be
 * lazily loaded by half the callers. The reporting line is used in exactly two places (escalation and
 * display), so it is stored as an id and resolved explicitly where needed.
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

	@Column(name = "employee_code", length = 50)
	private String employeeCode;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "username", nullable = false, length = 100)
	private String username;

	/**
	 * BCrypt hash when local authentication is used; null when authentication is delegated. Never
	 * mapped to a DTO and never serialised: no API returns it.
	 */
	@Column(name = "password_hash", length = 255)
	private String passwordHash;

	@Column(name = "department_id")
	private UUID departmentId;

	@Column(name = "branch_id")
	private UUID branchId;

	@Column(name = "designation", length = 150)
	private String designation;

	@Column(name = "manager_id")
	private UUID managerId;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	/** Display name, derived rather than stored, so a name change cannot leave two versions behind. */
	public String displayName() {
		return (this.firstName + " " + this.lastName).trim();
	}

	public String getEmployeeCode() {
		return this.employeeCode;
	}

	public void setEmployeeCode(String employeeCode) {
		this.employeeCode = employeeCode;
	}

	public String getFirstName() {
		return this.firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return this.lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getUsername() {
		return this.username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	/** True when this account authenticates locally rather than through a federated provider. */
	public boolean hasLocalCredential() {
		return this.passwordHash != null && !this.passwordHash.isBlank();
	}

	public UUID getDepartmentId() {
		return this.departmentId;
	}

	public void setDepartmentId(UUID departmentId) {
		this.departmentId = departmentId;
	}

	public UUID getBranchId() {
		return this.branchId;
	}

	public void setBranchId(UUID branchId) {
		this.branchId = branchId;
	}

	public String getDesignation() {
		return this.designation;
	}

	public void setDesignation(String designation) {
		this.designation = designation;
	}

	public UUID getManagerId() {
		return this.managerId;
	}

	public void setManagerId(UUID managerId) {
		this.managerId = managerId;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getLastLoginAt() {
		return this.lastLoginAt;
	}

	public void setLastLoginAt(Instant lastLoginAt) {
		this.lastLoginAt = lastLoginAt;
	}

}
