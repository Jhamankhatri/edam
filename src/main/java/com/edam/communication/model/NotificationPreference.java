package com.edam.communication.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "comm_notification_preferences")
public class NotificationPreference {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "in_app_enabled", nullable = false)
	private boolean inAppEnabled = true;

	@Column(name = "email_digest", nullable = false, length = 16)
	private String emailDigest = "IMMEDIATE";

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist @PreUpdate void stamp() { this.updatedAt = Instant.now(); }

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public UUID getUserId() { return userId; }
	public void setUserId(UUID v) { this.userId = v; }
	public boolean isInAppEnabled() { return inAppEnabled; }
	public void setInAppEnabled(boolean v) { this.inAppEnabled = v; }
	public String getEmailDigest() { return emailDigest; }
	public void setEmailDigest(String v) { this.emailDigest = v; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
