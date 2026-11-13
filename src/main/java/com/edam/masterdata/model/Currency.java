package com.edam.masterdata.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A transactable currency (Module D).
 *
 * <h2>Decimal places are stored, not assumed</h2>
 * The schema stores every monetary amount as {@code NUMERIC(19,4)}, which is a <em>storage</em>
 * precision. How many digits are meaningful is a property of the currency: JPY has none, KWD has three,
 * PKR has two. Reporting that renders 100.0000 JPY is wrong in a way an auditor notices, so the
 * presentation scale is a column rather than a constant.
 *
 * <p>Note that this does <strong>not</strong> license rounding inside a calculation. Conversion and
 * aggregation rules are business decisions, and no formula is invented here; see the expense request
 * service for the one conversion that is specified (amount × rate, HALF_UP, scale 2).
 *
 * <h2>Deactivation, not deletion</h2>
 * A currency referenced by a historical request or an exchange rate cannot disappear. Deactivating keeps
 * the reference resolvable while removing it from the choices offered for new requests.
 */
@Entity
@Table(name = "currencies")
public class Currency extends AuditableEntity {

	@Column(name = "currency_code", nullable = false, length = 3)
	private String code;

	@Column(name = "currency_name", nullable = false, length = 100)
	private String name;

	@Column(name = "symbol", length = 10)
	private String symbol;

	@Column(name = "decimal_places", nullable = false)
	private short decimalPlaces = 2;

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

	public String getSymbol() {
		return this.symbol;
	}

	public void setSymbol(String symbol) {
		this.symbol = symbol;
	}

	public short getDecimalPlaces() {
		return this.decimalPlaces;
	}

	public void setDecimalPlaces(short decimalPlaces) {
		this.decimalPlaces = decimalPlaces;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
