package com.edam.common.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wraps every incoming request so the body can be read more than once: once by the idempotency
 * interceptor for fingerprinting and again by Spring MVC for binding and validation. The body
 * is buffered on construction via {@link CachedBodyHttpServletRequest}.
 *
 * <p>Runs after the correlation-id filter (which is HIGHEST_PRECEDENCE) but before Spring Security
 * and the dispatcher servlet, so that any handler that reads the body — including the idempotency
 * aspect — sees a repeatable stream.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class CachedBodyFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request instanceof CachedBodyHttpServletRequest) {
			chain.doFilter(request, response);
			return;
		}
		// Only wrap stateful requests that carry a body; wrapping GET/HEAD/DELETE without a body
		// is harmless but avoids an unnecessary buffer allocation.
		String method = request.getMethod();
		boolean mayHaveBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
				|| "PATCH".equalsIgnoreCase(method);
		if (mayHaveBody) {
			chain.doFilter(new CachedBodyHttpServletRequest(request), response);
		}
		else {
			chain.doFilter(request, response);
		}
	}

}
