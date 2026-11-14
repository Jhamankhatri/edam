package com.edam.configuration.model;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * A simple feature flag (Phase 4B Module D).
 *
 * <p>No experimentation, no targeting rules, no per-actor rollout — the flag is either on
 * or off, optionally with a small validated JSON metadata blob. The service layer is the
 * only entry point for evaluation; callers never touch the repository directly, which
 * keeps missing flags as a safe default of "disabled."
 */
@Entity
@Table(name = "feature_flags")
public class FeatureFlag extends AuditableEntity {

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
}
