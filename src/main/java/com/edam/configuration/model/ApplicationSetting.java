package com.edam.configuration.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A single typed application setting (Phase 4B Module C).
 *
 * <p>Each row carries a declared data type (stored as its string name — see
 * {@code SettingType} in the configuration DTO package), default value, editable flag
 * and category. The service validates values against their declared type before
 * persisting, so an INTEGER setting can never store a free-form string.
 */
@Entity
@Table(name = "application_settings")
public class ApplicationSetting extends AuditableEntity {

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

	public UUID getId() { return super.getId(); }
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

	public void touch(String actor) { /* handled by JPA auditing */ }
	public void markCreated(String actor) { /* handled by JPA auditing */ }
}
