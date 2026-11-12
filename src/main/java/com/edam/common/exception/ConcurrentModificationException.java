package com.edam.common.exception;

import com.edam.common.api.ErrorCode;

/**
 * Optimistic-locking conflict: another user modified the record between load and save.
 * Maps to HTTP 409 so the UI can reload and re-present the operation.
 */
public class ConcurrentModificationException extends EdamException {

	public ConcurrentModificationException() {
		super(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.safeMessage());
	}

}
