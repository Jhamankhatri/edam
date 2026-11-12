package com.edam.common.audit;

/**
 * The verb of an audit event. Deliberately a closed set: BRD AUD-01 requires searching audit
 * history by action, which needs stable, filterable values rather than free text.
 */
public enum AuditAction {

	/** A record was created. */
	CREATE,
	/** A record's mutable fields were changed. */
	UPDATE,
	/** A record was activated or deactivated. */
	ACTIVATE,
	/** A record was deactivated. */
	DEACTIVATE,
	/** A record was deleted (physically or through an approved soft-delete policy). */
	DELETE,
	/** A configuration version was published and made effective. */
	PUBLISH,
	/** A previously published configuration version was restored. */
	ROLLBACK,
	/** A request was submitted into its workflow. */
	SUBMIT,
	/** An approval action was applied to a task. */
	APPROVE,
	/** An approval task was rejected. */
	REJECT,
	/** A request was returned to a previous approver or its creator. */
	REVERT,
	/** An approval task was forwarded to another user. */
	FORWARD,
	/** A request was cancelled. */
	CANCEL,
	/** A document was uploaded or replaced. */
	DOCUMENT_UPLOAD,
	/** A document was downloaded (authorized access). */
	DOCUMENT_DOWNLOAD,
	/** A document was deleted. */
	DOCUMENT_DELETE,
	/** A secret or credential was created or rotated. The value is never recorded. */
	SECRET_ROTATE,
	/** Data was exported. */
	EXPORT,
	/** A user signed in successfully. */
	LOGIN_SUCCESS,
	/** A sign-in attempt failed. */
	LOGIN_FAILURE,
	/** Authorization was denied for an authenticated caller. */
	ACCESS_DENIED

}
