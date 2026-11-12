package com.edam.web;

import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionSummary;

/**
 * View model for institution branding.
 *
 * <p>Resolved per request: the persisted institution record wins, falling back to the
 * deployment's configured profile. Views consume this object and never read raw
 * configuration, so a tenant can be re-branded from the database without a redeploy.
 */
public record InstitutionTheme(String name, String shortName, String languageTag, String primaryColor,
		String secondaryColor, String accentColor, String logoPath) {

	public static InstitutionTheme from(InstitutionProperties properties, InstitutionSummary persisted) {
		InstitutionProperties.Branding branding = properties.getBranding();
		return new InstitutionTheme(
				firstNonBlank((persisted != null) ? persisted.name() : null, properties.getName()),
				firstNonBlank((persisted != null) ? persisted.shortName() : null, properties.getShortName()),
				firstNonBlank((persisted != null) ? persisted.languageTag() : null, properties.getLocale().getLanguageTag()),
				firstNonBlank((persisted != null) ? persisted.primaryColor() : null, branding.getPrimaryColor()),
				firstNonBlank((persisted != null) ? persisted.secondaryColor() : null, branding.getSecondaryColor()),
				firstNonBlank((persisted != null) ? persisted.accentColor() : null, branding.getAccentColor()),
				branding.getLogoPath());
	}

	private static String firstNonBlank(String preferred, String fallback) {
		return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
	}

}
