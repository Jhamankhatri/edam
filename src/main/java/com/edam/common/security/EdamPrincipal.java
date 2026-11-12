package com.edam.common.security;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated EDAM user as seen by the application layer.
 *
 * <h2>Institution membership is a set</h2>
 * The BRD states explicitly that a user must not be assumed to belong to a single institution
 * (IAM-04). {@link #institutionRoles} maps institution code to the role codes held <em>there</em>;
 * {@link #permissionsByInstitution} maps institution code to permissions granted <em>there</em>.
 *
 * <h2>Why permissions are per institution (fixes audit defect D-1)</h2>
 * The previous model carried one flat permission set for the whole principal, so a permission
 * granted in institution A was silently effective in institution B — a cross-tenant privilege
 * escalation. Every authorization decision now has to name the institution it applies to:
 * {@link #hasPermissionIn(String, String)}. A permission held "globally" does not exist as a
 * concept in EDAM.
 *
 * <h2>Authority encoding</h2>
 * Spring Security needs flat authorities, so institution-scoped permissions are encoded as
 * {@code PERM_<institution>:<permission>}. Encoding the tenant into the authority means a
 * {@code hasAuthority} expression cannot accidentally match across tenants.
 *
 * @param employeeId stable business identifier of the employee
 * @param username login name captured at authentication time
 * @param displayName presentational name
 * @param institutionRoles institution code to role codes the user holds there
 * @param permissionsByInstitution institution code to permissions granted there
 * @param allInstitutions the institution codes known to the identity store for membership checks
 */
public record EdamPrincipal(String employeeId, String username, String displayName,
		Map<String, Set<String>> institutionRoles, Map<String, Set<String>> permissionsByInstitution,
		Set<String> allInstitutions) implements UserDetails {

	public EdamPrincipal {
		institutionRoles = immutableNested(institutionRoles);
		permissionsByInstitution = immutableNested(permissionsByInstitution);
		allInstitutions = (allInstitutions == null) ? Set.copyOf(institutionRoles.keySet())
				: Set.copyOf(allInstitutions);
	}

	private static Map<String, Set<String>> immutableNested(Map<String, Set<String>> source) {
		if (source == null || source.isEmpty()) {
			return Map.of();
		}
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		source.forEach((key, value) -> copy.put(key, value == null ? Set.of() : Set.copyOf(value)));
		return Collections.unmodifiableMap(copy);
	}

	/**
	 * Backwards-compatible view of the union of all permissions. Intended for diagnostics and
	 * for the {@code /me/context} read model only — <strong>never</strong> for an authorization
	 * decision, because it has lost the tenant dimension.
	 */
	public Set<String> permissions() {
		Set<String> union = new LinkedHashSet<>();
		this.permissionsByInstitution.values().forEach(union::addAll);
		return Set.copyOf(union);
	}

	/** Institutions this user may act for. Never exactly-one by assumption. */
	public Set<String> institutionCodes() {
		return Set.copyOf(this.allInstitutions);
	}

	public boolean belongsTo(String institutionCode) {
		return institutionCode != null && this.allInstitutions.contains(institutionCode);
	}

	public Set<String> rolesIn(String institutionCode) {
		return this.institutionRoles.getOrDefault(institutionCode, Set.of());
	}

	public Set<String> permissionsIn(String institutionCode) {
		return this.permissionsByInstitution.getOrDefault(institutionCode, Set.of());
	}

	/**
	 * The only sanctioned permission check. Both arguments are mandatory: an unknown institution
	 * yields {@code false} rather than "all permissions".
	 */
	public boolean hasPermissionIn(String institutionCode, String permission) {
		if (institutionCode == null || permission == null) {
			return false;
		}
		return permissionsIn(institutionCode).contains(permission);
	}

	public boolean hasRoleIn(String institutionCode, String role) {
		return role != null && rolesIn(institutionCode).contains(role);
	}

	/**
	 * One {@code ROLE_<institution>:<role>} authority per institution/role pair plus one
	 * {@code PERM_<institution>:<permission>} authority per institution/permission pair.
	 */
	@Override
	public Set<GrantedAuthority> getAuthorities() {
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();
		this.institutionRoles.forEach((institution, roles) -> roles.forEach(
				role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + institution + ":" + role))));
		this.permissionsByInstitution.forEach((institution, permissions) -> permissions.forEach(
				permission -> authorities.add(new SimpleGrantedAuthority("PERM_" + institution + ":" + permission))));
		return Set.copyOf(authorities);
	}

	/** Convenience: the encoded authority string for an institution-scoped permission. */
	public static String permissionAuthority(String institutionCode, String permission) {
		return "PERM_" + Objects.requireNonNull(institutionCode) + ":" + Objects.requireNonNull(permission);
	}

	// ------------------------------------------------------------------
	// UserDetails contract
	// ------------------------------------------------------------------

	/**
	 * EDAM never stores or exposes credential material on the principal. Authentication is
	 * performed against the identity provider, which returns a fully authenticated token.
	 */
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

}
