package com.edam.usermanagement.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.identity.service.AuthService;
import com.edam.identity.service.PasswordPolicy;
import com.edam.organization.model.Branch;
import com.edam.organization.model.Department;
import com.edam.organization.repository.BranchRepository;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.model.UserRole;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.rolemanagement.repository.UserRoleRepository;
import com.edam.usermanagement.dto.UserDtos.AssignRoleRequest;
import com.edam.usermanagement.dto.UserDtos.AssignScopeRequest;
import com.edam.usermanagement.dto.UserDtos.CreateUserRequest;
import com.edam.usermanagement.dto.UserDtos.UpdateUserRequest;
import com.edam.usermanagement.dto.UserDtos.UserCreatedResponse;
import com.edam.usermanagement.dto.UserDtos.UserResponse;
import com.edam.usermanagement.dto.UserDtos.UserRoleView;
import com.edam.usermanagement.dto.UserDtos.UserScopeView;
import com.edam.usermanagement.dto.UserDtos.UserSummary;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.model.UserDataScope;
import com.edam.usermanagement.repository.UserDataScopeRepository;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration (Module C).
 *
 * <h2>The three rules that keep this from becoming a privilege-escalation tool</h2>
 * <ol>
 *   <li><strong>No privilege beyond your own.</strong> A caller may only grant roles whose permission set
 *       is contained in their own. Without this, anyone holding {@code user:manage} could mint an account
 *       with {@code system:administer} and use it. That is the classic RBAC escalation hole, and it is
 *       closed here rather than in the UI.</li>
 *   <li><strong>No editing an account above you.</strong> The same containment test applies to the target.
 *       A Bank Administrator cannot deactivate a System Administrator, because doing so is itself an
 *       administrative act they were never granted.</li>
 *   <li><strong>No deleting.</strong> Users are deactivated. An approval is attributed to a user id
 *       forever; deleting the row would orphan the audit trail of who approved a payment.</li>
 * </ol>
 *
 * <h2>Multiple administrators is a first-class requirement</h2>
 * Nothing here restricts how many administrators exist, and no role is special-cased as "the" admin. The
 * catalogue ships fourteen system roles; a bank composes its own administrator set (system, bank, HR,
 * finance, expense-configuration) by granting those roles to different people. The only single-instance
 * concept in the product is the first administrator created by bootstrap, and that restriction lives in
 * the bootstrap state, not here.
 */
@Service
public class UserManagementService {

	private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

	private final UserRepository users;

	private final RoleRepository roles;

	private final UserRoleRepository userRoles;

	private final UserDataScopeRepository dataScopes;

	private final DepartmentRepository departments;

	private final BranchRepository branches;

	private final PasswordPolicy passwordPolicy;

	private final AuthService authService;

	private final AuditRecorder audit;

	public UserManagementService(UserRepository users, RoleRepository roles, UserRoleRepository userRoles,
			UserDataScopeRepository dataScopes, DepartmentRepository departments, BranchRepository branches,
			PasswordPolicy passwordPolicy, AuthService authService, AuditRecorder audit) {
		this.users = users;
		this.roles = roles;
		this.userRoles = userRoles;
		this.dataScopes = dataScopes;
		this.departments = departments;
		this.branches = branches;
		this.passwordPolicy = passwordPolicy;
		this.authService = authService;
		this.audit = audit;
	}

	// ---------------------------------------------------------------------------------------------
	// Read
	// ---------------------------------------------------------------------------------------------

	/**
	 * Lists users the caller may see.
	 *
	 * <h2>Why the scope filter is applied before paging</h2>
	 * Filtering the page *after* the query would return a page of ten rows containing three visible
	 * users, while the metadata still claimed the unfiltered total. The client would then paginate onto
	 * empty pages and show a count that includes records the caller may not see. The predicate is
	 * therefore pushed into the repository query, so the count and the rows describe the same set.
	 */
	@Transactional(readOnly = true)
	public PageResponse<UserResponse> list(String search, UUID departmentId, Boolean active, PageQuery page,
			EdamPrincipal caller) {
		var pageable = page.toPageable(Sort.by("lastName", "firstName"));
		ScopeFilter filter = ScopeFilter.forCaller(caller);
		var result = this.users.searchScoped(search, departmentId, active, filter.installationWide(),
				filter.departmentIds(), filter.branchIds(), caller == null ? null : caller.userId(), pageable);
		return PageResponse.of(result, this::toResponse);
	}

