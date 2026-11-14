package com.edam.configuration.model;

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

/**
 * A simple feature flag (Phase 4B Module D).
 *
 * <p>Does not extend {@link com.edam.common.audit.AuditableEntity} because V6 declared
 * {@code updated_by} as a UUID FK to users instead of the VARCHAR(100) auditor string
 * used on most tables. We leave that FK column unmapped here — stamp information is
 * captured in the audit log. V20 adds created_at/created_by to the table. The service
 * layer records audit events via {@link com.edam.common.audit.AuditRecorder}.
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "feature_key", nullable = false, length = 100, unique = true)
	private String featureKey;

	@Column(name = "display_name", nullable = false, length = 150)
	private String displayName;

	@Column(name = "description")
	private String description;

	@Column(name = "is_enabled", nullable = false)
	private boolean enabled = false;

	@Column(name = "configuration", columnDefinition = "jsonb")
	private String configurationJson;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "created_by", updatable = false, length = 100)
	private String createdBy;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	// updated_by UUID FK column is intentionally not mapped — audit log captures actor.

	@Version
	@Column(name = "version", nullable = false)
	private long version = 0L;

	public UUID getId() { return this.id; }
	public String getFeatureKey() { return this.featureKey; }
	public void setFeatureKey(String k) { this.featureKey = k; }
	public String getDisplayName() { return this.displayName; }
	public void setDisplayName(String v) { this.displayName = v; }
	public String getDescription() { return this.description; }
	public void setDescription(String v) { this.description = v; }
	public boolean isEnabled() { return this.enabled; }
	public void setEnabled(boolean v) { this.enabled = v; }
	public String getConfigurationJson() { return this.configurationJson; }
	public void setConfigurationJson(String v) { this.configurationJson = v; }
	public Instant getCreatedAt() { return this.createdAt; }
	public Instant getUpdatedAt() { return this.updatedAt; }
	public long getVersion() { return this.version; }

	public void touch(String actor) {
		this.updatedAt = Instant.now();
	}
	public void markCreated(String actor) {
		Instant now = Instant.now();
		this.createdAt = now;
		this.createdBy = actor;
		this.updatedAt = now;
	}

	@PrePersist
	void onInsert() {
		Instant now = Instant.now();
		if (this.createdAt == null) this.createdAt = now;
		if (this.updatedAt == null) this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}
}
