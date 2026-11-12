package com.edam.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the institution-scoped permission required to invoke a controller method.
 *
 * <p>This exists so that "deny by default" is enforceable and reviewable rather than a
 * convention. A {@code @RestController} method without this annotation, without an explicit
 * {@link PublicEndpoint}, is rejected by {@code EndpointAuthorizationArchitectureTest}, so an
 * endpoint cannot be added without either an authorization decision or a deliberate, testable
 * declaration that it is public.
 *
 * <p>The permission is evaluated against the <em>resolved institution</em>, never against a flat
 * principal-wide set (see {@link EdamPrincipal#hasPermissionIn(String, String)}).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EdamPermission {

	/** Required permission code, from {@link Permissions} or a database-defined code. */
	String value();

	/**
	 * When true, the caller only needs the permission if the endpoint is not otherwise satisfied
	 * by ownership. Reserved for endpoints whose authorization is expressed by the service layer
	 * (for example "read your own request"), where a blanket permission would over-grant.
	 */
	boolean ownershipMaySuffice() default false;

}
