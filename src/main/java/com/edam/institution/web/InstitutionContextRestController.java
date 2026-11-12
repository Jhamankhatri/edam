package com.edam.institution.web;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.institution.api.InstitutionBrandingService;
import com.edam.institution.api.InstitutionBrandingView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the active institution's resolved branding and locale configuration
 * for the authenticated user context.
 */
@RestController
@RequestMapping("/api/v1/institutions")
public class InstitutionContextRestController {

    private final InstitutionBrandingService brandingService;

    public InstitutionContextRestController(InstitutionBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<InstitutionBrandingView>> getCurrentInstitution() {
        InstitutionBrandingView branding = brandingService.getActiveBranding();
        return ResponseEntity.ok(ApiResponse.ok(CorrelationIdProvider.currentOrNew(), branding));
    }
}