	/**
	 * The caller's visibility, precomputed for the query.
	 *
	 * <p>An installation-scoped caller matches everything. Otherwise a user is visible when their
	 * department is one of the caller's, their branch is one of the caller's, or they are the caller.
	 * A caller with no scopes at all sees only themselves — never everybody, which is the failure mode a
	 * null-safe default would otherwise introduce.
	 */
	private record ScopeFilter(boolean installationWide, List<UUID> departmentIds, List<UUID> branchIds) {

		static ScopeFilter forCaller(EdamPrincipal caller) {
			if (caller == null) {
				return new ScopeFilter(true, List.of(), List.of());
			}
			if (caller.hasInstallationScope()) {
				return new ScopeFilter(true, List.of(), List.of());
			}
			List<UUID> departmentIds = caller.dataScopes()
				.stream()
				.filter(scope -> scope.type() == DataScope.Type.DEPARTMENT)
				.map(DataScope::referenceId)
				.filter(java.util.Objects::nonNull)
				.toList();
			List<UUID> branchIds = caller.dataScopes()
				.stream()
				.filter(scope -> scope.type() == DataScope.Type.BRANCH)
				.map(DataScope::referenceId)
				.filter(java.util.Objects::nonNull)
				.toList();
			return new ScopeFilter(false, departmentIds, branchIds);
		}

	}

	private boolean withinCallerScope(User target, EdamPrincipal caller) {
		if (caller == null || caller.hasInstallationScope()) {
			return true;
		}
		if (target.getDepartmentId() != null && caller.scopesToDepartment(target.getDepartmentId())) {
			return true;
		}
		if (target.getBranchId() != null && caller.scopesToBranch(target.getBranchId())) {
			return true;
		}
		// A caller can always see themselves.
		return caller.userId().equals(target.getId());
	}

	@Transactional(readOnly = true)
	public UserResponse get(UUID id, EdamPrincipal caller) {
		User user = require(id);
		if (!withinCallerScope(user, caller)) {
			// 404 rather than 403: revealing that a record exists but is out of scope is itself a leak.
			throw new ResourceNotFoundException("User", id);
		}
		return toResponse(user);
	}

	@Transactional(readOnly = true)
	public List<UserSummary> searchSummaries(String search) {
		return this.users.search(search, null, org.springframework.data.domain.PageRequest.of(0, 20))
			.stream()
			.map(user -> new UserSummary(user.getId(), user.getUsername(), user.displayName(),
					user.getEmployeeCode()))
			.toList();
	}

	// ---------------------------------------------------------------------------------------------
	// Create / update
	// ---------------------------------------------------------------------------------------------

	@Transactional
	public UserCreatedResponse create(CreateUserRequest request, EdamPrincipal caller) {
		String username = AuthService.normaliseUsername(request.username());
		if (this.users.existsByUsernameIgnoreCase(username)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That username is already in use.");
		}
		if (this.users.existsByEmailIgnoreCase(request.email())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That e-mail address is already in use.");
		}
		if (request.employeeCode() != null && !request.employeeCode().isBlank()
				&& this.users.existsByEmployeeCodeIgnoreCase(request.employeeCode())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That employee code is already in use.");
		}
		validateOrganizationReferences(request.departmentId(), request.branchId(), request.managerId(), null);

		User user = new User();
		user.setUsername(username);
		user.setFirstName(request.firstName().trim());
		user.setLastName(request.lastName().trim());
		user.setEmail(request.email().trim());
		user.setEmployeeCode(blankToNull(request.employeeCode()));
		user.setDesignation(blankToNull(request.designation()));
		user.setDepartmentId(request.departmentId());
		user.setBranchId(request.branchId());
		user.setManagerId(request.managerId());
		// No password is set here. The account cannot authenticate until the user redeems a setup grant,
		// which is the only way a credential should ever come into existence.
		user.setActive(true);
		User saved = this.users.save(user);

		for (UUID roleId : request.roleIds() == null ? List.<UUID>of() : request.roleIds()) {
			grantRole(saved, roleId, null, caller);
		}

		// A credential setup grant is issued so the user can choose their own password. The raw token is
		// not returned over HTTP; the notification layer delivers it. Until notifications exist, the
		// caller is told plainly that delivery is pending rather than being handed a token that would
		// then appear in a browser log.
		boolean issued = this.authService.issueAdministrativeReset(saved.getId()).isPresent();

		this.audit.record(AuditAction.CREATE, "User", saved.getId(), "Created user " + saved.getUsername());
		log.info("user.created userId={} username={} by={}", saved.getId(), saved.getUsername(),
				caller == null ? "system" : caller.userId());

		return new UserCreatedResponse(toResponse(saved), issued,
				issued ? "A password setup link has been issued for delivery to the user."
						: "No local credential setup was issued.");
	}

