package com.edam.common.security;

/**
 * Operational permission constants checked by {@code @PreAuthorize} expressions.
 *
 * <p>Deliberately generic: no banking job title is hard-coded. An institution maps its own
 * role names to these permissions in configuration. The BRD's full role-to-permission
 * matrix is an open decision (OD-12); these constants are the technical vocabulary, not an
 * approved business matrix.
 */
public final class Permissions {

	/** View the caller's own expense requests. */
	public static final String REQUEST_VIEW_OWN = "request:view-own";

	/** Create an OPEX or CAPEX request. */
	public static final String REQUEST_CREATE = "request:create";

	/** Act on an approval task assigned to the caller. */
	public static final String APPROVAL_ACT = "approval:act";

	/** Maintain categories, delegation matrix, currencies, limits and authorities. */
	public static final String CONFIGURATION_MANAGE = "configuration:manage";

	/** View reports and export data. */
	public static final String REPORTING_VIEW = "reporting:view";

	/** Read configuration and request audit trails. */
	public static final String AUDIT_VIEW = "audit:view";

	/** Administer institution settings, roles and users. */
	public static final String INSTITUTION_ADMINISTER = "institution:administer";

	private Permissions() {
	}

}
