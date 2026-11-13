package com.edam.common.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The product permission catalogue, mirrored from the {@code permissions} table.
 *
 * <h2>Why a catalogue in code AND a table in the database</h2>
 * The database is the runtime authority: a role's grants are rows, and a bank can compose roles
 * without a deployment. But the code must also know which capabilities <em>exist</em>, because a
 * permission nobody enforces is a lie in the UI and, worse, a permission that is enforced but absent
 * here cannot be granted at all.
 *
 * <p>So this class is the contract: every constant is installed by {@code V12__permission_catalogue}
 * and every {@code @EdamPermission} value must appear in {@link #all()}, which
 * {@code EndpointAuthorizationArchitectureTest} verifies. A code added here without a migration row
 * (or a row without a constant) is a defect the architecture test cannot catch, so
 * {@code PermissionCatalogueConsistencyTest} compares the two at runtime against the real schema.
 *
 * <h2>Naming</h2>
 * {@code <module>:<action>}, lower case, hyphenated. Module and action are independent dimensions, so
 * "expense-configuration:manage" does not imply any authority over requests or audit.
 *
 * <h2>What is deliberately absent</h2>
 * No job titles, no institution names, and no approval authority. Approval authority is never a bare
 * permission: {@link #APPROVAL_ACT} only permits acting on a task the workflow engine has assigned to
 * the caller.
 */
public final class Permissions {

	private Permissions() {
	}

	// ------------------------------------------------------------------
	// Organization and configuration (Module A, B)
	// ------------------------------------------------------------------

	public static final String ORGANIZATION_VIEW = "organization:view";
	public static final String ORGANIZATION_MANAGE = "organization:manage";
	public static final String CONFIGURATION_VIEW = "configuration:view";
	public static final String CONFIGURATION_MANAGE = "configuration:manage";
	public static final String SYSTEM_ADMINISTER = "system:administer";

	// ------------------------------------------------------------------
	// Access management (Module C)
	// ------------------------------------------------------------------

	public static final String USER_VIEW = "user:view";
	public static final String USER_MANAGE = "user:manage";
	public static final String ROLE_VIEW = "role:view";
	public static final String ROLE_MANAGE = "role:manage";
	public static final String USER_ROLE_ASSIGN = "user-role:assign";
	public static final String DATA_SCOPE_MANAGE = "data-scope:manage";

	// ------------------------------------------------------------------
	// Financial master data (Module D)
	// ------------------------------------------------------------------

	public static final String CURRENCY_VIEW = "currency:view";
	public static final String CURRENCY_MANAGE = "currency:manage";
	public static final String VENDOR_VIEW = "vendor:view";
	public static final String VENDOR_MANAGE = "vendor:manage";
	public static final String TAX_AUTHORITY_MANAGE = "tax-authority:manage";

	// ------------------------------------------------------------------
	// Expense configuration and delegation (Module E)
	// ------------------------------------------------------------------

	public static final String EXPENSE_CONFIGURATION_VIEW = "expense-configuration:view";
	public static final String EXPENSE_CONFIGURATION_MANAGE = "expense-configuration:manage";
	public static final String DELEGATION_VIEW = "delegation:view";
	public static final String DELEGATION_MANAGE = "delegation:manage";
	public static final String APPROVAL_LIMIT_VIEW = "approval-limit:view";
	public static final String APPROVAL_LIMIT_MANAGE = "approval-limit:manage";
	public static final String RECOMMENDING_AUTHORITY_VIEW = "recommending-authority:view";
	public static final String RECOMMENDING_AUTHORITY_MANAGE = "recommending-authority:manage";
	public static final String EXCEEDING_LIMIT_VIEW = "exceeding-limit:view";
	public static final String EXCEEDING_LIMIT_MANAGE = "exceeding-limit:manage";

	// ------------------------------------------------------------------
	// Committees (Module F)
	// ------------------------------------------------------------------

	public static final String COMMITTEE_VIEW = "committee:view";
	public static final String COMMITTEE_MANAGE = "committee:manage";

	// ------------------------------------------------------------------
	// Requests (Module G)
	// ------------------------------------------------------------------

	public static final String REQUEST_CREATE = "request:create";
	public static final String REQUEST_VIEW_OWN = "request:view-own";
	public static final String REQUEST_VIEW_SCOPE = "request:view-scope";
	public static final String REQUEST_VIEW_ALL = "request:view-all";
	public static final String REQUEST_EDIT = "request:edit";
	public static final String REQUEST_SUBMIT = "request:submit";
	public static final String REQUEST_CANCEL = "request:cancel";
	public static final String REQUEST_REVERSE = "request:reverse";
	public static final String REQUEST_EXPORT = "request:export";

	// ------------------------------------------------------------------
	// Documents (Module H)
	// ------------------------------------------------------------------

	public static final String DOCUMENT_UPLOAD = "document:upload";
	public static final String DOCUMENT_DOWNLOAD = "document:download";
	public static final String DOCUMENT_DELETE = "document:delete";

	// ------------------------------------------------------------------
	// Approvals (Module I)
	// ------------------------------------------------------------------

	public static final String APPROVAL_VIEW = "approval:view";
	public static final String APPROVAL_ACT = "approval:act";

	// ------------------------------------------------------------------
	// Workflow and rules (Modules I, J)
	// ------------------------------------------------------------------

	public static final String WORKFLOW_VIEW = "workflow:view";
	public static final String WORKFLOW_MANAGE = "workflow:manage";
	public static final String WORKFLOW_PUBLISH = "workflow:publish";
	public static final String RULE_VIEW = "rule:view";
	public static final String RULE_MANAGE = "rule:manage";
	public static final String RULE_PUBLISH = "rule:publish";

	// ------------------------------------------------------------------
	// Audit (Module K)
	// ------------------------------------------------------------------

	public static final String AUDIT_VIEW = "audit:view";
	public static final String AUDIT_EXPORT = "audit:export";

	// ------------------------------------------------------------------
	// Notifications and email (Module L)
	// ------------------------------------------------------------------

	public static final String NOTIFICATION_VIEW = "notification:view";
	public static final String EMAIL_CONFIGURATION_MANAGE = "email-configuration:manage";
	public static final String NOTIFICATION_TEMPLATE_MANAGE = "notification-template:manage";

	// ------------------------------------------------------------------
	// Reporting and dashboards (Module M)
	// ------------------------------------------------------------------

	public static final String REPORT_VIEW = "report:view";
	public static final String REPORT_EXPORT = "report:export";
	public static final String DASHBOARD_MANAGE = "dashboard:manage";

	// ------------------------------------------------------------------
	// Integrations (Module N)
	// ------------------------------------------------------------------

	public static final String INTEGRATION_VIEW = "integration:view";
	public static final String INTEGRATION_MANAGE = "integration:manage";

	/**
	 * Every catalogue code.
	 *
	 * <p>Used by the endpoint architecture test (a declared permission must exist) and by the
	 * catalogue consistency test (this set must equal the rows in {@code permissions}). Kept as an
	 * explicit set rather than reflection so that a typo in a constant name cannot silently add a
	 * permission.
	 */
	public static Set<String> all() {
		Set<String> codes = new LinkedHashSet<>();
		codes.add(ORGANIZATION_VIEW);
		codes.add(ORGANIZATION_MANAGE);
		codes.add(CONFIGURATION_VIEW);
		codes.add(CONFIGURATION_MANAGE);
		codes.add(SYSTEM_ADMINISTER);
		codes.add(USER_VIEW);
		codes.add(USER_MANAGE);
		codes.add(ROLE_VIEW);
		codes.add(ROLE_MANAGE);
		codes.add(USER_ROLE_ASSIGN);
		codes.add(DATA_SCOPE_MANAGE);
		codes.add(CURRENCY_VIEW);
		codes.add(CURRENCY_MANAGE);
		codes.add(VENDOR_VIEW);
		codes.add(VENDOR_MANAGE);
		codes.add(TAX_AUTHORITY_MANAGE);
		codes.add(EXPENSE_CONFIGURATION_VIEW);
		codes.add(EXPENSE_CONFIGURATION_MANAGE);
		codes.add(DELEGATION_VIEW);
		codes.add(DELEGATION_MANAGE);
		codes.add(APPROVAL_LIMIT_VIEW);
		codes.add(APPROVAL_LIMIT_MANAGE);
		codes.add(RECOMMENDING_AUTHORITY_VIEW);
		codes.add(RECOMMENDING_AUTHORITY_MANAGE);
		codes.add(EXCEEDING_LIMIT_VIEW);
		codes.add(EXCEEDING_LIMIT_MANAGE);
		codes.add(COMMITTEE_VIEW);
		codes.add(COMMITTEE_MANAGE);
		codes.add(REQUEST_CREATE);
		codes.add(REQUEST_VIEW_OWN);
		codes.add(REQUEST_VIEW_SCOPE);
		codes.add(REQUEST_VIEW_ALL);
		codes.add(REQUEST_EDIT);
		codes.add(REQUEST_SUBMIT);
		codes.add(REQUEST_CANCEL);
		codes.add(REQUEST_REVERSE);
		codes.add(REQUEST_EXPORT);
		codes.add(DOCUMENT_UPLOAD);
		codes.add(DOCUMENT_DOWNLOAD);
		codes.add(DOCUMENT_DELETE);
		codes.add(APPROVAL_VIEW);
		codes.add(APPROVAL_ACT);
		codes.add(WORKFLOW_VIEW);
		codes.add(WORKFLOW_MANAGE);
		codes.add(WORKFLOW_PUBLISH);
		codes.add(RULE_VIEW);
		codes.add(RULE_MANAGE);
		codes.add(RULE_PUBLISH);
		codes.add(AUDIT_VIEW);
		codes.add(AUDIT_EXPORT);
		codes.add(NOTIFICATION_VIEW);
		codes.add(EMAIL_CONFIGURATION_MANAGE);
		codes.add(NOTIFICATION_TEMPLATE_MANAGE);
		codes.add(REPORT_VIEW);
		codes.add(REPORT_EXPORT);
		codes.add(DASHBOARD_MANAGE);
		codes.add(INTEGRATION_VIEW);
		codes.add(INTEGRATION_MANAGE);
		return Set.copyOf(codes);
	}

}
