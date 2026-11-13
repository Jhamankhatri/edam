package com.edam.usermanagement.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.usermanagement.dto.UserDtos.AssignRoleRequest;
import com.edam.usermanagement.dto.UserDtos.AssignScopeRequest;
import com.edam.usermanagement.dto.UserDtos.CreateUserRequest;
import com.edam.usermanagement.dto.UserDtos.UpdateUserRequest;
import com.edam.usermanagement.dto.UserDtos.UserCreatedResponse;
import com.edam.usermanagement.dto.UserDtos.UserResponse;
import com.edam.usermanagement.dto.UserDtos.UserRoleView;
import com.edam.usermanagement.dto.UserDtos.UserScopeView;
import com.edam.usermanagement.dto.UserDtos.UserSummary;
import com.edam.usermanagement.service.UserManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration (Module C).
 *
 * <h2>Deactivation, never deletion</h2>
 * There is no {@code DELETE /users/{id}}. An approval is attributed to a user id for the life of the
 * record, and an expense request approved three years ago must still be able to say who approved it.
 * Deleting the row would either orphan that reference or require a cascade that destroys the approval
 * history — the one thing an auditor will ask for. Deactivation is exposed instead, and is reversible.
 *
 * <h2>No password in any request</h2>
 * Creating a user issues a password-setup grant rather than accepting a credential. An administrator
 * who chose a user's password would know it, and in practice would share it in a ticket or a chat
 * message. The user's first credential is therefore one only they have ever seen.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Users", description = "User administration, role assignment and data scopes")
public class UserController {

	private final UserManagementService service;

	public UserController(UserManagementService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.USER_VIEW)
	@Operation(summary = "Search users, paged and restricted to the caller's data scope",
			description = "Returns only users the caller may see. The scope predicate is applied in the "
					+ "query, so the paging metadata describes the same set as the rows.")
	public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) UUID departmentId, @RequestParam(required = false) Boolean active,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.list(search, departmentId, active, PageQuery.of(page, size, sort), caller), request);
	}

	@GetMapping("/search")
	@EdamPermission(Permissions.USER_VIEW)
	@Operation(summary = "Lightweight user lookup for pickers (manager, approver)")
	public ApiResponse<List<UserSummary>> search(@RequestParam(required = false) String q,
			HttpServletRequest request) {
		return ok(this.service.searchSummaries(q), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.USER_VIEW)
	@Operation(summary = "Get one user with roles and data scopes",
			description = "A user outside the caller's data scope is reported as not found, not forbidden, "
					+ "so the caller cannot map which records exist.")
	public ApiResponse<UserResponse> get(@PathVariable UUID id, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.get(id, caller), request);
	}

	@PostMapping
	@EdamPermission(Permissions.USER_MANAGE)
	@Operation(summary = "Create a user",
			description = "No password is accepted. A credential setup grant is issued and the user chooses "
					+ "their own password. Cannot grant a role holding permissions the caller lacks.")
	public ResponseEntity<ApiResponse<UserCreatedResponse>> create(@Valid @RequestBody CreateUserRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ok(this.service.create(body, caller), request));
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.USER_MANAGE)
	@Operation(summary = "Update a user's profile and organizational placement")
	public ApiResponse<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.update(id, body, caller), request);
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.USER_MANAGE)
	@Operation(summary = "Activate or deactivate a user",
			description = "A user cannot deactivate their own account, which would otherwise be a way to "
					+ "permanently lock out the only administrator.")
	public ApiResponse<UserResponse> setActive(@PathVariable UUID id, @RequestParam boolean active,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.setActive(id, active, caller), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Roles
	// ---------------------------------------------------------------------------------------------

	@GetMapping("/{id}/roles")
	@EdamPermission(Permissions.USER_VIEW)
	@Operation(summary = "Roles assigned to a user, including expired assignments for history")
	public ApiResponse<List<UserRoleView>> roles(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.service.rolesOf(id), request);
	}

	@PostMapping("/{id}/roles")
	@EdamPermission(Permissions.USER_ROLE_ASSIGN)
	@Operation(summary = "Grant a role",
			description = "An optional expiry makes temporary elevation self-terminating. "
					+ "Refused if the role holds permissions the caller does not have.")
	public ResponseEntity<ApiResponse<UserRoleView>> assignRole(@PathVariable UUID id,
			@Valid @RequestBody AssignRoleRequest body, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ok(this.service.assignRole(id, body, caller), request));
	}

	@DeleteMapping("/{id}/roles/{assignmentId}")
	@EdamPermission(Permissions.USER_ROLE_ASSIGN)
	@Operation(summary = "Revoke a role assignment")
	public ApiResponse<Void> revokeRole(@PathVariable UUID id, @PathVariable UUID assignmentId,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		this.service.revokeRole(id, assignmentId, caller);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ---------------------------------------------------------------------------------------------
	// Data scopes
	// ---------------------------------------------------------------------------------------------

	@PostMapping("/{id}/scopes")
	@EdamPermission(Permissions.DATA_SCOPE_MANAGE)
	@Operation(summary = "Grant a data scope",
			description = "Narrows where the user's role permissions apply. Only an installation-scoped "
					+ "caller may grant installation-wide scope.")
	public ResponseEntity<ApiResponse<UserScopeView>> assignScope(@PathVariable UUID id,
			@Valid @RequestBody AssignScopeRequest body, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ok(this.service.assignScope(id, body, caller), request));
	}

	@DeleteMapping("/{id}/scopes/{scopeId}")
	@EdamPermission(Permissions.DATA_SCOPE_MANAGE)
	@Operation(summary = "Remove a data scope")
	public ApiResponse<Void> removeScope(@PathVariable UUID id, @PathVariable UUID scopeId,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		this.service.removeScope(id, scopeId, caller);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ---------------------------------------------------------------------------------------------
	// Credential
	// ---------------------------------------------------------------------------------------------

	@PostMapping("/{id}/password-reset")
	@EdamPermission(Permissions.USER_MANAGE)
	@Operation(summary = "Issue a password reset for a user",
			description = "The administrator never sees or sets the password; a reset grant is issued and "
					+ "the link is delivered to the user.")
	public ApiResponse<Void> triggerPasswordReset(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		this.service.triggerPasswordReset(id, caller);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
