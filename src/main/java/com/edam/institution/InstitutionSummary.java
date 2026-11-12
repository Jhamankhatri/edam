package com.edam.institution;

/**
 * Read model exposed outside the institution module.
 *
 * <p>Deliberately a record rather than the JPA entity: modules must not be coupled to
 * another module's persistence mapping, and the summary cannot trigger lazy loading.
 */
public record InstitutionSummary(Long id, String code, String name, String shortName, String baseCurrency,
		String primaryColor, String secondaryColor, String accentColor, String languageTag, String timeZone,
		boolean active) {
}
