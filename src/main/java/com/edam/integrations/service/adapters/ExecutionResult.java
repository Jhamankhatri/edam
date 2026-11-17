package com.edam.integrations.service.adapters;

import java.util.Map;

/**
 * Structured outcome of an adapter execution.
 *
 * @param success           whether the operation completed successfully
 * @param externalReference provider-side identifier for correlation (nullable)
 * @param retryable         whether the failure is safe to retry (timeout/5xx/network)
 * @param errorCode         stable machine code for failures (nullable)
 * @param errorMessage      sanitised diagnostic (nullable; no secrets)
 * @param responseMetadata  bounded safe metadata for history (nullable; JSON-serialisable)
 */
public record ExecutionResult(
		boolean success,
		String externalReference,
		boolean retryable,
		String errorCode,
		String errorMessage,
		Map<String, Object> responseMetadata) {

	public static ExecutionResult succeeded(String externalReference, Map<String, Object> meta) {
		return new ExecutionResult(true, externalReference, false, null, null, meta);
	}
	public static ExecutionResult failed(boolean retryable, String code, String message,
			Map<String, Object> meta) {
		return new ExecutionResult(false, null, retryable, code, message, meta);
	}
}
