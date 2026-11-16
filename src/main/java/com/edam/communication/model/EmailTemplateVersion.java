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
@Table(name = "comm_email_template_versions")
public class EmailTemplateVersion {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "template_id", nullable = false)
	private UUID templateId;

	@Column(name = "version_number", nullable = false)
	private int versionNumber;

	@Column(name = "subject", nullable = false, length = 500)
	private String subject;

	@Column(name = "html_body", nullable = false, columnDefinition = "TEXT")
	private String htmlBody;

	@Column(name = "text_body", columnDefinition = "TEXT")
	private String textBody;

	@Column(name = "locale", nullable = false, length = 16)
	private String locale;

	@Column(name = "change_note", columnDefinition = "TEXT")
	private String changeNote;

	@Column(name = "created_by")
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist void onPersist() {
		if (this.createdAt == null) this.createdAt = Instant.now();
	}

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public UUID getTemplateId() { return templateId; }
	public void setTemplateId(UUID v) { this.templateId = v; }
	public int getVersionNumber() { return versionNumber; }
	public void setVersionNumber(int v) { this.versionNumber = v; }
	public String getSubject() { return subject; }
	public void setSubject(String v) { this.subject = v; }
	public String getHtmlBody() { return htmlBody; }
	public void setHtmlBody(String v) { this.htmlBody = v; }
	public String getTextBody() { return textBody; }
	public void setTextBody(String v) { this.textBody = v; }
	public String getLocale() { return locale; }
	public void setLocale(String v) { this.locale = v; }
	public String getChangeNote() { return changeNote; }
	public void setChangeNote(String v) { this.changeNote = v; }
	public UUID getCreatedBy() { return createdBy; }
	public void setCreatedBy(UUID v) { this.createdBy = v; }
	public Instant getCreatedAt() { return createdAt; }
	public void setCreatedAt(Instant v) { this.createdAt = v; }
}
