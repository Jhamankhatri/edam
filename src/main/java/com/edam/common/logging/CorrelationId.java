package com.edam.common.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Request correlation identifier used to tie every log line, error response and audit
 * record of a single request together.
 *
 * <p>The value is always generated server-side as a UUID. An inbound correlation header is
 * accepted only when it strictly matches the UUID shape; anything else is discarded and
 * replaced. This prevents log injection and unbounded header values while still allowing
 * an upstream gateway to propagate an existing identifier.
 */
public final class CorrelationId {

	/** Request/response header carrying the correlation identifier. */
	public static final String HEADER = "X-Correlation-Id";

	/** MDC key used by the logging pattern and structured JSON output. */
	public static final String MDC_KEY = "correlationId";

	/** Legacy header names some clients send; read for compatibility, never echoed. */
	static final String[] ACCEPTED_INBOUND_HEADERS = { HEADER, "X-Request-Id", "X-Trace-Id" };

	private static final Pattern UUID_SHAPE = Pattern
		.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	private CorrelationId() {
	}

	/**
	 * Returns {@code candidate} when it is a well-formed UUID, otherwise generates a new
	 * one. Null, blank, over-long or structurally invalid values are rejected.
	 */
	public static String sanitizeOrGenerate(String candidate) {
		if (candidate != null && candidate.length() == 36 && UUID_SHAPE.matcher(candidate).matches()) {
			return candidate.toLowerCase();
		}
		return generate();
	}

	public static String generate() {
		return UUID.randomUUID().toString();
	}

	/** Correlation ID bound to the current thread, or {@code null} when outside a request. */
	public static String current() {
		return org.slf4j.MDC.get(MDC_KEY);
	}

}
