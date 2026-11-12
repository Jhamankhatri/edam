package com.edam.common.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the stable operation identifier used for idempotency records.
 *
 * <p>Recorded per endpoint rather than per URL pattern so that a resource id in the path does not
 * let a client reuse one key across two different records. Example:
 * {@code @IdempotentOperation("POST /api/v1/requests/{id}/submit")}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentOperation {

	/** Stable operation identifier; must not change once released, or old keys stop matching. */
	String value();

}
