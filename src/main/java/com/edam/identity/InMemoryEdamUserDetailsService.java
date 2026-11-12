package com.edam.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Development-only identity store.
 *
 * <p>Activated solely by {@code edam.security.dev-users.enabled=true}, which is set only
 * in the {@code local} profile. It exists so the foundation can be exercised end to end
 * without committing real credentials or pretending an authentication protocol has been
 * chosen (OD-11). Production profiles must leave it disabled; with no
 * {@link EdamUserDetailsService} bean, authentication fails closed.
 *
 * <p>Users model <em>institution membership as a set</em>: {@code admin} belongs to two
 * institutions to prove that the application never assumes a single-tenant user.
 *
 * <p>The raw passwords below are development fixtures, not secrets. They are never
 * logged and must never be copied into a deployed environment.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "edam.security.dev-users", name = "enabled", havingValue = "true")
public class InMemoryEdamUserDetailsService {

	@Bean
	@ConditionalOnMissingBean(EdamUserDetailsService.class)
	EdamUserDetailsService devEdamUserDetailsService(org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
		Map<String, DemoUser> users = new HashMap<>();
		users.put("admin", new DemoUser("admin", "Administrator", "admin-change-me",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")),
				Set.of("request:view-own", "request:create", "approval:act", "configuration:manage",
						"reporting:view", "audit:view", "institution:administer")));
		users.put("approver", new DemoUser("approver", "Approver", "approver-change-me",
				Map.of("albaraka", Set.of("APPROVER")),
				Set.of("request:view-own", "approval:act", "reporting:view")));
		users.put("requester", new DemoUser("requester", "Requester", "requester-change-me",
				Map.of("albaraka", Set.of("REQUESTER")), Set.of("request:view-own", "request:create")));
		return username -> {
			DemoUser user = users.get(username);
			if (user == null) {
				throw new org.springframework.security.core.userdetails.UsernameNotFoundException("Unknown user");
			}
			return user.toPrincipal(this.passwordEncoder);
		};
	}

	/** Development fixture; see the class Javadoc before reusing this anywhere. */
	private record DemoUser(String username, String displayName, String rawPassword,
			Map<String, Set<String>> institutionRoles, Set<String> permissions) {

		/**
		 * Wraps the application principal with the encoded credential required by
		 * {@code DaoAuthenticationProvider}. The password is hashed on first use with the
		 * configured encoder and never leaves this object.
		 */
		DevUserDetails toPrincipal(org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
			return new DevUserDetails(
					new EdamPrincipal(this.username, this.username, this.displayName, this.institutionRoles,
							this.permissions),
					passwordEncoder.encode(this.rawPassword));
		}

	}

}
