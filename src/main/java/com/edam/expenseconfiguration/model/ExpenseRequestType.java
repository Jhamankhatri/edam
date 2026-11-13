package com.edam.expenseconfiguration.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * An administrative request type (Module E): a budget transfer, a re-appropriation, a pre-approval.
 *
 * <h2>Why this is distinct from OPEX/CAPEX</h2>
 * OPEX and CAPEX describe <em>spending</em>. Some requests this bank routes through the same approval
 * machinery do not spend money at all — they move it between budget lines. Modelling them as a request
 * type keeps them in the same audit and approval trail without pretending a budget transfer is an
 * expense, which would corrupt every spend report.
 *
 * <p>{@code expense_delegation_rules.request_type_id} is nullable and means "any": a rule that does not
 * care about the request type applies to all of them.
 */
@Entity
@Table(name = "expense_request_types")
public class ExpenseRequestType extends AuditableEntity {

	@Column(name = "type_code", nullable = false, length = 64)
	private String code;

	@Column(name = "type_name", nullable = false, length = 200)
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
