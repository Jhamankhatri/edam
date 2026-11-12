package com.edam.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC wiring for the JSP UI.
 *
 * <p>View resolution is declarative ({@code spring.mvc.view.prefix}/{@code suffix}) so it
 * can change per environment without a rebuild; this class only maps pages that have no
 * model behaviour of their own.
 *
 * <p>Localisation is intentionally not enabled yet: the BRD leaves supported languages,
 * number formats and RTL support open (NFR-10). Adding a resolver before that decision
 * would bake in an unapproved convention, so the UI renders in the institution's configured
 * language tag and locale support is a separate, approved change.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Spring Security handles the login POST; this mapping renders the form.
		registry.addViewController("/login").setViewName("login");
		registry.addViewController("/access-denied").setViewName("access-denied");
	}

}
