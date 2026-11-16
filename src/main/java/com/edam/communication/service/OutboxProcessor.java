package com.edam.communication.service;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import com.edam.communication.model.Notification;
import com.edam.communication.model.NotificationOutbox;
import com.edam.communication.model.OutboxStatus;
import com.edam.communication.repository.NotificationOutboxRepository;
import com.edam.communication.repository.NotificationRepository;
import com.edam.communication.support.SmtpConfigurationHolder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the transactional outbox and delivers messages.
 *
 * <h2>Delivery guarantees</h2>
 * <ul>
 *   <li>Claim uses {@code SELECT … FOR UPDATE SKIP LOCKED} so multiple pods do not double-deliver.</li>
 *   <li>Stale locks (worker crashed mid-send) are reaped after {@code lock_ttl}.</li>
 *   <li>Exponential backoff with jitter on transient failures.</li>
 *   <li>Permanent SMTP/auth failures transition directly to {@code FAILED} with no further retries.</li>
 *   <li>Outbox rows are retained for diagnostic inspection; {@code max_attempts} transitions the row
 *       to a terminal state so it stops being retried.</li>
 *   <li>Notification inbox rows are written inside the same transaction as the outbox state flip
 *       to SENT, so "enqueued but not delivered" is impossible for IN_APP.</li>
 * </ul>
 *
 * <p>Email delivery failures are logged and counted but never re-thrown to the caller's
 * transaction — a mail-server outage must never roll back an already-committed expense approval.
 */
@Service
public class OutboxProcessor {

	private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

	private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(30);
	private static final Duration MAX_BACKOFF = Duration.ofHours(2);
	private static final Duration LOCK_TTL = Duration.ofMinutes(5);
	private static final int BATCH_SIZE = 25;

	private final NotificationOutboxRepository outbox;
	private final NotificationRepository notifications;
	private final SmtpConfigurationHolder smtpHolder;
	private final Clock clock;
	private final boolean enabled;
	private String workerId;

	public OutboxProcessor(NotificationOutboxRepository outbox, NotificationRepository notifications,
			SmtpConfigurationHolder smtpHolder, Clock clock,
			@Value("${edam.mail.outbox.enabled:true}") boolean enabled) {
		this.outbox = outbox;
		this.notifications = notifications;
		this.smtpHolder = smtpHolder;
		this.clock = clock;
		this.enabled = enabled;
	}

