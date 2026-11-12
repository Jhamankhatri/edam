package com.edam.workflow;

/** Lifecycle of a workflow version (BRD §8.6: draft, publish, roll back). */
public enum WorkflowVersionStatus {

	/** Editable; never used to route a request. */
	DRAFT,

	/** Immutable and executable. At most one per definition (enforced by a unique index). */
	PUBLISHED,

	/** Superseded. Requests that pinned it continue to use it. */
	RETIRED

}
