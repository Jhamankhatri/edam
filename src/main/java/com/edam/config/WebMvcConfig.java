package com.edam.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC configuration.
 *
 * <h2>What happened to the view layer</h2>
 * EDAM's earlier revision shipped a server-rendered JSP interface. The scope is now fixed:
 * this delivery is <strong>backend only</strong>, with REST APIs that a React client consumes. The JSP
 * pages, the theme interceptor, the view-attribute interceptor and the Jasper/JSTL dependencies are gone.
 *
 * <p>Nothing needs a view resolver. The container's error dispatch is handled by
 * {@code ErrorEndpointAttributes}, which answers with the standard JSON envelope, so a navigation that
 * fails before reaching a controller still receives a well-formed error rather than a stack trace.
 * Removing the resolver is deliberate: a resolver with no views is dead configuration that implies a
 * presentation layer which no longer exists.
 *
 * <p>{@link WebMvcConfigurer} is still implemented so that future cross-cutting MVC concerns (a
 * {@code HandlerInterceptor} for metrics, a message converter for a catalogue-specific type) have an
 * obvious home without introducing a second configuration class.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

	/**
	 * The application clock.
	 *
	 * <p>Effective dating makes "now" a business input: whether an exchange rate or a delegation rule
	 * applies depends on the instant of the decision. Injecting a {@link Clock} rather than calling
	 * {@code Instant.now()} at each site lets a test place a call on either side of a period boundary
	 * without sleeping, and it keeps the time-zone question in one place — the clock is UTC, and the
	 * organization profile decides how a timestamp is <em>rendered</em>.
	 */
	@Bean
	Clock applicationClock() {
		return Clock.systemUTC();
	}

}
