package com.edam.communication.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "comm_notification_outbox")
public class NotificationOutbox {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "idempotency_key", nullable = false, length = 120, unique = true)
	private String idempotencyKey;

	@Column(name = "event_code", nullable = false, length = 80)
	private String eventCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel", nullable = false, length = 16)
	private Channel channel;

	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "recipient_email", length = 255)
	private String recipientEmail;

	@Column(name = "template_version_id")
	private UUID templateVersionId;

	@Column(name = "subject", length = 500)
	private String subject;

	@Column(name = "body_html", columnDefinition = "TEXT")
	private String bodyHtml;

	@Column(name = "body_text", columnDefinition = "TEXT")
	private String bodyText;

	@Column(name = "payload_json", columnDefinition = "TEXT")
	private String payloadJson;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private OutboxStatus status = OutboxStatus.PENDING;

	@Column(name = "attempts", nullable = false)
	private int attempts = 0;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts = 5;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "locked_by", length = 100)
	private String lockedBy;

	@Column(name = "locked_at")
	private Instant lockedAt;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist void onPersist() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		if (this.nextAttemptAt == null) this.nextAttemptAt = now;
	}

	public enum Channel { EMAIL, IN_APP }

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public String getIdempotencyKey() { return idempotencyKey; }
	public void setIdempotencyKey(String v) { this.idempotencyKey = v; }
	public String getEventCode() { return eventCode; }
	public void setEventCode(String v) { this.eventCode = v; }
	public Channel getChannel() { return channel; }
	public void setChannel(Channel v) { this.channel = v; }
	public UUID getUserId() { return userId; }
	public void setUserId(UUID v) { this.userId = v; }
	public String getRecipientEmail() { return recipientEmail; }
	public void setRecipientEmail(String v) { this.recipientEmail = v; }
	public UUID getTemplateVersionId() { return templateVersionId; }
	public void setTemplateVersionId(UUID v) { this.templateVersionId = v; }
	public String getSubject() { return subject; }
	public void setSubject(String v) { this.subject = v; }
	public String getBodyHtml() { return bodyHtml; }
	public void setBodyHtml(String v) { this.bodyHtml = v; }
	public String getBodyText() { return bodyText; }
	public void setBodyText(String v) { this.bodyText = v; }
	public String getPayloadJson() { return payloadJson; }
	public void setPayloadJson(String v) { this.payloadJson = v; }
	public OutboxStatus getStatus() { return status; }
	public void setStatus(OutboxStatus v) { this.status = v; }
	public int getAttempts() { return attempts; }
	public void setAttempts(int v) { this.attempts = v; }
	public int getMaxAttempts() { return maxAttempts; }
	public void setMaxAttempts(int v) { this.maxAttempts = v; }
	public Instant getNextAttemptAt() { return nextAttemptAt; }
	public void setNextAttemptAt(Instant v) { this.nextAttemptAt = v; }
	public Instant getLastAttemptAt() { return lastAttemptAt; }
	public void setLastAttemptAt(Instant v) { this.lastAttemptAt = v; }
	public String getLastError() { return lastError; }
	public void setLastError(String v) { this.lastError = v; }
	public String getLockedBy() { return lockedBy; }
	public void setLockedBy(String v) { this.lockedBy = v; }
	public Instant getLockedAt() { return lockedAt; }
	public void setLockedAt(Instant v) { this.lockedAt = v; }
	public Instant getSentAt() { return sentAt; }
	public void setSentAt(Instant v) { this.sentAt = v; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant v) { this.createdAt = v; }
}
