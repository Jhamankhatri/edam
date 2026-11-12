package com.edam.configplane.limit;

import java.math.BigDecimal;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The General Delegation threshold for the Department Head (BRD FM-10, §10.2).
 *
 * <h2>Exactly one record per institution</h2>
 * The BRD states: "One General Delegation record only, including an inactive record; update or
 * reactivate the existing record." That is a <em>table-level</em> invariant, so it is enforced by a
 * unique constraint on {@code institution_id} in the migration rather than by a service check that
 * a second concurrent insert could bypass. The service therefore updates in place and never
 * inserts a second row.
 *
 * <h2>Routing consequence</h2>
 * Approval amount at or below this threshold routes to the Department Head only (Phase 1); above
 * it, the request proceeds through Phase 1 and then the configured later route. That decision lives
 * in {@code RoutingService}, not here: this entity is data.
 *
 * <h2>Legacy fallback</h2>
 * The legacy implementation defaulted to PKR 20,000 when no limit was configured. EDAM keeps the
 * fallback as <em>configuration</em> ({@code edam.configuration.department-head-limit-fallback}),
 * never as a constant, and the value in use is reported by the configuration API so an operator can
 * see which default is actually applied.
 */
@Entity
@Table(name = "department_head_limit")
public class DepartmentHeadLimit extends InstitutionOwnedEntity {

	@NotNull
	@DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
	@Digits(integer = 17, fraction = 2, message = "must have at most two decimal places")
	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Size(max = 500)
	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected DepartmentHeadLimit() {
		// JPA
	}

	public DepartmentHeadLimit(BigDecimal amount, String description) {
		this.amount = amount;
		this.description = description;
	}

	public void update(BigDecimal amount, String description) {
		this.amount = amount;
		this.description = description;
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	public BigDecimal getAmount() {
		return this.amount;
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isActive() {
		return this.active;
	}

}
