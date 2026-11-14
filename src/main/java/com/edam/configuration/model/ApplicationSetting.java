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
 * A single typed application setting (Phase 4B Module C).
 *
 * <p>Not auditable via {@link com.edam.common.audit.AuditableEntity} because V6 declared
 * {@code updated_by} as a UUID FK to users instead of the VARCHAR(100) auditor string
 * used on most other tables. We leave that FK column unmapped here — stamp information is
 * captured in the audit log. The created_at/created_by columns are added by V20. The
 * service layer records audit events via
 * {@link com.edam.common.audit.AuditRecorder}; this entity only carries stamps and
 * optimistic locking.
 */
@Entity
@Table(name = "application_settings")
public class ApplicationSetting {

	@Id
	@Generated(event = EventType.INSERT)
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "setting_key", nullable = false, length = 150, unique = true)
	private String key;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "description")
	private String description;

	@Column(name = "category", nullable = false, length = 80)
	private String category = "general";

	@Column(name = "setting_type", nullable = false, length = 20)
	private String type = "STRING";

	@Column(name = "setting_value", columnDefinition = "jsonb")
	private String valueJson;

	@Column(name = "default_value", columnDefinition = "jsonb")
	private String defaultValueJson;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "is_editable", nullable = false)
	private boolean editable = true;

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
	public String getKey() { return this.key; }
	public void setKey(String key) { this.key = key; }
	public String getDisplayName() { return this.displayName; }
	public void setDisplayName(String v) { this.displayName = v; }
	public String getDescription() { return this.description; }
	public void setDescription(String v) { this.description = v; }
	public String getCategory() { return this.category; }
	public void setCategory(String v) { this.category = v; }
	public String getType() { return this.type; }
	public void setType(String t) { this.type = t; }
	public String getValueJson() { return this.valueJson; }
	public void setValueJson(String v) { this.valueJson = v; }
	public String getDefaultValueJson() { return this.defaultValueJson; }
	public void setDefaultValueJson(String v) { this.defaultValueJson = v; }
	public boolean isActive() { return this.active; }
	public void setActive(boolean v) { this.active = v; }
	public boolean isEditable() { return this.editable; }
	public void setEditable(boolean v) { this.editable = v; }
	public Instant getCreatedAt() { return this.createdAt; }
	public String getCreatedBy() { return this.createdBy; }
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
