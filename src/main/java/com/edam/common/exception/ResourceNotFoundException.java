package com.edam.common.exception;

import com.edam.common.api.ErrorCode;

/**
 * The addressed resource does not exist or is outside the caller's permitted scope.
 *
 * <p>The two cases are deliberately indistinguishable to the caller: revealing that an
 * out-of-scope record exists would be an information leak across institutions.
 */
public class ResourceNotFoundException extends EdamException {

	public ResourceNotFoundException(String resourceName, Object identifier) {
		super(ErrorCode.NOT_FOUND, resourceName + " was not found.");
	}

	public ResourceNotFoundException(String safeMessage) {
		super(ErrorCode.NOT_FOUND, safeMessage);
	}

}