	@Transactional
	public UserResponse update(UUID id, UpdateUserRequest request, EdamPrincipal caller) {
		User user = require(id);
		requireAuthorityOver(user, caller);

		if (!user.getEmail().equalsIgnoreCase(request.email())
				&& this.users.existsByEmailIgnoreCase(request.email())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That e-mail address is already in use.");
		}
		if (request.employeeCode() != null && !request.employeeCode().isBlank()
				&& !request.employeeCode().equalsIgnoreCase(user.getEmployeeCode())
				&& this.users.existsByEmployeeCodeIgnoreCase(request.employeeCode())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That employee code is already in use.");
		}
		validateOrganizationReferences(request.departmentId(), request.branchId(), request.managerId(), id);

		Map<String, Object> before = snapshot(user);
		user.setFirstName(request.firstName().trim());
		user.setLastName(request.lastName().trim());
		user.setEmail(request.email().trim());
		user.setEmployeeCode(blankToNull(request.employeeCode()));
		user.setDesignation(blankToNull(request.designation()));
		user.setDepartmentId(request.departmentId());
		user.setBranchId(request.branchId());
		user.setManagerId(request.managerId());
		User saved = this.users.save(user);
		this.audit.recordChange(AuditAction.UPDATE, "User", saved.getId(), before, snapshot(saved));
		return toResponse(saved);
	}

