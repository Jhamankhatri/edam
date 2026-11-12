package com.edam.web;

import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionService;
import com.edam.institution.InstitutionSummary;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;

import java.util.List;

/**
 * Landing pages of the embedded JSP UI.
 *
 * <p>Kept intentionally thin: it assembles a view model from module services and performs
 * no business logic. {@code /} redirects to the dashboard when signed in.
 */
@Controller
public class HomeController {

	private final InstitutionService institutionService;

	private final InstitutionProperties institutionProperties;

	private final InstitutionContext institutionContext;

	public HomeController(InstitutionService institutionService, InstitutionProperties institutionProperties,
			InstitutionContext institutionContext) {
		this.institutionService = institutionService;
		this.institutionProperties = institutionProperties;
		this.institutionContext = institutionContext;
	}

	@GetMapping("/")
	public String root() {
		return "redirect:/dashboard";
	}

	@GetMapping("/dashboard")
	public String dashboard(Model model, @AuthenticationPrincipal EdamPrincipal principal) {
		model.addAttribute("activeNav", "dashboard");
		model.addAttribute("pageTitle", "Dashboard");
		model.addAttribute("principal", principal);
		model.addAttribute("institutionName", this.institutionProperties.getName());
		model.addAttribute("institutionCode", this.institutionProperties.getCode());
		model.addAttribute("baseCurrency", this.institutionProperties.getBaseCurrency());

		// Tenant resolution is demonstrated on the landing page: a multi-institution user
		// must select one before tenant-scoped screens become available.
		List<InstitutionSummary> institutions = this.institutionService.listActive();
		model.addAttribute("institutions", institutions);
		model.addAttribute("currentInstitution", this.institutionContext.currentInstitution().orElse(null));
		model.addAttribute("institutionSelectionRequired", principal != null && principal.institutionCodes().size() > 1);
		return "dashboard";
	}

}
