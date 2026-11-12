package com.edam.configplane.feature;

/**
 * The catalogue of institution-level feature flags (BRD INS-04).
 *
 * <h2>Why flags are an enum here and permissions are not</h2>
 * A permission grants a capability to a role; a feature flag switches a behaviour for a whole
 * institution. A flag that no code reads is dead weight, and an unknown flag name in the database
 * would be indistinguishable from a typo. The set is therefore closed in code and stored as data —
 * the opposite of permissions, where the BRD explicitly requires institutions to compose their own
 * vocabulary.
 *
 * <h2>Every flag must be consulted</h2>
 * {@code FeatureFlagService} is the only reader, and each flag documents where it is enforced. A
 * flag that is merely stored would be a promise the product does not keep, which is precisely audit
 * defect D-3 (a global flag that pretended to be per-institution).
 */
public enum FeatureKey {

	/**
	 * Allow an institution to restrict CAPEX requests to configured departments (OD-21 default).
	 * Enforced when a request is created.
	 */
	CAPEX_DEPARTMENT_RESTRICTION("CAPEX department restriction",
			"When enabled, only departments configured for CAPEX may raise a CAPEX request.", true),

	/**
	 * Whether documents are mandatory for a request before submission. The BRD requires supporting
	 * documents for some categories but does not enumerate them (OD-29), so the behaviour is a flag
	 * rather than a hardcoded rule.
	 */
	REQUIRE_SUPPORTING_DOCUMENTS("Supporting documents required",
			"When enabled, a request must carry at least one supporting document before it can be submitted.",
			false),

	/**
	 * Allow requesters to cancel their own request after submission. The BRD permits cancellation
	 * but does not fix the exact boundary (OD-28); the flag makes the default explicit and reversible.
	 */
	REQUESTER_CANCELLATION("Requester cancellation",
			"When enabled, a requester may cancel an in-flight request before the first approval.", true),

	/**
	 * Send email notifications for approval events. Off by default so a deployment without a
	 * configured mail provider does not accumulate failures.
	 */
	EMAIL_NOTIFICATIONS("Email notifications",
			"When enabled, workflow events send email through the configured provider.", false),

	/**
	 * Expose the audit trail to institution auditors through the API. The BRD requires audit access
	 * but leaves the operational model to the bank (OD-33).
	 */
	AUDIT_API_ACCESS("Audit API access", "When enabled, the audit search and export endpoints are available.",
			true);

	private final String displayName;

	private final String description;

	private final boolean defaultEnabled;

	FeatureKey(String displayName, String description, boolean defaultEnabled) {
		this.displayName = displayName;
		this.description = description;
		this.defaultEnabled = defaultEnabled;
	}

	public String displayName() {
		return this.displayName;
	}

	public String description() {
		return this.description;
	}

	/**
	 * Default when the institution has never expressed a preference.
	 *
	 * <p>Flags whose absence must not silently enable a risky behaviour default to {@code false};
	 * the defaults are therefore part of the security posture and are documented.
	 */
	public boolean defaultEnabled() {
		return this.defaultEnabled;
	}

	/**
	 * True when the flag can be changed by an institution administrator. Reserved for future
	 * platform-controlled flags; currently every flag is institution-configurable.
	 */
	public boolean configurable() {
		return true;
	}

	public static FeatureKey fromKey(String key) {
		for (FeatureKey candidate : values()) {
			if (candidate.name().equals(key)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("Unknown feature key");
	}

}
