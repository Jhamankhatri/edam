package com.edam.usermanagement.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * User administration contract (Module C).
 *
 * <h2>No credential crosses this boundary in either direction</h2>
 * A create request carries no password: the administrator does not know, choose, or transmit the user's
 * credential. Instead the user is created and a reset grant is issued, so the account's only secret is
 * one the user sets themselves. A password in a create payload would be known to two parties the moment
 * it is typed, and would usually end up in a support ticket.
 */
public final class UserDtos {

	private UserDtos() {
	}

	/** Create a user. No password field, by design. */
	public record CreateUserRequest(
			@NotBlank @Size(max = 100) String username,
			@NotBlank @Size(max = 100) String firstName,
			@NotBlank @Size(max = 100) String lastName,
			@NotBlank @Email @Size(max = 255) String email,
			@Size(max = 50) String employeeCode,
			@Size(max = 150) String designation,
			UUID departmentId,
			UUID branchId,
			UUID managerId,
			/** Roles to grant at creation. Empty means "create now, grant later", which is allowed. */
			List<UUID> roleIds) {
	}

	/** Update a user's profile fields. Credentials and status are changed through their own endpoints. */
	public record UpdateUserRequest(
			@NotBlank @Size(max = 100) String firstName,
			@NotBlank @Size(max = 100) String lastName,
			@NotBlank @Email @Size(max = 255) String email,
			@Size(max = 50) String employeeCode,
			@Size(max = 150) String designation,
			UUID departmentId,
			UUID branchId,
			UUID managerId) {
	}

	/** One role held by a user, including its expiry. */
	public record UserRoleView(UUID assignmentId, UUID roleId, String roleCode, String roleName,
			Instant assignedAt, Instant expiresAt, boolean live) {
	}

	/** One data scope held by a user. */
	public record UserScopeView(UUID id, String scopeType, UUID referenceId, String referenceName) {
	}

	/** A user as returned by the administration API. */
	public record UserResponse(UUID id, String username, String employeeCode, String firstName, String lastName,
			String displayName, String email, String designation, UUID departmentId, String departmentName,
			UUID branchId, String branchName, UUID managerId, String managerName, boolean active,
			Instant lastLoginAt, Instant createdAt, List<UserRoleView> roles, List<UserScopeView> scopes) {
	}

	/** A role assignment request, with an optional expiry for temporary elevation. */
	public record AssignRoleRequest(@NotNull UUID roleId, Instant expiresAt) {
	}

	/** A data-scope grant. {@code referenceId} is required exactly for the reference-bearing types. */
	public record AssignScopeRequest(@NotBlank String scopeType, UUID referenceId) {
	}

	/** Result of creating a user, including whether a credential setup was issued. */
	public record UserCreatedResponse(UserResponse user, boolean credentialSetupIssued, String credentialSetupNote) {
	}

	/** Minimal reference for pickers: users, departments, branches. */
	public record UserSummary(UUID id, String username, String displayName, String employeeCode) {
	}

}
