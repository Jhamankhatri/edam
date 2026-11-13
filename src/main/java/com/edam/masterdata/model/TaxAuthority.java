package com.edam.masterdata.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A body to which tax is owed (Module D): the federal revenue authority or a provincial one.
 *
 * <p>No rate lives here; see {@code MasterDataDtos.TaxAuthorityResponse} for why. This entity exists so
 * that a withheld-tax amount on an invoice can be attributed to the authority it is payable to, which is
 * what the finance team needs when preparing the monthly return.
 */
@Entity
@Table(name = "tax_authorities")
public class TaxAuthority extends AuditableEntity {

	@Column(name = "authority_name", nullable = false, length = 200)
	private String name;

	@Column(name = "authority_code", nullable = false, length = 64)
	private String code;

	@Column(name = "description")
	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCode() {
		return this.code;
	}

	public void setCode(String code) {
		this.code = code;
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
