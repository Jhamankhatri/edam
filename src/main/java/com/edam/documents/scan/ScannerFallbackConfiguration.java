package com.edam.documents.scan;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Installs a fail-closed {@link MalwareScanner} when no real scanner (or test fake) is in
 * the context. Registered via an explicit {@code @Bean} method with
 * {@code @ConditionalOnMissingBean} so that {@code @Primary} test fakes reliably take
 * precedence — component-scanned {@code @Component} candidates with the same condition
 * are evaluated in a separate phase and can conflict with test-local beans.
 */
@Configuration
public class ScannerFallbackConfiguration {

	@Bean
	@ConditionalOnMissingBean(MalwareScanner.class)
	public MalwareScanner failClosedScanner() {
		return new FailClosedScanner();
	}
}
