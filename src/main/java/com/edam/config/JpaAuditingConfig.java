package com.edam.config;

import java.util.Optional;

import com.edam.common.security.EdamPrincipal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Enables JPA auditing and names the current actor for {@code created_by} and
 * {@code updated_by}.
 *
 * <p>The auditor is the authenticated employee's stable identifier — the bank's employee code where it
 * supplies one, otherwise the login name — never a display name (which can change) and never a session
 * id. Background jobs use the reserved {@value #SYSTEM_AUDITOR} name so that automated changes are
 * distinguishable from human ones in an audit review.
 *
 * <p>The column is 100 characters and nullable in the schema, because a system-initiated change during
 * deployment legitimately has no actor row to point at.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
public class JpaAuditingConfig {

	static final String SYSTEM_AUDITOR = "system";

	@Bean
	AuditorAware<String> springSecurityAuditorAware() {
		return () -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.isAuthenticated()
					&& authentication.getPrincipal() instanceof EdamPrincipal principal) {
				// The employee code is the bank's own stable identifier; fall back to the login name
				// when the bank does not supply one, so the column is never blank for a real actor.
				return Optional.ofNullable(principal.employeeCode() != null ? principal.employeeCode()
						: principal.getUsername());
			}
			return Optional.of(SYSTEM_AUDITOR);
		};
	}

}
