package com.edam.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-origin policy for the separately-deployed React client (Module C).
 *
 * <h2>Credentials force an explicit origin list</h2>
 * The API authenticates with a cookie, so requests must be credentialed. A browser rejects
 * {@code Access-Control-Allow-Origin: *} on a credentialed request, and rightly so — a wildcard would
 * let any site read an authenticated user's data. The allowed origins are therefore an explicit,
 * configurable list, and the list is never derived from the incoming request.
 *
 * <h2>Why the defaults are loopback only</h2>
 * The default is a development origin and nothing else. A production deployment must name its own
 * origin in {@code EDAM_CORS_ALLOWED_ORIGINS}; if it forgets, the browser refuses cross-origin calls
 * and the misconfiguration is loud rather than silently permissive.
 *
 * <h2>Preflight</h2>
 * {@code OPTIONS} preflight is answered by Spring's CORS filter before authorization runs, so a
 * preflight never requires a session — a browser cannot attach credentials to it. Only the methods and
 * headers the client actually needs are exposed.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

	private final List<String> allowedOrigins;

	private final long maxAgeSeconds;

	public CorsConfig(@Value("${edam.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String origins,
			@Value("${edam.cors.preflight-max-age-seconds:1800}") long maxAgeSeconds) {
		this.allowedOrigins = List.of(origins.split(",")).stream().map(String::trim)
			.filter(origin -> !origin.isEmpty() && !"*".equals(origin)).toList();
		this.maxAgeSeconds = maxAgeSeconds;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		// setAllowedOrigins (exact match) rather than allowedOriginPatterns: with credentials, an exact
		// origin list is the only form that cannot be widened by a pattern accident.
		configuration.setAllowedOrigins(this.allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-XSRF-TOKEN",
				"X-Requested-With", "X-Correlation-ID", "If-Match", "Idempotency-Key"));
		// The client needs these to read the CSRF token and the correlation id it should quote in a
		// support request. Nothing else is exposed.
		configuration.setExposedHeaders(List.of("X-XSRF-TOKEN", "X-Correlation-ID"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(this.maxAgeSeconds);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}

	/** The origins this deployment will accept, for a startup warning when none is configured. */
	List<String> allowedOrigins() {
		return this.allowedOrigins;
	}

}
