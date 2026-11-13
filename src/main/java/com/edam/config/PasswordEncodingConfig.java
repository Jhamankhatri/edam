package com.edam.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The password encoder, in its own configuration class.
 *
 * <h2>Why this is not in {@code SecurityConfig}</h2>
 * It was, and that created a bean cycle: {@code SecurityConfig} builds the filter chain, which needs the
 * authentication provider, which needs the {@code EdamUserDetailsService}; but that service's factory also
 * takes a {@code PasswordEncoder} declared inside {@code SecurityConfig} itself. Spring resolved the
 * encoder as "declared on a class that is currently being configured", and the whole context failed to
 * start.
 *
 * <p>Declaring the encoder separately breaks the cycle structurally rather than by adding
 * {@code @Lazy} somewhere — a lazy proxy would have hidden the dependency instead of removing it, and the
 * next person to touch either class would reintroduce the problem.
 *
 * <h2>Why BCrypt with default strength</h2>
 * It is salted, deliberately slow and supported by Spring Security without extra configuration. A faster
 * hash would be the wrong trade for an authentication secret. The cost factor is left at the library
 * default so that a future increase is a deliberate, reviewable change rather than an inherited one.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncodingConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
