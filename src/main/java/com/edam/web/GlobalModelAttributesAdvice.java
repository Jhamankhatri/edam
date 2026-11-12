package com.edam.web;

import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.security.CurrentUserContext;
import com.edam.common.security.InstitutionContext;
import com.edam.institution.api.InstitutionBrandingService;
import com.edam.institution.api.InstitutionBrandingView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Populates shared presentation attributes (active institution branding, correlation ID,
 * and authenticated actor context) for all MVC JSP views.
 */
@ControllerAdvice(basePackages = "com.edam.web")
public class GlobalModelAttributesAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalModelAttributesAdvice.class);

    private final InstitutionBrandingService brandingService;
    private final CurrentUserContext currentUserContext;

    public GlobalModelAttributesAdvice(
            InstitutionBrandingService brandingService,
            CurrentUserContext currentUserContext) {
        this.brandingService = brandingService;
        this.currentUserContext = currentUserContext;
    }

    @ModelAttribute("branding")
    public InstitutionBrandingView branding() {
        try {
            return brandingService.getActiveBranding();
        } catch (DataAccessException ex) {
            log.warn("Database unavailable while resolving institution branding for view; using configured fallback.");
            return brandingService.getFallbackBranding();
        }
    }

    @ModelAttribute("correlationId")
    public String correlationId() {
        return CorrelationIdProvider.currentOrNew();
    }

    @ModelAttribute("activeUsername")
    public String activeUsername() {
        return currentUserContext.getAuthenticatedUsername().orElse(null);
    }

    @ModelAttribute("activeInstitutionCode")
    public String activeInstitutionCode() {
        return currentUserContext.getInstitutionContext()
                .map(InstitutionContext::institutionCode)
                .orElse(null);
    }
}
