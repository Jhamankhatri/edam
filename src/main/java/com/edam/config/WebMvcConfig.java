package com.edam.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * MVC view wiring for the container error page.
 *
 * <h2>Why this class is now this small</h2>
 * EDAM's earlier revision shipped a server-rendered JSP interface. The scope has since been fixed:
 * this delivery is <strong>backend only</strong>, with REST APIs that a React client consumes. The
 * JSP pages, the theme interceptor and the institution view attributes were therefore removed —
 * keeping a second, unreviewed presentation layer alive would be dead code that still has to be
 * secured.
 *
 * <p>One view remains, and deliberately so: the servlet container's error dispatch needs a page to
 * render when a request fails <em>before</em> it reaches a {@code @RestController} (a malformed URL, a
 * filter exception). Returning JSON for that path would be wrong for a browser navigation and would
 * also mask the real status; the container's own error page, with the safe message resolved by
 * {@code MvcExceptionHandler}, is the correct fallback.
 *
 * <p>{@code redirectContextRelative} keeps redirects inside an arbitrary servlet context path, which
 * matters because EDAM is packaged as a WAR deployed under a context path such as {@code /edam}.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	/**
	 * The application clock.
	 *
	 * <p>Effective dating makes "now" a business input: whether an exchange rate or a delegation rule
	 * applies depends on the instant of the decision. Injecting a {@link Clock} rather than calling
	 * {@code Instant.now()} at each site lets a test place the call on either side of a period boundary
	 * without sleeping, and it keeps the time zone question in one place — the clock is UTC, and the
	 * organization profile decides how a timestamp is <em>rendered</em>.
	 */
	@Bean
	Clock applicationClock() {
		return Clock.systemUTC();
	}

	@Bean
	InternalResourceViewResolver defaultViewResolver(@Value("${spring.mvc.view.prefix:/WEB-INF/jsp/}") String prefix,
			@Value("${spring.mvc.view.suffix:.jsp}") String suffix) {
		InternalResourceViewResolver resolver = new InternalResourceViewResolver();
		resolver.setPrefix(prefix);
		resolver.setSuffix(suffix);
		resolver.setRedirectContextRelative(true);
		resolver.setRedirectHttp10Compatible(false);
		return resolver;
	}

}
