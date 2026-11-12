package com.edam.common.exception;

import com.edam.common.api.ErrorCode;

/**
 * Root of EDAM's business exception hierarchy.
 *
 * <p>Every instance carries a stable {@link ErrorCode} and a message that is explicitly
 * safe to show to the caller. Messages must describe the business condition ("Request
 * approval amount must be greater than zero") and must never embed SQL, class names,
 * stack traces or personal data.
 */
public abstract class EdamException extends RuntimeException {

	private final ErrorCode errorCode;

	protected EdamException(ErrorCode errorCode, String safeMessage) {
		super(safeMessage);
		this.errorCode = errorCode;
	}

	protected EdamException(ErrorCode errorCode, String safeMessage, Throwable cause) {
		super(safeMessage, cause);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return this.errorCode;
	}

}
