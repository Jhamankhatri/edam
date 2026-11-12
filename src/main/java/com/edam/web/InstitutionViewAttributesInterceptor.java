package com.edam.web;

import java.util.Map;

import com.edam.common.api.ApiPaths;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.InstitutionContext;
import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionService;
import com.edam.institution.InstitutionSummary;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Adds the attributes every server-rendered view needs — institution branding and the request
 * correlation ID — to the model of each MVC response.
 *
 * <h2>Why an interceptor and not {@code @ControllerAdvice}</h2>
 * <p>{@code @ModelAttribute} methods on a {@code @ControllerAdvice} are only invoked for
 * <em>handler methods</em>, because Spring MVC populates the model through
 * {@code ModelFactory} while invoking a controller. Pages rendered by a
 * {@code ParameterizableViewController} — registered with {@code ViewControllerRegistry}, which
 * is how {@code /login} and {@code /access-denied} are rendered — never pass through
 * {@code ModelFactory}, so advice-provided attributes are silently absent. The symptom is an
 * HTTP 200 page with blank branding, which no layer test catches.
 *
 * <p>A {@link HandlerInterceptor} runs for <em>every</em> {@code HandlerExecutionChain},
 * including view controllers, so the attributes are guaranteed to be present regardless of how
 * a view is resolved. It also keeps working if a page is later switched between a view
 * controller and a handler method.
 *
 * <h2>Failure behaviour</h2>
 * <p>Branding must never break page rendering. A database outage while resolving tenant
 * branding falls back to the deployment's configured profile and logs one WARN line: the login
 * and error pages are precisely the pages that must keep working during an incident. API
 * requests are skipped entirely — no JSON contract consumes the theme, so no lookup is issued.
 *
 * <p>This class contributes view attributes only. It performs no business logic and authorizes
 * nothing.
 */
public class InstitutionViewAttributesInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(InstitutionViewAttributesInterceptor.class);

	private final InstitutionProperties properties;

	private final InstitutionService institutionService;

	private final InstitutionContext institutionContext;

	public InstitutionViewAttributesInterceptor(InstitutionProperties properties, InstitutionService institutionService,
			InstitutionContext institutionContext) {
		this.properties = properties;
		this.institutionService = institutionService;
		this.institutionContext = institutionContext;
	}

	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		// ModelAndView#getModelMap is the supported API in Spring 7 (hasObject was removed).
		if (modelAndView == null
				|| modelAndView.getModelMap().containsAttribute(ViewConstants.INSTITUTION_THEME)) {
			return;
		}
		if (modelAndView.getViewName() != null && modelAndView.getViewName().startsWith("redirect:")) {
			// Redirects render no markup; the theme would be dead weight in the flash model.
			return;
		}
		Map<String, Object> model = modelAndView.getModel();
		model.put(ViewConstants.INSTITUTION_THEME, themeFor(request));
		model.putIfAbsent(ViewConstants.CORRELATION_ID, CorrelationId.current());
	}

	private InstitutionTheme themeFor(HttpServletRequest request) {
		if (ApiPaths.wantsApiResponse(request)) {
			return InstitutionTheme.from(this.properties, null);
		}
		return InstitutionTheme.from(this.properties, persistedSummary());
	}

	/**
	 * Tenant branding override from the database, or {@code null} when there is none.
	 *
	 * <p>Contained on purpose: a failure here degrades to configured branding rather than
	 * turning a page render into a 500. The exception type is logged so the incident remains
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
