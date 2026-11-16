package com.edam.communication.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "comm_notification_event_configs")
public class NotificationEventConfig {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "event_code", nullable = false, length = 80, unique = true)
	private String eventCode;

	@Column(name = "email_enabled", nullable = false)
	private boolean emailEnabled = true;

	@Column(name = "in_app_enabled", nullable = false)
	private boolean inAppEnabled = true;

	@Column(name = "email_template_id")
	private UUID emailTemplateId;

	/** JSON array of RecipientGroup strings. */
	@Column(name = "recipient_groups", nullable = false, columnDefinition = "TEXT")
	private String recipientGroups;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@PrePersist @PreUpdate void stamp() { this.updatedAt = Instant.now(); }

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public String getEventCode() { return eventCode; }
	public void setEventCode(String v) { this.eventCode = v; }
	public boolean isEmailEnabled() { return emailEnabled; }
	public void setEmailEnabled(boolean v) { this.emailEnabled = v; }
	public boolean isInAppEnabled() { return inAppEnabled; }
	public void setInAppEnabled(boolean v) { this.inAppEnabled = v; }
	public UUID getEmailTemplateId() { return emailTemplateId; }
	public void setEmailTemplateId(UUID v) { this.emailTemplateId = v; }
	public String getRecipientGroups() { return recipientGroups; }
	public void setRecipientGroups(String v) { this.recipientGroups = v; }
	public boolean isActive() { return active; }
	public void setActive(boolean v) { this.active = v; }
	public UUID getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(UUID v) { this.updatedBy = v; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
