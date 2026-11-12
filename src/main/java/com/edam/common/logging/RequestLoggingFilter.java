package com.edam.common.logging;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Emits exactly one structured access log line per request containing method, path,
 * status and duration.
 *
 * <p>Design notes:
 * <ul>
 * <li>Query strings are <strong>never</strong> logged: they may carry identifiers or
 * search terms that constitute personal data.</li>
 * <li>Static resources are skipped to keep production logs signal-rich.</li>
 * <li>Server errors are logged at ERROR (with stack traces handled by the exception
 * handlers); 4xx are logged at WARN so abuse and client faults remain visible.</li>
 * <li>No request or response bodies are logged, ever.</li>
 * </ul>
 *
 * <p>Runs after {@link CorrelationIdFilter} so that the MDC already holds the
 * correlation ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	/** Prefixes of requests that would otherwise flood the log with noise. */
	private static final Set<String> STATIC_PREFIXES = Set.of("/css/", "/js/", "/images/", "/favicon.ico",
			"/webjars/");

	/** Paths excluded entirely: liveness probes are logged by the platform, not here. */
	private static final Set<String> EXCLUDED_PATHS = Set.of("/actuator/health", "/actuator/health/liveness",
			"/actuator/health/readiness");

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (EXCLUDED_PATHS.contains(path)) {
			return true;
		}
		String contextPath = request.getContextPath();
		String applicationPath = (contextPath != null && !contextPath.isEmpty()) ? path.substring(contextPath.length())
				: path;
		return STATIC_PREFIXES.stream().anyMatch(applicationPath::startsWith);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		try {
			chain.doFilter(request, response);
		}
		finally {
			long durationMillis = (System.nanoTime() - start) / 1_000_000;
			int status = response.getStatus();
			if (status >= 500) {
				log.error("http.request method={} path={} status={} durationMs={}", request.getMethod(),
						safePath(request), status, durationMillis);
			}
			else if (status >= 400) {
				log.warn("http.request method={} path={} status={} durationMs={}", request.getMethod(), safePath(request),
						status, durationMillis);
			}
			else {
				log.info("http.request method={} path={} status={} durationMs={}", request.getMethod(), safePath(request),
						status, durationMillis);
			}
		}
	}

	/**
	 * Returns the request URI without the context path and with any control characters
	 * stripped. Path variables are high-cardinality but non-sensitive; keeping them makes
	 * the log actionable. Query strings are intentionally excluded.
	 */
	private String safePath(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path == null) {
			return "";
		}
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}
		StringBuilder sanitized = new StringBuilder(path.length());
		path.codePoints().forEach(codePoint -> {
			if (codePoint < 0x20 || codePoint == 0x7f) {
				sanitized.append('_');
			}
			else {
				sanitized.appendCodePoint(codePoint);
			}
		});
		return sanitized.toString().toLowerCase(Locale.ROOT).length() == 0 ? "/" : sanitized.toString();
	}

}
