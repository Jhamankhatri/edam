package com.edam.config;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * Servlet-container filter registrations that the Spring Security chain cannot express.
 *
 * <h2>The error-dispatch problem this solves</h2>
 * <p>When a view fails to render (a JSP compile error, for example), the container performs
 * an <em>error dispatch</em> to {@code /error}. If the Spring Security filter chain covers
 * that dispatch, an anonymous visitor is redirected to {@code /login} — a 302 that hides a
 * server fault behind a sign-in prompt and makes the failure look like an authentication
 * problem. The container's error path must therefore not be intercepted by authorization
 * filters.
 *
 * <p>Two rules apply:
 * <ul>
 * <li>The security filter chain is registered for {@code REQUEST} dispatches only
 * ({@code spring.security.filter.dispatcher-types} in {@code application.yml}). The login
 * page, the access-denied page and every JSP forward are plain {@code REQUEST} handling
 * plus an internal {@code FORWARD}, so authorization remains complete.</li>
 * <li>An error dispatch goes through the servlet container and lands on
 * {@code MvcExceptionHandler}, which renders the safe error page with the original status
 * code and no internal detail.</li>
 * </ul>
 *
 * <p>Because the chain no longer runs on error dispatches, an authenticated user's error
 * page would otherwise render without a populated security context. The registration below
 * re-populates the context for error dispatches only by restoring the session-backed
 * context; it performs no authorization decision. Anonymous error dispatches have no session
 * context and pass straight through.
 */
@Configuration(proxyBeanMethods = false)
public class ServletFilterConfig {

	/**
	 * Restores the session-backed security context for container error dispatches so the
	 * error page renders the signed-in user instead of an anonymous shell. No authorization
	 * is performed by this filter.
	 */
	@Bean
	FilterRegistrationBean<SecurityContextHolderFilter> errorDispatchSecurityContextFilter() {
		FilterRegistrationBean<SecurityContextHolderFilter> registration = new FilterRegistrationBean<>(
				new SecurityContextHolderFilter(new HttpSessionSecurityContextRepository()));
		registration.setName("errorDispatchSecurityContextFilter");
		registration.addUrlPatterns("/*");
		registration.setDispatcherTypes(DispatcherType.ERROR);
		// After the (REQUEST-only) security chain, before the container renders /error.
		registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
		return registration;
	}

	/**
	 * Resolves the request's institution after authentication and before any controller runs.
	 *
	 * <p>Ordered after the Spring Security chain (which populates the authentication) and before
	 * request handling. The filter itself performs no authorization; it re-checks the
	 * {@code X-Institution} header against the principal's verified memberships and binds the id
	 * services need. Running it here rather than in a controller argument means a service can never
	 * be invoked with an unresolved tenant in a web request.
	 */

}
