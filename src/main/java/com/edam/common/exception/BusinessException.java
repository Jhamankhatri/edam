package com.edam.common.exception;

import com.edam.common.api.ErrorCode;

/**
 * A documented business rule rejected an operation. The message is safe to display and
 * should identify the rule violated. Thrown from the application/domain layer only; the
 * web layer never decides business validity.
 */
public class BusinessException extends EdamException {

	public BusinessException(String safeMessage) {
		super(ErrorCode.BUSINESS_RULE_VIOLATION, safeMessage);
	}

	public BusinessException(ErrorCode errorCode, String safeMessage) {
		super(errorCode, safeMessage);
	}

}
