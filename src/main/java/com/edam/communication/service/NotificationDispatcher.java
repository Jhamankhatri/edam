package com.edam.communication.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.communication.model.CommunicationEvent;
import com.edam.communication.model.EmailTemplate;
import com.edam.communication.model.EmailTemplateVersion;
import com.edam.communication.model.NotificationEventConfig;
import com.edam.communication.model.NotificationOutbox;
import com.edam.communication.model.OutboxStatus;
import com.edam.communication.repository.EmailTemplateRepository;
import com.edam.communication.repository.EmailTemplateVersionRepository;
import com.edam.communication.repository.NotificationEventConfigRepository;
import com.edam.communication.repository.NotificationOutboxRepository;
import com.edam.communication.support.SmtpConfigurationHolder;
import com.edam.communication.support.TemplateRenderer;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a {@link CommunicationEvent} by looking up routing config, rendering the selected
 * template (when email is enabled), and enqueuing rows into {@code notification_outbox}.
 *
 * <p>Must be called inside the same transaction as the business operation: outbox rows commit
 * together with the request/approval state, so a crash can never lose a notification. Actual
 * mail/notification handoff is asynchronous via {@link OutboxProcessor}.
 */
@Service
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	private final NotificationEventConfigRepository eventConfigs;
	private final EmailTemplateRepository templates;
	private final EmailTemplateVersionRepository versions;
	private final NotificationOutboxRepository outbox;
	private final UserRepository users;
	private final TemplateRenderer renderer;
	private final SmtpConfigurationHolder smtp;
	private final Clock clock;

	public NotificationDispatcher(NotificationEventConfigRepository eventConfigs,
			EmailTemplateRepository templates, EmailTemplateVersionRepository versions,
			NotificationOutboxRepository outbox,
			UserRepository users, TemplateRenderer renderer, SmtpConfigurationHolder smtp, Clock clock) {
		this.eventConfigs = eventConfigs;
		this.templates = templates;
		this.versions = versions;
		this.outbox = outbox;
		this.users = users;
		this.renderer = renderer;
		this.smtp = smtp;
		this.clock = clock;
	}

	/** Dispatch a single event to one user. */
	@Transactional
	public void dispatch(CommunicationEvent event, UUID recipientUserId, Map<String, String> placeholders,
			String inAppTitle, String inAppBody, String inAppLink, UUID refRequestId) {
		NotificationEventConfig cfg = eventConfigs.findByEventCode(event.name()).orElse(null);
		if (cfg == null || !cfg.isActive()) return;
		User recipient = users.findById(recipientUserId).orElse(null);
		if (recipient == null || !recipient.isActive()) return;

		Instant now = clock.instant();
		// Mandatory events ignore opt-out; this is enforced server-side, never by the client.
		boolean emailAllowed = cfg.isEmailEnabled() && smtp.isConfigured()
				&& recipient.getEmail() != null && !recipient.getEmail().isBlank();
		boolean inAppAllowed = cfg.isInAppEnabled();

		if (emailAllowed) enqueueEmail(event, cfg, recipient, placeholders, refRequestId, now);
		if (inAppAllowed) enqueueInApp(event, recipient, inAppTitle, inAppBody, inAppLink, refRequestId, now);
	}

	private void enqueueEmail(CommunicationEvent event, NotificationEventConfig cfg, User recipient,
			Map<String, String> placeholders, UUID refRequestId, Instant now) {
		if (cfg.getEmailTemplateId() == null) return;
		EmailTemplate t = templates.findById(cfg.getEmailTemplateId()).orElse(null);
		if (t == null || !t.isActive()) return;
		EmailTemplateVersion v = versions.findFirstByTemplateIdOrderByVersionNumberDesc(t.getId()).orElse(null);
		if (v == null) return;
		Set<String> allowed = renderer.extractPlaceholders(v.getHtmlBody());
		allowed.addAll(renderer.extractPlaceholders(v.getSubject()));
		if (v.getTextBody() != null) allowed.addAll(renderer.extractPlaceholders(v.getTextBody()));
		String subject = renderer.renderText(v.getSubject(), placeholders, allowed);
		String html = renderer.renderHtml(v.getHtmlBody(), placeholders, allowed);
		String text = v.getTextBody() == null ? "" : renderer.renderText(v.getTextBody(), placeholders, allowed);

		NotificationOutbox row = new NotificationOutbox();
		row.setIdempotencyKey(idempotency(event, recipient.getId(), "EMAIL", refRequestId));
		row.setEventCode(event.name());
		row.setChannel(NotificationOutbox.Channel.EMAIL);
		row.setUserId(recipient.getId());
		row.setRecipientEmail(recipient.getEmail());
		row.setTemplateVersionId(v.getId());
		row.setSubject(subject);
		row.setBodyHtml(html);
		row.setBodyText(text);
		row.setStatus(OutboxStatus.PENDING);
		row.setAttempts(0);
		row.setMaxAttempts(5);
		row.setNextAttemptAt(now);
		outbox.save(row);
	}

	private void enqueueInApp(CommunicationEvent event, User recipient, String title, String body,
			String link, UUID refRequestId, Instant now) {
		NotificationOutbox row = new NotificationOutbox();
		row.setIdempotencyKey(idempotency(event, recipient.getId(), "IN_APP", refRequestId));
		row.setEventCode(event.name());
		row.setChannel(NotificationOutbox.Channel.IN_APP);
		row.setUserId(recipient.getId());
		row.setSubject(title);
		row.setBodyText(body);
		row.setPayloadJson(link == null ? null : link);
		row.setStatus(OutboxStatus.PENDING);
		row.setAttempts(0);
		row.setMaxAttempts(3);
		row.setNextAttemptAt(now);
		outbox.save(row);
	}

	/** Stable idempotency key: repeated dispatches for the same (event, user, channel, request) are deduplicated. */
	private static String idempotency(CommunicationEvent event, UUID userId, String channel, UUID refRequestId) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(event.name().getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			md.update(userId.toString().getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			md.update(channel.getBytes(StandardCharsets.UTF_8));
			md.update((byte) '|');
			md.update((refRequestId == null ? "" : refRequestId.toString()).getBytes(StandardCharsets.UTF_8));
			return event.name() + ":" + HexFormat.of().formatHex(md.digest()).substring(0, 40);
		} catch (Exception ex) {
			throw new IllegalStateException("edam.idempotency.digest_failed", ex);
		}
	}
}
