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
 * A password-reset grant (Module C).
 *
 * <h2>Only the hash is stored</h2>
 * The raw token exists only in the message handed to the user. A database dump therefore yields no
 * usable reset links — the same reasoning that applies to session tokens. A stolen backup is inert.
 *
 * <h2>Single use, time limited</h2>
 * {@code used_at} makes a token single-use, and {@code expires_at} bounds it in time. Marking used
 * rather than deleting preserves the evidence that a reset occurred, which an auditor needs.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected PasswordResetToken() {
	}

	public PasswordResetToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	/** True when the token may still be redeemed: unused and unexpired. */
	public boolean isRedeemable(Instant now) {
		return this.usedAt == null && this.expiresAt.isAfter(now);
	}

	public void markUsed(Instant when) {
		if (this.usedAt != null) {
			throw new IllegalStateException("Reset token has already been used");
		}
		this.usedAt = when;
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public String getTokenHash() {
		return this.tokenHash;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public Instant getUsedAt() {
		return this.usedAt;
	}

}
