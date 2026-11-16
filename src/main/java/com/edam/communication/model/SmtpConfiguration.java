package com.edam.communication.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "comm_smtp_configuration")
public class SmtpConfiguration {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "host", nullable = false, length = 255)
	private String host;

	@Column(name = "port", nullable = false)
	private int port;

	@Column(name = "username", length = 255)
	private String username;

	@Column(name = "encrypted_password")
	private String encryptedPassword;

	@Column(name = "password_version", nullable = false)
	private int passwordVersion = 0;

	@Enumerated(EnumType.STRING)
	@Column(name = "security_mode", nullable = false, length = 16)
	private SecurityMode securityMode = SecurityMode.STARTTLS;

	@Column(name = "from_email", nullable = false, length = 255)
	private String fromEmail;

	@Column(name = "from_display_name", length = 255)
	private String fromDisplayName;

	@Column(name = "reply_to", length = 255)
	private String replyTo;

	@Column(name = "connection_timeout_ms", nullable = false)
	private int connectionTimeoutMs = 10_000;

	@Column(name = "read_timeout_ms", nullable = false)
	private int readTimeoutMs = 30_000;

	@Column(name = "active", nullable = false)
	private boolean active = false;

	@Column(name = "last_test_at")
	private Instant lastTestAt;
	@Column(name = "last_test_success")
	private Boolean lastTestSuccess;
	@Column(name = "last_test_message")
	private String lastTestMessage;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	public UUID getId() { return id; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public String getHost() { return host; }
	public void setHost(String v) { this.host = v; }
	public int getPort() { return port; }
	public void setPort(int v) { this.port = v; }
	public String getUsername() { return username; }
	public void setUsername(String v) { this.username = v; }
	public String getEncryptedPassword() { return encryptedPassword; }
	public void setEncryptedPassword(String v) { this.encryptedPassword = v; }
	public int getPasswordVersion() { return passwordVersion; }
	public void setPasswordVersion(int v) { this.passwordVersion = v; }
	public SecurityMode getSecurityMode() { return securityMode; }
	public void setSecurityMode(SecurityMode v) { this.securityMode = v; }
	public String getFromEmail() { return fromEmail; }
	public void setFromEmail(String v) { this.fromEmail = v; }
	public String getFromDisplayName() { return fromDisplayName; }
	public void setFromDisplayName(String v) { this.fromDisplayName = v; }
	public String getReplyTo() { return replyTo; }
	public void setReplyTo(String v) { this.replyTo = v; }
	public int getConnectionTimeoutMs() { return connectionTimeoutMs; }
	public void setConnectionTimeoutMs(int v) { this.connectionTimeoutMs = v; }
	public int getReadTimeoutMs() { return readTimeoutMs; }
	public void setReadTimeoutMs(int v) { this.readTimeoutMs = v; }
	public boolean isActive() { return active; }
	public void setActive(boolean v) { this.active = v; }
	public Instant getLastTestAt() { return lastTestAt; }
	public void setLastTestAt(Instant v) { this.lastTestAt = v; }
	public Boolean getLastTestSuccess() { return lastTestSuccess; }
	public void setLastTestSuccess(Boolean v) { this.lastTestSuccess = v; }
	public String getLastTestMessage() { return lastTestMessage; }
	public void setLastTestMessage(String v) { this.lastTestMessage = v; }

	@PrePersist void onPersist() {
		java.time.Instant now = java.time.Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		if (this.updatedAt == null) this.updatedAt = now;
	}
	@PreUpdate void onUpdate() { this.updatedAt = java.time.Instant.now(); }
}
