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
	/** A page or size parameter is outside the permitted bounds. */
	INVALID_PAGINATION("EDAM-1003", "The requested page or page size is not permitted."),
	/** The request body or path named an institution the caller cannot act for. */
	INSTITUTION_REQUIRED("EDAM-2003", "Select the institution you are acting for before continuing."),
	/** The authenticated identity is not a member of the addressed institution. */
	INSTITUTION_ACCESS_DENIED("EDAM-2004", "You are not permitted to act for this institution."),
	/** The resource is visible but the caller lacks the specific permission. */
	PERMISSION_DENIED("EDAM-2005", "You do not have permission to perform this operation."),
	/** An idempotency key was reused with a different request payload. */
	IDEMPOTENCY_CONFLICT("EDAM-1004", "This request was already submitted with different content."),
	/** An identical request using the same idempotency key is currently being processed. */
	IDEMPOTENCY_IN_PROGRESS("EDAM-1005", "An identical request is already being processed. Retry after a short delay."),
	/**
	 * A prior request with the same idempotency key did not complete normally (process crash
	 * between business commit and record finalisation). The client must query the resource to
	 * determine outcome; the server will not execute the operation a second time.
	 */
	IDEMPOTENCY_UNKNOWN_OUTCOME("EDAM-1006", "A prior request with this idempotency key did not complete normally."),
	/** A required precondition (e.g. If-Match version) was not supplied or did not match. */
	PRECONDITION_FAILED("EDAM-3003", "The record changed since it was loaded. Reload and try again."),
	/** A configuration or state transition is not permitted in the current state. */
	INVALID_STATE_TRANSITION("EDAM-3004", "The operation is not permitted in the current state."),
	/** Business rule rejected the operation; safe to show to the submitter. */
	BUSINESS_RULE_VIOLATION("EDAM-3001", "The operation is not permitted by the current business rules."),
	/** Concurrent modification detected while applying an operation. */
	CONCURRENT_MODIFICATION("EDAM-3002", "The record was changed by another user. Reload and try again."),
	/** Authentication is required to perform the operation. */
	UNAUTHENTICATED("EDAM-2001", "Sign in is required."),
	/** The authenticated user lacks permission for the operation. */
	ACCESS_DENIED("EDAM-2002", "You are not permitted to perform this operation."),
	/**
	 * Credentials were rejected. Deliberately identical for an unknown user, a wrong password and an
	 * inactive account: any distinction lets an attacker enumerate accounts.
	 */
	INVALID_CREDENTIALS("EDAM-2006", "The username or password is incorrect."),
	/** The account is temporarily locked after repeated failed sign-in attempts. */
	ACCOUNT_LOCKED("EDAM-2007", "This account is temporarily locked after repeated failed sign-in attempts."),
	/** The presented password does not satisfy the configured policy. */
	WEAK_PASSWORD("EDAM-2008", "The password does not meet the required policy."),
	/** A password-reset token is unknown, already used or expired. */
	INVALID_RESET_TOKEN("EDAM-2009", "This reset link is invalid or has expired."),
	/** Installation has already been completed, so bootstrap is closed. */
	INSTALLATION_ALREADY_COMPLETED("EDAM-2010", "This installation has already been set up."),
	/** The installation has not been bootstrapped yet, so no user can sign in. */
	INSTALLATION_NOT_BOOTSTRAPPED("EDAM-2011", "This installation has not been set up yet."),
	/** The caller tried to change a protected system role in a way that would break the product. */
	SYSTEM_ROLE_PROTECTED("EDAM-3005", "This system role cannot be changed in that way."),
	/** The addressed resource does not exist or is not visible to the caller. */
	NOT_FOUND("EDAM-4001", "The requested resource was not found."),
	/** Persistence layer rejected or failed the operation. */
	PERSISTENCE_ERROR("EDAM-5001", "The request could not be saved. Please retry; contact support with the correlation ID if it persists."),
	/** The request conflicts with an existing record's unique business key. */
	DUPLICATE_RESOURCE("EDAM-5002", "A record with those details already exists."),
	/** An external provider (storage, mail) failed. */
	EXTERNAL_SERVICE_FAILURE("EDAM-5003", "A connected service could not complete the operation. Please retry."),
	/** A document upload was rejected. */
	INVALID_DOCUMENT("EDAM-5004", "The file type, size or content is not permitted."),
	/** A configuration value failed its domain validation. */
	INVALID_CONFIGURATION("EDAM-5005", "The configuration is not valid."),
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
