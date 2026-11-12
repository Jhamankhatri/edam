package com.edam.approval;

/** Status of a single approval task (BRD §8.4). */
public enum ApprovalTaskStatus {

	/** Awaiting action. Exactly one per request at a time (unique index). */
	PENDING,

	/** Approved by the assignee. */
	APPROVED,

	/** Rejected by the assignee. Terminal for the request. */
	REJECTED,

	/** Returned to the requester (or to the last Phase-1 approver, for an EC revert). */
	REVERTED,

	/** Passed to another employee; a new task carries the decision onward. */
	FORWARDED,

	/** Request withdrawn before this task was actioned. */
	CANCELLED,

	/** Not needed: the request was rejected or approved at an earlier step. */
	SKIPPED

}
