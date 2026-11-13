package com.edam.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that requires a signed-in caller but no specific permission.
 *
 * <h2>Why a third category is needed</h2>
 * Two categories were not enough. {@link EdamPermission} means "authenticated <em>and</em> holding this
 * capability". {@link PublicEndpoint} means "no session required". Some endpoints are neither: reading
 * your own profile, or changing your own password, must require a session but must not require a
 * privilege — a user with no roles at all must still be able to see who they are and fix their own
 * credential. Requiring a permission there would lock out exactly the user who most needs access.
 *
 * <p>Being explicit also keeps the deny-by-default architecture test meaningful: an endpoint with no
 * annotation is still a build failure, so "requires authentication but nothing more" is a decision
 * someone wrote down rather than an omission nobody noticed.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuthenticatedEndpoint {

	/** Why this endpoint needs a session but no particular permission. Reviewed with the code. */
	String reason();

}
