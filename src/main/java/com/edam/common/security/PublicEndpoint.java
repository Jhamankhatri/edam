package com.edam.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that intentionally requires no authorization.
 *
 * <p>Deliberately verbose: it forces the author to state that the endpoint is public, and it lets
 * {@code EndpointAuthorizationArchitectureTest} prove that every other controller method carries
 * a {@link EdamPermission}. The only legitimate users are health/readiness probes and the
 * pre-authentication bootstrap contract (platform branding, login failure message catalogue).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {

	/** Why this endpoint is safe to expose without authentication. Reviewed with the code. */
	String reason();

}
