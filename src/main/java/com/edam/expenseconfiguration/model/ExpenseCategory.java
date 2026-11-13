package com.edam.expenseconfiguration.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A top-level expense category (Module E): Travel, Utilities, IT Equipment, and so on.
 *
 * <h2>Expense type is part of the category, not a separate taxonomy</h2>
 * A category is OPEX, CAPEX or BOTH. That single column is what decides which request form a requester
 * sees and which detail table is populated, so it must be a property of the category rather than a
 * classification an administrator adds later. A category that is "BOTH" produces two independent
 * subcategory trees' worth of behaviour from one row, which is why it is allowed at all: "Vehicles"
 * legitimately contains both fuel (OPEX) and a vehicle purchase (CAPEX).
 *
 * <h2>Why categories are never deleted</h2>
 * A request that was raised under a category must remain classifiable for reporting and audit. Deleting
 * the category would either orphan those requests or force a cascade that destroys them. Deactivation
 * removes the category from the choices for new requests and leaves history intact.
 *
 * <h2>No seed data</h2>
 * The product ships no categories. A bank's chart of expense categories is a business decision that must
 * come from its finance function, not from a vendor's idea of what a bank spends money on.
 */
@Entity
@Table(name = "expense_categories")
public class ExpenseCategory extends AuditableEntity {

	/** Which request type a category may be used for. */
	public enum ExpenseType {

		/** Operating expenditure: the cost is consumed in the period. */
		OPEX,
		/** Capital expenditure: the spend creates or improves an asset. */
		CAPEX,
		/** The category is legitimate for both; the requester's choice decides the form. */
		BOTH

	}

	@Column(name = "category_code", nullable = false, length = 64)
	private String code;

	@Column(name = "category_name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "expense_type", nullable = false, length = 20)
	private ExpenseType expenseType = ExpenseType.BOTH;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	/** True when a request of the given type may use this category. */
	public boolean supports(ExpenseType requestType) {
		return this.expenseType == ExpenseType.BOTH || this.expenseType == requestType;
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

	public ExpenseType getExpenseType() {
		return this.expenseType;
	}

	public void setExpenseType(ExpenseType expenseType) {
		this.expenseType = expenseType;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
