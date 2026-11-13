package com.edam.rolemanagement.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Assignment of a role to a user, with an optional expiry (Module C).
 *
 * <h2>Why an entity rather than a {@code @ManyToMany}</h2>
 * The assignment carries its own facts: who granted it, when, and when it lapses. A plain join table
 * could not express those, and "who granted this temporary elevation" is precisely the question an
 * auditor asks.
 *
 * <h2>Expiry is authoritative</h2>
 * {@link #isLive} is the only correct way to read this row. A grant whose {@code expiresAt} has passed
 * confers nothing, even if nobody has cleaned the table — the identity adapter filters on it. A
 * scheduled job clears expired rows for tidiness; correctness never depends on that job running.
 *
 * <h2>One live grant per user and role</h2>
 * Enforced by a partial unique index in V5 for non-expiring grants. Re-granting after expiry inserts a
 * new row rather than reactivating the old one, so the history keeps both.
 */
@Entity
@Table(name = "user_roles")
public class UserRole {

	@jakarta.persistence.Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "role_id", nullable = false, updatable = false)
	private UUID roleId;

	@Column(name = "assigned_by", updatable = false)
	private UUID assignedBy;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

	@Column(name = "expires_at", updatable = false)
	private Instant expiresAt;

	protected UserRole() {
		// JPA
	}

	public UserRole(UUID userId, UUID roleId, UUID assignedBy, Instant expiresAt) {
		if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
			throw new IllegalArgumentException("A role grant cannot expire in the past");
		}
		this.userId = userId;
		this.roleId = roleId;
		this.assignedBy = assignedBy;
		this.assignedAt = Instant.now();
		this.expiresAt = expiresAt;
	}

	/** True when this grant currently confers the role. */
	public boolean isLive() {
		return this.expiresAt == null || this.expiresAt.isAfter(Instant.now());
	}

	public UUID getId() {
		return this.id;
	}

	public UUID getUserId() {
		return this.userId;
	}

	public UUID getRoleId() {
		return this.roleId;
	}

	public UUID getAssignedBy() {
		return this.assignedBy;
	}

	public Instant getAssignedAt() {
		return this.assignedAt;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

}
