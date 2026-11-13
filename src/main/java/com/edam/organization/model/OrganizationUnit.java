package com.edam.organization.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * One node of the bank's organizational hierarchy (Module A).
 *
 * <h2>Why the hierarchy is rows, not columns</h2>
 * Banks differ: some have head office → region → branch, others head office → zone → region → branch,
 * and a third group models departments underneath divisions. A fixed parent chain in code would fit
 * exactly one of them. A self-referencing tree with a configurable
 * {@link OrganizationUnitType} fits all, and depth is discovered rather than declared.
 *
 * <h2>Cycle protection</h2>
 * The database forbids a node being its own parent (CHECK) but cannot cheaply forbid a longer cycle
 * (A→B→C→A) without a recursive trigger. The service that moves a node therefore walks the ancestor
 * chain before writing; that is the only place a cycle can be introduced.
 *
 * <h2>Deactivation rather than deletion</h2>
 * A unit that has held requests, approvals or users cannot be removed without destroying history. The
 * flag keeps it addressable for reports while preventing new work from being routed to it.
 */
@Entity
@Table(name = "organization_units")
public class OrganizationUnit extends AuditableEntity {

	@Column(name = "parent_unit_id")
	private UUID parentUnitId;

	@Column(name = "unit_type_id", nullable = false)
	private UUID unitTypeId;

	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	/** True when this is the top of the hierarchy. */
	public boolean isRoot() {
		return this.parentUnitId == null;
	}

	public UUID getParentUnitId() {
		return this.parentUnitId;
	}

	public void setParentUnitId(UUID parentUnitId) {
		this.parentUnitId = parentUnitId;
	}

	public UUID getUnitTypeId() {
		return this.unitTypeId;
	}

	public void setUnitTypeId(UUID unitTypeId) {
		this.unitTypeId = unitTypeId;
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

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
