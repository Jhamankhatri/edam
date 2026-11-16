package com.edam.communication.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "comm_notifications")
public class Notification {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "event_code", nullable = false, length = 80)
	private String eventCode;

	@Column(name = "title", nullable = false, length = 500)
	private String title;

	@Column(name = "body", nullable = false, columnDefinition = "TEXT")
	private String body;

	@Column(name = "payload_json", columnDefinition = "TEXT")
	private String payloadJson;

	@Column(name = "is_read", nullable = false)
	private boolean read;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist void onPersist() {
		if (this.createdAt == null) this.createdAt = Instant.now();
	}

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public UUID getUserId() { return userId; }
	public void setUserId(UUID v) { this.userId = v; }
	public String getEventCode() { return eventCode; }
	public void setEventCode(String v) { this.eventCode = v; }
	public String getTitle() { return title; }
	public void setTitle(String v) { this.title = v; }
	public String getBody() { return body; }
	public void setBody(String v) { this.body = v; }
	public String getPayloadJson() { return payloadJson; }
	public void setPayloadJson(String v) { this.payloadJson = v; }
	public boolean isRead() { return read; }
	public void setRead(boolean v) { this.read = v; }
	public Instant getReadAt() { return readAt; }
	public void setReadAt(Instant v) { this.readAt = v; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant v) { this.createdAt = v; }
}
