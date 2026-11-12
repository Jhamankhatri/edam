package com.edam.identity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;
import com.edam.common.security.Permissions;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Development-only identity store.
 *
 * <p>Activated solely by {@code edam.security.dev-users.enabled=true}, which is set only in the
 * {@code local} profile. It exists so the platform can be exercised end to end without
 * committing real credentials or pretending an authentication protocol has been chosen (OD-24).
 * Production profiles must leave it disabled; with no {@link EdamUserDetailsService} bean,
 * authentication fails closed.
 *
 * <p>Users model <strong>institution membership as a set</strong>: {@code admin} belongs to two
 * institutions to prove that the application never assumes a single-tenant user, and its
 * permissions differ per institution so that the tenant-scoped permission model is exercised
 * rather than assumed.
 *
 * <p>The raw passwords below are development fixtures, not secrets. They are never logged and
 * must never be copied into a deployed environment.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "edam.security.dev-users", name = "enabled", havingValue = "true")
public class InMemoryEdamUserDetailsService {

	private static Set<String> allPermissions() {
		return Permissions.all();
	}

	@Bean
	@ConditionalOnMissingBean(EdamUserDetailsService.class)
	EdamUserDetailsService devEdamUserDetailsService(
			org.springframework.security.crypto.password.PasswordEncoder encoder) {
		Map<String, DemoUser> users = new HashMap<>();

		// Full administrator in both institutions: memberships are a set, not a single tenant.
		users.put("admin", new DemoUser("admin", "Administrator", "admin-change-me",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")),
				Map.of("albaraka", allPermissions(), "demo-bank", allPermissions())));

		// Approver: only in albaraka. Holding no role in demo-bank must make demo-bank invisible.
		users.put("approver", new DemoUser("approver", "Approver", "approver-change-me",
				Map.of("albaraka", Set.of("APPROVER")),
				Map.of("albaraka", Set.of(Permissions.REQUEST_VIEW_OWN, Permissions.APPROVAL_ACT,
						Permissions.REPORTING_VIEW))));

		// Requester: only in albaraka, no approval authority and no configuration authority.
		users.put("requester", new DemoUser("requester", "Requester", "requester-change-me",
				Map.of("albaraka", Set.of("REQUESTER")),
				Map.of("albaraka", Set.of(Permissions.REQUEST_VIEW_OWN, Permissions.REQUEST_CREATE))));

		// Cross-tenant fixture: an institution administrator in demo-bank only. Used by the
		// isolation tests to prove that demo-bank authority does not reach albaraka data.
		users.put("demo-admin", new DemoUser("demo-admin", "Demo Administrator", "demo-change-me",
				Map.of("demo-bank", Set.of("INSTITUTION_ADMINISTRATOR")),
				Map.of("demo-bank", allPermissions())));

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
	private record DemoUser(String username, String displayName, String rawPassword,
			Map<String, Set<String>> institutionRoles, Map<String, Set<String>> permissionsByInstitution) {

		/**
		 * Wraps the application principal with the encoded credential required by
		 * {@code DaoAuthenticationProvider}. The password is hashed on first use with the
		 * configured encoder and never leaves this object.
		 */
		DevUserDetails toPrincipal(org.springframework.security.crypto.password.PasswordEncoder encoder) {
			Map<String, Set<String>> immutablePermissions = new LinkedHashMap<>();
			this.permissionsByInstitution
				.forEach((institution, permissions) -> immutablePermissions.put(institution, Set.copyOf(permissions)));
			EdamPrincipal principal = new EdamPrincipal(this.username, this.username, this.displayName,
					this.institutionRoles, immutablePermissions, this.institutionRoles.keySet());
			return new DevUserDetails(principal, encoder.encode(this.rawPassword));
		}

	}

	/** Sorted human-readable description of the fixtures, used by the local startup log. */
	static String describe(Map<String, Set<String>> institutionRoles) {
		return institutionRoles.entrySet()
			.stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue().stream().sorted().collect(Collectors.joining("|")))
			.collect(Collectors.joining(","));
	}

}
