package com.edam.institution.api;

/**
 * Public presentation and locale projection for an institution's branding and theme.
 * Separated from core business rules and JPA persistence entities.
 */
public record InstitutionBrandingView(
        String institutionCode,
        String name,
        String shortName,
        String productTitle,
        String logoPath,
        String faviconPath,
        String primaryColor,
        String secondaryColor,
        String accentColor,
        String themeMode,
        String footerText,
        String defaultCurrencyCode,
        String locale,
        String timeZone) {
}
