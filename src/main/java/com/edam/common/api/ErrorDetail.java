package com.edam.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Standardized error detail structure exposed to API clients without internal implementation details.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorDetail(
        String code,
        String message,
        String path,
        List<FieldValidationError> fieldErrors) {

    public ErrorDetail {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public static ErrorDetail of(String code, String message, String path) {
        return new ErrorDetail(code, message, path, List.of());
    }

    public static ErrorDetail validation(String code, String message, String path, List<FieldValidationError> fieldErrors) {
        return new ErrorDetail(code, message, path, fieldErrors);
    }
}
