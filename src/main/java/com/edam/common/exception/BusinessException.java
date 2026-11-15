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

	public BusinessException(ErrorCode errorCode, String safeMessage, Throwable cause) {
		super(errorCode, safeMessage, cause);
	}

	/**
	 * Failure identified only by its catalogue entry: the catalogue's safe message is used verbatim.
	 * Preferred when the catalogue text already says exactly what the caller needs to know,
	 * because it keeps one wording in one place.
	 */
	public BusinessException(ErrorCode errorCode) {
		super(errorCode, errorCode.safeMessage());
	}

}
