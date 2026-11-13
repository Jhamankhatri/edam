package com.edam.identity.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Whether this installation has been set up (Module C).
 *
 * <p>A single row, pinned to id 1 by a CHECK constraint. This is a security fact, not a
 * convenience setting: it decides whether the one-time first-administrator bootstrap is still open.
 * It therefore lives in its own table so that it cannot be edited through a general configuration
 * API by someone who merely holds {@code configuration:manage}.
 *
 * <h2>Why not "no users exist yet"</h2>
 * That check races (two concurrent bootstrap calls both see zero users) and it reopens the
 * installation if the last user is ever deleted. A persisted, one-way flag closes both holes.
 */
@Entity
@Table(name = "installation_state")
public class InstallationState {

	/** The only row that can exist. */
	public static final short SINGLETON_ID = 1;

	@Id
	@Column(name = "id", nullable = false)
	private Short id = SINGLETON_ID;

	@Column(name = "bootstrapped", nullable = false)
	private boolean bootstrapped;

	@Column(name = "bootstrapped_at")
	private Instant bootstrappedAt;

	@Column(name = "bootstrapped_by")
	private UUID bootstrappedBy;

	@Column(name = "bootstrap_attempts", nullable = false)
	private int bootstrapAttempts;

	@Column(name = "last_failed_attempt_at")
	private Instant lastFailedAttemptAt;

	protected InstallationState() {
	}

	/**
	 * Marks the installation complete. Idempotent-hostile on purpose: completing an installation that
	 * is already complete is a programming error, and silently accepting it would overwrite the
	 * evidence of who first set the system up.
	 */
	public void complete(UUID administratorId, Instant when) {
		if (this.bootstrapped) {
			throw new IllegalStateException("Installation is already bootstrapped");
		}
		this.bootstrapped = true;
		this.bootstrappedAt = when;
		this.bootstrappedBy = administratorId;
	}

	/** Records a rejected bootstrap attempt so repeated probing is visible to operators. */
	public void recordFailedAttempt(Instant when) {
		this.bootstrapAttempts++;
		this.lastFailedAttemptAt = when;
	}

	public Short getId() {
		return this.id;
	}

	public boolean isBootstrapped() {
		return this.bootstrapped;
	}

	public Instant getBootstrappedAt() {
		return this.bootstrappedAt;
	}

	public UUID getBootstrappedBy() {
		return this.bootstrappedBy;
	}

	public int getBootstrapAttempts() {
		return this.bootstrapAttempts;
	}

	public Instant getLastFailedAttemptAt() {
		return this.lastFailedAttemptAt;
	}

}
