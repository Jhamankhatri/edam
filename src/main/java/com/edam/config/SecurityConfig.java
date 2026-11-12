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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
 * <p>Every request requires authentication except the login page, static assets and the
 * health probes, which are read-only and expose no business data.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * Content-Security-Policy for the server-rendered UI. {@code 'unsafe-inline'} is
	 * required only for the institution theme's style block; scripts stay external so no
	 * inline JavaScript can execute.
	 */
	private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; "
			+ "script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
			+ "font-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
		RequestMatcher[] publicMatchers = { PathRequest.toStaticResources().atCommonLocations(),
				PathPatternRequestMatcher.pathPattern("/login"),
				// Rendered by a view controller. A 403 must explain itself, not redirect to
				// the login page, which would suggest an authentication problem.
				PathPatternRequestMatcher.pathPattern("/access-denied"),
				// The container's error dispatch re-enters the filter chain. Without this,
				// an authenticated 500 would turn into a 302 to /login.
				PathPatternRequestMatcher.pathPattern("/error"),
				PathPatternRequestMatcher.pathPattern("/css/**"),
				PathPatternRequestMatcher.pathPattern("/js/**"),
				PathPatternRequestMatcher.pathPattern("/images/**"),
				PathPatternRequestMatcher.pathPattern("/favicon.ico"),
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
			.formLogin(form -> form.loginPage("/login")
				.defaultSuccessUrl("/dashboard", true)
				.failureUrl("/login?error")
				.permitAll())
			.logout(logout -> logout.logoutUrl("/logout")
				.logoutSuccessUrl("/login?logout")
				.invalidateHttpSession(true)
				.deleteCookies("JSESSIONID"))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				.sessionFixation(fixation -> fixation.migrateSession()))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(new JsonApiAuthenticationEntryPoint(jsonMapper))
				.accessDeniedHandler(new JsonApiAccessDeniedHandler(jsonMapper)))
			.headers(headers -> headers.frameOptions(frame -> frame.deny())
				.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY)))
			// The JSP UI owns its own navigation and has no saved-request flow.
			.requestCache(cache -> cache.disable());
		return http.build();
	}

	/**
	 * Password hashing for any credential EDAM stores. BCrypt with the default strength;
	 * a bean so a future identity adapter can swap it without touching call sites.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
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
			if (loaded instanceof com.edam.identity.DevUserDetails devUserDetails) {
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
