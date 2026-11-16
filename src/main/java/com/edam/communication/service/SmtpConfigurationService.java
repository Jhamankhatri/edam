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
import com.edam.communication.crypto.MailCredentialCrypto;
import com.edam.communication.dto.SmtpConfigurationRequest;
import com.edam.communication.dto.SmtpConfigurationResponse;
import com.edam.communication.model.SecurityMode;
import com.edam.communication.model.SmtpConfiguration;
import com.edam.communication.model.SmtpTestHistory;
import com.edam.communication.repository.SmtpConfigurationRepository;
import com.edam.communication.repository.SmtpTestHistoryRepository;
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
 *   <li>The plaintext password is provided by the caller; we never decrypt it back for display.
 *       {@link com.edam.communication.dto.SmtpConfigurationResponse#passwordSet()} reports presence.</li>
 *   <li>{@code password == null} on update → keep existing ciphertext (partial edits of host/port
 *       must not clear the credential).</li>
 *   <li>{@code password == ""} → clear the credential (auth disabled).</li>
 *   <li>Otherwise → re-encrypt and bump {@code password_version}.</li>
 * </ul>
 *
 * <p>Deactivation immediately clears the in-memory live session via {@link SmtpConfigurationHolder#clear()},
 * so an inactive configuration cannot continue sending email.
 */
@Service
public class SmtpConfigurationService {

	private static final Logger log = LoggerFactory.getLogger(SmtpConfigurationService.class);

	private final SmtpConfigurationRepository repository;
	private final MailCredentialCrypto crypto;
	private final SmtpConfigurationHolder holder;
	private final SmtpTestHistoryRepository history;
	private final AuditRecorder audit;
	private final Clock clock;

	public SmtpConfigurationService(SmtpConfigurationRepository repository, MailCredentialCrypto crypto,
			SmtpConfigurationHolder holder, SmtpTestHistoryRepository history,
			AuditRecorder audit, Clock clock) {
		this.repository = repository;
		this.crypto = crypto;
		this.holder = holder;
		this.history = history;
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
			SmtpConfiguration prior = existingOpt.get();
			before = diffSnapshot(prior);
			prior.setActive(false);
			repository.save(prior);
			active = new SmtpConfiguration();
		}

		applyRequest(active, req, existingOpt.orElse(null));
		active.setActive(Boolean.TRUE.equals(req.active()));
		if (active.getConnectionTimeoutMs() <= 0) active.setConnectionTimeoutMs(10_000);
		if (active.getReadTimeoutMs() <= 0) active.setReadTimeoutMs(30_000);
		SmtpConfiguration saved = repository.save(active);

		audit.recordChange(isNew ? AuditAction.CREATE : AuditAction.UPDATE,
				"smtp_configuration", saved.getId(), before, diffSnapshot(saved));

		if (saved.isActive()) {
			String plaintext = null;
			if (req.password() != null && !req.password().isEmpty()) {
				plaintext = req.password();
			}
			holder.refresh(saved, plaintext);
		} else {
			holder.clear();
		}
		return toResponse(saved);
	}

	/** Deactivate any currently active SMTP configuration and clear in-memory credentials. */
	@Transactional
	public void deactivate() {
		Optional<SmtpConfiguration> existingOpt = repository.findFirstByActiveTrueOrderByUpdatedAtDesc();
		if (existingOpt.isEmpty()) return;
		SmtpConfiguration active = existingOpt.get();
		Map<String, Object> before = diffSnapshot(active);
		active.setActive(false);
		repository.save(active);
		audit.recordChange(AuditAction.DEACTIVATE, "smtp_configuration", active.getId(), before,
				Map.of("active", false));
		holder.clear();
		log.info("edam.mail.deactivated config_id={}", active.getId());
	}

	@Transactional
	public boolean sendTestEmail(String recipient, SmtpConfigurationRequest candidate) {
		SmtpConfiguration cfg;
		String plaintext;
		UUID configId;
		if (candidate != null) {
			cfg = new SmtpConfiguration();
			applyRequest(cfg, candidate, null);
			plaintext = candidate.password();
			// Unsaved candidate config — associate with the active config if any, otherwise leave
			// history entry with a null config id (we use sentinel-less persistence via direct column).
			configId = repository.findFirstByActiveTrueOrderByUpdatedAtDesc()
					.map(SmtpConfiguration::getId).orElse(null);
		} else {
			SmtpConfiguration active = repository.findFirstByActiveTrueOrderByUpdatedAtDesc()
					.orElseThrow(() -> new BusinessException("edam.mail.not_configured"));
			cfg = active;
			plaintext = null;
			configId = active.getId();
		}
		validateEmail(recipient);
		Instant now = clock.instant();
		String diagnostic = null;
		boolean success = false;
		try {
			jakarta.mail.Session session = (candidate != null)
					? holder.buildTestSession(cfg, plaintext)
					: holder.currentSession();
			MimeMessage msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress(cfg.getFromEmail(), cfg.getFromDisplayName() == null ? "" : cfg.getFromDisplayName()));
			msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
			msg.setSubject("[EDAM] SMTP test email");
			msg.setText("This is a test email from EDAM. If you received this, SMTP is configured correctly.", "UTF-8");
			msg.setSentDate(java.util.Date.from(now));
			jakarta.mail.Transport.send(msg);
			success = true;
			log.info("edam.mail.test_sent recipient={}", recipient);
		} catch (Exception ex) {
			diagnostic = sanitizeDiagnostic(ex);
			log.warn("edam.mail.test_failed recipient={} reason={}", recipient, diagnostic);
		}

		// Persist history row (safe diagnostics only; no secrets).
		SmtpTestHistory h = new SmtpTestHistory();
		h.setSmtpConfigId(configId);
		h.setRecipient(recipient);
		h.setSuccess(success);
		h.setDiagnostic(diagnostic);
		h.setSentAt(now);
		history.save(h);

		// Update last_test_* on the active/saved configuration.
		final String diagFinal = diagnostic;
		final boolean successFinal = success;
		final Instant nowFinal = now;
		if (configId != null) {
			repository.findById(configId).ifPresent(persisted -> {
				persisted.setLastTestAt(nowFinal);
				persisted.setLastTestSuccess(successFinal);
				persisted.setLastTestMessage(successFinal ? "sent" : truncate(diagFinal, 500));
				repository.save(persisted);
			});
		}

		if (!success) {
			throw new BusinessException("edam.mail.send_failed: " + truncate(diagFinal, 200));
		}
		return true;
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
		// Password handling: null = preserve existing, "" = clear, non-empty = rotate.
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

	/** Strip credentials / AUTH tokens from SMTP diagnostics before persistence. */
	private static String sanitizeDiagnostic(Exception ex) {
		String msg = ex.getMessage();
		if (msg == null) msg = ex.getClass().getSimpleName();
		String sanitized = msg.replaceAll("(?i)(AUTH|password|passwd|credential|username)[^\\s]*\\s+\\S+", "$1 ***");
		// Remove anything that looks like a long base64 blob (typical AUTH challenge/response).
		sanitized = sanitized.replaceAll("[A-Za-z0-9+/=]{40,}", "***");
		return truncate(sanitized, 1000);
	}

	private static String truncate(String s, int max) {
		if (s == null) return null;
		return s.length() <= max ? s : s.substring(0, max);
	}
}
