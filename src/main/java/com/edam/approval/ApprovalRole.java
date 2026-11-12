package com.edam.approval;

import com.edam.configplane.category.ApproverType;

/**
 * How a task is addressed to its approver.
 *
 * <p>Mapped one-to-one from {@link ApproverType} so the configuration plane's vocabulary cannot drift
 * from the engine's. Assignment rules (BRD §8.3):
 * <ul>
 *   <li>{@link #DEPARTMENT_HEAD}: addressed to the requester's department; any active member of that
 *       department holding the approval permission may act — the BRD selects the Department Head on
 *       the request form but the delegation authority model is still under confirmation (OD-25), so
 *       the task is department-addressed and the audit records exactly who acted.</li>
 *   <li>{@link #RECOMMENDING_AUTHORITY}: addressed to the specific employee configured for that
 *       authority in the delegation matrix.</li>
 *   <li>{@link #EXPENSE_COMMITTEE}: addressed to every member of the configured committee level, with
 *       the level's quorum deciding when the step completes.</li>
 *   <li>{@link #CEO} / {@link #CFO} / {@link #HEAD_OF_ADMIN_GSD}: addressed to the single active
 *       office holder.</li>
 * </ul>
 */
public enum ApprovalRole {

	DEPARTMENT_HEAD,

	RECOMMENDING_AUTHORITY,

	EXPENSE_COMMITTEE,

	CEO,

	CFO,

	HEAD_OF_ADMIN_GSD;

	public static ApprovalRole from(ApproverType type) {
		return switch (type) {
			case DEPARTMENT_HEAD -> DEPARTMENT_HEAD;
			case RECOMMENDING_AUTHORITY -> RECOMMENDING_AUTHORITY;
			case EXPENSE_COMMITTEE -> EXPENSE_COMMITTEE;
			case CEO -> CEO;
			case CFO -> CFO;
			case HEAD_OF_ADMIN_GSD -> HEAD_OF_ADMIN_GSD;
		};
	}

}
