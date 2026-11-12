package com.edam.common.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the correlation identifier for every request, exposes it on the response and
 * binds it to the SLF4J MDC so that all log lines emitted while handling the request carry
 * it. Registered first so that even failures in later filters are traceable.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String correlationId = resolve(request);
		MDC.put(CorrelationId.MDC_KEY, correlationId);
		try {
			response.setHeader(CorrelationId.HEADER, correlationId);
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove(CorrelationId.MDC_KEY);
		}
	}

	private String resolve(HttpServletRequest request) {
		for (String header : CorrelationId.ACCEPTED_INBOUND_HEADERS) {
			String value = request.getHeader(header);
			if (value != null && !value.isBlank()) {
				return CorrelationId.sanitizeOrGenerate(value);
			}
		}
		return CorrelationId.generate();
	}

}
