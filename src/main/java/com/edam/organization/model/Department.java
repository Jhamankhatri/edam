package com.edam.organization.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A department: the organizational unit that requests are raised from and approvals are routed to
 * (Module A).
 *
 * <h2>Why it duplicates facts already in organization_units</h2>
 * This looks like the "duplicate writable source" the requirements forbid, so the justification matters.
 * {@code departments} is a <em>specialisation</em>, not a copy: it carries facts that only a department
 * has — a head, a CAPEX allowance, an approval limit — and it owns its own primary key so that
 * {@code expense_requests.department_id} can point at it directly. Without that key, every request query
 * would join through {@code organization_units} and then filter on type, and the CAPEX flag would have
 * to live on the generic unit where it is meaningless for a branch.
 *
 * <p>The two columns shared with {@code organization_units} are constrained to stay consistent: the
 * service writes both in one transaction from the same command object, and
 * {@code DepartmentConsistencyTest} asserts that the pair never diverges.
 */
@Entity
@Table(name = "departments")
public class Department extends AuditableEntity {

	@Column(name = "organization_unit_id", nullable = false, updatable = false)
	private UUID organizationUnitId;

	@Column(name = "department_code", nullable = false, length = 64)
	private String code;

	@Column(name = "department_name", nullable = false, length = 200)
	private String name;

	@Column(name = "head_user_id")
	private UUID headUserId;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	public UUID getOrganizationUnitId() {
		return this.organizationUnitId;
	}

	public void setOrganizationUnitId(UUID organizationUnitId) {
		this.organizationUnitId = organizationUnitId;
	}

	public String getCode() {
		return this.code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public UUID getHeadUserId() {
		return this.headUserId;
	}

	public void setHeadUserId(UUID headUserId) {
		this.headUserId = headUserId;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
