package com.edam.web;

import com.edam.common.security.InstitutionContext;
import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionService;
import com.edam.institution.InstitutionSummary;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies cross-cutting view attributes (branding, current institution, correlation ID) to
 * every MVC page.
 *
 * <p>Deliberately a {@code @ControllerAdvice} rather than a servlet filter: it runs inside
 * MVC so that it has access to resolved configuration beans and contributes only to view
 * rendering. It must never throw: an unresolvable institution must not break the login or
 * error pages, so lookup failures fall back to the configured profile.
 */
@ControllerAdvice(basePackages = { "com.edam.web", "com.edam.common.exception" })
public class InstitutionThemeAdvice {

	private final InstitutionProperties properties;

	private final InstitutionService institutionService;

	private final InstitutionContext institutionContext;

	public InstitutionThemeAdvice(InstitutionProperties properties, InstitutionService institutionService,
			InstitutionContext institutionContext) {
		this.properties = properties;
		this.institutionService = institutionService;
		this.institutionContext = institutionContext;
	}

	@ModelAttribute("institutionTheme")
	public InstitutionTheme institutionTheme() {
		InstitutionSummary persisted = this.institutionContext.currentInstitution()
			.flatMap(this.institutionService::findActive)
			.orElse(null);
		return InstitutionTheme.from(this.properties, persisted);
	}

	@ModelAttribute("correlationId")
	public String correlationId() {
		return com.edam.common.logging.CorrelationId.current();
	}

}
