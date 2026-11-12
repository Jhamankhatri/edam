package com.edam.iam;

import java.util.LinkedHashSet;
import java.util.Set;

import com.edam.common.tenant.InstitutionOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An institution-scoped role composed from permissions (BRD IAM-01).
 *
 * <h2>Why roles live in the institution, not in code</h2>
 * BRD §41 forbids hardcoding banking roles. A role is a name the bank chooses, mapped to the
 * capability vocabulary; only the vocabulary is product data.
 *
 * <h2>System roles</h2>
 * {@code systemRole} marks the seeded templates (institution administrator, requester, department
 * head, …). They are ordinary editable roles — the BRD permits controlled customisation — but the
 * flag records provenance in the audit trail and prevents deletion of the last role that can
 * administer access.
 */
@Entity
@Table(name = "role")
public class Role extends InstitutionOwnedEntity {

	@NotBlank
	@Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "must be upper case letters, digits or underscores")
	@Column(name = "code", nullable = false, length = 64)
	private String code;

	@NotBlank
	@Size(max = 120)
	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Size(max = 300)
	@Column(name = "description", length = 300)
	private String description;

	@Column(name = "system_role", nullable = false)
	private boolean systemRole;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<PermissionDefinition> permissions = new LinkedHashSet<>();

	protected Role() {
		// JPA
	}

	public Role(String code, String name, String description, boolean systemRole) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.systemRole = systemRole;
	}

	public void update(String name, String description, Set<PermissionDefinition> permissions) {
		this.name = name;
		this.description = description;
		this.permissions.clear();
		this.permissions.addAll(permissions);
	}

	public void activate() {
		this.active = true;
	}

	public void deactivate() {
		this.active = false;
	}

	/** Permission codes granted by this role. */
	public Set<String> permissionCodes() {
		return this.permissions.stream().map(PermissionDefinition::getCode)
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
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

	public boolean isSystemRole() {
		return this.systemRole;
	}

	public boolean isActive() {
		return this.active;
	}

	public Set<PermissionDefinition> getPermissions() {
		return Set.copyOf(this.permissions);
	}

}
