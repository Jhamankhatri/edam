package com.edam.common.exception;

/**
 * Raised on duplicate business keys, concurrent modifications, or state transition conflicts.
 */
public class ConflictException extends EdamException {

    public ConflictException(String safeMessage) {
        super(ErrorCode.CONFLICT, safeMessage);
    }
}
