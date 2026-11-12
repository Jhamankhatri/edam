package com.edam.department;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A department of an institution.
 *
 * <h2>Why EDAM stores departments at all</h2>
 * The BRD leaves the authoritative source of departments open (INT-01/OD-36): it may be the HR system
 * or the core banking system. Until that integration exists, requests, memberships, authorities and
 * reports all need a department identifier that resolves to a human-readable name, so institutions can
 * maintain them here. When the integration lands, this table becomes a synchronised cache and the
 * identifiers stay stable.
 *
 * <h2>CAPEX eligibility</h2>
 * {@code capexEligible} replaces the legacy hardcoded list of three departments (OD-21). It is only
 * consulted when the institution's {@code CAPEX_DEPARTMENT_RESTRICTION} flag is enabled.
 */
@Entity
@Table(name = "department")
public class Department extends InstitutionOwnedEntity {

	@NotBlank
	@Size(max = 64)
	@Column(name = "code", nullable = false, length = 64, updatable = false)
	private String code;

	@NotBlank
	@Size(max = 200)
	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "capex_eligible", nullable = false)
	private boolean capexEligible;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected Department() {
		// JPA
	}

	public Department(String code, String name, boolean capexEligible) {
		this.code = code;
		this.name = name;
		this.capexEligible = capexEligible;
	}

	public void update(String name, boolean capexEligible) {
		this.name = name;
		this.capexEligible = capexEligible;
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	public String getCode() {
		return this.code;
	}

	public String getName() {
		return this.name;
	}

	public boolean isCapexEligible() {
		return this.capexEligible;
	}

	public boolean isActive() {
		return this.active;
	}

}
