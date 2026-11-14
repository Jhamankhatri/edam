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
 * There is exactly one active row. The requirement is explicit that this is "the purchasing bank's
 * own configuration; it is not a multi-bank registry", and migration V6 enforces it with a partial
 * unique index on {@code (TRUE) WHERE is_active}. That index is the real guarantee — a service-level
 * check would be bypassed by any other writer.
 *
 * <h2>Field scope</h2>
 * The profile carries every identity/contact field required by Phase 4B Module A: organization
 * name, display name, legal name, organization code, contact email/phone, website, postal address,
 * support email, logo/favicon asset references, and presentation defaults (timezone, date format,
 * default currency). No multi-tenant institution_id column.
 */
@Entity
@Table(name = "organization_profile")
public class OrganizationProfile extends AuditableEntity {

	@Column(name = "organization_name", nullable = false, length = 200)
	private String organizationName;

	@Column(name = "display_name", length = 200)
	private String displayName;

	@Column(name = "legal_name", length = 300)
	private String legalName;

	@Column(name = "organization_code", length = 60)
	private String organizationCode;

	@Column(name = "contact_email", length = 255)
	private String contactEmail;

	@Column(name = "contact_phone", length = 40)
	private String contactPhone;

	@Column(name = "website", length = 255)
	private String website;

	@Column(name = "address_line1", length = 300)
	private String addressLine1;

	@Column(name = "address_line2", length = 300)
	private String addressLine2;

	@Column(name = "address_city", length = 120)
	private String addressCity;

	@Column(name = "address_state", length = 120)
	private String addressState;

	@Column(name = "address_postal_code", length = 30)
	private String addressPostalCode;

	@Column(name = "address_country", length = 100)
	private String addressCountry;

	@Column(name = "support_email", length = 255)
	private String supportEmail;

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

	public String getOrganizationName() { return this.organizationName; }
	public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

	public String getDisplayName() { return this.displayName; }
	public void setDisplayName(String displayName) { this.displayName = displayName; }

	public String getLegalName() { return this.legalName; }
	public void setLegalName(String legalName) { this.legalName = legalName; }

	public String getOrganizationCode() { return this.organizationCode; }
	public void setOrganizationCode(String organizationCode) { this.organizationCode = organizationCode; }

	public String getContactEmail() { return this.contactEmail; }
	public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

	public String getContactPhone() { return this.contactPhone; }
	public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

	public String getWebsite() { return this.website; }
	public void setWebsite(String website) { this.website = website; }

	public String getAddressLine1() { return this.addressLine1; }
	public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

	public String getAddressLine2() { return this.addressLine2; }
	public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

	public String getAddressCity() { return this.addressCity; }
	public void setAddressCity(String addressCity) { this.addressCity = addressCity; }

	public String getAddressState() { return this.addressState; }
	public void setAddressState(String addressState) { this.addressState = addressState; }

	public String getAddressPostalCode() { return this.addressPostalCode; }
	public void setAddressPostalCode(String addressPostalCode) { this.addressPostalCode = addressPostalCode; }

	public String getAddressCountry() { return this.addressCountry; }
	public void setAddressCountry(String addressCountry) { this.addressCountry = addressCountry; }

	public String getSupportEmail() { return this.supportEmail; }
	public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }

	public UUID getLogoAssetId() { return this.logoAssetId; }
	public void setLogoAssetId(UUID logoAssetId) { this.logoAssetId = logoAssetId; }

	public UUID getFaviconAssetId() { return this.faviconAssetId; }
	public void setFaviconAssetId(UUID faviconAssetId) { this.faviconAssetId = faviconAssetId; }

	public String getTimezone() { return this.timezone; }
	public void setTimezone(String timezone) { this.timezone = timezone; }

	public String getDateFormat() { return this.dateFormat; }
	public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }

	public UUID getDefaultCurrencyId() { return this.defaultCurrencyId; }
	public void setDefaultCurrencyId(UUID defaultCurrencyId) { this.defaultCurrencyId = defaultCurrencyId; }

	public boolean isActive() { return this.active; }
	public void setActive(boolean active) { this.active = active; }

}
