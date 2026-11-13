package com.edam.rolemanagement.model;

import java.util.LinkedHashSet;
import java.util.Set;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

/**
 * A reusable capability bundle (Module C).
 *
 * <h2>A role is global, not per-institution</h2>
 * "Expense Configuration Administrator" is one definition that exists once. Two departments using it
 * do not get two rows; they get two <em>users</em>, each with different
 * {@link com.edam.common.security.DataScope} rows. Duplicating the role per organizational unit would
 * mean a permission fix has to be applied N times, and would guarantee that the copies drift.
 *
 * <h2>isSystemRole protects the product, not the row</h2>
 * A system role cannot be deleted, because deleting "REQUESTER" would leave the request module with no
 * coherent default. It can still be renamed and re-granted: the requirement asks for a configurable
 * role model, and a role whose permissions cannot be changed is not configurable.
 *
 * <h2>What a role does not grant</h2>
 * A role never implies an organizational scope. Holding a role with zero {@code user_data_scopes} rows
 * confers no access to any record — an unscoped user is a configuration error, and failing closed is
 * the only safe interpretation of that state.
 */
@Entity
@Table(name = "roles")
public class Role extends AuditableEntity {

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "code", nullable = false, length = 100)
	private String code;

	@Column(name = "description")
	private String description;

	@Column(name = "is_system_role", nullable = false)
	private boolean systemRole;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	/**
	 * Granted permissions.
	 *
	 * <p>Eagerly fetched because authorization needs the set on every request and a role holds a handful
	 * of rows; lazily loading it during authentication would require an open session at a point where
	 * one is not guaranteed. The join table has no auditable columns of its own, which is why the
	 * mapping is a plain {@code @ManyToMany} instead of an entity.
	 */
	@ManyToMany(fetch = jakarta.persistence.FetchType.EAGER)
	@JoinTable(name = "role_permissions",
			joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_id"))
	private Set<Permission> permissions = new LinkedHashSet<>();

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCode() {
		return this.code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isSystemRole() {
		return this.systemRole;
	}

	public void setSystemRole(boolean systemRole) {
		this.systemRole = systemRole;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Set<Permission> getPermissions() {
		return this.permissions;
	}

	public void setPermissions(Set<Permission> permissions) {
		this.permissions = permissions;
	}

	/** Permission codes held through this role. */
	public Set<String> permissionCodes() {
		Set<String> codes = new LinkedHashSet<>();
		this.permissions.forEach(permission -> codes.add(permission.getCode()));
		return codes;
	}

}
