package com.edam.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns or validates a correlation ID on every incoming HTTP request,
 * propagates it to SLF4J MDC and response headers, and emits a structured
 * request completion summary without logging payloads or sensitive headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long startNanos = System.nanoTime();
        String incoming = request.getHeader(CorrelationIdProvider.CORRELATION_ID_HEADER);
        String correlationId = LogSanitizer.isValidCorrelationId(incoming)
                ? incoming
                : UUID.randomUUID().toString();

        String safeMethod = LogSanitizer.sanitize(request.getMethod());
        String safePath = LogSanitizer.sanitize(request.getRequestURI());

        MDC.put(CorrelationIdProvider.MDC_CORRELATION_ID, correlationId);
        MDC.put(CorrelationIdProvider.MDC_REQUEST_METHOD, safeMethod);
        MDC.put(CorrelationIdProvider.MDC_REQUEST_PATH, safePath);
        response.setHeader(CorrelationIdProvider.CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.getStatus();
            if (isStaticAssetPath(safePath)) {
                log.debug("Static asset request completed method={} path={} status={} durationMs={}",
                        safeMethod, safePath, status, durationMs);
            } else {
                log.info("HTTP request completed method={} path={} status={} durationMs={}",
                        safeMethod, safePath, status, durationMs);
            }
            MDC.clear();
        }
    }

    private boolean isStaticAssetPath(String path) {
        return path.startsWith("/assets/") || "/favicon.ico".equals(path);
    }
}
