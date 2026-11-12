package com.edam.web;

import com.edam.common.api.ApiPaths;
import com.edam.common.security.InstitutionContext;
import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionService;
import com.edam.institution.InstitutionSummary;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies cross-cutting view attributes (branding, current institution, correlation ID) to
 * every MVC page.
 *
 * <h2>Why this advice is intentionally global</h2>
 * <p>It is deliberately <em>not</em> restricted with {@code basePackages}. Views such as the
 * login page are rendered by {@code ParameterizableViewController} instances registered
 * through {@code ViewControllerRegistry}; those handlers belong to no application package,
 * so a package-scoped advice silently skips them and the page renders with blank branding.
 * That defect is invisible to layer tests but breaks every unauthenticated page, so the
 * advice is global and the trade-off (a branding lookup on API requests too) is handled
 * explicitly below.
 *
 * <h2>Failure behaviour</h2>
 * <p>Branding must never break page rendering: a database outage while resolving tenant
 * branding falls back to the deployment's configured profile and logs one WARN line. The
 * error and login pages are precisely the pages that must keep working during an incident,
 * which is why a lookup failure is contained here rather than propagated. API requests skip
 * the database entirely — no JSON contract consumes the theme.
 *
 * <p>This class only contributes view attributes. It performs no business logic and never
 * authorizes anything.
 */
@ControllerAdvice
public class InstitutionThemeAdvice {

	private static final Logger log = LoggerFactory.getLogger(InstitutionThemeAdvice.class);

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
	public InstitutionTheme institutionTheme(HttpServletRequest request) {
		if (ApiPaths.wantsApiResponse(request)) {
			// JSON responses carry no branding; avoid a needless query per API call.
			return InstitutionTheme.from(this.properties, null);
		}
		return InstitutionTheme.from(this.properties, persistedSummary());
	}

	@ModelAttribute("correlationId")
	public String correlationId() {
		return com.edam.common.logging.CorrelationId.current();
	}

	/**
	 * Tenant branding override from the database, or {@code null} when there is none.
	 *
	 * <p>Contained on purpose: a failure here must degrade to configured branding, never
	 * turn a page render into a 500. The exception type is logged so the incident is still
	 * diagnosable from the correlation ID.
	 */
	private InstitutionSummary persistedSummary() {
		try {
			return this.institutionContext.currentInstitution()
				.flatMap(this.institutionService::findActive)
				.orElse(null);
		}
		catch (RuntimeException ex) {
			log.warn("branding.lookup_failed fallback=configuration reason={}", ex.getClass().getSimpleName());
			return null;
		}
	}

}
