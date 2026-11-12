package com.edam.expense;

import java.util.EnumSet;
import java.util.Set;

/**
 * The request lifecycle (BRD §8.1..8.5, §7.4).
 *
 * <h2>This is a partial state machine, deliberately</h2>
 * The BRD names the statuses (Pending, Approved, Rejected, Reverted, Forwarded, Submitted) but states
 * that "the complete state machine, terminal statuses, allowed transitions, cancellation/deletion
 * policy and concurrency behaviour are not supplied. A formally approved state-transition matrix is a
 * release prerequisite" (BRD line 837). Implementing transitions beyond the ones the BRD does
 * describe would be inventing bank policy.
 *
 * <p>What is implemented is therefore exactly the behaviour the BRD states, and every transition
 * here is traceable to a sentence:
 * <ul>
 *   <li>a draft becomes {@code SUBMITTED} on submit (§8.2);</li>
 *   <li>a submitted request enters {@code IN_APPROVAL} when a task is open;</li>
 *   <li>all approvals complete → {@code APPROVED}; a rejection → {@code REJECTED} (§8.4);</li>
 *   <li>a revert returns the request to {@code RETURNED} for editing and resubmission (§7.4);</li>
 *   <li>a requester may cancel before any approval has been recorded (§8.1, gated by a feature
 *       flag because the exact boundary is OD-28);</li>
 *   <li>{@code REVERSED} is the post-approval reversal state the BRD requires for approved requests
 *       (audit trail plus status history) without defining the finance mechanics.</li>
 * </ul>
 *
 * <p>{@link #isTerminal()} is used by the API to refuse edits rather than silently ignoring them.
 */
public enum RequestState {

	/** Being prepared by the requester; not visible to approvers. */
	DRAFT,

	/** Submitted into a workflow; the first task is open. */
	SUBMITTED,

	/** Some approvals recorded; at least one task still pending. */
	IN_APPROVAL,

	/** Returned to the requester for correction (§7.4). Editable and resubmittable. */
	RETURNED,

	/** All required approvals recorded. */
	APPROVED,

	/** Rejected by an authorised approver. */
	REJECTED,

	/** Withdrawn by the requester before completion. */
	CANCELLED,

	/** An approved request that was later reversed; the original approval remains in history. */
	REVERSED;

	private static final Set<RequestState> TERMINAL = EnumSet.of(APPROVED, REJECTED, CANCELLED, REVERSED);

	/** True when no further workflow action is possible. */
	public boolean isTerminal() {
		return TERMINAL.contains(this);
	}

	/** True when the requester may change the content. */
	public boolean isEditable() {
		return this == DRAFT || this == RETURNED;
	}

	/** True when the request is somewhere in the approval pipeline. */
	public boolean isInFlight() {
		return this == SUBMITTED || this == IN_APPROVAL || this == RETURNED;
	}

}
