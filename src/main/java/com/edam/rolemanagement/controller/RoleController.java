package com.edam.rolemanagement.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.rolemanagement.dto.RoleDtos.PermissionCatalogue;
import com.edam.rolemanagement.dto.RoleDtos.RoleResponse;
import com.edam.rolemanagement.dto.RoleDtos.SaveRoleRequest;
import com.edam.rolemanagement.dto.RoleDtos.SetPermissionsRequest;
import com.edam.rolemanagement.service.RoleManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role and permission administration (Module C).
 *
 * <h2>The catalogue is read-only, deliberately</h2>
 * {@code GET /roles/permissions} lists the installed permission catalogue but no endpoint creates a
 * permission. Permission codes are referenced by endpoints ({@code @EdamPermission}), by role grants and
 * by API contracts, so an administrator inventing codes would produce grants that authorize nothing —
 * or worse, an expectation of authority the application does not enforce. The catalogue ships by
 * migration and grows by migration.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/roles", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Roles", description = "Role definitions, permission catalogue and permission assignment")
public class RoleController {

	private final RoleManagementService service;

	public RoleController(RoleManagementService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.ROLE_VIEW)
	@Operation(summary = "List roles with their permissions and active holder counts")
	public ApiResponse<List<RoleResponse>> list(@RequestParam(defaultValue = "false") boolean includeInactive,
			HttpServletRequest request) {
		return ok(this.service.list(includeInactive), request);
	}

	@GetMapping("/permissions")
	@EdamPermission(Permissions.ROLE_VIEW)
	@Operation(summary = "The permission catalogue, grouped by module",
			description = "Read-only: permission codes are a public contract installed by migration.")
	public ApiResponse<PermissionCatalogue> catalogue(HttpServletRequest request) {
		return ok(this.service.catalogue(), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.ROLE_VIEW)
	@Operation(summary = "Get one role")
	public ApiResponse<RoleResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.service.get(id), request);
	}

	@PostMapping
	@EdamPermission(Permissions.ROLE_MANAGE)
	@Operation(summary = "Create a role",
			description = "The code must be unique and is immutable afterwards, because workflow configuration "
					+ "and audit records refer to roles by code.")
	public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody SaveRoleRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ok(this.service.create(body, caller), request));
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.ROLE_MANAGE)
	@Operation(summary = "Rename or re-describe a role",
			description = "The code cannot be changed. System roles may be edited but not deactivated.")
	public ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody SaveRoleRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.update(id, body, caller), request);
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.ROLE_MANAGE)
	@Operation(summary = "Activate or deactivate a role")
	public ApiResponse<RoleResponse> setActive(@PathVariable UUID id, @RequestParam boolean active,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.setActive(id, active, caller), request);
	}

	@PutMapping("/{id}/permissions")
	@EdamPermission(Permissions.ROLE_MANAGE)
	@Operation(summary = "Replace a role's permission set",
			description = "A caller cannot grant a permission they do not hold themselves, which is what stops "
					+ "role management from becoming a privilege-escalation path.")
	public ApiResponse<RoleResponse> setPermissions(@PathVariable UUID id,
			@Valid @RequestBody SetPermissionsRequest body, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.setPermissions(id, body, caller), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
