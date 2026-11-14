package com.edam.configuration.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A single typed application setting (Phase 4B Module C).
 *
 * <p>Each row carries a declared data type, default value, editable flag and category.
 * The service validates values against their declared type before persisting, so an
 * INTEGER setting can never store a free-form string. JSON values are stored as jsonb
 * and parsed for structural validity; no executable content is ever interpreted.
 */
@Entity
@Table(name = "application_settings")
public class ApplicationSetting extends AuditableEntity {

	public enum SettingType { STRING, BOOLEAN, INTEGER, DECIMAL, JSON, DATE }

	@Column(name = "setting_key", nullable = false, length = 150, unique = true)
	private String key;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "description")
	private String description;

	@Column(name = "category", nullable = false, length = 80)
	private String category = "general";

	@Enumerated(EnumType.STRING)
	@Column(name = "setting_type", nullable = false, length = 20)
	private SettingType type = SettingType.STRING;

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
	public SettingType getType() { return this.type; }
	public void setType(SettingType t) { this.type = t; }
	public String getValueJson() { return this.valueJson; }
	public void setValueJson(String v) { this.valueJson = v; }
	public String getDefaultValueJson() { return this.defaultValueJson; }
	public void setDefaultValueJson(String v) { this.defaultValueJson = v; }
	public boolean isActive() { return this.active; }
	public void setActive(boolean v) { this.active = v; }
	public boolean isEditable() { return this.editable; }
	public void setEditable(boolean v) { this.editable = v; }
}
