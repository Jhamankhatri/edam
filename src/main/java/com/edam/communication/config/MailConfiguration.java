package com.edam.communication.config;

import com.edam.communication.crypto.MailCredentialCrypto;
import com.edam.communication.repository.SmtpConfigurationRepository;
import com.edam.communication.support.SmtpConfigurationHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads the active SMTP configuration into the live holder at startup so that the outbox
 * can begin delivering immediately after boot without waiting for an admin to touch the UI.
 */
@Configuration
public class MailConfiguration {

	private static final Logger log = LoggerFactory.getLogger(MailConfiguration.class);

	@Bean
	CommandLineRunner loadActiveSmtpOnBoot(SmtpConfigurationRepository repo, SmtpConfigurationHolder holder,
			MailCredentialCrypto crypto) {
		return args -> repo.findFirstByActiveTrueOrderByUpdatedAtDesc().ifPresentOrElse(
				cfg -> {
					try {
						holder.refresh(cfg, null);
					} catch (Exception ex) {
						log.warn("edam.mail.startup_reload_failed reason={}", ex.getMessage());
					}
				},
				() -> log.info("edam.mail.no_active_configuration_at_boot"));
	}
}
