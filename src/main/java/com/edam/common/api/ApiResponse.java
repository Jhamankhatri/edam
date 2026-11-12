package com.edam.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;

/**
 * Standardized REST API response envelope used across all EDAM endpoints.
 *
 * @param <T> payload type for successful responses
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        Instant timestamp,
        String correlationId,
        String message,
        T data,
        ErrorDetail error) {

    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public static <T> ApiResponse<T> ok(int status, String correlationId, String message, T data) {
        return new ApiResponse<>(true, status, Instant.now(), correlationId, message, data, null);
    }

    public static <T> ApiResponse<T> ok(String correlationId, T data) {
        return ok(200, correlationId, null, data);
    }

    public static <T> ApiResponse<T> of(boolean success, int status, String correlationId, String message, T data) {
        return new ApiResponse<>(success, status, Instant.now(), correlationId, message, data, null);
    }

    public static ApiResponse<Void> error(int status, String correlationId, ErrorDetail error) {
        return new ApiResponse<>(false, status, Instant.now(), correlationId, error.message(), null, error);
    }
}
