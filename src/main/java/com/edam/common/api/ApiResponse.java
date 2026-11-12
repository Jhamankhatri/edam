package com.edam.common.api;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard envelope for every non-HTML response produced by EDAM.
 *
 * <p>Introspection is intentionally shallow: domain errors carry a code and a safe
 * message, never stack traces, SQL, class names or infrastructure details. The
 * {@code correlationId} is the bridge to the server-side log; support staff use it to
 * locate the full diagnostic record without exposing it to the caller.
 *
 * @param <T> payload type
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		boolean success,
		T data,
		ErrorBody error,
		List<FieldErrorDetail> fieldErrors,
		String correlationId,
		String path,
		Instant timestamp) {

	public ApiResponse {
		fieldErrors = (fieldErrors == null) ? null : List.copyOf(fieldErrors);
		timestamp = (timestamp == null) ? Instant.now() : timestamp;
	}

	/** Successful response with a payload. */
	public static <T> ApiResponse<T> success(T data, String correlationId, String path) {
		return new ApiResponse<>(true, data, null, null, correlationId, path, Instant.now());
	}

	/** Successful response without a payload (e.g. an acknowledgement). */
	public static ApiResponse<Void> success(String correlationId, String path) {
		return new ApiResponse<>(true, null, null, null, correlationId, path, Instant.now());
	}

	/** Failure carrying a stable error code and a safe message. */
	public static <T> ApiResponse<T> failure(ErrorCode code, String safeMessage, String correlationId, String path) {
		return new ApiResponse<>(false, null, new ErrorBody(code.code(), safeMessage), null, correlationId, path,
				Instant.now());
	}

	/** Failure whose cause is validation; {@code fieldErrors} explains each field. */
	public static <T> ApiResponse<T> validationFailure(List<FieldErrorDetail> fieldErrors, String correlationId,
			String path) {
		return new ApiResponse<>(false, null,
				new ErrorBody(ErrorCode.VALIDATION_FAILED.code(), ErrorCode.VALIDATION_FAILED.safeMessage()),
				fieldErrors, correlationId, path, Instant.now());
	}

	/**
	 * Machine-readable error identity. {@code code} is stable; {@code message} is safe for
	 * end users and must never contain internal detail.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ErrorBody(String code, String message) {
	}

	/**
	 * One rejected field. {@code field} is the request-path expression; {@code message} is
	 * the validation message. Rejected values are deliberately not echoed back.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record FieldErrorDetail(String field, String message) {
	}

}
