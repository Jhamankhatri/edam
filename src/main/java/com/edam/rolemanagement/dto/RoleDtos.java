package com.edam.rolemanagement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Role and permission administration contract (Module C).
 *
 * <p>Permission codes are a public contract: they are stored in role grants, referenced by endpoints and
 * relied on by other roles. They can be added to the catalogue by migration but are never renamed or
 * reused, which is why no endpoint here accepts a permission code for creation.
 */
public final class RoleDtos {

	private RoleDtos() {
	}

	/** Create or update a role. */
	public record SaveRoleRequest(
			@NotBlank @Size(max = 100) String name,
			@NotBlank @Size(min = 2, max = 100)
			@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$",
					message = "Role codes must start with a letter and contain only uppercase letters, digits and underscores") String code,
			@Size(max = 2000) String description) {
	}

	/** A permission in the catalogue. */
	public record PermissionView(UUID id, String code, String module, String description) {
	}

	/** A role with its permissions and how many users currently hold it. */
	public record RoleResponse(UUID id, String name, String code, String description, boolean systemRole,
			boolean active, List<PermissionView> permissions, long activeUserCount, Instant createdAt,
			Instant updatedAt) {
	}

	/** Replace a role's permission set. */
	public record SetPermissionsRequest(@NotNull List<@NotBlank String> permissionCodes) {
	}

	/** Permissions grouped by module, for a checkbox grid in the UI. */
	public record PermissionCatalogue(List<PermissionGroup> modules) {
	}

	public record PermissionGroup(String module, List<PermissionView> permissions) {
	}
}
