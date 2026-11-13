package com.edam.identity.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Append-only evidence of one sign-in attempt (Module C).
 *
 * <p>Never records the presented password, and deliberately records the <em>attempted</em> username
 * even when no such user exists — that is the case an operator most needs to see when investigating
 * a brute-force attempt.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "username", nullable = false, length = 100)
	private String username;

	@Column(name = "ip_address", length = 64)
	private String ipAddress;

	@Column(name = "success", nullable = false)
	private boolean success;

	@Column(name = "failure_reason", length = 200)
	private String failureReason;

	@Column(name = "attempted_at", nullable = false)
	private Instant attemptedAt;

	protected LoginAttempt() {
	}

	public LoginAttempt(String username, String ipAddress, boolean success, String failureReason, Instant attemptedAt) {
		this.username = username;
		this.ipAddress = ipAddress;
		this.success = success;
		this.failureReason = failureReason;
		this.attemptedAt = attemptedAt;
	}

	public UUID getId() {
		return this.id;
	}

	public String getUsername() {
		return this.username;
	}

	public String getIpAddress() {
		return this.ipAddress;
	}

	public boolean isSuccess() {
		return this.success;
	}

	public String getFailureReason() {
		return this.failureReason;
	}

	public Instant getAttemptedAt() {
		return this.attemptedAt;
	}

}
