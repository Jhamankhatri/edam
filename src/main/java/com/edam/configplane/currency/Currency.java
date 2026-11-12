package com.edam.configplane.currency;

import java.math.BigDecimal;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A currency and its conversion rate to the institution's base currency (BRD FM-09).
 *
 * <h2>Rules</h2>
 * <ul>
 *   <li>Code is upper case ISO-4217 and unique per institution; it is <strong>locked during
 *       edit</strong> (BRD §8.6), so {@code code} is {@code updatable = false}.</li>
 *   <li>Rate is required and limited to four decimal places — the BRD's explicit precision. A
 *       {@code @Digits(integer=15, fraction=4)} check is enforced twice: by validation and by a
 *       database {@code CHECK} on the scale, because a direct SQL import must not be able to
 *       create a five-decimal rate.</li>
 *   <li>Deletion is blocked while the currency is in use; that is a service-level guard against
 *       request references and is backstopped by the foreign keys from the request tables.</li>
 * </ul>
 *
 * <p>The rate is <em>not</em> a conversion formula. BRD FR-REQ-007 leaves the conversion formula
 * and rounding as an open decision, so this entity stores the administered rate and the conversion
 * helper documents its provisional rounding explicitly.
 */
@Entity
@Table(name = "currency")
public class Currency extends InstitutionOwnedEntity {

	@NotBlank
	@Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 three-letter upper-case code")
	@Column(name = "code", nullable = false, updatable = false, length = 3)
	private String code;

	@NotBlank
	@Size(max = 100)
	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@NotNull
	@DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
	@Digits(integer = 15, fraction = 4, message = "must have at most four decimal places")
	@Column(name = "rate_to_base", nullable = false, precision = 19, scale = 4)
	private BigDecimal rateToBase;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected Currency() {
		// JPA
	}

	public Currency(String code, String name, BigDecimal rateToBase) {
		this.code = code == null ? null : code.toUpperCase(java.util.Locale.ROOT);
		this.name = name;
		this.rateToBase = rateToBase;
	}

	/** Code is immutable after creation; only name, rate and status may change. */
	public void update(String name, BigDecimal rateToBase) {
		this.name = name;
		this.rateToBase = rateToBase;
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

	public BigDecimal getRateToBase() {
		return this.rateToBase;
	}

	public boolean isActive() {
		return this.active;
	}

}
