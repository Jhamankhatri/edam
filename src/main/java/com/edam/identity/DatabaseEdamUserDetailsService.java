package com.edam.identity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;
import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.model.UserDataScope;
import com.edam.usermanagement.repository.UserDataScopeRepository;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Database-backed identity adapter (Module C).
 *
 * <h2>Position in the authentication seam</h2>
 * An approved identity provider (bank SSO/OIDC/SAML, LDAP, MFA) is still an open business decision.
 * This adapter does not choose one; it fills the {@link EdamUserDetailsService} seam with the local
 * schema so that:
 * <ul>
 *   <li>roles, permissions and data scopes are real persisted data, not fixtures;</li>
 *   <li>replacing it with an OIDC adapter is one bean swap, not a rewrite;</li>
 *   <li>no credential is invented here — if the user has no local hash, this adapter cannot verify a
 *       password and says so, rather than accepting any.</li>
 * </ul>
 *
 * <h2>What it loads, and in what order</h2>
 * <ol>
 *   <li>The user row; inactive users are rejected exactly like unknown users, so deactivation cannot be
 *       probed for.</li>
 *   <li>Active roles only: {@code RoleRepository#findActiveRolesForUser} applies the assignment-expiry
 *       predicate in SQL, so an expired grant cannot be loaded by mistake.</li>
 *   <li>Data scopes. A user with no scopes gets an <em>empty</em> set, never an implicit ALL.</li>
 * </ol>
 *
 * <h2>The unscoped-user case</h2>
 * A user with roles but no data scope is a configuration error. This adapter returns the principal with
 * no scopes; the authorization layer then denies record access. It deliberately does not throw at login:
 * refusing sign-in would hide the misconfiguration behind "wrong password", and the requirement is that
 * an administrator can see and fix their own account state.
 */
public class DatabaseEdamUserDetailsService implements EdamUserDetailsService {

	private static final Logger log = LoggerFactory.getLogger(DatabaseEdamUserDetailsService.class);

	private final UserRepository users;

	private final RoleRepository roles;

	private final UserDataScopeRepository dataScopes;

	public DatabaseEdamUserDetailsService(UserRepository users, RoleRepository roles,
			UserDataScopeRepository dataScopes) {
		this.users = users;
		this.roles = roles;
		this.dataScopes = dataScopes;
	}

	/**
	 * Loads the principal.
	 *
	 * @param username login name, matched case-insensitively
	 * @return the principal wrapped with the encoded credential when the account authenticates locally,
	 * or with {@code null} when it does not
	 */
	@Override
	public EdamUserDetails loadUserByUsername(String username) {
		User user = resolveActiveUser(username)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown or inactive user"));

		List<Role> activeRoles = this.roles.findActiveRolesForUser(user.getId());
		Set<String> roleCodes = new LinkedHashSet<>();
		Set<String> permissions = new LinkedHashSet<>();
		for (Role role : activeRoles) {
			roleCodes.add(role.getCode());
			permissions.addAll(role.permissionCodes());
		}

		Set<DataScope> scopes = new LinkedHashSet<>();
		for (UserDataScope row : this.dataScopes.findByUserId(user.getId())) {
			scopes.add(row.toDataScope());
		}
		if (!activeRoles.isEmpty() && scopes.isEmpty()) {
			// Configuration error, not an authentication failure. Recorded so an operator can see it.
			log.warn("identity.unscoped_user userId={} roles={} — record access will be denied until a data scope is assigned",
					user.getId(), roleCodes);
		}

		EdamPrincipal principal = new EdamPrincipal(user.getId(), user.getEmployeeCode(), user.getUsername(),
				user.displayName(), roleCodes, permissions, scopes);
		return new EdamUserDetails(principal, user.getPasswordHash());
	}

	private Optional<User> resolveActiveUser(String username) {
		if (username == null || username.isBlank()) {
			return Optional.empty();
		}
		return this.users.findByUsernameIgnoreCase(username.trim()).filter(User::isActive);
	}

}
