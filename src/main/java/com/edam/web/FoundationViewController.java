package com.edam.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Foundation MVC controller serving the sample dashboard workspace, login page, and access-denied view.
 */
@Controller
public class FoundationViewController {

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("pageTitle", "Platform Foundation Overview");
        model.addAttribute("activeNav", "foundation");
        return "foundation/dashboard";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "Sign In");
        return "auth/login";
    }

    @GetMapping("/access-denied")
    public String accessDenied(HttpServletResponse response, Model model) {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        model.addAttribute("pageTitle", "Access Denied");
        model.addAttribute("statusCode", HttpStatus.FORBIDDEN.value());
        model.addAttribute("errorMessage", "You do not have permission to access the requested page.");
        return "error/access-denied";
    }
}
