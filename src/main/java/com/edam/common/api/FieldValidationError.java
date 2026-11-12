package com.edam.common.api;

/**
 * Represents a single bean/request validation error for API clients.
 */
public record FieldValidationError(
        String field,
        String message,
        String code) {
}
