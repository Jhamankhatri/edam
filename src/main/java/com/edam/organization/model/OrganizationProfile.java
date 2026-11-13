package com.edam.organization.model;

import java.util.UUID;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The installation's own identity: the bank this EDAM instance serves (Module A).
 *
 * <h2>Singleton, by design and by constraint</h2>
 * There is exactly one row. The requirement is explicit that this is "the purchasing bank's own
 * configuration; it is not a multi-bank registry", and migration V6 enforces it with a partial unique
 * index on {@code (TRUE) WHERE is_active}. That index is the real guarantee — a service-level check
 * would be bypassed by any other writer — and {@link #SINGLETON_ID} documents the row a fresh install
 * receives.
 *
 * <h2>Why defaults exist for timezone and date format</h2>
 * Every other value is the bank's to supply. These two have defensible defaults (Pakistan Standard
 * Time, {@code dd MMM yyyy}) because a brand-new installation must be able to render a timestamp before
 * an administrator has configured anything. They are settings, not business rules, and are editable.
 */
@Entity
@Table(name = "organization_profile")
public class OrganizationProfile extends AuditableEntity {

	@Column(name = "organization_name", nullable = false, length = 200)
	private String organizationName;

	@Column(name = "legal_name", length = 300)
	private String legalName;

	@Column(name = "logo_asset_id")
	private UUID logoAssetId;

	@Column(name = "favicon_asset_id")
	private UUID faviconAssetId;

	@Column(name = "timezone", nullable = false, length = 60)
	private String timezone = "Asia/Karachi";

	@Column(name = "date_format", nullable = false, length = 40)
	private String dateFormat = "dd MMM yyyy";

	@Column(name = "default_currency_id")
	private UUID defaultCurrencyId;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	public String getOrganizationName() {
		return this.organizationName;
	}

	public void setOrganizationName(String organizationName) {
		this.organizationName = organizationName;
	}

	public String getLegalName() {
		return this.legalName;
	}

	public void setLegalName(String legalName) {
		this.legalName = legalName;
	}

	public UUID getLogoAssetId() {
		return this.logoAssetId;
	}

	public void setLogoAssetId(UUID logoAssetId) {
		this.logoAssetId = logoAssetId;
	}

	public UUID getFaviconAssetId() {
		return this.faviconAssetId;
	}

	public void setFaviconAssetId(UUID faviconAssetId) {
		this.faviconAssetId = faviconAssetId;
	}

	public String getTimezone() {
		return this.timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getDateFormat() {
		return this.dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public UUID getDefaultCurrencyId() {
		return this.defaultCurrencyId;
	}

	public void setDefaultCurrencyId(UUID defaultCurrencyId) {
		this.defaultCurrencyId = defaultCurrencyId;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
