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

	/**
	 * Consecutive failed sign-ins since the last success.
	 *
	 * <p>Kept on the user row rather than derived from {@code login_attempts} on every sign-in: the
	 * lockout decision is on the authentication hot path and must not depend on scanning an
	 * append-only evidence table. The evidence table remains the record of what happened; these
	 * columns are the decision.
	 */
	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

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


	public int getFailedLoginCount() {
		return this.failedLoginCount;
	}

	public Instant getLockedUntil() {
		return this.lockedUntil;
	}

	public Instant getPasswordChangedAt() {
		return this.passwordChangedAt;
	}

	/** True when the lockout window has not yet elapsed. */
	public boolean isLocked(Instant now) {
		return this.lockedUntil != null && this.lockedUntil.isAfter(now);
	}

	/** Records one failed authentication. */
	public void recordFailedLogin(int lockThreshold, java.time.Duration lockDuration, Instant now) {
		this.failedLoginCount++;
		if (this.failedLoginCount >= lockThreshold) {
			this.lockedUntil = now.plus(lockDuration);
		}
	}

	/** Clears the failure count and any lockout. Called on a successful sign-in or an admin unlock. */
	public void clearFailedLogins() {
		this.failedLoginCount = 0;
		this.lockedUntil = null;
	}

	/**
	 * Sets the credential and stamps the change time.
	 *
	 * <p>The timestamp is what lets a session established before a password change be treated as
	 * stale; without it a stolen session would survive the very reset intended to revoke it.
	 */
	public void changePassword(String passwordHash, Instant when) {
		this.passwordHash = passwordHash;
		this.passwordChangedAt = when;
		this.failedLoginCount = 0;
		this.lockedUntil = null;
	}

}
