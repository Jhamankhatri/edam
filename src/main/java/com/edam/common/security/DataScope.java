package com.edam.common.security;

import java.util.UUID;

/**
 * One organizational data scope: <em>where</em> a user's permissions apply.
 *
 * <h2>Why this exists</h2>
 * The requirement is explicit that "a role is not automatically a global administrator.
 * Permissions and organizational data scopes determine what the user can manage." A permission says
 * <em>what</em> a user may do; a scope says <em>over which part of the organization</em>.
 *
 * <p>Without scopes every role would be installation-wide, and granting "Expense Configuration
 * Administrator" to a branch manager would let them reconfigure the whole bank. That is the single
 * most likely way an administrator model goes wrong, so scope is a first-class concept rather than a
 * filter applied in one or two queries.
 *
 * <h2>The ALL scope is not a back door</h2>
 * {@link Type#ALL} covers the whole installation for the permissions the user holds — it does not
 * confer additional permissions, and it is not implied by any role. It is granted deliberately, per
 * user, and every grant is audited.
 *
 * @param type the kind of scope
 * @param referenceId the department/branch/organization-unit id; null for {@link Type#ALL} and
 * {@link Type#SELF}
 */
public record DataScope(Type type, UUID referenceId) {

	/** The kinds of scope a user can hold. */
	public enum Type {

		/** The entire installation. */
		ALL,

		/** A department and, by configuration, its subordinate units. */
		DEPARTMENT,

		/** A branch. */
		BRANCH,

		/** Any node of the organization hierarchy. */
		ORGANIZATION_UNIT,

		/** Only records the user owns (their own requests). */
		SELF

	}

	public DataScope {
		if (type == null) {
			throw new IllegalArgumentException("A data scope requires a type");
		}
		boolean needsReference = type == Type.DEPARTMENT || type == Type.BRANCH
				|| type == Type.ORGANIZATION_UNIT;
		if (needsReference && referenceId == null) {
			throw new IllegalArgumentException("A " + type + " data scope requires a reference id");
		}
		if (!needsReference && referenceId != null) {
			throw new IllegalArgumentException("An " + type + " data scope must not carry a reference id");
		}
	}

	/** True when this scope covers the whole installation. */
	public boolean isInstallationWide() {
		return this.type == Type.ALL;
	}

	/** True when this scope is the caller's own records only. */
	public boolean isSelfOnly() {
		return this.type == Type.SELF;
	}

}
