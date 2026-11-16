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
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the transactional outbox and delivers messages.
 *
 * <h2>Transaction model</h2>
 * <ol>
 *   <li>{@link #sweep()} is NOT transactional. It calls {@link #claimBatch} in a short
 *       transaction that atomically selects due rows via {@code FOR UPDATE SKIP LOCKED} and
 *       flips them to PROCESSING, releasing the connection immediately.</li>
 *   <li>Each row is then delivered inside its own {@code REQUIRES_NEW} transaction
 *       ({@link #processRow}), so one failure can never roll back a sibling row's SENT/FAILED
 *       transition.</li>
 *   <li>SMTP/network failures are caught per row and converted to FAILED/RETRY_PENDING with
 *       exponential backoff. They never propagate.</li>
 * </ol>
 *
 * <p>Outbox rows are inserted in the same transaction as the business state change by
 * {@link NotificationDispatcher}; a rollback therefore never produces an orphan notification,
 * and a commit always has matching outbox rows. Actual mail delivery is asynchronous here,
 * so mail-server outages cannot affect business commits.
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
	/** Self-proxy so that {@code @Transactional(REQUIRES_NEW)} actually applies when sweep()
	 *  invokes its own per-row methods (Spring AOP does not intercept {@code this} calls). */
	private final OutboxProcessor self;
	private String workerId;

	public OutboxProcessor(NotificationOutboxRepository outbox, NotificationRepository notifications,
			SmtpConfigurationHolder smtpHolder, Clock clock,
			@Lazy OutboxProcessor self,
			@Value("${edam.mail.outbox.enabled:true}") boolean enabled) {
		this.outbox = outbox;
		this.notifications = notifications;
		this.smtpHolder = smtpHolder;
		this.clock = clock;
		this.self = self;
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

	@Scheduled(fixedDelayString = "${edam.mail.outbox.interval-ms:15000}",
			initialDelayString = "${edam.mail.outbox.initial-delay-ms:10000}")
	public void sweep() {
		if (!enabled) return;
		Instant now = clock.instant();
		List<NotificationOutbox> batch;
		try {
			// Call via the self-proxy so REQUIRES_NEW actually starts a new transaction.
			batch = self.claimBatch(now);
		} catch (Exception ex) {
			log.warn("edam.outbox.claim_failed reason={}", ex.getMessage());
			return;
		}
		for (NotificationOutbox row : batch) {
			try {
				// Per-row REQUIRES_NEW transaction via the proxy; one failure cannot roll back siblings.
				self.processRow(row.getId());
			} catch (Exception ex) {
				log.warn("edam.outbox.process_failed id={} reason={}", row.getId(), ex.getMessage());
			}
		}
	}

	/** Atomically select a batch of due rows and mark them claimed. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<NotificationOutbox> claimBatch(Instant now) {
		Instant stale = now.minus(LOCK_TTL);
		List<NotificationOutbox> rows = outbox.claimBatch(now, stale, PageRequest.of(0, BATCH_SIZE));
		for (NotificationOutbox r : rows) {
			r.setStatus(OutboxStatus.PROCESSING);
			r.setLockedBy(workerId);
			r.setLockedAt(now);
			r.setLastAttemptAt(now);
			r.setAttempts(r.getAttempts() + 1);
		}
		return outbox.saveAll(rows);
	}

	/** Deliver one claimed row in its own transaction. All exceptions are caught by sweep(). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processRow(UUID id) {
		NotificationOutbox row = outbox.findById(id).orElse(null);
		if (row == null) return;
		if (row.getStatus() != OutboxStatus.PROCESSING) return;
		// Double-check lock ownership.
		if (!workerId.equals(row.getLockedBy())) return;

		Instant now = clock.instant();
		try {
			if (row.getChannel() == NotificationOutbox.Channel.EMAIL) {
				deliverEmail(row);
			} else {
				deliverInApp(row);
			}
			outbox.markSent(id, workerId, OutboxStatus.SENT, now);
		} catch (Exception ex) {
			boolean permanent = isPermanentFailure(ex);
			String safeMsg = safe(ex);
			Instant next;
			OutboxStatus newStatus;
			if (permanent || row.getAttempts() >= row.getMaxAttempts()) {
				newStatus = OutboxStatus.FAILED;
				next = null;
				log.warn("edam.outbox.failed id={} channel={} attempt={} permanent={} reason={}",
						id, row.getChannel(), row.getAttempts(), permanent, safeMsg);
			} else {
				newStatus = OutboxStatus.RETRY_PENDING;
				long seconds = Math.min(MAX_BACKOFF.getSeconds(),
						INITIAL_BACKOFF.getSeconds() * (1L << Math.max(0, row.getAttempts() - 1)));
				double jitter = 0.8 + Math.random() * 0.4;
				next = now.plusSeconds((long) (seconds * jitter));
			}
			outbox.markFailed(id, workerId, newStatus,
					safeMsg.substring(0, Math.min(500, safeMsg.length())), next);
		}
	}

	private void deliverEmail(NotificationOutbox row) throws Exception {
		if (!smtpHolder.isConfigured()) {
			throw new TransientMailException("edam.mail.not_configured");
		}
		jakarta.mail.Session session = smtpHolder.currentSession();
		SmtpConfigurationHolder.SenderEnvelope sender = smtpHolder.currentSender();
		MimeMessage msg = new MimeMessage(session);
		msg.setFrom(new InternetAddress(sender.fromEmail(),
				sender.fromDisplayName() == null ? "" : sender.fromDisplayName(),
				java.nio.charset.StandardCharsets.UTF_8.name()));
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

	private static boolean isPermanentFailure(Exception ex) {
		if (ex instanceof PermanentMailException) return true;
		if (ex instanceof TransientMailException) return false;
		String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
		return msg.contains("authenticationfailed")
				|| msg.contains("535") || msg.contains("550 5")
				|| msg.contains("invalid address")
				|| msg.contains("unknown host")
				|| msg.contains("illegal address");
	}

	private static String safe(Exception ex) {
		String m = ex.getMessage();
		if (m == null) return ex.getClass().getSimpleName();
		// Sanitize potential AUTH/credential echoes.
		return m.replaceAll("(?i)(AUTH|password|passwd|credential)[^\\s]*\\s+\\S+", "$1 ***");
	}

	public static class TransientMailException extends Exception {
		public TransientMailException(String msg) { super(msg); }
	}
	public static class PermanentMailException extends Exception {
		public PermanentMailException(String msg) { super(msg); }
	}
}
