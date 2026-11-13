package com.edam.organization.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A branch: a physical or operational location, used as a data-scope boundary and shown on requests
 * (Module A).
 *
 * <p>Like {@link Department}, this is a specialisation of {@link OrganizationUnit} rather than a copy:
 * the address and the branch code belong to a branch and to nothing else, and the separate primary key
 * lets {@code users.branch_id} and {@code user_data_scopes} address it directly.
 */
@Entity
@Table(name = "branches")
public class Branch extends AuditableEntity {

	@Column(name = "organization_unit_id", nullable = false, updatable = false)
	private UUID organizationUnitId;

	@Column(name = "branch_code", nullable = false, length = 64)
	private String code;

	@Column(name = "branch_name", nullable = false, length = 200)
	private String name;

	@Column(name = "address")
	private String address;

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

	public String getAddress() {
		return this.address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
