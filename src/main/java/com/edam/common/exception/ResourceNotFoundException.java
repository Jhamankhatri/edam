package com.edam.common.exception;

/**
 * Raised when a requested entity or domain resource cannot be found within the active institution scope.
 */
public class ResourceNotFoundException extends EdamException {

    public ResourceNotFoundException(String safeMessage) {
        super(ErrorCode.RESOURCE_NOT_FOUND, safeMessage);
    }
}
