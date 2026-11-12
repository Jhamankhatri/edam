package com.edam.institution.web;

import com.edam.common.validation.HexColorValidator;
import com.edam.institution.api.InstitutionBrandingService;
import com.edam.institution.api.InstitutionBrandingView;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Dynamically serves institution CSS theme custom properties so institution color changes
 * apply immediately without rebuilding code or permitting unsafe inline {@code <style>} tags.
 */
@Controller
public class InstitutionThemeController {

    private final InstitutionBrandingService brandingService;

    public InstitutionThemeController(InstitutionBrandingService brandingService) {
        this.brandingService = brandingService;
    }

    @GetMapping(value = "/assets/theme/institution-theme.css", produces = "text/css;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> institutionThemeCss() {
        InstitutionBrandingView branding = brandingService.getActiveBranding();
        String primary = HexColorValidator.sanitizeOrDefault(branding.primaryColor(), "#1B365D");
        String secondary = HexColorValidator.sanitizeOrDefault(branding.secondaryColor(), "#334E68");
        String accent = HexColorValidator.sanitizeOrDefault(branding.accentColor(), "#0F766E");

        String css = """
                :root {
                  --edam-primary: %s;
                  --edam-secondary: %s;
                  --edam-accent: %s;
                }
                """.formatted(primary, secondary, accent);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/css;charset=UTF-8"))
                .body(css);
    }
}
