package com.edam.identity;

import com.edam.common.security.EdamUserDetailsService;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.usermanagement.repository.UserDataScopeRepository;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chooses <strong>one</strong> identity adapter and says which one, in one place.
 *
 * <h2>Why precedence is explicit</h2>
 * Two adapters exist: the development fixture store ({@code edam.security.dev-users.enabled}) and
 * the database-backed adapter (users, roles, data scopes). Expressing that as two {@code @ConditionalOnMissingBean} beans lets
 * their outcome depend on bean-definition ordering, which is not a contract — it produced a
 * duplicate-definition startup failure and, worse, could have selected the fixture store in a
 * profile that merely left it enabled by accident.
 *
 * <p>The rule here is deliberate:
 * <ol>
 *   <li><strong>Development fixtures win when explicitly enabled.</strong> The property is set only
 *       in the {@code local} profile, and a named demo account must log in as documented even if the
 *       database also holds memberships.</li>
 *   <li><strong>Otherwise the database adapter is used.</strong></li>
 *   <li><strong>If the database has no users</strong>, every login still fails uniformly —
 *       authentication is never bypassed just because an adapter is present.</li>
 * </ol>
 *
 * <p>Swapping in LDAP, OIDC or SAML means adding a bean here and removing nothing else, which is the
 * point of keeping the {@link EdamUserDetailsService} seam.
 */
@Configuration(proxyBeanMethods = false)
public class IdentityConfiguration {

	private static final Logger log = LoggerFactory.getLogger(IdentityConfiguration.class);

	@Bean
	EdamUserDetailsService edamUserDetailsService(Environment environment, UserRepository users,
			RoleRepository roles, UserDataScopeRepository dataScopes, InMemoryEdamUserDetailsService developmentStore,
			org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
		boolean developmentUsersEnabled = environment.getProperty("edam.security.dev-users.enabled", Boolean.class,
				false);
		if (developmentUsersEnabled) {
			log.warn("security.identity_provider=in_memory_fixtures "
					+ "reason=edam.security.dev-users.enabled=true — development credentials are active");
			return developmentStore.userDetailsService(passwordEncoder);
		}
		log.info("security.identity_provider=database_users");
		return new DatabaseEdamUserDetailsService(users, roles, dataScopes);
	}

}
