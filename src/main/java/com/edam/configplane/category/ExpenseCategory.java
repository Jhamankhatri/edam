package com.edam.configplane.category;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An expense category (BRD FM-07).
 *
 * <h2>Business rules expressed in the schema</h2>
 * "Active category names are unique" (BRD §8.6) is enforced by a <strong>partial unique
 * index</strong> in the migration rather than by a service check alone, because a service check
 * races under concurrent administration. Deactivation is a state change, not a delete, so the
 * uniqueness predicate only covers active rows — which is exactly why it is a partial index and
 * not a table constraint.
 *
 * <p>"Cannot delete while sub-categories exist" is a delete guard implemented in the service and
 * backed by the foreign key from {@code expense_subcategory} (the database refuses an orphan).
 */
@Entity
@Table(name = "expense_category")
public class ExpenseCategory extends InstitutionOwnedEntity {

	@NotBlank
	@Size(max = 120)
	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Size(max = 500)
	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	/** Sort position for pickers; keeps display order stable and administrable. */
	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	protected ExpenseCategory() {
		// JPA
	}

	public ExpenseCategory(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public void update(String name, String description, Integer displayOrder) {
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

	public void setDisplayOrder(int displayOrder) {
		this.displayOrder = displayOrder;
	}

}
