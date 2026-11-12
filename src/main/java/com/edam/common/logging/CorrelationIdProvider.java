package com.edam.common.logging;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * Provides safe access to the current request's correlation ID stored in SLF4J {@link MDC}.
 */
public final class CorrelationIdProvider {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_REQUEST_METHOD = "requestMethod";
    public static final String MDC_REQUEST_PATH = "requestPath";
    public static final String MDC_INSTITUTION_CODE = "institutionCode";
    public static final String MDC_ACTOR = "actor";

    private CorrelationIdProvider() {
    }

    /**
     * Returns the active request correlation ID from MDC, or generates a fallback ID if outside a web request.
     */
    public static String currentOrNew() {
        String existing = MDC.get(MDC_CORRELATION_ID);
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }
}
