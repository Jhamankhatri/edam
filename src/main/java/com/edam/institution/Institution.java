package com.edam.institution;

import java.math.BigDecimal;

import com.edam.common.audit.AuditableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A financial institution EDAM is deployed for.
 *
 * <p>This is the anchor of the multi-institution model (BRD 15.3). Identity, branding and
 * institution-level switches live here rather than in code, so that a new client requires
 * configuration — not a fork.
 *
 * <p>Isolation note: the BRD's chosen isolation strategy (OD-20) is not yet approved, so
 * this table deliberately contains no cross-tenant references. Everything tenant-owned will
 * reference this row once the model is signed off; the code {@code code} is the stable
 * human-readable key and is immutable after creation.
 */
@Entity
@Table(name = "institution")
public class Institution extends AuditableEntity {

	/** Stable tenant key referenced by configuration and audit records. */
	@NotBlank
	@Pattern(regexp = "[a-z0-9][a-z0-9-]{1,31}", message = "must be 2-32 lower-case letters, digits or hyphens")
	@Column(name = "code", nullable = false, unique = true, length = 32)
	private String code;

	@NotBlank
	@Size(max = 200)
	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@NotBlank
	@Size(max = 40)
	@Column(name = "short_name", nullable = false, length = 40)
	private String shortName;

	/** ISO-4217 base currency; amounts are recorded in it for reporting. */
	@NotBlank
	@Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 three-letter upper-case code")
	@Column(name = "base_currency", nullable = false, length = 3)
	private String baseCurrency;

	@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour")
	@Column(name = "primary_color", length = 7)
	private String primaryColor;

	@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour")
	@Column(name = "secondary_color", length = 7)
	private String secondaryColor;

	@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour")
	@Column(name = "accent_color", length = 7)
	private String accentColor;

	/** BCP-47 language tag used to render this institution's UI. */
	@NotBlank
	@Column(name = "language_tag", nullable = false, length = 20)
	private String languageTag = "en-PK";

	@NotBlank
	@Column(name = "time_zone", nullable = false, length = 40)
	private String timeZone = "Asia/Karachi";

	/**
	 * Department Head approval threshold in the base currency, or {@code null} to fall back
	 * to the platform-configured default (BRD 10.2, OD-06). Nullable on purpose: "not
	 * configured" is a business state, not zero.
	 */
	@Column(name = "department_head_limit", precision = 19, scale = 2)
	private BigDecimal departmentHeadLimit;

	/** Whether only configured departments may raise CAPEX requests (replaces OD-21 hard-coding). */
	@NotNull
	@Column(name = "capex_department_restriction_enabled", nullable = false)
	private boolean capexDepartmentRestrictionEnabled = true;

	@NotNull
	@Column(name = "active", nullable = false)
	private boolean active = true;

	public String getCode() {
		return this.code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getShortName() {
		return this.shortName;
	}

	public void setShortName(String shortName) {
		this.shortName = shortName;
	}

	public String getBaseCurrency() {
		return this.baseCurrency;
	}

	public void setBaseCurrency(String baseCurrency) {
		this.baseCurrency = baseCurrency;
	}

	public String getPrimaryColor() {
		return this.primaryColor;
	}

	public void setPrimaryColor(String primaryColor) {
		this.primaryColor = primaryColor;
	}

	public String getSecondaryColor() {
		return this.secondaryColor;
	}

	public void setSecondaryColor(String secondaryColor) {
		this.secondaryColor = secondaryColor;
	}

	public String getAccentColor() {
		return this.accentColor;
	}

	public void setAccentColor(String accentColor) {
		this.accentColor = accentColor;
	}

	public String getLanguageTag() {
		return this.languageTag;
	}

	public void setLanguageTag(String languageTag) {
		this.languageTag = languageTag;
	}

	public String getTimeZone() {
		return this.timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public BigDecimal getDepartmentHeadLimit() {
		return this.departmentHeadLimit;
	}

	public void setDepartmentHeadLimit(BigDecimal departmentHeadLimit) {
		this.departmentHeadLimit = departmentHeadLimit;
	}

	public boolean isCapexDepartmentRestrictionEnabled() {
		return this.capexDepartmentRestrictionEnabled;
	}

	public void setCapexDepartmentRestrictionEnabled(boolean capexDepartmentRestrictionEnabled) {
		this.capexDepartmentRestrictionEnabled = capexDepartmentRestrictionEnabled;
	}

	public boolean isActive() {
		return this.active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
