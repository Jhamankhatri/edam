package com.edam.organization.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The vocabulary of organizational unit kinds (Module A): HEAD_OFFICE, REGION, ZONE, BRANCH,
 * DEPARTMENT, DIVISION.
 *
 * <h2>Why this is a table and not an enum</h2>
 * An enum would be closed at compile time, so a bank that calls its middle layer a "circle" would need
 * a release. As rows, the vocabulary is data the bank can extend. The product ships the six kinds above
 * because code branches on {@code DEPARTMENT} and {@code BRANCH} in a few places (approval routing,
 * data scope resolution); anything else is purely descriptive.
 *
 * <h2>Read-only through the application</h2>
 * Rows are installed by migration and there is no create/update API. Adding a kind that no code
 * understands is harmless but meaningless, so the surface is deliberately absent rather than present
 * and unvalidated.
 */
@Entity
@Table(name = "organization_unit_types")
public class OrganizationUnitType {

	/** The two codes the application interprets, in addition to the descriptive kinds. */
	public static final String DEPARTMENT = "DEPARTMENT";

	public static final String BRANCH = "BRANCH";

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "code", nullable = false, updatable = false, length = 50)
	private String code;

	@Column(name = "name", nullable = false, updatable = false, length = 100)
	private String name;

	@Column(name = "description", updatable = false)
	private String description;

	/**
	 * Whether this kind is offered when creating a unit. A bank that does not use "Zone" can hide it
	 * without deleting the rows that already reference it.
	 */
	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	protected OrganizationUnitType() {
		// JPA
	}

	public UUID getId() {
		return this.id;
	}

	public String getCode() {
		return this.code;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isActive() {
		return this.active;
	}

}
