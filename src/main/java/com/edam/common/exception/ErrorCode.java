package com.edam.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Standardized platform and domain error codes returned in {@link com.edam.common.api.ErrorDetail}.
 */
public enum ErrorCode {

    VALIDATION_FAILED("EDAM-VAL-400", HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST("EDAM-REQ-400", HttpStatus.BAD_REQUEST),
    AUTHENTICATION_REQUIRED("EDAM-AUTH-401", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("EDAM-SEC-403", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("EDAM-NOT-FOUND-404", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED("EDAM-MNA-405", HttpStatus.METHOD_NOT_ALLOWED),
    CONFLICT("EDAM-CONFLICT-409", HttpStatus.CONFLICT),
    BUSINESS_RULE_VIOLATION("EDAM-BUS-422", HttpStatus.UNPROCESSABLE_CONTENT),
    DATABASE_ERROR("EDAM-DB-503", HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR("EDAM-SYS-500", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final HttpStatus defaultHttpStatus;

    ErrorCode(String code, HttpStatus defaultHttpStatus) {
        this.code = code;
        this.defaultHttpStatus = defaultHttpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getDefaultHttpStatus() {
        return defaultHttpStatus;
    }
}
