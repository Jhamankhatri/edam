package com.edam.communication.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.edam.communication.model.CommunicationEvent;
import com.edam.communication.model.EmailTemplate;
import com.edam.communication.model.EmailTemplateVersion;
import com.edam.communication.model.NotificationEventConfig;
import com.edam.communication.model.NotificationOutbox;
import com.edam.communication.model.OutboxStatus;
import com.edam.communication.model.RecipientGroup;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a {@link CommunicationEvent} by looking up routing config, rendering the selected
 * template, resolving recipients from the configured {@link RecipientGroup}s, and enqueuing
 * rows into {@code comm_notification_outbox}.
 *
 * <h2>Transaction semantics</h2>
 * <ul>
 *   <li><b>Enqueue runs in the caller's transaction.</b> This is the canonical transactional-outbox
 *       pattern: the outbox row and the business state change commit atomically, so a crash after
 *       commit never loses a notification and a rolled-back business operation never leaves an
 *       orphan notification.</li>
 *   <li><b>Duplicate idempotency keys are safe no-ops.</b> The unique index on
 *       {@code idempotency_key} is the final concurrency safeguard; we catch the resulting
 *       {@link DataIntegrityViolationException} per row and log it rather than failing the
 *       business operation.</li>
 *   <li><b>Other DB errors propagate.</b> They will (correctly) roll back the business transaction
 *       rather than committing business state without its matching outbox row.</li>
 *   <li><b>SMTP/network failures do not touch this path.</b> Actual mail delivery is performed
 *       asynchronously in {@link OutboxProcessor} with its own per-row transaction boundary.</li>
 * </ul>
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
	private final NotificationRecipientResolver resolver;
	private final NotificationPreferenceService prefService;
	private final Clock clock;

	public NotificationDispatcher(NotificationEventConfigRepository eventConfigs,
			EmailTemplateRepository templates, EmailTemplateVersionRepository versions,
			NotificationOutboxRepository outbox,
			UserRepository users, TemplateRenderer renderer, SmtpConfigurationHolder smtp,
			NotificationRecipientResolver resolver, NotificationPreferenceService prefService,
			Clock clock) {
		this.eventConfigs = eventConfigs;
		this.templates = templates;
		this.versions = versions;
		this.outbox = outbox;
		this.users = users;
		this.renderer = renderer;
		this.smtp = smtp;
		this.resolver = resolver;
		this.prefService = prefService;
		this.clock = clock;
	}

	/**
	 * Dispatch an event from inside the caller's business transaction. Outbox rows are written
	 * on the same transaction; the caller does not need an explicit {@code @Transactional} for
	 * correctness but SHOULD be transactional so the business state and outbox commit together.
	 *
	 * @param event        closed enum identifying the event
	 * @param placeholders per-call placeholder values rendered into the active email template
	 * @param inAppTitle   in-app notification title
	 * @param inAppBody    in-app notification body
	 * @param inAppLink    in-app deep link (stored as plain text, not interpreted)
	 * @param refRequestId expense request id for recipient resolution and idempotency grouping
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void dispatch(CommunicationEvent event, Map<String, String> placeholders,
			String inAppTitle, String inAppBody, String inAppLink, UUID refRequestId) {
		dispatch(event, placeholders, inAppTitle, inAppBody, inAppLink, refRequestId, null);
	}

	/**
	 * Dispatch directly to a single, already-resolved user, ignoring the configured
	 * recipient_groups. Used by forward (where the delegate is decided by the caller at action
	 * time) and by tests. Note that mandatory-event and per-user preference rules still apply.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void dispatchToUser(CommunicationEvent event, UUID userId, Map<String, String> placeholders,
			String inAppTitle, String inAppBody, String inAppLink, UUID refRequestId) {
		NotificationEventConfig cfg = eventConfigs.findByEventCode(event.name()).orElse(null);
		if (cfg == null || !cfg.isActive()) return;
		User recipient = users.findById(userId).orElse(null);
		if (recipient == null || !recipient.isActive()) return;
		Instant now = clock.instant();
		EmailTemplateVersion emailVersion = null;
		String subject = null, html = null, text = null;
		boolean emailReady = cfg.isEmailEnabled() && smtp.isConfigured() && cfg.getEmailTemplateId() != null;
		if (emailReady) {
			EmailTemplate t = templates.findById(cfg.getEmailTemplateId()).orElse(null);
			if (t != null && t.isActive()) {
				emailVersion = versions.findFirstByTemplateIdOrderByVersionNumberDesc(t.getId()).orElse(null);
				if (emailVersion != null) {
					Set<String> allowed = new LinkedHashSet<>();
					allowed.addAll(renderer.extractPlaceholders(emailVersion.getHtmlBody()));
					allowed.addAll(renderer.extractPlaceholders(emailVersion.getSubject()));
					if (emailVersion.getTextBody() != null) allowed.addAll(renderer.extractPlaceholders(emailVersion.getTextBody()));
					Map<String, String> vars = placeholders == null ? Map.of() : placeholders;
					subject = renderer.renderText(emailVersion.getSubject(), vars, allowed);
					html = renderer.renderHtml(emailVersion.getHtmlBody(), vars, allowed);
					text = emailVersion.getTextBody() == null ? "" : renderer.renderText(emailVersion.getTextBody(), vars, allowed);
				}
			}
		}
		boolean emailForUser = emailReady && emailVersion != null && prefService.emailEnabled(userId, event)
				&& recipient.getEmail() != null && !recipient.getEmail().isBlank();
		boolean inAppForUser = cfg.isInAppEnabled() && prefService.inAppEnabled(userId, event);
		if (emailForUser || inAppForUser) {
			enqueueForRecipient(event, recipient, emailVersion, subject, html, text,
					inAppTitle, inAppBody, inAppLink, refRequestId, now, inAppForUser, emailForUser);
		}
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void dispatch(CommunicationEvent event, Map<String, String> placeholders,
			String inAppTitle, String inAppBody, String inAppLink, UUID refRequestId,
			UUID overrideRecipient) {
		NotificationEventConfig cfg = eventConfigs.findByEventCode(event.name()).orElse(null);
		if (cfg == null || !cfg.isActive()) {
			log.debug("notification.dispatch_skipped event={} reason=not_configured", event);
			return;
		}

		Set<RecipientGroup> groups = parseGroups(cfg.getRecipientGroups());
		Set<UUID> recipientIds = resolver.resolve(groups, refRequestId, overrideRecipient);
		if (recipientIds.isEmpty()) {
			log.debug("notification.dispatch_skipped event={} reason=no_recipients", event);
			return;
		}

		Instant now = clock.instant();
		List<User> recipients = users.findAllById(recipientIds);

		// Resolve template once (placeholders are user-independent for the current event set).
		EmailTemplateVersion emailVersion = null;
		String subject = null;
		String html = null;
		String text = null;
		boolean emailReady = cfg.isEmailEnabled() && smtp.isConfigured() && cfg.getEmailTemplateId() != null;
		if (emailReady) {
			EmailTemplate t = templates.findById(cfg.getEmailTemplateId()).orElse(null);
			if (t != null && t.isActive()) {
				emailVersion = versions.findFirstByTemplateIdOrderByVersionNumberDesc(t.getId()).orElse(null);
				if (emailVersion != null) {
					Set<String> allowed = new LinkedHashSet<>();
					allowed.addAll(renderer.extractPlaceholders(emailVersion.getHtmlBody()));
					allowed.addAll(renderer.extractPlaceholders(emailVersion.getSubject()));
					if (emailVersion.getTextBody() != null) allowed.addAll(renderer.extractPlaceholders(emailVersion.getTextBody()));
					Map<String, String> vars = placeholders == null ? Map.of() : placeholders;
					subject = renderer.renderText(emailVersion.getSubject(), vars, allowed);
					html = renderer.renderHtml(emailVersion.getHtmlBody(), vars, allowed);
					text = emailVersion.getTextBody() == null ? "" : renderer.renderText(emailVersion.getTextBody(), vars, allowed);
				}
			}
		}
		final boolean hasEmail = emailReady && emailVersion != null;
		final boolean hasInApp = cfg.isInAppEnabled();

		for (User recipient : recipients) {
			if (recipient == null || !recipient.isActive()) continue;
			boolean emailForUser = hasEmail && prefService.emailEnabled(recipient.getId(), event)
					&& recipient.getEmail() != null && !recipient.getEmail().isBlank();
			boolean inAppForUser = hasInApp && prefService.inAppEnabled(recipient.getId(), event);
			if (!emailForUser && !inAppForUser) continue;
			enqueueForRecipient(event, recipient, emailVersion, subject, html, text,
					inAppTitle, inAppBody, inAppLink, refRequestId, now, inAppForUser, emailForUser);
		}
	}

	private void enqueueForRecipient(CommunicationEvent event, User recipient,
			EmailTemplateVersion emailVersion, String subject, String html, String text,
			String inAppTitle, String inAppBody, String inAppLink, UUID refRequestId, Instant now,
			boolean inAppAllowed, boolean emailAllowed) {
		if (emailAllowed && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
			saveRow(buildEmail(event, recipient, emailVersion, subject, html, text, refRequestId, now));
		}
		if (inAppAllowed) {
			saveRow(buildInApp(event, recipient, inAppTitle, inAppBody, inAppLink, refRequestId, now));
		}
	}

	private NotificationOutbox buildEmail(CommunicationEvent event, User recipient,
			EmailTemplateVersion v, String subject, String html, String text,
			UUID refRequestId, Instant now) {
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
		return row;
	}

	private NotificationOutbox buildInApp(CommunicationEvent event, User recipient,
			String title, String body, String link, UUID refRequestId, Instant now) {
		NotificationOutbox row = new NotificationOutbox();
		row.setIdempotencyKey(idempotency(event, recipient.getId(), "IN_APP", refRequestId));
		row.setEventCode(event.name());
		row.setChannel(NotificationOutbox.Channel.IN_APP);
		row.setUserId(recipient.getId());
		row.setSubject(title);
		row.setBodyText(body);
		row.setPayloadJson(link);
		row.setStatus(OutboxStatus.PENDING);
		row.setAttempts(0);
		row.setMaxAttempts(3);
		row.setNextAttemptAt(now);
		return row;
	}

	private void saveRow(NotificationOutbox row) {
		try {
			outbox.save(row);
		} catch (DataIntegrityViolationException dup) {
			// Duplicate idempotency key from a retry/concurrent dispatch — safe no-op.
			log.info("notification.duplicate event={} user={} channel={} key={}",
					row.getEventCode(), row.getUserId(), row.getChannel(), row.getIdempotencyKey());
		}
	}

	private Set<RecipientGroup> parseGroups(String json) {
		if (json == null || json.isBlank()) return Set.of();
		try {
			var om = new tools.jackson.databind.ObjectMapper();
			List<String> names = om.readValue(json, new tools.jackson.core.type.TypeReference<List<String>>() {});
			Set<RecipientGroup> out = new LinkedHashSet<>();
			for (String n : names) {
				try { out.add(RecipientGroup.valueOf(n)); }
				catch (IllegalArgumentException ex) {
					log.warn("notification.unknown_group code={}", n);
				}
			}
			return out;
		} catch (Exception ex) {
			log.warn("notification.bad_groups_json reason={}", ex.getMessage());
			return Set.of();
		}
	}

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
