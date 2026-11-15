package com.edam.config;

import java.util.List;

import com.edam.documents.scan.FailClosedScanner;
import com.edam.documents.scan.MalwareScanner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a fail-closed {@link MalwareScanner} when the deployment has not wired one.
 * Without this, an environment with no scanner would fail to start because
 * {@code DocumentService} injects {@code MalwareScanner}; with it, the app starts and
 * uploads remain quarantined until a real scanner is wired.
 */
@Configuration
public class ScanAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(MalwareScanner.class)
	public MalwareScanner failClosedScanner() {
		return new FailClosedScanner();
	}
}
