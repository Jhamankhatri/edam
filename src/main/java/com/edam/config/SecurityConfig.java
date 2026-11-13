package com.edam.config;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;
import com.edam.common.security.JsonApiAccessDeniedHandler;
import com.edam.common.security.JsonApiAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Security foundation.
 *
 * <p>Explicit separation of concerns (BRD 13):
 * <ul>
 * <li><strong>Authentication</strong> — delegated to {@link EdamUserDetailsService}. The
 * foundation ships a form-login shell and, in the {@code local} profile only, an in-memory
 * fixture store. No SSO/AD/OIDC/SAML decision is made here (OD-11).</li>
 * <li><strong>Authorization</strong> — server-side only, through URL rules here and
 * {@code @PreAuthorize} on operations. Hiding a button in a JSP is never a control.</li>
 * <li><strong>Institution context</strong> — resolved by {@code InstitutionContext};
 * membership is a set, never a single assumed tenant.</li>
 * </ul>
 *
 * <p>Every request requires authentication except the login page, the denial page, static
 * assets and the health probes, which are read-only and expose no business data.
 *
 * <p>JSP rendering happens on a servlet FORWARD dispatch (view controller → JSP). The chain
 * is restricted to the REQUEST dispatcher type through
 * {@code spring.security.filter.dispatcher-types} (application.yml) for two reasons: an
 * internal forward to {@code /WEB-INF/jsp/login.jsp} must not be re-authorized as a URL
 * (which would redirect anonymous visitors to {@code /login} in a loop), and a container
 * error dispatch must not be turned into a redirect, which would disguise server faults as
 * authentication problems. {@code ServletFilterConfig} re-establishes the security context
 * for error dispatches so the error page can still render the signed-in user.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * Content-Security-Policy for an API that serves JSON only.
	 *
	 * <p>{@code default-src 'none'} rather than a page-friendly policy: the React client is deployed
	 * separately, so nothing this server returns ever needs to load a script, style or font. If a browser
	 * is ever induced to treat a JSON response as HTML, nothing in it can execute.
	 * {@code frame-ancestors 'none'} blocks clickjacking of the API explorer and {@code base-uri 'self'}
	 * stops a base-tag injection from retargeting relative URLs.
	 */
	private static final String CONTENT_SECURITY_POLICY = "default-src 'none'; "
			+ "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
		RequestMatcher[] publicMatchers = {
				// The container's error dispatch re-enters the filter chain. Without this,
				// an authenticated 500 would turn into a 302 to the login page.
				PathPatternRequestMatcher.pathPattern("/error"),
				// The OpenAPI schema documents the contract, not the data. It is public so a client can
				// be generated from it; every operation it lists still requires authentication.
				PathPatternRequestMatcher.pathPattern("/v3/api-docs"),
				PathPatternRequestMatcher.pathPattern("/v3/api-docs/**"),
				PathPatternRequestMatcher.pathPattern("/swagger-ui.html"),
				PathPatternRequestMatcher.pathPattern("/swagger-ui/**"),
				PathPatternRequestMatcher.pathPattern("/actuator/health"),
				PathPatternRequestMatcher.pathPattern("/actuator/health/**"),
				PathPatternRequestMatcher.pathPattern("/actuator/info") };
		http.authorizeHttpRequests(authorize -> authorize.requestMatchers(publicMatchers).permitAll()
			.anyRequest()
			.authenticated())
			// CSRF protection stays enabled for the session-based JSP UI. API callers must
			// present the token; disabling it would undermine the transactional-safety
			// requirements of the approval workflow.
			.csrf(csrf -> csrf.ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern("/actuator/**")))
			// Form login is retained as the fallback local mechanism until the bank decides its identity
			// provider. It authenticates through the EdamUserDetailsService seam, so an OIDC adapter
			// replaces it without touching a single authorization rule.
			.formLogin(form -> form.permitAll())
			.logout(logout -> logout.logoutUrl("/logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				.sessionFixation(fixation -> fixation.migrateSession()))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new JsonApiAuthenticationEntryPoint(jsonMapper))
				.accessDeniedHandler(new JsonApiAccessDeniedHandler(jsonMapper)))
			.headers(headers -> headers.frameOptions(frame -> frame.deny())
				.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
			// No saved-request flow: replaying a stale POST after sign-in would be wrong, and an API
			// client retries explicitly anyway.
			.requestCache(cache -> cache.disable());
		return http.build();
	}

	/**
	 * Authentication provider backed by the {@link EdamUserDetailsService} seam.
	 *
	 * <p>When no identity service is available the provider still exists but rejects every
	 * attempt, which fails closed and logs a single actionable warning instead of leaving
	 * the context without an authentication mechanism.
	 */
	@Bean
	AuthenticationProvider authenticationProvider(ObjectProvider<EdamUserDetailsService> userDetailsService,
			PasswordEncoder passwordEncoder) {
		EdamUserDetailsService service = userDetailsService.getIfAvailable();
		if (service == null) {
			return new UnconfiguredAuthenticationProvider();
		}
		return new EdamDaoAuthenticationProvider(service, passwordEncoder);
	}

	/**
	 * Verifies a username/password against {@link EdamUserDetailsService} and promotes the
	 * returned user's {@link EdamPrincipal} to the authentication principal.
	 *
	 * <p>Written explicitly rather than using {@code DaoAuthenticationProvider} so that the
	 * token principal is the application's own principal type by construction, not as a
	 * post-processing step.
	 */
	static final class EdamDaoAuthenticationProvider implements AuthenticationProvider {

		private final EdamUserDetailsService userDetailsService;

		private final PasswordEncoder passwordEncoder;

		EdamDaoAuthenticationProvider(EdamUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
			this.userDetailsService = userDetailsService;
			this.passwordEncoder = passwordEncoder;
		}

		@Override
		public Authentication authenticate(Authentication authentication) {
			String username = authentication.getName();
			String presentedPassword = (authentication.getCredentials() != null)
					? authentication.getCredentials().toString() : "";
			Object loaded;
			try {
				loaded = this.userDetailsService.loadUserByUsername(username);
			}
			catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
				// Identical outcome for "unknown user" and "wrong password": no account
				// enumeration through timing-independent messages.
				throw new org.springframework.security.authentication.BadCredentialsException("Bad credentials");
			}
			EdamPrincipal principal = toPrincipal(loaded);
			String storedPassword = (loaded instanceof UserDetails details) ? details.getPassword() : null;
			if (storedPassword == null || !this.passwordEncoder.matches(presentedPassword, storedPassword)) {
				throw new org.springframework.security.authentication.BadCredentialsException("Bad credentials");
			}
			UsernamePasswordAuthenticationToken result = UsernamePasswordAuthenticationToken.authenticated(principal,
					null, principal.getAuthorities());
			result.setDetails(authentication.getDetails());
			return result;
		}

		private static EdamPrincipal toPrincipal(Object loaded) {
			if (loaded instanceof EdamPrincipal principal) {
				return principal;
			}
			if (loaded instanceof com.edam.identity.EdamUserDetails devUserDetails) {
				return devUserDetails.principal();
			}
			throw new AuthenticationServiceException("Identity adapter did not return an EDAM principal");
		}

		@Override
		public boolean supports(Class<?> authentication) {
			return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
		}

	}

	/**
	 * Explicit "no identity provider configured" provider: rejects every attempt and logs
	 * once per attempt so operators see misconfiguration immediately.
	 */
	static final class UnconfiguredAuthenticationProvider implements AuthenticationProvider {

		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
			.getLogger(UnconfiguredAuthenticationProvider.class);

		@Override
		public Authentication authenticate(Authentication authentication) {
			log.warn("security.authentication_rejected reason=no_identity_provider_configured");
			throw new AuthenticationServiceException("Authentication is not configured");
		}

		@Override
		public boolean supports(Class<?> authentication) {
			return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
		}

	}

}
