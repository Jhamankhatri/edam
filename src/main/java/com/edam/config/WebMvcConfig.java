package com.edam.config;

import com.edam.institution.InstitutionProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;

/**
 * MVC wiring for the JSP UI.
 *
 * <p>View resolution is declarative ({@code spring.mvc.view.prefix}/{@code suffix}) so it
 * can change per environment without a rebuild. This class adds locale negotiation (the
 * institution's language is the default, a cookie may override it) and maps the two
 * parameter-less pages.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	private final InstitutionProperties institutionProperties;

	public WebMvcConfig(InstitutionProperties institutionProperties) {
		this.institutionProperties = institutionProperties;
	}

	@Override
	public void addViewControllers(ViewControllerRegistry registry) {
		// Spring Security intercepts the login POST; this mapping renders the form.
		registry.addViewController("/login").setViewName("login");
		registry.addViewController("/access-denied").setViewName("access-denied");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
		interceptor.setParamName("lang");
		registry.addInterceptor(interceptor);
	}

	/**
	 * Defaults to the institution's language tag; an explicit {@code ?lang=} switches the
	 * cookie. Never hard-codes a locale in a view.
	 */
	@Bean
	LocaleResolver localeResolver() {
		CookieLocaleResolver resolver = new CookieLocaleResolver("EDAM_LOCALE");
		resolver.setDefaultLocale(this.institutionProperties.getLocale().getLocale());
		resolver.setCookieMaxAge(Duration.ofDays(30));
		return resolver;
	}

}
