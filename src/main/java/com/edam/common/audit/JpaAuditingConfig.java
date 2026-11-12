package com.edam.common.audit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing independently from the main application class
 * so web-slice tests can disable JPA auditing cleanly without requiring a JPA metamodel.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "edam.jpa.auditing.enabled", havingValue = "true", matchIfMissing = true)
@EnableJpaAuditing(auditorAwareRef = "springSecurityAuditorAware")
public class JpaAuditingConfig {
}
