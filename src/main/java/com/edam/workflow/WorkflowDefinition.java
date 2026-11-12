package com.edam.workflow;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A named approval workflow (BRD FM-01..FM-06, §8.3).
 *
 * <p>The definition is the stable identity (for example {@code HO-OPEX}); its behaviour lives in
 * immutable {@link WorkflowVersion} rows. That split is what makes "publishing a new version must not
 * affect in-flight requests" (BRD §8.6) implementable: a request pins a version, and a new version is
 * a new row rather than an edit of the old one.
 */
@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinition extends InstitutionOwnedEntity {

	@NotBlank
	@Pattern(regexp = "[A-Z][A-Z0-9_-]{1,63}", message = "must be upper case letters, digits, hyphen or underscore")
	@Column(name = "code", nullable = false, length = 64, updatable = false)
	private String code;

	@NotBlank
	@Size(max = 200)
	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Size(max = 1000)
	@Column(name = "description", length = 1000)
	private String description;

	protected WorkflowDefinition() {
		// JPA
	}

	public WorkflowDefinition(String code, String name, String description) {
		this.code = code;
		this.name = name;
		this.description = description;
	}

	public void update(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public String getCode() {
		return this.code;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

}
