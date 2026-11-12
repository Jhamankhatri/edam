package com.edam.common.exception;

/**
 * Raised when a domain or configurable business rule validation fails.
 */
public class BusinessException extends EdamException {

    public BusinessException(String safeMessage) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, safeMessage);
    }

    public BusinessException(ErrorCode errorCode, String safeMessage) {
        super(errorCode, safeMessage);
    }
}
