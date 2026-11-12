package com.edam.configplane.limit;

import com.edam.configplane.ExpenseNature;

import java.math.BigDecimal;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The OPEX/CAPEX exceeding-limit threshold (BRD FM-13).
 *
 * <h2>Deliberately not enforced in routing</h2>
 * BRD FM-13 says: "Maintain one threshold per expense nature; audit history. <strong>Enforcement in
 * routing is unresolved.</strong>" The previous foundation configuration contained a boolean
 * {@code exceeding-limit-enforced-in-routing}, which pretended the question was answered. This
 * entity replaces that with <em>data only</em>: the threshold is administered, audited and exposed,
 * and no routing code consults it. If enforcement is approved later, it becomes a workflow
 * condition — not a change to this table.
 *
 * <p>One record per institution and nature, enforced by a unique constraint.
 */
@Entity
@Table(name = "exceeding_limit")
public class ExceedingLimit extends InstitutionOwnedEntity {

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(name = "expense_nature", nullable = false, length = 8)
	private ExpenseNature expenseNature;

	@NotNull
	@DecimalMin(value = "0.0", inclusive = false, message = "must be greater than zero")
	@Digits(integer = 17, fraction = 2, message = "must have at most two decimal places")
	@Column(name = "amount", nullable = false, precision = 19, scale = 2)
	private BigDecimal amount;

	@Size(max = 255)
	@Column(name = "description", length = 255)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	protected ExceedingLimit() {
		// JPA
	}

	public ExceedingLimit(ExpenseNature expenseNature, BigDecimal amount, String description) {
		this.expenseNature = expenseNature;
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

	public ExpenseNature getExpenseNature() {
		return this.expenseNature;
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
