package com.edam.common.audit;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;

import org.springframework.stereotype.Component;

/**
 * Supplies the actor identity stamped on audit records and on
 * {@link AuditableEntity} audit columns.
 *
 * <p>This replaces the previous {@code "system"} fallback as the <em>normal</em> case. BRD AUD-01
 * requires actor attribution; an audit trail that says "system" for human actions is not an audit
 * trail. The principal is read from the security context, never from request input.
 *
 * <p>Falling back to {@code system} is legitimate only for genuinely system-initiated work
 * (Flyway seeding, scheduled jobs); callers should prefer {@link #actor()} and let the
 * {@link #SYSTEM} constant cover those cases explicitly.
 */
@Component
public class Auditor {

	/** Actor recorded for work that no human initiated. */
	public static final String SYSTEM = "system";

	private final InstitutionContext institutionContext;

	public Auditor(InstitutionContext institutionContext) {
		this.institutionContext = institutionContext;
	}

	/** Employee id of the authenticated user, or {@link #SYSTEM}. */
	public String actor() {
		return this.institutionContext.currentPrincipal().map(EdamPrincipal::employeeId).orElse(SYSTEM);
	}

	/** Login name of the authenticated user, or {@link #SYSTEM}. */
	public String username() {
		return this.institutionContext.currentPrincipal().map(EdamPrincipal::getUsername).orElse(SYSTEM);
	}

	public boolean isAuthenticated() {
		return this.institutionContext.currentPrincipal().isPresent();
	}

}
