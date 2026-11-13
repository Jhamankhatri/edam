package com.edam.common.security;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Enforces {@link EdamPermission} at the controller boundary.
 *
 * <h2>Order of checks (fail closed)</h2>
 * <ol>
 *   <li>An authenticated principal exists.</li>
 *   <li>The principal holds the permission.</li>
 * </ol>
 *
 * <h2>Why the data-scope check is NOT here</h2>
 * A scope answers "over which department/branch may this be done?", and that question needs the
 * <em>record</em> being touched. The aspect runs before the method body, so it has no record. Scopes are
 * therefore enforced in the services that load the record
 * ({@code DataScopeGuard}), which is the only place the answer is knowable. Putting a coarse
 * "has any scope" check here would look like protection while granting access to records outside it.
 *
 * <h2>What a permission does not grant</h2>
 * Holding {@code approval:act} does not permit approving anything: the workflow engine additionally
 * requires the caller to be the assignee of the open task. That check lives with the assignment data.
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
		EdamPrincipal principal = this.institutionContext.principal();
		if (!principal.hasPermission(edamPermission.value())) {
			throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorCode.PERMISSION_DENIED.safeMessage());
		}
	}

	/** Human-readable location, used when reporting a denial. */
	static String describe(JoinPoint joinPoint) {
		if (joinPoint.getSignature() instanceof MethodSignature signature) {
			return signature.getDeclaringType().getSimpleName() + "#" + signature.getName();
		}
		return joinPoint.getSignature().toShortString();
	}

}
