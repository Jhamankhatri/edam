package com.edam.common.security;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The technical permission vocabulary checked through {@code @PreAuthorize} and
 * {@code @EdamPermission}.
 *
 * <h2>Why these are strings and not an enum</h2>
 * BRD IAM-02 requires institution-configurable, granular permissions with stable
 * machine-readable codes. Rows in the database therefore have to reference codes that may be
 * added by configuration; an enum in application code would make every new permission a code
 * change. These constants are the <em>built-in catalogue</em>; the database
 * ({@code permission_definition}) is the authority for what exists at runtime, and
 * institution roles compose them.
 *
 * <h2>Naming</h2>
 * {@code <module>:<action>} in lower case, hyphenated. Module and action are independent
 * dimensions (IAM-02) so a role can be granted "OPEX: create" without "OPEX: approve".
 *
 * <h2>What is deliberately absent</h2>
 * No banking job title, no institution-specific permission, and no approval authority. Approval
 * authority is never a permission alone: BRD §23.3 requires identity + membership + permission +
 * <em>workflow assignment</em>. {@link #APPROVAL_ACT} only permits the caller to act on a task
 * that the workflow has assigned to them.
 */
public final class Permissions {

	// ------------------------------------------------------------------
	// Institution and platform administration (BRD INS-01..04, §21)
	// ------------------------------------------------------------------

	/** View institution settings and configuration. */
	public static final String INSTITUTION_VIEW = "institution:view";

	/** Create, update, activate or suspend an institution. */
	public static final String INSTITUTION_ADMINISTER = "institution:administer";

	/** Administer feature flags for an institution (INS-04). */
	public static final String INSTITUTION_FEATURE_MANAGE = "institution-feature:manage";

	// ------------------------------------------------------------------
	// Identity and access management (BRD IAM-01..05, §23)
	// ------------------------------------------------------------------

	/** View roles, permissions and memberships. */
	public static final String IAM_VIEW = "iam:view";

	/** Create and maintain roles and their permission grants. */
	public static final String IAM_ROLE_MANAGE = "iam-role:manage";

	/** Add, remove or re-scope user memberships. */
	public static final String IAM_MEMBERSHIP_MANAGE = "iam-membership:manage";

	/** Export permission assignments for access recertification (IAM-05). */
	public static final String IAM_REVIEW_EXPORT = "iam-review:export";

	// ------------------------------------------------------------------
	// Configuration plane (BRD FM-07..FM-11, FM-13, §8.6)
	// ------------------------------------------------------------------

	/** View the configuration plane (categories, matrix, currencies, limits). */
	public static final String CONFIGURATION_VIEW = "configuration:view";

	/** Maintain the configuration plane. */
	public static final String CONFIGURATION_MANAGE = "configuration:manage";

	/** Publish or roll back a configuration/theme/workflow version. */
	public static final String CONFIGURATION_PUBLISH = "configuration:publish";

	// ------------------------------------------------------------------
	// Expenditure requests (BRD FM-01..FM-06, §8)
	// ------------------------------------------------------------------

	/** View the caller's own requests. */
	public static final String REQUEST_VIEW_OWN = "request:view-own";

	/** View requests in the caller's data scope beyond their own (department/branch). */
	public static final String REQUEST_VIEW_SCOPE = "request:view-scope";

	/** View any request in the institution (administrative/audit use). */
	public static final String REQUEST_VIEW_ALL = "request:view-all";

	/** Create an OPEX or CAPEX request. */
	public static final String REQUEST_CREATE = "request:create";

	/** Edit a returned/reverted request the caller owns or is designated to correct. */
	public static final String REQUEST_EDIT_RETURNED = "request:edit-returned";

	/** Submit a request into its configured workflow. */
	public static final String REQUEST_SUBMIT = "request:submit";

	/** Cancel a request where policy permits. */
	public static final String REQUEST_CANCEL = "request:cancel";

	/** Upload or replace a supporting document on an owned request. */
	public static final String REQUEST_DOCUMENT_UPLOAD = "request-document:upload";

	/** Download a supporting document the caller is authorized to see. */
	public static final String REQUEST_DOCUMENT_DOWNLOAD = "request-document:download";

	// ------------------------------------------------------------------
	// Approval (BRD FM-03, FM-06, §8.5, §10)
	// ------------------------------------------------------------------

	/**
	 * Act on an approval task that the workflow has assigned to the caller. This permission
	 * alone never grants approval authority over a request (BRD §23.3).
	 */
	public static final String APPROVAL_ACT = "approval:act";

	/** View the approval inbox. */
	public static final String APPROVAL_VIEW = "approval:view";

	// ------------------------------------------------------------------
	// Workflow and rules (BRD §24, §25, WF-01..06, RULE-01..02)
	// ------------------------------------------------------------------

	/** View workflow definitions and versions. */
	public static final String WORKFLOW_VIEW = "workflow:view";

	/** Create and edit workflow drafts. */
	public static final String WORKFLOW_MANAGE = "workflow:manage";

	/** Validate and simulate a workflow definition. */
	public static final String WORKFLOW_VALIDATE = "workflow:validate";

	/** Publish or retire a workflow version. */
	public static final String WORKFLOW_PUBLISH = "workflow:publish";

	/** View business rule sets. */
	public static final String RULE_VIEW = "rule:view";

	/** Create and edit business rule sets. */
	public static final String RULE_MANAGE = "rule:manage";

	/** Publish a business rule version. */
	public static final String RULE_PUBLISH = "rule:publish";

	// ------------------------------------------------------------------
	// Audit and reporting (BRD §26, §33, AUD-01..03)
	// ------------------------------------------------------------------

	/** Search and view audit events. */
	public static final String AUDIT_VIEW = "audit:view";

	/** Export audit data. */
	public static final String AUDIT_EXPORT = "audit:export";

	/** View reports and dashboards. */
	public static final String REPORTING_VIEW = "reporting:view";

	/** Export report data. */
	public static final String REPORTING_EXPORT = "reporting:export";

	// ------------------------------------------------------------------
	// Notifications and email administration (BRD §28)
	// ------------------------------------------------------------------

	/** View the caller's own notifications. */
	public static final String NOTIFICATION_VIEW = "notification:view";

	/** Administer email provider settings and rotate credentials (MAIL-01/02). */
	public static final String MAIL_CONFIGURATION_MANAGE = "mail-configuration:manage";

	/** Create, edit, preview and publish email templates (MAIL-03). */
	public static final String MAIL_TEMPLATE_MANAGE = "mail-template:manage";

	// ------------------------------------------------------------------
	// Documents (BRD §31, DOC-01)
	// ------------------------------------------------------------------

	/** View document metadata and download authorized documents. */
	public static final String DOCUMENT_VIEW = "document:view";

	/** Upload documents outside the request lifecycle (branding assets, templates). */
	public static final String DOCUMENT_MANAGE = "document:manage";

	private static final Set<String> ALL = Set.of(INSTITUTION_VIEW, INSTITUTION_ADMINISTER,
			INSTITUTION_FEATURE_MANAGE, IAM_VIEW, IAM_ROLE_MANAGE, IAM_MEMBERSHIP_MANAGE, IAM_REVIEW_EXPORT,
			CONFIGURATION_VIEW, CONFIGURATION_MANAGE, CONFIGURATION_PUBLISH, REQUEST_VIEW_OWN, REQUEST_VIEW_SCOPE,
			REQUEST_VIEW_ALL, REQUEST_CREATE, REQUEST_EDIT_RETURNED, REQUEST_SUBMIT, REQUEST_CANCEL,
			REQUEST_DOCUMENT_UPLOAD, REQUEST_DOCUMENT_DOWNLOAD, APPROVAL_ACT, APPROVAL_VIEW, WORKFLOW_VIEW,
			WORKFLOW_MANAGE, WORKFLOW_VALIDATE, WORKFLOW_PUBLISH, RULE_VIEW, RULE_MANAGE, RULE_PUBLISH, AUDIT_VIEW,
			AUDIT_EXPORT, REPORTING_VIEW, REPORTING_EXPORT, NOTIFICATION_VIEW, MAIL_CONFIGURATION_MANAGE,
			MAIL_TEMPLATE_MANAGE, DOCUMENT_VIEW, DOCUMENT_MANAGE);

	private Permissions() {
	}

	/** Every built-in permission. Used to seed the catalogue and by the local admin fixture. */
	public static Set<String> all() {
		return new LinkedHashSet<>(ALL).stream().sorted().collect(
				java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}

}
