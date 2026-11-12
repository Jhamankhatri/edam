package com.edam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * MVC wiring for the JSP UI.
 *
 * <p>Spring MVC 7 renders {@code redirect:} views as plain relative URLs by default. EDAM is
 * deployed under an arbitrary servlet context path, so redirects must be
 * context-relative or the browser is sent to a path outside the application. The view
 * resolver is therefore declared explicitly instead of relying on the auto-configured
 * default, which also makes the JSP prefix/suffix reviewable in one place.
 *
 * <p>Localisation is intentionally not enabled yet: supported languages, number formats and
 * RTL support remain open decisions (NFR-10). The UI renders in the institution's configured
 * language tag until that is approved.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	/**
	 * JSP resolver. {@code redirectContextRelative} prepends the context path to redirect
	 * locations; {@code exposeContextBeansAsAttributes} stays disabled so JSPs cannot reach
	 * arbitrary Spring beans.
	 */
	@Bean
	ViewResolver defaultViewResolver(@Value("${spring.mvc.view.prefix:/WEB-INF/jsp/}") String prefix,
			@Value("${spring.mvc.view.suffix:.jsp}") String suffix) {
		InternalResourceViewResolver resolver = new InternalResourceViewResolver();
		resolver.setPrefix(prefix);
		resolver.setSuffix(suffix);
		resolver.setRedirectContextRelative(true);
		resolver.setRedirectHttp10Compatible(false);
		return resolver;
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Spring Security handles the login POST; this mapping renders the form.
		registry.addViewController("/login").setViewName("login");
		registry.addViewController("/access-denied").setViewName("access-denied");
	}

}
