package com.edam.common.api;

/**
 * Catalogue of stable, machine-readable error codes returned to clients.
 *
 * <p>Codes are part of the public API contract and must never be renamed or reused for a
 * different meaning. The numeric range identifies the error family so that support staff
 * can classify an incident from a correlation ID and an error code alone.
 *
 * <pre>
 *   EDAM-1xxx  request/validation
 *   EDAM-2xxx  authentication/authorization
 *   EDAM-3xxx  business rule violations
 *   EDAM-4xxx  resource resolution
 *   EDAM-5xxx  persistence/infrastructure
 *   EDAM-9xxx  unexpected/internal
 * </pre>
 *
 * <p>Only codes listed here may reach a client. Internal exception details never do; see
 * {@code GlobalExceptionHandler}.
 */
public enum ErrorCode {

	/** A field or request payload failed validation. */
	VALIDATION_FAILED("EDAM-1001", "One or more fields are invalid."),
	/** A required request parameter was missing or malformed. */
	INVALID_REQUEST("EDAM-1002", "The request could not be processed as received."),
	/** Business rule rejected the operation; safe to show to the submitter. */
	BUSINESS_RULE_VIOLATION("EDAM-3001", "The operation is not permitted by the current business rules."),
	/** Concurrent modification detected while applying an operation. */
	CONCURRENT_MODIFICATION("EDAM-3002", "The record was changed by another user. Reload and try again."),
	/** Authentication is required to perform the operation. */
	UNAUTHENTICATED("EDAM-2001", "Sign in is required."),
	/** The authenticated user lacks permission for the operation. */
	ACCESS_DENIED("EDAM-2002", "You are not permitted to perform this operation."),
	/** The addressed resource does not exist or is not visible to the caller. */
	NOT_FOUND("EDAM-4001", "The requested resource was not found."),
	/** Persistence layer rejected or failed the operation. */
	PERSISTENCE_ERROR("EDAM-5001", "The request could not be saved. Please retry; contact support with the correlation ID if it persists."),
	/** Unexpected server-side failure; correlation ID is the only detail exposed. */
	INTERNAL_ERROR("EDAM-9001", "An unexpected error occurred.");

	private final String code;

	private final String safeMessage;

	ErrorCode(String code, String safeMessage) {
		this.code = code;
		this.safeMessage = safeMessage;
	}

	public String code() {
		return this.code;
	}

	/**
	 * Default, non-sensitive message that is safe to return to any client. Call sites may
	 * override it with an equally safe, more specific message.
	 */
	public String safeMessage() {
		return this.safeMessage;
	}

}