	@PostConstruct
	void initWorkerId() {
		try {
			this.workerId = InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 6);
		} catch (Exception ex) {
			this.workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);
		}
	}

	/** Every 15 seconds, sweep a batch of pending rows. */
	@Scheduled(fixedDelayString = "${edam.mail.outbox.interval-ms:15000}", initialDelayString = "${edam.mail.outbox.initial-delay-ms:10000}")
	@Transactional
	public void sweep() {
		if (!enabled) return;
		Instant now = clock.instant();
		Instant stale = now.minus(LOCK_TTL);
		List<NotificationOutbox> batch = outbox.claimBatch(now, stale, PageRequest.of(0, BATCH_SIZE));
		for (NotificationOutbox row : batch) {
			try {
				deliver(row);
			} catch (Exception ex) {
				// A single failed message must not abort the sweep.
				log.warn("edam.outbox.delivery_failed id={} channel={} attempt={} reason={}",
						row.getId(), row.getChannel(), row.getAttempts() + 1, safe(ex));
				markFailed(row, ex, now);
			}
		}
	}

	void deliver(NotificationOutbox row) throws Exception {
		Instant now = clock.instant();
		outbox.claim(row.getId(), workerId, OutboxStatus.PROCESSING, now);
		row.setStatus(OutboxStatus.PROCESSING);
		row.setLockedBy(workerId);
		row.setLockedAt(now);
		row.setAttempts(row.getAttempts() + 1);
		row.setLastAttemptAt(now);
		if (row.getChannel() == NotificationOutbox.Channel.EMAIL) {
			deliverEmail(row);
		} else {
			deliverInApp(row);
		}
		row.setStatus(OutboxStatus.SENT);
		row.setSentAt(now);
		row.setLockedBy(null);
		row.setLockedAt(null);
		row.setNextAttemptAt(null);
		outbox.save(row);
	}

	private void deliverEmail(NotificationOutbox row) throws Exception {
		if (!smtpHolder.isConfigured()) {
			throw new TransientMailException("edam.mail.not_configured");
		}
		jakarta.mail.Session session = smtpHolder.currentSession();
		SmtpConfigurationHolder.SenderEnvelope sender = smtpHolder.currentSender();
		MimeMessage msg = new MimeMessage(session);
		msg.setFrom(new InternetAddress(sender.fromEmail(),
				sender.fromDisplayName() == null ? "" : sender.fromDisplayName(), java.nio.charset.StandardCharsets.UTF_8.name()));
		msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(row.getRecipientEmail()));
		msg.setSubject(row.getSubject(), "UTF-8");

		boolean hasHtml = row.getBodyHtml() != null && !row.getBodyHtml().isBlank();
		boolean hasText = row.getBodyText() != null && !row.getBodyText().isBlank();

		if (hasHtml && hasText) {
			MimeMultipart alt = new MimeMultipart("alternative");
			MimeBodyPart textPart = new MimeBodyPart();
			textPart.setText(row.getBodyText(), "UTF-8", "plain");
			alt.addBodyPart(textPart);
			MimeBodyPart htmlPart = new MimeBodyPart();
			htmlPart.setContent(row.getBodyHtml(), "text/html; charset=UTF-8");
			alt.addBodyPart(htmlPart);
			msg.setContent(alt);
		} else if (hasHtml) {
			msg.setContent(row.getBodyHtml(), "text/html; charset=UTF-8");
		} else {
			msg.setText(row.getBodyText() == null ? "" : row.getBodyText(), "UTF-8");
		}
		msg.setSentDate(java.util.Date.from(clock.instant()));
		jakarta.mail.Transport.send(msg);
	}

	private void deliverInApp(NotificationOutbox row) {
		Notification n = new Notification();
		n.setUserId(row.getUserId());
		n.setEventCode(row.getEventCode() == null ? "" : row.getEventCode());
		n.setTitle(row.getSubject() == null ? "" : row.getSubject());
		n.setBody(row.getBodyText() == null ? "" : row.getBodyText());
		n.setPayloadJson(row.getPayloadJson());
		n.setRead(false);
		notifications.save(n);
	}

	private void markFailed(NotificationOutbox row, Exception ex, Instant now) {
		boolean permanent = isPermanentFailure(ex);
		row.setLastError(safe(ex).substring(0, Math.min(500, safe(ex).length())));
		row.setLockedBy(null);
		row.setLockedAt(null);
		if (permanent || row.getAttempts() >= row.getMaxAttempts()) {
			row.setStatus(OutboxStatus.FAILED);
			row.setNextAttemptAt(null);
		} else {
			// Exponential backoff: 30s * 2^attempts, plus small jitter
			long seconds = Math.min(MAX_BACKOFF.getSeconds(), INITIAL_BACKOFF.getSeconds() * (1L << (row.getAttempts() - 1)));
			double jitter = 0.8 + Math.random() * 0.4;
			row.setNextAttemptAt(now.plusSeconds((long)(seconds * jitter)));
			row.setStatus(OutboxStatus.RETRY_PENDING);
		}
		outbox.save(row);
	}

	/** Classify SMTP errors. Auth/config errors are permanent; network/connect errors are transient. */
	private static boolean isPermanentFailure(Exception ex) {
		if (ex instanceof PermanentMailException) return true;
		if (ex instanceof TransientMailException) return false;
		String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
		if (msg.contains("authenticationfailed") || msg.contains("auth failed")
				|| msg.contains("535") || msg.contains("550") || msg.contains("invalid address")
				|| msg.contains("unknown host") || msg.contains("illegal address")) {
			return true;
		}
		return false;
	}

	private static String safe(Exception ex) {
		String m = ex.getMessage();
		return m == null ? ex.getClass().getSimpleName() : m;
	}

	/** Internal markers for failure classification. */
	public static class TransientMailException extends Exception {
		public TransientMailException(String msg) { super(msg); }
	}
	public static class PermanentMailException extends Exception {
		public PermanentMailException(String msg) { super(msg); }
	}
}
