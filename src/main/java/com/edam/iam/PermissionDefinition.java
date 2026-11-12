package com.edam.iam;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A permission in the runtime catalogue (BRD IAM-02).
 *
 * <p>Platform-level rather than institution-owned: the vocabulary of capabilities is product-wide
 * and stable, while <em>which</em> institution's role holds which permission is institution data
 * ({@link Role}). Splitting them this way means adding a capability is one row, not one row per
 * tenant.
 *
 * <p>{@code builtIn} distinguishes platform capabilities from institution-defined ones. The BRD
 * permits granular customisation, but a capability the backend cannot enforce is not a permission;
 * custom definitions are therefore descriptive groupings only until the code enforces them.
 */
@Entity
@Table(name = "permission_definition")
public class PermissionDefinition extends AuditableEntity {

	@NotBlank
	@Pattern(regexp = "[a-z][a-z0-9-]*:[a-z][a-z0-9-]*",
			message = "must be '<module>:<action>' in lower case, hyphenated")
	@Column(name = "code", nullable = false, unique = true, length = 80)
	private String code;

	@NotBlank
	@Size(max = 40)
	@Column(name = "module", nullable = false, length = 40)
	private String module;

	@NotBlank
	@Size(max = 40)
	@Column(name = "action", nullable = false, length = 40)
	private String action;

	@Size(max = 300)
	@Column(name = "description", length = 300)
	private String description;

	@Column(name = "built_in", nullable = false)
	private boolean builtIn = true;

	protected PermissionDefinition() {
		// JPA
	}

	public String getCode() {
		return this.code;
	}

	public String getModule() {
		return this.module;
	}

	public String getAction() {
		return this.action;
	}

	public String getDescription() {
		return this.description;
	}

	public boolean isBuiltIn() {
		return this.builtIn;
	}

}
