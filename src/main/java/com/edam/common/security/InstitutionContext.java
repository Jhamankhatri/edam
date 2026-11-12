package com.edam.common.security;

import java.util.Optional;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the institution ("tenant") the current request operates on.
 *
 * <p>Tenant isolation is the single most consequential architectural decision in EDAM and
 * is still an open decision (OD-20). This class is the <em>seam</em> that all later
 * tenant-scoped data access must go through, so the isolation mechanism can be chosen
 * without touching business code.
 *
 * <p>Current foundation behaviour:
 * <ul>
 * <li>A user who belongs to exactly one institution resolves implicitly.</li>
 * <li>A user with several institutions must supply an explicit selection; the foundation
 * refuses to guess (fails closed with {@link BusinessException}).</li>
 * <li>Anonymous requests have no tenant.</li>
 * </ul>
 *
 * <p>Nothing here is persisted: the resolved institution is a request-scoped value. A
 * later phase will bind it to the Hibernate tenant filter once the isolation model is
 * approved.
 */
@Component
public class InstitutionContext {

	private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

	/** Explicitly bind an institution for the current request (login selection, admin tools). */
	public void bind(String institutionCode) {
		CURRENT.set(institutionCode);
	}

	public void clear() {
		CURRENT.remove();
	}

	/**
	 * The institution for the current request, or empty when there is none. Does not throw:
	 * callers rendering generic pages (login, error) must still work.
	 */
	public Optional<String> currentInstitution() {
		String explicit = CURRENT.get();
		if (explicit != null) {
			return Optional.of(explicit);
		}
		EdamPrincipal principal = currentPrincipal().orElse(null);
		if (principal == null) {
			return Optional.empty();
		}
		var institutions = principal.institutionCodes();
		if (institutions.size() == 1) {
			return Optional.of(institutions.iterator().next());
		}
		return Optional.empty();
	}

	/**
	 * The institution the caller is acting for. Fails closed when the caller belongs to
	 * several institutions and has not selected one; never silently picks the first.
	 */
	public String requireInstitution() {
		return currentInstitution().orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED,
				"Select the institution you are acting for before continuing."));
	}

	public Optional<EdamPrincipal> currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		if (authentication.getPrincipal() instanceof EdamPrincipal principal) {
			return Optional.of(principal);
		}
		return Optional.empty();
	}

	/** True when the principal may act for the given institution. */
	public boolean isMemberOf(String institutionCode) {
		return currentPrincipal().map(principal -> principal.belongsTo(institutionCode)).orElse(false);
	}

}
