package com.edam.communication.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.exception.ResourceNotFoundException;
import com.edam.communication.crypto.MailCredentialCrypto;
import com.edam.communication.dto.SmtpConfigurationRequest;
import com.edam.communication.dto.SmtpConfigurationResponse;
import com.edam.communication.model.SecurityMode;
import com.edam.communication.model.SmtpConfiguration;
import com.edam.communication.repository.SmtpConfigurationRepository;
import com.edam.communication.support.SmtpConfigurationHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the single-active SMTP configuration.
 *
 * <p>Credential rules:
 * <ul>
 *   <li>The plaintext password is provided by the caller (admin form); we never decrypt it back
 *       for display. {@link SmtpConfigurationResponse#passwordSet()} reports presence only.</li>
 *   <li>{@code password == null} on update → keep the existing ciphertext (partial edits of
 *       host/port/timeouts must not clear the credential).</li>
 *   <li>{@code password == ""} → clear the credential (auth disabled).</li>
 *   <li>Otherwise → re-encrypt and bump {@code password_version}.</li>
 * </ul>
 *
 * <p>At most one row can be {@code active = true} in the database (Postgres partial unique index).
 * When a new configuration is activated, the previous active row is deactivated within the same
 * transaction.
 */
@Service
public class SmtpConfigurationService {

	private static final Logger log = LoggerFactory.getLogger(SmtpConfigurationService.class);

	private final SmtpConfigurationRepository repository;
	private final MailCredentialCrypto crypto;
	private final SmtpConfigurationHolder holder;
	private final AuditRecorder audit;
	private final Clock clock;

	public SmtpConfigurationService(SmtpConfigurationRepository repository, MailCredentialCrypto crypto,
			SmtpConfigurationHolder holder, AuditRecorder audit, Clock clock) {
		this.repository = repository;
		this.crypto = crypto;
		this.holder = holder;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Optional<SmtpConfigurationResponse> getActive() {
		return repository.findFirstByActiveTrueOrderByUpdatedAtDesc().map(this::toResponse);
	}

	@Transactional
	public SmtpConfigurationResponse save(SmtpConfigurationRequest req) {
		Instant now = clock.instant();
		Optional<SmtpConfiguration> existingOpt = repository.findFirstByActiveTrueOrderByUpdatedAtDesc();
		SmtpConfiguration active;
		Map<String, Object> before;
		boolean isNew = existingOpt.isEmpty();
		if (isNew) {
			active = new SmtpConfiguration();
			before = Map.of();
		} else {
			// Deactivate prior row and create a fresh row, so history is preserved but only one is
			// active (enforced at DB level by partial unique index).
			SmtpConfiguration prior = existingOpt.get();
			before = diffSnapshot(prior);
			prior.setActive(false);
			repository.save(prior);
			active = new SmtpConfiguration();
		}

		applyRequest(active, req, existingOpt.orElse(null));
		active.setActive(Boolean.TRUE.equals(req.active()));
		// Clamp non-null defaults
		if (active.getConnectionTimeoutMs() <= 0) active.setConnectionTimeoutMs(10_000);
		if (active.getReadTimeoutMs() <= 0) active.setReadTimeoutMs(30_000);
		SmtpConfiguration saved = repository.save(active);

		Map<String, Object> after = diffSnapshot(saved);
		audit.recordChange(isNew ? AuditAction.CREATE : AuditAction.UPDATE,
				"smtp_configuration", saved.getId(), before, after);

		// Only reload live SMTP when the new row is marked active
		if (saved.isActive()) {
			String plaintext = null;
			if (req.password() != null && !req.password().isEmpty()) {
				plaintext = req.password();
			}
			holder.refresh(saved, plaintext);
		}
		return toResponse(saved);
	}

	/** Send a test email. If {@code candidate} is supplied it is used (for pre-save preview). */
	@Transactional(readOnly = true)
	public boolean sendTestEmail(String recipient, SmtpConfigurationRequest candidate) {
		SmtpConfiguration cfg;
		String plaintext;
		if (candidate != null) {
			cfg = new SmtpConfiguration();
			applyRequest(cfg, candidate, null);
			plaintext = candidate.password();
		} else {
			cfg = repository.findFirstByActiveTrueOrderByUpdatedAtDesc()
					.orElseThrow(() -> new BusinessException("edam.mail.not_configured"));
			plaintext = null; // holder will decrypt
		}
		validateEmail(recipient);
		try {
			jakarta.mail.Session session = (candidate != null)
					? holder.buildTestSession(cfg, plaintext)
					: holder.currentSession();
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(cfg.getFromEmail(), cfg.getFromDisplayName() == null ? "" : cfg.getFromDisplayName()));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
			msg.setSubject("[EDAM] SMTP test email");
			String text = "This is a test email from EDAM. If you received this, SMTP is configured correctly.";
			msg.setText(text, "UTF-8");
			msg.setSentDate(java.util.Date.from(clock.instant()));
			jakarta.mail.Transport.send(msg);
			log.info("edam.mail.test_sent recipient={}", recipient);
			return true;
		} catch (Exception ex) {
			log.warn("edam.mail.test_failed recipient={} reason={}", recipient, ex.getMessage());
			throw new BusinessException("edam.mail.send_failed: " + safeMessage(ex), ex);
		}
	}

	private void applyRequest(SmtpConfiguration target, SmtpConfigurationRequest req, SmtpConfiguration existing) {
		target.setHost(req.host());
		target.setPort(req.port());
		target.setUsername(emptyToNull(req.username()));
		target.setSecurityMode(SecurityMode.valueOf(req.securityMode() == null ? "STARTTLS" : req.securityMode()));
		target.setFromEmail(req.fromEmail());
		target.setFromDisplayName(emptyToNull(req.fromDisplayName()));
		target.setReplyTo(emptyToNull(req.replyTo()));
		if (req.connectionTimeoutMs() != null) target.setConnectionTimeoutMs(req.connectionTimeoutMs());
		else if (existing != null) target.setConnectionTimeoutMs(existing.getConnectionTimeoutMs());
		if (req.readTimeoutMs() != null) target.setReadTimeoutMs(req.readTimeoutMs());
		else if (existing != null) target.setReadTimeoutMs(existing.getReadTimeoutMs());
		// Password handling: null = preserve existing, "" = clear, non-empty = rotate
		if (req.password() == null && existing != null) {
			target.setEncryptedPassword(existing.getEncryptedPassword());
			target.setPasswordVersion(existing.getPasswordVersion());
		} else if (req.password() == null) {
			target.setEncryptedPassword(null);
			target.setPasswordVersion(0);
		} else if (req.password().isEmpty()) {
			target.setEncryptedPassword(null);
			target.setPasswordVersion(0);
		} else {
			String ciphertext = crypto.encrypt(req.password());
			target.setEncryptedPassword(ciphertext);
			target.setPasswordVersion(existing == null ? 1 : existing.getPasswordVersion() + 1);
		}
	}

	private SmtpConfigurationResponse toResponse(SmtpConfiguration c) {
		return new SmtpConfigurationResponse(
				c.getId(), c.getHost(), c.getPort(), c.getUsername(),
				c.getEncryptedPassword() != null,
				c.getSecurityMode().name(), c.getFromEmail(), c.getFromDisplayName(), c.getReplyTo(),
				c.getConnectionTimeoutMs(), c.getReadTimeoutMs(), c.isActive(),
				c.getLastTestAt(), c.getLastTestSuccess(), c.getLastTestMessage(),
				c.getCreatedAt(), c.getUpdatedAt());
	}

	/** Snapshot for audit (password column is deliberately omitted; AuditValueRedactor also scrubs password-named fields). */
	private Map<String, Object> diffSnapshot(SmtpConfiguration c) {
		return Map.of(
				"host", c.getHost() == null ? "" : c.getHost(),
				"port", c.getPort(),
				"username", c.getUsername() == null ? "" : c.getUsername(),
				"securityMode", c.getSecurityMode().name(),
				"fromEmail", c.getFromEmail() == null ? "" : c.getFromEmail(),
				"active", c.isActive()
		);
	}

	private static String emptyToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }

	private static void validateEmail(String recipient) {
		if (recipient == null || !recipient.contains("@")) {
			throw new BusinessException("edam.mail.invalid_recipient");
		}
	}

	/** Map exceptions to short, safe diagnostics — never echoes the password or server responses verbatim. */
	private static String safeMessage(Exception ex) {
		String msg = ex.getMessage();
		if (msg == null) return ex.getClass().getSimpleName();
		// Strip potential credential echo or AUTH commands
		String sanitized = msg.replaceAll("(?i)(AUTH|password|passwd|credential)[^\\s]*\\s+\\S+", "$1 ***");
		return sanitized.length() > 200 ? sanitized.substring(0, 200) : sanitized;
	}
}
