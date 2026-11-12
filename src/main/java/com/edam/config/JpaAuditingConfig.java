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
 * <p>The auditor is the authenticated employee's stable identifier, never a display name
 * (which can change) and never a session ID. Background jobs use the reserved
 * {@value #SYSTEM_AUDITOR} name so that automated changes are distinguishable from human
 * ones in an audit review.
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
				return Optional.ofNullable(principal.employeeId());
			}
			return Optional.of(SYSTEM_AUDITOR);
		};
	}

}
