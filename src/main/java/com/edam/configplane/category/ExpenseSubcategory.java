package com.edam.configplane.category;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A sub-category, which always belongs to exactly one category (BRD FM-07).
 *
 * <p>"Active sub-category names are unique <em>within a category</em>" is a partial unique index
 * on {@code (institution_id, category_id, lower(name))} in the migration.
 *
 * <p>The category is a real FK, so "cannot delete a category that still has sub-categories" is
 * enforced by the database, not only by a service check.
 */
@Entity
@Table(name = "expense_subcategory")
public class ExpenseSubcategory extends InstitutionOwnedEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private ExpenseCategory category;

	@NotBlank
	@Size(max = 120)
	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Size(max = 500)
	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected ExpenseSubcategory() {
		// JPA
	}

	public ExpenseSubcategory(ExpenseCategory category, String name, String description) {
		this.category = category;
		this.name = name;
		this.description = description;
	}

	public void update(ExpenseCategory category, String name, String description, Integer displayOrder) {
		this.category = category;
		this.name = name;
		this.description = description;
		if (displayOrder != null) {
			this.displayOrder = displayOrder;
		}
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	public ExpenseCategory getCategory() {
		return this.category;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isActive() {
		return this.active;
	}

	public int getDisplayOrder() {
		return this.displayOrder;
	}

}
