package com.edam.businessrules.model;

/**
 * Lifecycle of a {@link BusinessRuleVersion}.
 *
 * <p>Only one PUBLISHED version per rule set may exist at a time (enforced by
 * {@code uk_business_rule_versions_single_published}). DRAFT versions are editable; PUBLISHED
 * versions are immutable; RETIRED versions are kept for audit/history but are no longer evaluated.
 */
public enum RuleVersionStatus {
	DRAFT,
	PUBLISHED,
	RETIRED
}
