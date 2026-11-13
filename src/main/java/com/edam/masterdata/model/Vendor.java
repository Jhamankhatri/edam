package com.edam.masterdata.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A supplier named on an expense request or quotation (Module D).
 *
 * <h2>Not a full supplier-master</h2>
 * EDAM is not a procurement system: it records which vendor a quotation came from and what was quoted.
 * Payment terms, bank details and credit limits belong to the core banking or ERP system this request is
 * posted to, and duplicating them here would create a second source of truth for payee data — the exact
 * hazard the requirements warn about. The fields present are the ones a quotation needs.
 *
 * <p>{@code taxNumber} is the NTN/STRN used on the invoice. It is validated for shape only: EDAM does
 * not verify registration status, because that requires a live FBR integration that is out of scope.
 */
@Entity
@Table(name = "vendors")
public class Vendor extends AuditableEntity {

	@Column(name = "vendor_code", nullable = false, length = 64)
	private String code;

	@Column(name = "vendor_name", nullable = false, length = 300)
	private String name;

	@Column(name = "tax_number", length = 64)
	private String taxNumber;

	@Column(name = "contact_person", length = 200)
	private String contactPerson;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "phone", length = 50)
	private String phone;

	@Column(name = "address")
	private String address;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

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

	public String getTaxNumber() {
		return this.taxNumber;
	}

	public void setTaxNumber(String taxNumber) {
		this.taxNumber = taxNumber;
	}

	public String getContactPerson() {
		return this.contactPerson;
	}

	public void setContactPerson(String contactPerson) {
		this.contactPerson = contactPerson;
	}

	public String getEmail() {
		return this.email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getPhone() {
		return this.phone;
	}

	public void setPhone(String phone) {
		this.phone = phone;
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
