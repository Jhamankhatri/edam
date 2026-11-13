package com.edam.expenseconfiguration.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A second-level expense classification within a category (Module E).
 *
 * <h2>Why two levels and not an arbitrary tree</h2>
 * The requirement specifies category and subcategory, and the delegation matrix is configured per
 * subcategory. An open-ended tree would make "which level does the approver matrix address?" ambiguous,
 * and every approval would inherit an interpretation. Two fixed levels keep the routing rule legible.
 *
 * <p>The subcategory code is unique <em>within</em> its category, not globally: two categories may both
 * have a "MISC" code, and a global unique index would force banks to invent artificial prefixes.
 */
@Entity
@Table(name = "expense_subcategories")
public class ExpenseSubcategory extends AuditableEntity {

	@Column(name = "category_id", nullable = false, updatable = false)
	private UUID categoryId;

	@Column(name = "subcategory_code", nullable = false, length = 64)
	private String code;

	@Column(name = "subcategory_name", nullable = false, length = 200)
	private String name;

	@Column(name = "description")
	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	public UUID getCategoryId() {
		return this.categoryId;
	}

	public void setCategoryId(UUID categoryId) {
		this.categoryId = categoryId;
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

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
