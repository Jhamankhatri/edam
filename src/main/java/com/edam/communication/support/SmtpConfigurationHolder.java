package com.edam.communication.support;

import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;

import com.edam.communication.crypto.MailCredentialCrypto;
import com.edam.communication.model.SecurityMode;
import com.edam.communication.model.SmtpConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the currently-active SMTP settings and produces Jakarta Mail {@link Session} instances.
 * Refresh is triggered by {@link #refresh(SmtpConfiguration, String)} from the service after a
 * successful save/test so that a running installation picks up SMTP changes without a restart.
 *
 * <p>The master key and decrypted password never leave this class: {@code Session} is built from
 * an {@code Authenticator} that closes over the plaintext, and the password is not retained
 * outside the auth callback.
 */
@Component
public class SmtpConfigurationHolder {

	private static final Logger log = LoggerFactory.getLogger(SmtpConfigurationHolder.class);

	private final MailCredentialCrypto crypto;
	private final Clock clock;

	/** Null until the first configuration is activated. */
	private final AtomicReference<LiveConfig> live = new AtomicReference<>();

	public SmtpConfigurationHolder(MailCredentialCrypto crypto, Clock clock) {
		this.crypto = crypto;
		this.clock = clock;
	}

	/** Build a Jakarta Mail {@link Session} from the currently-active config. */
	public Session currentSession() {
		LiveConfig cfg = this.live.get();
		if (cfg == null) {
			throw new IllegalStateException("edam.mail.not_configured: no active SMTP configuration");
		}
		return cfg.session;
	}

	/**
	 * True when an SMTP configuration is active and ready for use.
	 */
	public boolean isConfigured() { return this.live.get() != null; }

	/** Return sender metadata (from/reply-to) for the active config. */
	public SenderEnvelope currentSender() {
		LiveConfig cfg = this.live.get();
		if (cfg == null) {
			throw new IllegalStateException("edam.mail.not_configured: no active SMTP configuration");
		}
		return cfg.envelope;
	}

	/**
	 * Build a disposable Session for testing a candidate configuration (used by the test-email
	 * endpoint before it is saved). Never replaces the live config.
	 */
	public Session buildTestSession(SmtpConfiguration candidate, String plaintextPassword) {
		return buildSession(candidate, plaintextPassword);
	}

	/**
	 * Replace the live config atomically. Decrypts the stored ciphertext once here; the
	 * resulting Session is reused for every send.
	 */
	public void refresh(SmtpConfiguration config, String plaintextPasswordOverride) {
		String plaintext;
		if (plaintextPasswordOverride != null) {
			plaintext = plaintextPasswordOverride;
		} else if (config.getEncryptedPassword() != null && this.crypto.isAvailable()) {
			plaintext = this.crypto.decrypt(config.getEncryptedPassword());
		} else {
			plaintext = null;
		}
		Session session = buildSession(config, plaintext);
		SenderEnvelope envelope = new SenderEnvelope(config.getFromEmail(), config.getFromDisplayName(),
				config.getReplyTo());
		this.live.set(new LiveConfig(session, envelope, clock.instant()));
		log.info("edam.mail.configuration_reloaded host={} port={} security={} user={}",
				config.getHost(), config.getPort(), config.getSecurityMode(),
				config.getUsername() == null ? "<none>" : "***");
	}

	public void clear() { this.live.set(null); }

	private Session buildSession(SmtpConfiguration cfg, String plaintextPassword) {
		Properties p = new Properties();
		p.put("mail.smtp.host", cfg.getHost());
		p.put("mail.smtp.port", String.valueOf(cfg.getPort()));
		p.put("mail.smtp.connectiontimeout", String.valueOf(cfg.getConnectionTimeoutMs()));
		p.put("mail.smtp.timeout", String.valueOf(cfg.getReadTimeoutMs()));
		p.put("mail.smtp.writetimeout", String.valueOf(cfg.getReadTimeoutMs()));
		if (cfg.getSecurityMode() == SecurityMode.SSL_TLS) {
			p.put("mail.smtp.ssl.enable", "true");
			p.put("mail.smtp.socketFactory.port", String.valueOf(cfg.getPort()));
			p.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		} else {
			p.put("mail.smtp.ssl.enable", "false");
		}
		if (cfg.getSecurityMode() == SecurityMode.STARTTLS) {
			p.put("mail.smtp.starttls.enable", "true");
			p.put("mail.smtp.starttls.required", "true");
		} else {
			p.put("mail.smtp.starttls.enable", "false");
			p.put("mail.smtp.starttls.required", "false");
		}
		final String username = cfg.getUsername();
		final String password = plaintextPassword;
		if (username != null && !username.isBlank() && password != null) {
			p.put("mail.smtp.auth", "true");
			return Session.getInstance(p, new jakarta.mail.Authenticator() {
				@Override protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(username, password);
				}
			});
		}
		p.put("mail.smtp.auth", "false");
		return Session.getInstance(p);
	}

	private record LiveConfig(Session session, SenderEnvelope envelope, java.time.Instant refreshedAt) {}

	public record SenderEnvelope(String fromEmail, String fromDisplayName, String replyTo) {
		public String composeFrom() {
			if (fromDisplayName == null || fromDisplayName.isBlank()) return fromEmail;
			// Escape quotes in display name if any
			String safe = fromDisplayName.replace("\"", "'");
			return "\"" + safe + "\" <" + fromEmail + ">";
		}
	}
}
