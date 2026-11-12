package com.edam.common.exception;

/**
 * Raised when an authenticated user lacks domain-level permission or institution membership scope.
 */
public class AccessDeniedDomainException extends EdamException {

    public AccessDeniedDomainException(String safeMessage) {
        super(ErrorCode.ACCESS_DENIED, safeMessage);
    }
}
