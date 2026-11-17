package com.edam.integrations.service.adapters;

/**
 * Result of an adapter connection test.
 *
 * @param success        whether the provider confirmed connectivity and authentication
 * @param message        sanitised human-readable diagnostic (must not include credentials)
 * @param respondedInMs  observed latency, or null if no response was received
 * @param diagnosticCode stable machine-readable code (e.g. "AUTH_FAILED", "TIMEOUT", "OK")
 */
public record ConnectionTestResult(
		boolean success,
		String message,
		Integer respondedInMs,
		String diagnosticCode) {

	public static ConnectionTestResult ok(Integer latencyMs) {
		return new ConnectionTestResult(true, "Connection successful.", latencyMs, "OK");
	}
	public static ConnectionTestResult failure(String code, String sanitizedMessage, Integer latencyMs) {
		return new ConnectionTestResult(false, sanitizedMessage, latencyMs, code);
	}
}
