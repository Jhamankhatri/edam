package com.edam.common.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated EDAM user as seen by the application layer.
 *
 * <p>Institution membership is a <strong>set</strong>: the BRD explicitly states that a
 * user must not be assumed to belong to a single institution. Permissions are carried as
 * institution-scoped authorities in the form {@code PERM_<CODE>} so that authorization
 * checks can never be satisfied by a role name alone.
 *
 * <p>This type is a projection of the identity store, not a JPA entity; it is safe to hold
 * in the security context. It implements {@link UserDetails} so that Spring Security's
 * standard authentication machinery can use it directly.
 *
 * @param employeeId stable business identifier of the employee
 * @param username login name captured at authentication time
 * @param displayName presentational name
 * @param institutionRoles institution code to role codes the user holds there
 * @param permissions institution-independent operational permissions
 */
public record EdamPrincipal(String employeeId, String username, String displayName,
		Map<String, Set<String>> institutionRoles, Set<String> permissions) implements UserDetails {

	public EdamPrincipal {
		institutionRoles = (institutionRoles == null) ? Map.of() : Map.copyOf(institutionRoles);
		permissions = (permissions == null) ? Set.of() : Set.copyOf(permissions);
	}

	/** Institutions this user may act for. Never exactly-one by assumption. */
	public Set<String> institutionCodes() {
		return Collections.unmodifiableSet(new HashSet<>(this.institutionRoles.keySet()));
	}

	public boolean belongsTo(String institutionCode) {
		return institutionCode != null && this.institutionRoles.containsKey(institutionCode);
	}

	public Set<String> rolesIn(String institutionCode) {
		return this.institutionRoles.getOrDefault(institutionCode, Set.of());
	}

	/**
	 * Granted authorities: one {@code ROLE_<institution>:<role>} entry per
	 * institution/role pair plus one {@code PERM_*} entry per operational permission. Institution scoping of a role is
	 * encoded in the authority so that cross-tenant use cannot be accidental.
	 */
	public Set<GrantedAuthority> authorities() {
		Set<GrantedAuthority> authorities = new HashSet<>();
		this.institutionRoles.forEach((institution, roles) -> roles
			.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + institution + ":" + role))));
		this.permissions.forEach(permission -> authorities.add(new SimpleGrantedAuthority("PERM_" + permission)));
		return Set.copyOf(authorities);
	}

	// ------------------------------------------------------------------
	// UserDetails contract
	// ------------------------------------------------------------------

	@Override
	public Set<GrantedAuthority> getAuthorities() {
		return authorities();
	}

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
