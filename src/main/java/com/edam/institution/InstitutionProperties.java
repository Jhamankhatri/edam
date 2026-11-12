package com.edam.institution;

import java.util.Locale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Institution (tenant) profile: identity, branding, locale and the institution-level
 * switches that must never be hard-coded.
 *
 * <p>This is the <em>authoring</em> model. Persisted, per-institution configuration is
 * eventually stored in the {@code institution} tables; the YAML values here describe the
 * institution this deployment serves and act as the safe default for a fresh install.
 *
 * <p>Deliberately excluded from this object: secrets, database credentials, SMTP
 * passwords and anything else that belongs in a secret store.
 */
@Validated
@ConfigurationProperties(prefix = "edam.institution")
public class InstitutionProperties {

	/** Stable, lower-case tenant key used for logging, audit and configuration lookups. */
	@NotBlank
	@Pattern(regexp = "[a-z0-9][a-z0-9-]{1,31}", message = "must be 2-32 characters of lower-case letters, digits or hyphen")
	private String code = "default";

	/** Legal institution name shown on documents and the UI. Never hard-code in views. */
	@NotBlank
	private String name = "EDAM Institution";

	/** Compact label for the sidebar and browser title. */
	@NotBlank
	@Size(max = 40)
	private String shortName = "EDAM";

	/** ISO-4217 code of the institution's base (reporting) currency. */
	@NotBlank
	@Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 three-letter upper-case code")
	private String baseCurrency = "PKR";

	@Valid
	@NotNull
	private Branding branding = new Branding();

	@Valid
	@NotNull
	private LocaleSettings locale = new LocaleSettings();

	/**
	 * Institution-level switches that replace legacy hard-coded behaviour. Each flag
	 * corresponds to an open BRD decision and defaults to the conservative value.
	 */
	@Valid
	@NotNull
	private Features features = new Features();

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

	public Branding getBranding() {
		return this.branding;
	}

	public void setBranding(Branding branding) {
		this.branding = branding;
	}

	public LocaleSettings getLocale() {
		return this.locale;
	}

	public void setLocale(LocaleSettings locale) {
		this.locale = locale;
	}

	public Features getFeatures() {
		return this.features;
	}

	public void setFeatures(Features features) {
		this.features = features;
	}

	/** Theme tokens consumed by the shared layout. Colours are CSS-safe hex values. */
	public static class Branding {

		/** Path (served by the application) to the institution logo; optional. */
		private String logoPath;

		@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour")
		private String primaryColor = "#0f5132";

		@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour")
		private String secondaryColor = "#14532d";

		@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour")
		private String accentColor = "#d1a055";

		public String getLogoPath() {
			return this.logoPath;
		}

		public void setLogoPath(String logoPath) {
			this.logoPath = logoPath;
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

	}

	/** Date, number and time-zone conventions. Values are resolved, never assumed. */
	public static class LocaleSettings {

		/** BCP-47 language tag, e.g. {@code en-PK}. */
		@NotBlank
		private String languageTag = "en-PK";

		/** Business time zone, e.g. {@code Asia/Karachi}. */
		@NotBlank
		private String timeZone = "Asia/Karachi";

		/** Pattern used to render dates in the UI. */
		@NotBlank
		private String datePattern = "dd MMM yyyy";

		public String getLanguageTag() {
			return this.languageTag;
		}

		public void setLanguageTag(String languageTag) {
			this.languageTag = languageTag;
		}

		public Locale getLocale() {
			return Locale.forLanguageTag(this.languageTag);
		}

		public String getTimeZone() {
			return this.timeZone;
		}

		public void setTimeZone(String timeZone) {
			this.timeZone = timeZone;
		}

		public String getDatePattern() {
			return this.datePattern;
		}

		public void setDatePattern(String datePattern) {
			this.datePattern = datePattern;
		}

	}

	/**
	 * Configurable switches for decisions the BRD leaves open. Defaults preserve current
	 * documented behaviour; changing them is a configuration change, not a code change.
	 */
	public static class Features {

		/**
		 * Whether only the departments configured as CAPEX-eligible may raise CAPEX
		 * requests. The legacy application hard-coded three departments (OD-21).
		 */
		private boolean capexDepartmentRestrictionEnabled = true;

		/**
		 * Department Head approval threshold fallback in the institution base currency,
		 * used only when no configured limit exists. Legacy used a hard-coded 20,000
		 * (OD-06, OD-21).
		 */
		@NotNull
		private java.math.BigDecimal departmentHeadLimitFallback = new java.math.BigDecimal("20000.00");

		/**
		 * Whether the "exceeding limit" configuration (FM-13) participates in routing.
		 * Unverified in the reference implementation (OD-05), therefore off by default.
		 */
		private boolean exceedingLimitEnforcedInRouting = false;

		/** Whether tenant context must be supplied on every request (see SECURITY_GUIDELINES). */
		private boolean requireInstitutionContext = false;

		public boolean isCapexDepartmentRestrictionEnabled() {
			return this.capexDepartmentRestrictionEnabled;
		}

		public void setCapexDepartmentRestrictionEnabled(boolean capexDepartmentRestrictionEnabled) {
			this.capexDepartmentRestrictionEnabled = capexDepartmentRestrictionEnabled;
		}

		public java.math.BigDecimal getDepartmentHeadLimitFallback() {
			return this.departmentHeadLimitFallback;
		}

		public void setDepartmentHeadLimitFallback(java.math.BigDecimal departmentHeadLimitFallback) {
			this.departmentHeadLimitFallback = departmentHeadLimitFallback;
		}

		public boolean isExceedingLimitEnforcedInRouting() {
			return this.exceedingLimitEnforcedInRouting;
		}

		public void setExceedingLimitEnforcedInRouting(boolean exceedingLimitEnforcedInRouting) {
			this.exceedingLimitEnforcedInRouting = exceedingLimitEnforcedInRouting;
		}

		public boolean isRequireInstitutionContext() {
			return this.requireInstitutionContext;
		}

		public void setRequireInstitutionContext(boolean requireInstitutionContext) {
			this.requireInstitutionContext = requireInstitutionContext;
		}

	}

}
