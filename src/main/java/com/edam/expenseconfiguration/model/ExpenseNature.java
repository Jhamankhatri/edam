package com.edam.expenseconfiguration.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The nature of an expense (Module E): the "what kind of cost is this" dimension.
 *
 * <h2>Why nature is separate from category</h2>
 * Category answers "what was bought"; nature answers "what kind of cost is it" — recurring, one-off, a
 * statutory payment, a reimbursement. The delegation matrix keys on both, because a one-off advance for
 * travel and a monthly utility payment of the same amount take different approval routes at this bank.
 * Folding nature into the category name would make the matrix unexpressible without combinatorial rows.
 */
@Entity
@Table(name = "expense_natures")
public class ExpenseNature extends AuditableEntity {

	@Column(name = "nature_code", nullable = false, length = 64)
	private String code;

	@Column(name = "nature_name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

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
