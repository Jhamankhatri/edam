package com.edam.storage;

import com.edam.common.exception.BusinessException;
import com.edam.common.api.ErrorCode;

/**
 * Thrown when a storage provider fails (upload / download / delete) in a way that the
 * caller must respond to. The message never includes credentials or signed URLs.
 */
public class StorageException extends BusinessException {
	public StorageException(String message) { super(ErrorCode.EXTERNAL_SERVICE_FAILURE, message); }
	public StorageException(String message, Throwable cause) { super(ErrorCode.EXTERNAL_SERVICE_FAILURE, message, cause); }
}
