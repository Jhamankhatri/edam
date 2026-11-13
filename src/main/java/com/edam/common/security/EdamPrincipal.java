package com.edam.common.security;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The authenticated identity, with permissions and data scopes resolved once per request.
 *
 * <h2>Three separate things, deliberately kept separate</h2>
 * <ol>
 *   <li><strong>Permissions</strong> — capabilities, the union of the user's active roles. Answers
 *       "may this person manage expense configuration at all?"</li>
 *   <li><strong>Data scopes</strong> — the organizational units those capabilities apply to. Answers
 *       "may they do it for this department, or only for their own?"</li>
 *   <li><strong>Role codes</strong> — kept for display and for role-targeted workflow assignment.
 *       Authorization logic must never branch on a role code: branching on a role is how a configurable
 *       model quietly becomes hardcoded again.</li>
 * </ol>
 *
 * <h2>Expiry is applied at construction</h2>
 * Role assignments carry an optional {@code expires_at}. An expired assignment must not grant anything,
 * so the adapter that builds this principal excludes expired and inactive roles. That is why there is no
 * "isExpired" check anywhere downstream: a permission this object holds is valid <em>now</em>.
 *
 * <h2>No credential material</h2>
 * This record never carries a password hash. Credential verification happens in the identity adapter;
 * the principal is what survives into the security context, the session and (potentially) a log line.
 *
 * @param userId the {@code users.id} primary key
 * @param employeeCode the bank's employee code, or null when the bank does not supply one
 * @param username unique login name
 * @param displayName full name for display
 * @param roleCodes active role codes, for display and role-targeted assignment
 * @param permissions union of active role permissions
 * @param dataScopes organizational scopes the permissions apply to
 */
public record EdamPrincipal(UUID userId, String employeeCode, String username, String displayName,
		Set<String> roleCodes, Set<String> permissions, Set<DataScope> dataScopes) implements UserDetails {

	public EdamPrincipal {
		roleCodes = (roleCodes == null) ? Set.of() : Set.copyOf(roleCodes);
		permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
		dataScopes = (dataScopes == null) ? Set.of() : Set.copyOf(dataScopes);
	}

	/**
	 * True when the user holds the permission at all.
	 *
	 * <p>Callers must still check the data scope: holding {@code expense-configuration:manage} means
	 * they may manage categories <em>somewhere</em>, not everywhere.
	 */
	public boolean hasPermission(String permission) {
		return permission != null && this.permissions.contains(permission);
	}

	/** True when the user holds every listed permission. */
	public boolean hasAllPermissions(String... required) {
		for (String permission : required) {
			if (!hasPermission(permission)) {
				return false;
			}
		}
		return true;
	}

	/** True when the user has installation-wide scope for the permissions they hold. */
	public boolean hasInstallationScope() {
		return this.dataScopes.stream().anyMatch(DataScope::isInstallationWide);
	}

	/** True when the user may act on records of the given department. */
	public boolean scopesToDepartment(UUID departmentId) {
		if (hasInstallationScope()) {
			return true;
		}
		return departmentId != null
				&& this.dataScopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.DEPARTMENT
						&& departmentId.equals(scope.referenceId()));
	}

	/** True when the user may act on records of the given branch. */
	public boolean scopesToBranch(UUID branchId) {
		if (hasInstallationScope()) {
			return true;
		}
		return branchId != null
				&& this.dataScopes.stream().anyMatch(scope -> scope.type() == DataScope.Type.BRANCH
						&& branchId.equals(scope.referenceId()));
	}

	/** True when the user may act on records of the given organization unit. */
	public boolean scopesToOrganizationUnit(UUID organizationUnitId) {
		if (hasInstallationScope()) {
			return true;
		}
		return organizationUnitId != null && this.dataScopes.stream()
			.anyMatch(scope -> scope.type() == DataScope.Type.ORGANIZATION_UNIT
					&& organizationUnitId.equals(scope.referenceId()));
	}

	/** True when the only scope the user holds is their own records. */
	public boolean isSelfScopedOnly() {
		return !hasInstallationScope() && this.dataScopes.stream().allMatch(DataScope::isSelfOnly);
	}

	/** Grouping of permissions by module, used by the identity endpoint and the admin UI. */
	public Map<String, Set<String>> permissionsByModule() {
		Map<String, Set<String>> byModule = new LinkedHashMap<>();
		this.permissions.stream().sorted().forEach(code -> {
			int separator = code.indexOf(':');
			String module = separator < 0 ? "other" : code.substring(0, separator);
			byModule.computeIfAbsent(module, key -> new LinkedHashSet<>()).add(code);
		});
		return Map.copyOf(byModule);
	}

	/**
	 * Spring Security authorities, derived from permissions only.
	 *
	 * <p>Role codes are deliberately <em>not</em> authorities. If a role were an authority, URL rules
	 * such as {@code hasRole('SYSTEM_ADMINISTRATOR')} would become possible, and a bank-renamed role
	 * would silently stop matching a rule written months earlier. Permissions are the stable contract.
	 */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		List<GrantedAuthority> authorities = this.permissions.stream()
			.sorted()
			.map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
			.toList();
		return List.copyOf(authorities);
	}

	/** Never exposed: credential verification is the identity adapter's job. */
	@Override
	public String getPassword() {
		return null;
	}

	@Override
	public String getUsername() {
		return this.username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String toString() {
		return "EdamPrincipal[" + this.username + ", roles=" + this.roleCodes + "]";
	}

}
