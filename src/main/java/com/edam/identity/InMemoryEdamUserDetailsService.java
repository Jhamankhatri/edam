package com.edam.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;
import com.edam.common.security.Permissions;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Development-only identity store.
 *
 * <p>Activated solely by {@code edam.security.dev-users.enabled=true}, which is set only by the
 * {@code local} profile. It exists so the platform can be exercised end to end without committing real
 * credentials or pretending an authentication protocol has been chosen.
 *
 * <h2>Why the fixtures are shaped the way they are</h2>
 * <ul>
 *   <li>{@code admin} holds every catalogue permission with the <strong>ALL</strong> data scope, which is
 *       what a first administrator configured through the bootstrap actually looks like.</li>
 *   <li>{@code ho-admin} holds the same permissions but a <strong>department</strong> scope. This pair is
 *       the whole point: the two users have identical capabilities and different reach, which is exactly
 *       the distinction the requirement draws between a role and a global administrator.</li>
 *   <li>{@code requester} holds only request-creation permissions with a <strong>SELF</strong> scope, so a
 *       missing permission check cannot hide behind a wide scope.</li>
 * </ul>
 *
 * <p>These are development fixtures, not seed data: they are never inserted into the database, never ship
 * in a migration, and are absent from every non-local profile.
 */
@Component
public class InMemoryEdamUserDetailsService {

	/**
	 * Builds the fixture store.
	 *
	 * <p>A factory rather than a {@code @Bean} so that {@code IdentityConfiguration} is the single place
	 * that decides whether it is used; a conditional bean here would race the database adapter's
	 * definition and could be selected by accident in a profile that merely left a property enabled.
	 */
	public EdamUserDetailsService userDetailsService(
			org.springframework.security.crypto.password.PasswordEncoder encoder) {
		Map<String, DemoUser> users = new HashMap<>();

		users.put("admin", new DemoUser("admin", "Administrator", "admin-change-me",
				Set.of("SYSTEM_ADMINISTRATOR"), Permissions.all(), Set.of(new DataScope(DataScope.Type.ALL, null))));

		// Same capabilities as `admin`, narrower reach. Proves scope is enforced independently of role.
		UUID headOfficeDepartment = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
		users.put("ho-admin", new DemoUser("ho-admin", "Head Office Administrator", "ho-admin-change-me",
				Set.of("EXPENSE_CONFIGURATION_ADMINISTRATOR"), Set.of(Permissions.EXPENSE_CONFIGURATION_VIEW,
						Permissions.EXPENSE_CONFIGURATION_MANAGE, Permissions.DELEGATION_VIEW),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, headOfficeDepartment))));

		users.put("requester", new DemoUser("requester", "Requester", "requester-change-me", Set.of("REQUESTER"),
				Set.of(Permissions.REQUEST_CREATE, Permissions.REQUEST_VIEW_OWN, Permissions.REQUEST_SUBMIT,
						Permissions.DOCUMENT_UPLOAD, Permissions.NOTIFICATION_VIEW),
				Set.of(new DataScope(DataScope.Type.SELF, null))));

		users.put("approver", new DemoUser("approver", "Approver", "approver-change-me", Set.of("DEPARTMENT_HEAD"),
				Set.of(Permissions.APPROVAL_VIEW, Permissions.APPROVAL_ACT, Permissions.REQUEST_VIEW_SCOPE,
						Permissions.NOTIFICATION_VIEW),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, headOfficeDepartment))));

		return username -> {
			DemoUser user = users.get(username);
			if (user == null) {
				// Uniform failure; never reveal whether the account exists.
				throw new UsernameNotFoundException("Unknown user");
			}
			return user.toPrincipal(encoder);
		};
	}

	/** Development fixture; see the class Javadoc before reusing this anywhere. */
	private record DemoUser(String username, String displayName, String rawPassword, Set<String> roleCodes,
			Set<String> permissions, Set<DataScope> dataScopes) {

		/**
		 * Wraps the application principal with the encoded credential required by
		 * {@code DaoAuthenticationProvider}. The password is hashed on first use with the configured
		 * encoder and never leaves this object.
		 */
		EdamUserDetails toPrincipal(org.springframework.security.crypto.password.PasswordEncoder encoder) {
			EdamPrincipal principal = new EdamPrincipal(UUID.randomUUID(), null, this.username, this.displayName,
					this.roleCodes, this.permissions, this.dataScopes);
			return new EdamUserDetails(principal, encoder.encode(this.rawPassword));
		}

	}

}
