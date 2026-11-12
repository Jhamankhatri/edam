package com.edam.configplane.category;

/**
 * The approver kinds a delegation rule may select (BRD FM-07, §10.1).
 *
 * <p>The documented route is Phase 1 (department/unit head) → recommending authority → Expense
 * Committee level(s), with CEO and/or CFO participation when configured. Standalone CEO/HOD routing
 * is described in the BRD but explicitly <em>not</em> specified (§10.4), so the corresponding kinds
 * exist as data vocabulary while the routing engine refuses to build a route from them alone.
 */
public enum ApproverType {

	/** Phase 1: the requesting department's head. */
	DEPARTMENT_HEAD,

	/** The configured recommending authority for the category. */
	RECOMMENDING_AUTHORITY,

	/** A configured Expense Committee level. */
	EXPENSE_COMMITTEE,

	/** Chief Executive Officer participation. */
	CEO,

	/** Chief Financial Officer participation. */
	CFO,

	/** Head of Admin &amp; General Services Department. */
	HEAD_OF_ADMIN_GSD

}
