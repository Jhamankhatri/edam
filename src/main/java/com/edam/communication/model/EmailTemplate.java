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
@Table(name = "comm_email_templates")
public class EmailTemplate {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "code", nullable = false, length = 80, unique = true)
	private String code;

	@Column(name = "display_name", nullable = false, length = 200)
	private String displayName;

	@Column(name = "description", columnDefinition = "TEXT")
	private String description;

	@Column(name = "event_code", nullable = false, length = 80)
	private String eventCode;

	@Column(name = "subject", nullable = false, length = 500)
	private String subject;

	@Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
	private String htmlBody;

	@Column(name = "text_body", columnDefinition = "TEXT")
	private String textBody;

	@Column(name = "locale", nullable = false, length = 16)
	private String locale = "en";

	@Column(name = "active", nullable = false)
	private boolean active = false;

	@Column(name = "current_version", nullable = false)
	private int currentVersion = 1;

	@Column(name = "created_by", updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by")
	private UUID updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@PrePersist void onPersist() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		if (this.updatedAt == null) this.updatedAt = now;
	}
	@PreUpdate void onUpdate() { this.updatedAt = Instant.now(); }

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public String getCode() { return code; }
	public void setCode(String v) { this.code = v; }
	public String getDisplayName() { return displayName; }
	public void setDisplayName(String v) { this.displayName = v; }
	public String getDescription() { return description; }
	public void setDescription(String v) { this.description = v; }
	public String getEventCode() { return eventCode; }
	public void setEventCode(String v) { this.eventCode = v; }
	public String getSubject() { return subject; }
	public void setSubject(String v) { this.subject = v; }
	public String getHtmlBody() { return htmlBody; }
	public void setHtmlBody(String v) { this.htmlBody = v; }
	public String getTextBody() { return textBody; }
	public void setTextBody(String v) { this.textBody = v; }
	public String getLocale() { return locale; }
	public void setLocale(String v) { this.locale = v; }
	public boolean isActive() { return active; }
	public void setActive(boolean v) { this.active = v; }
	public int getCurrentVersion() { return currentVersion; }
	public void setCurrentVersion(int v) { this.currentVersion = v; }
	public UUID getCreatedBy() { return createdBy; }
	public void setCreatedBy(UUID v) { this.createdBy = v; }
	public UUID getUpdatedBy() { return updatedBy; }
	public void setUpdatedBy(UUID v) { this.updatedBy = v; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant v) { this.createdAt = v; }
	public Instant getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
