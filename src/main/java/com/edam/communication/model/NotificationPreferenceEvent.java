package com.edam.communication.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "comm_notification_preference_events")
public class NotificationPreferenceEvent {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "preference_id", nullable = false)
	private UUID preferenceId;

	@Column(name = "event_code", nullable = false, length = 80)
	private String eventCode;

	@Column(name = "email_enabled", nullable = false)
	private boolean emailEnabled = true;

	@Column(name = "in_app_enabled", nullable = false)
	private boolean inAppEnabled = true;

	public UUID getId() { return id; }
	public void setId(UUID id) { this.id = id; }
	public UUID getPreferenceId() { return preferenceId; }
	public void setPreferenceId(UUID preferenceId) { this.preferenceId = preferenceId; }
	public String getEventCode() { return eventCode; }
	public void setEventCode(String eventCode) { this.eventCode = eventCode; }
	public boolean isEmailEnabled() { return emailEnabled; }
	public void setEmailEnabled(boolean v) { this.emailEnabled = v; }
	public boolean isInAppEnabled() { return inAppEnabled; }
	public void setInAppEnabled(boolean v) { this.inAppEnabled = v; }
}
