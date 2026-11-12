package com.edam.common.exception;

import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * Base runtime exception for predictable domain and application errors in EDAM.
 * User-facing messages carried by subclasses must remain safe for external display.
 */
public abstract class EdamException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;

    protected EdamException(ErrorCode errorCode, String safeMessage) {
        this(errorCode, errorCode.getDefaultHttpStatus(), safeMessage, null);
    }

    protected EdamException(ErrorCode errorCode, String safeMessage, Throwable cause) {
        this(errorCode, errorCode.getDefaultHttpStatus(), safeMessage, cause);
    }

    protected EdamException(ErrorCode errorCode, HttpStatus httpStatus, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.httpStatus = Objects.requireNonNull(httpStatus, "httpStatus must not be null");
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
