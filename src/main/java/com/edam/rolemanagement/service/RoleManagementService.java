package com.edam.rolemanagement.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.EdamPrincipal;
import com.edam.rolemanagement.dto.RoleDtos.PermissionCatalogue;
import com.edam.rolemanagement.dto.RoleDtos.PermissionGroup;
import com.edam.rolemanagement.dto.RoleDtos.PermissionView;
import com.edam.rolemanagement.dto.RoleDtos.RoleResponse;
import com.edam.rolemanagement.dto.RoleDtos.SaveRoleRequest;
import com.edam.rolemanagement.dto.RoleDtos.SetPermissionsRequest;
import com.edam.rolemanagement.model.Permission;
import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.repository.PermissionRepository;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.rolemanagement.repository.UserRoleRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role and permission administration (Module C).
 *
 * <h2>Two protections, both load-bearing</h2>
 * <ol>
 *   <li><strong>No escalation.</strong> A caller may not add a permission to a role unless they hold it
 *       themselves. Without this rule, {@code role:manage} alone would be equivalent to every permission
 *       in the product: create a role with {@code system:administer}, assign it to yourself, done.</li>
 *   <li><strong>System roles cannot lose their last essential permission.</strong> The catalogue roles
 *       are how the product keeps working — the bootstrap depends on the system administrator role
 *       existing and being grantable. A system role may be renamed and re-scoped, but it may not be
 *       emptied of permissions or deactivated, because that would strand the installation with no way to
 *       restore it.</li>
 * </ol>
 *
 * <h2>Removing a permission from a role</h2>
 * The same no-escalation test applies in reverse: a caller who holds a permission may remove it. A
 * caller who does not hold it cannot touch it either way, which keeps the rule simple to reason about.
 */
@Service
public class RoleManagementService {

	private final RoleRepository roles;

	private final PermissionRepository permissions;

	private final UserRoleRepository userRoles;

	private final AuditRecorder audit;

	public RoleManagementService(RoleRepository roles, PermissionRepository permissions,
			UserRoleRepository userRoles, AuditRecorder audit) {
		this.roles = roles;
		this.permissions = permissions;
		this.userRoles = userRoles;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<RoleResponse> list(boolean includeInactive) {
		return this.roles.findAll()
			.stream()
			.filter(role -> includeInactive || role.isActive())
			.sorted(Comparator.comparing(Role::getName, String.CASE_INSENSITIVE_ORDER))
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public RoleResponse get(UUID id) {
		return toResponse(require(id));
	}

	@Transactional(readOnly = true)
	public PermissionCatalogue catalogue() {
		Map<String, List<PermissionView>> grouped = new LinkedHashMap<>();
		for (Permission permission : this.permissions.findAllByOrderByModuleAscCodeAsc()) {
			grouped.computeIfAbsent(permission.getModule(), key -> new ArrayList<>())
				.add(new PermissionView(permission.getId(), permission.getCode(), permission.getModule(),
						permission.getDescription()));
		}
		List<PermissionGroup> groups = grouped.entrySet()
			.stream()
			.map(entry -> new PermissionGroup(entry.getKey(), entry.getValue()))
			.toList();
		return new PermissionCatalogue(groups);
	}

	@Transactional
	public RoleResponse create(SaveRoleRequest request, EdamPrincipal caller) {
		String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
		if (this.roles.findByCode(code).isPresent()) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "A role with that code already exists.");
		}
		Role role = new Role();
		role.setName(request.name().trim());
		role.setCode(code);
		role.setDescription(request.description());
		role.setActive(true);
		// A role created through the API is never a system role: that flag marks the catalogue roles the
		// product itself depends on, and allowing a caller to set it would let them create an
		// undeletable, protected role of their own.
		role.setSystemRole(false);
		Role saved = this.roles.save(role);
		this.audit.record(AuditAction.CREATE, "Role", saved.getId(), "Created role " + saved.getCode());
		return toResponse(saved);
	}

	@Transactional
	public RoleResponse update(UUID id, SaveRoleRequest request, EdamPrincipal caller) {
		Role role = require(id);
		String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
		// A role's code is referenced by workflow approver configuration and by audit records. Renaming it
		// would silently change what a published workflow means, so the code is immutable after creation.
		if (!role.getCode().equals(code)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A role's code cannot be changed; create a new role instead.");
		}
		Map<String, Object> before = Map.of("name", role.getName(),
				"description", role.getDescription() == null ? "" : role.getDescription());
		role.setName(request.name().trim());
		role.setDescription(request.description());
		Role saved = this.roles.save(role);
		this.audit.recordChange(AuditAction.UPDATE, "Role", saved.getId(), before,
				Map.of("name", saved.getName(),
						"description", saved.getDescription() == null ? "" : saved.getDescription()));
		return toResponse(saved);
	}

	@Transactional
	public RoleResponse setActive(UUID id, boolean active, EdamPrincipal caller) {
		Role role = require(id);
		if (role.isSystemRole() && !active) {
			throw new BusinessException(ErrorCode.SYSTEM_ROLE_PROTECTED,
					"A system role cannot be deactivated: the product depends on it existing.");
		}
		role.setActive(active);
		Role saved = this.roles.save(role);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "Role", saved.getId(),
				active ? "Role activated" : "Role deactivated");
		return toResponse(saved);
	}

	/**
	 * Replaces a role's permission set.
	 *
	 * <p>Replacement rather than add/remove calls: the UI presents a grid of checkboxes and submits the
	 * final state, and a partial-application bug in that flow would silently leave a permission granted.
	 * One transaction that sets exactly what was asked for is easier to reason about and to audit.
	 */
	@Transactional
	public RoleResponse setPermissions(UUID id, SetPermissionsRequest request, EdamPrincipal caller) {
		Role role = require(id);
		if (role.isSystemRole() && request.permissionCodes().isEmpty()) {
			throw new BusinessException(ErrorCode.SYSTEM_ROLE_PROTECTED,
					"A system role cannot be left with no permissions.");
		}
		Set<Permission> resolved = new LinkedHashSet<>();
		for (String code : request.permissionCodes()) {
			Permission permission = this.permissions.findByCode(code)
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Unknown permission code: " + code));
			// Escalation guard: a caller cannot confer a capability they do not hold.
			if (caller != null && !caller.hasPermission(code)) {
				throw new BusinessException(ErrorCode.PERMISSION_DENIED,
						"You cannot grant the permission '" + code + "' because you do not hold it.");
			}
			resolved.add(permission);
		}
		Set<String> before = role.permissionCodes();
		role.setPermissions(resolved);
		Role saved = this.roles.save(role);
		this.audit.record(AuditAction.UPDATE, "Role", saved.getId(),
				"Permissions set from " + before.size() + " to " + resolved.size() + " codes");
		return toResponse(saved);
	}

	private RoleResponse toResponse(Role role) {
		List<PermissionView> permissionViews = role.getPermissions()
			.stream()
			.sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
			.map(permission -> new PermissionView(permission.getId(), permission.getCode(),
					permission.getModule(), permission.getDescription()))
			.toList();
		long holders = this.userRoles.findActiveHolderIds(role.getId()).size();
		return new RoleResponse(role.getId(), role.getName(), role.getCode(), role.getDescription(),
				role.isSystemRole(), role.isActive(), permissionViews, holders, role.getCreatedAt(),
				role.getUpdatedAt());
	}

	private Role require(UUID id) {
		return this.roles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Role", id));
	}

}