	/** Activates or deactivates an account. Deactivation is the only "removal" this product performs. */
	@Transactional
	public UserResponse setActive(UUID id, boolean active, EdamPrincipal caller) {
		User user = require(id);
		requireAuthorityOver(user, caller);
		if (user.getId().equals(caller.userId()) && !active) {
			throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
					"You cannot deactivate your own account.");
		}
		user.setActive(active);
		User saved = this.users.save(user);
		this.audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE, "User", saved.getId(),
				active ? "Account activated" : "Account deactivated");
		return toResponse(saved);
	}

	// ---------------------------------------------------------------------------------------------
	// Role assignment
	// ---------------------------------------------------------------------------------------------

	@Transactional
	public UserRoleView assignRole(UUID userId, AssignRoleRequest request, EdamPrincipal caller) {
		User user = require(userId);
		requireAuthorityOver(user, caller);
		UserRole assignment = grantRole(user, request.roleId(), request.expiresAt(), caller);
		Role role = this.roles.findById(request.roleId()).orElseThrow();
		this.audit.record(AuditAction.UPDATE, "User", userId, "Granted role " + role.getCode());
		return toRoleView(assignment, role);
	}

	/**
	 * Grants a role, refusing to grant anything the caller does not already hold.
	 *
	 * <p>The containment test is on the *permission set*, not on the role identity: a caller who holds
	 * every permission of a role can delegate it, even if they hold it through a different role. Testing
	 * role identity instead would make delegation impossible without also making escalation possible.
	 */
	private UserRole grantRole(User user, UUID roleId, Instant expiresAt, EdamPrincipal caller) {
		Role role = this.roles.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
		if (!role.isActive()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "That role is inactive and cannot be granted.");
		}
		if (caller != null && !caller.hasAllPermissions(role.permissionCodes().toArray(String[]::new))) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED,
					"You cannot grant a role that holds permissions you do not have.");
		}
		if (this.userRoles.existsByUserIdAndRoleId(user.getId(), roleId)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That role is already assigned to this user.");
		}
		UserRole assignment = new UserRole(user.getId(), roleId, caller == null ? null : caller.userId(),
				expiresAt);
		return this.userRoles.save(assignment);
	}

	/** Revokes a role. Recorded as history rather than deleted, but the effective grant ends now. */
	@Transactional
	public void revokeRole(UUID userId, UUID assignmentId, EdamPrincipal caller) {
		User user = require(userId);
		requireAuthorityOver(user, caller);
		UserRole assignment = this.userRoles.findById(assignmentId)
			.filter(candidate -> candidate.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Role assignment", assignmentId));
		Role role = this.roles.findById(assignment.getRoleId()).orElse(null);
		this.userRoles.delete(assignment);
		this.audit.record(AuditAction.UPDATE, "User", userId,
				"Revoked role " + (role == null ? assignment.getRoleId() : role.getCode()));
	}

	@Transactional(readOnly = true)
	public List<UserRoleView> rolesOf(UUID userId) {
		require(userId);
		Map<UUID, Role> cache = new HashMap<>();
		List<UserRoleView> result = new ArrayList<>();
		for (UserRole assignment : this.userRoles.findByUserId(userId)) {
			Role role = cache.computeIfAbsent(assignment.getRoleId(),
					key -> this.roles.findById(key).orElse(null));
			if (role != null) {
				result.add(toRoleView(assignment, role));
			}
		}
		return result;
	}

	// ---------------------------------------------------------------------------------------------
	// Data scopes
	// ---------------------------------------------------------------------------------------------

	@Transactional
	public UserScopeView assignScope(UUID userId, AssignScopeRequest request, EdamPrincipal caller) {
		User user = require(userId);
		requireAuthorityOver(user, caller);
		DataScope scope = parseScope(request);
		// A grantor cannot widen someone's reach beyond their own: that would let a department-scoped
		// administrator create an installation-scoped user and then use it.
		if (caller != null && !caller.hasInstallationScope() && scope.type() == DataScope.Type.ALL) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED,
					"Only a caller with installation-wide scope can grant installation-wide scope.");
		}
		boolean duplicate = this.dataScopes.findByUserId(userId)
			.stream()
			.anyMatch(existing -> existing.getScopeType() == scope.type()
					&& java.util.Objects.equals(existing.getScopeReferenceId(), scope.referenceId()));
		if (duplicate) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That data scope is already assigned.");
		}
		UserDataScope saved = this.dataScopes.save(new UserDataScope(userId, scope,
				caller == null ? "system" : caller.getUsername()));
		this.audit.record(AuditAction.UPDATE, "User", userId, "Granted data scope " + scope.type());
		return toScopeView(saved);
	}

	@Transactional
	public void removeScope(UUID userId, UUID scopeId, EdamPrincipal caller) {
		User user = require(userId);
		requireAuthorityOver(user, caller);
		UserDataScope scope = this.dataScopes.findById(scopeId)
			.filter(candidate -> candidate.getUserId().equals(userId))
			.orElseThrow(() -> new ResourceNotFoundException("Data scope", scopeId));
		this.dataScopes.delete(scope);
		this.audit.record(AuditAction.UPDATE, "User", userId, "Removed data scope " + scope.getScopeType());
	}

	private DataScope parseScope(AssignScopeRequest request) {
		DataScope.Type type;
		try {
			type = DataScope.Type.valueOf(request.scopeType().trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown data scope type: "
					+ request.scopeType() + ". Expected one of ALL, DEPARTMENT, BRANCH, ORGANIZATION_UNIT, SELF.");
		}
		boolean needsReference = type == DataScope.Type.DEPARTMENT || type == DataScope.Type.BRANCH
				|| type == DataScope.Type.ORGANIZATION_UNIT;
		if (needsReference && request.referenceId() == null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A " + type + " scope requires a referenceId.");
		}
		if (!needsReference && request.referenceId() != null) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"A " + type + " scope must not carry a referenceId.");
		}
		// Referential integrity for the polymorphic reference is enforced here, because the database
		// cannot express a foreign key that points at one of three tables.
		if (type == DataScope.Type.DEPARTMENT) {
			this.departments.findById(request.referenceId())
				.orElseThrow(() -> new ResourceNotFoundException("Department", request.referenceId()));
		}
		else if (type == DataScope.Type.BRANCH) {
			this.branches.findById(request.referenceId())
				.orElseThrow(() -> new ResourceNotFoundException("Branch", request.referenceId()));
		}
		return new DataScope(type, request.referenceId());
	}

	// ---------------------------------------------------------------------------------------------
	// Password reset (administrative)
	// ---------------------------------------------------------------------------------------------

	/**
	 * Triggers a password reset for a user.
	 *
	 * <p>The administrator never sees the new password and never sets it: they can only cause a secure
	 * link to be issued. This is the difference between "reset" and "take over the account".
	 */
	@Transactional
	public boolean triggerPasswordReset(UUID userId, EdamPrincipal caller) {
		User user = require(userId);
		requireAuthorityOver(user, caller);
		return this.authService.issueAdministrativeReset(userId).isPresent();
	}

	// ---------------------------------------------------------------------------------------------
	// Guards
	// ---------------------------------------------------------------------------------------------

	/**
	 * Refuses to act on an account that holds permissions the caller does not.
	 *
	 * <p>Returns 404 rather than 403 so that a caller cannot map out the privileged accounts by probing.
	 */
	private void requireAuthorityOver(User target, EdamPrincipal caller) {
		if (caller == null || caller.hasInstallationScope()) {
			return;
		}
		java.util.Set<String> targetPermissions = new java.util.LinkedHashSet<>();
		for (Role role : this.roles.findActiveRolesForUser(target.getId())) {
			targetPermissions.addAll(role.permissionCodes());
		}
		boolean callerOutranks = targetPermissions.stream().allMatch(caller::hasPermission);
		boolean visible = withinCallerScope(target, caller);
		if (!callerOutranks || !visible) {
			throw new ResourceNotFoundException("User", target.getId());
		}
	}

	private void validateOrganizationReferences(UUID departmentId, UUID branchId, UUID managerId, UUID selfId) {
		if (departmentId != null) {
			this.departments.findById(departmentId)
				.orElseThrow(() -> new ResourceNotFoundException("Department", departmentId));
		}
		if (branchId != null) {
			this.branches.findById(branchId).orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
		}
		if (managerId != null) {
			if (managerId.equals(selfId)) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A user cannot be their own manager.");
			}
			this.users.findById(managerId).orElseThrow(() -> new ResourceNotFoundException("User", managerId));
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Mapping
	// ---------------------------------------------------------------------------------------------

	private UserResponse toResponse(User user) {
		String departmentName = (user.getDepartmentId() == null) ? null
				: this.departments.findById(user.getDepartmentId()).map(Department::getName).orElse(null);
		String branchName = (user.getBranchId() == null) ? null
				: this.branches.findById(user.getBranchId()).map(Branch::getName).orElse(null);
		String managerName = (user.getManagerId() == null) ? null
				: this.users.findById(user.getManagerId()).map(User::displayName).orElse(null);

		List<UserRoleView> roleViews = new ArrayList<>();
		for (UserRole assignment : this.userRoles.findByUserId(user.getId())) {
			this.roles.findById(assignment.getRoleId())
				.ifPresent(role -> roleViews.add(toRoleView(assignment, role)));
		}
		List<UserScopeView> scopeViews = this.dataScopes.findByUserId(user.getId())
			.stream()
			.map(this::toScopeView)
			.toList();

		return new UserResponse(user.getId(), user.getUsername(), user.getEmployeeCode(), user.getFirstName(),
				user.getLastName(), user.displayName(), user.getEmail(), user.getDesignation(),
				user.getDepartmentId(), departmentName, user.getBranchId(), branchName, user.getManagerId(),
				managerName, user.isActive(), user.getLastLoginAt(), user.getCreatedAt(), roleViews, scopeViews);
	}

	private UserRoleView toRoleView(UserRole assignment, Role role) {
		return new UserRoleView(assignment.getId(), role.getId(), role.getCode(), role.getName(),
				assignment.getAssignedAt(), assignment.getExpiresAt(), assignment.isLive());
	}

	private UserScopeView toScopeView(UserDataScope scope) {
		String referenceName = null;
		if (scope.getScopeReferenceId() != null) {
			referenceName = switch (scope.getScopeType()) {
				case DEPARTMENT -> this.departments.findById(scope.getScopeReferenceId())
					.map(Department::getName).orElse(null);
				case BRANCH -> this.branches.findById(scope.getScopeReferenceId()).map(Branch::getName).orElse(null);
				default -> null;
			};
		}
		return new UserScopeView(scope.getId(), scope.getScopeType().name(), scope.getScopeReferenceId(),
				referenceName);
	}

	private Map<String, Object> snapshot(User user) {
		Map<String, Object> values = new java.util.LinkedHashMap<>();
		values.put("firstName", user.getFirstName());
		values.put("lastName", user.getLastName());
		values.put("email", user.getEmail());
		values.put("employeeCode", user.getEmployeeCode());
		values.put("designation", user.getDesignation());
		values.put("departmentId", user.getDepartmentId());
		values.put("branchId", user.getBranchId());
		values.put("managerId", user.getManagerId());
		values.put("active", user.isActive());
		return values;
	}

	private User require(UUID id) {
		return this.users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

	/** Exposed for the password-policy endpoint and for tests. */
	public PasswordPolicy policy() {
		return this.passwordPolicy;
	}

}
