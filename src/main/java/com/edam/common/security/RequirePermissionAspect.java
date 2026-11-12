package com.edam.common.security;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.stereotype.Component;

/**
 * Enforces {@link EdamPermission} at the service boundary.
 *
 * <h2>Why an aspect rather than only {@code @PreAuthorize}</h2>
 * Spring Security's method security evaluates flat authorities. EDAM's permissions are
 * institution-scoped, and the institution is resolved <em>per request</em> from the authenticated
 * identity plus an explicit selection. Expressing "permission X in the institution this request
 * resolved to" as a SpEL expression in every annotation would be unreadable and easy to get wrong.
 * This aspect resolves the tenant once, then checks membership + permission together.
 *
 * <h2>Order of checks (fail closed)</h2>
 * <ol>
 *   <li>Authentication exists.</li>
 *   <li>An institution context is resolved.</li>
 *   <li>The principal is a member of that institution (direct object-reference protection).</li>
 *   <li>The principal holds the permission <em>in that institution</em>.</li>
 * </ol>
 * A caller who fails step 3 receives the same response as one who fails step 4, and the same
 * response as one addressing a non-existent record, so cross-tenant probing reveals nothing.
 *
 * <h2>Scope</h2>
 * Applies to methods annotated directly; the controller layer declares the annotation, and the
 * aspect is deliberately usable on services too for defence in depth.
 */
@Aspect
@Component
public class RequirePermissionAspect {

	private final InstitutionContext institutionContext;

	public RequirePermissionAspect(InstitutionContext institutionContext) {
		this.institutionContext = institutionContext;
	}

	@Before("@annotation(edamPermission)")
	public void check(JoinPoint joinPoint, EdamPermission edamPermission) {
		EdamPrincipal principal = this.institutionContext.currentPrincipal()
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED,
					ErrorCode.UNAUTHENTICATED.safeMessage()));
		String institutionCode = this.institutionContext.currentInstitution()
			.orElseThrow(() -> new BusinessException(ErrorCode.INSTITUTION_REQUIRED,
					"Select the institution you are acting for before continuing."));
		if (!principal.belongsTo(institutionCode)) {
			// Same safe message as a permission failure: do not reveal tenant existence.
			throw new BusinessException(ErrorCode.INSTITUTION_ACCESS_DENIED,
					ErrorCode.INSTITUTION_ACCESS_DENIED.safeMessage());
		}
		if (!principal.hasPermissionIn(institutionCode, edamPermission.value())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED.safeMessage());
		}
	}

	/** Human-readable location for the audit log when a check fails. */
	static String describe(JoinPoint joinPoint) {
		if (joinPoint.getSignature() instanceof MethodSignature signature) {
			return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
		}
		return joinPoint.getSignature().toShortString();
	}

}
