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
@Table(name = "comm_smtp_test_history")
public class SmtpTestHistory {
	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "smtp_config_id", nullable = false)
	private UUID smtpConfigId;

	@Column(name = "recipient", nullable = false, length = 255)
	private String recipient;

	@Column(name = "success", nullable = false)
	private boolean success;

	@Column(name = "diagnostic", columnDefinition = "TEXT")
	private String diagnostic;

	@Column(name = "sent_at", nullable = false)
	private Instant sentAt;

	@PrePersist void onPersist() { if (this.sentAt == null) this.sentAt = Instant.now(); }

	public UUID getId() { return id; }
	public void setId(UUID v) { this.id = v; }
	public UUID getSmtpConfigId() { return smtpConfigId; }
	public void setSmtpConfigId(UUID v) { this.smtpConfigId = v; }
	public String getRecipient() { return recipient; }
	public void setRecipient(String v) { this.recipient = v; }
	public boolean isSuccess() { return success; }
	public void setSuccess(boolean v) { this.success = v; }
	public String getDiagnostic() { return diagnostic; }
	public void setDiagnostic(String v) { this.diagnostic = v; }
	public Instant getSentAt() { return sentAt; }
	public void setSentAt(Instant v) { this.sentAt = v; }
}
