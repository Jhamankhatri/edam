package com.edam.config;

import com.edam.common.security.EdamUserDetailsService;
import com.edam.common.security.JsonApiAccessDeniedHandler;
import com.edam.common.security.JsonApiAuthenticationEntryPoint;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.PathPatternRequestMatcher;
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
 * {@code @PreAuthorize} on operations. Hiding a button in JSP is never a control.</li>
 * <li><strong>Institution context</strong> — resolved by
 * {@code InstitutionContext}; membership is a set, never a single assumed tenant.</li>
 * </ul>
 *
 * <p>Every request is authenticated except the login page, static assets and the health
 * probes (which are read-only and expose no business data).
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	private static final RequestMatcher[] PUBLIC_MATCHERS = {
			PathPatternRequestMatcher.pathPattern("/login"),
			PathPatternRequestMatcher.pathPattern("/css/**"),
			PathPatternRequestMatcher.pathPattern("/js/**"),
			PathPatternRequestMatcher.pathPattern("/images/**"),
			PathPatternRequestMatcher.pathPattern("/favicon.ico"),
			PathPatternRequestMatcher.pathPattern("/actuator/health"),
			PathPatternRequestMatcher.pathPattern("/actuator/health/**"),
			PathPatternRequestMatcher.pathPattern("/actuator/info") };

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper) throws Exception {
		http.authorizeHttpRequests(authorize -> authorize.requestMatchers(PUBLIC_MATCHERS).permitAll()
			.anyRequest()
			.authenticated())
			// CSRF protection stays enabled for the session-based JSP UI. REST clients must
			// send the token; disabling it would break the BRD's transactional safety goals.
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
			// The JSP UI is server-rendered and owns its own navigation.
			.requestCache(cache -> cache.disable());
		return http.build();
	}

	/**
	 * Content-Security-Policy for the server-rendered UI. {@code 'unsafe-inline'} is
	 * required only for style attributes emitted by the theme tokens; scripts stay
	 * external so that no inline JavaScript can execute.
	 */
	private static final String CONTENT_SECURITY_POLICY = "default-src 'self'; "
			+ "script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
			+ "font-src 'self'; form-action 'self'; frame-ancestors 'none'; base-uri 'self'; object-src 'none'";

	/**
	 * Password hashing for any credential EDAM stores. BCrypt with the default strength;
	 * the encoder is a bean so a future identity adapter can swap it without touching
	 * call sites.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * Authentication provider backed by the {@link EdamUserDetailsService} seam. When the
	 * local profile disables the fixture store there is no such service, the provider is
	 * not created, and login fails closed — the intended production posture until an
	 * identity integration is approved.
	 */
	@Bean
	org.springframework.security.authentication.AuthenticationProvider authenticationProvider(
			ObjectProvider<EdamUserDetailsService> userDetailsService, PasswordEncoder passwordEncoder) {
		EdamUserDetailsService service = userDetailsService.getIfAvailable();
		if (service == null) {
			return new NoOpAuthenticationProvider();
		}
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(service);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	/**
	 * Explicit "no identity provider configured" provider. Rejecting every attempt is
	 * safer than starting with no authentication provider at all, which can fail in ways
	 * that are easier to misconfigure.
	 */
	static final class NoOpAuthenticationProvider
			implements org.springframework.security.authentication.AuthenticationProvider {

		private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
			.getLogger(NoOpAuthenticationProvider.class);

		@Override
		public org.springframework.security.core.Authentication authenticate(
				org.springframework.security.core.Authentication authentication) {
			log.warn("security.authentication_rejected reason=no_identity_provider_configured");
			throw new org.springframework.security.authentication.AuthenticationServiceException(
					"Authentication is not configured");
		}

		@Override
		public boolean supports(Class<?> authentication) {
			return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class
				.isAssignableFrom(authentication);
		}

	}

}
