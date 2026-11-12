package com.edam.common.security;

import java.util.Optional;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves which institution ("tenant") the current request is acting for.
 *
 * <h2>Security rule (BRD §8)</h2>
 * A tenant identifier supplied by the client is <strong>never</strong> an authorization
 * decision. It may <em>select</em> among institutions the authenticated principal is a verified
 * member of; anything else fails closed. There is no code path that reads a tenant id from a
 * request body and trusts it.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>An explicit binding for this request (set by the API layer from the {@code X-Institution}
 *       header or an institution path segment, after the membership check below).</li>
 *   <li>Otherwise, if the principal belongs to exactly one institution, that institution.</li>
 *   <li>Otherwise fail closed. EDAM refuses to guess when a user belongs to several
 *       institutions — the previous behaviour silently returned "no tenant", which would have
 *       become a silent cross-tenant read as soon as business tables arrived.</li>
 * </ol>
 *
 * <p>The resolved value is request-scoped and cleared by
 * {@link com.edam.common.logging.TenantContextFilter}; it is never stored in the session, because
 * a session-wide tenant would leak across concurrent tabs and across logins.
 */
@Component
public class InstitutionContext {

	private static final ThreadLocal<InstitutionSelection> CURRENT = new ThreadLocal<>();

	/** Explicitly bind an institution for the current request. */
	public void bind(String institutionCode) {
		if (institutionCode == null || institutionCode.isBlank()) {
			CURRENT.remove();
			return;
		}
		CURRENT.set(new InstitutionSelection(institutionCode.trim().toLowerCase(java.util.Locale.ROOT), null));
	}

	/**
	 * Bind a resolved institution together with its numeric id. The id is what tenant-owned rows
	 * store, so resolving it once per request keeps services from re-querying by code.
	 */
	public void bind(String institutionCode, Long institutionId) {
		if (institutionCode == null || institutionCode.isBlank() || institutionId == null) {
			CURRENT.remove();
			return;
		}
		CURRENT.set(new InstitutionSelection(institutionCode.trim().toLowerCase(java.util.Locale.ROOT),
				institutionId));
	}

	public void clear() {
		CURRENT.remove();
	}

	/** The explicitly bound selection, if the API layer resolved one. */
	public Optional<InstitutionSelection> selection() {
		return Optional.ofNullable(CURRENT.get());
	}

	/**
	 * The institution code for the current request, or empty when the caller is anonymous.
	 * Does not throw: generic pages (login, error, health) must still work.
	 */
	public Optional<String> currentInstitution() {
		InstitutionSelection explicit = CURRENT.get();
		if (explicit != null) {
			return Optional.of(explicit.code());
		}
		return currentPrincipal().flatMap(principal -> {
			var institutions = principal.institutionCodes();
			return institutions.size() == 1 ? Optional.of(institutions.iterator().next()) : Optional.empty();
		});
	}

	/**
	 * The institution the caller is acting for. Fails closed when the caller belongs to several
	 * institutions and has not selected one, and when the caller is anonymous.
	 */
	public String requireInstitution() {
		return currentInstitution().orElseThrow(() -> new BusinessException(ErrorCode.INSTITUTION_REQUIRED,
				"Select the institution you are acting for before continuing."));
	}

	/**
	 * The numeric institution id the caller is acting for. Tenant-owned rows store this, so it is
	 * the value every service must use when reading or writing business data.
	 */
	public Long requireInstitutionId() {
		InstitutionSelection explicit = CURRENT.get();
		if (explicit != null && explicit.id() != null) {
			return explicit.id();
		}
		throw new BusinessException(ErrorCode.INSTITUTION_REQUIRED,
				"The institution context for this request could not be resolved.");
	}

	public Optional<Long> currentInstitutionId() {
		InstitutionSelection explicit = CURRENT.get();
		return (explicit == null) ? Optional.empty() : Optional.ofNullable(explicit.id());
	}

	public Optional<EdamPrincipal> currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return Optional.empty();
		}
		if (authentication.getPrincipal() instanceof EdamPrincipal principal) {
			return Optional.of(principal);
		}
		// The development adapter wraps the principal with a credential holder.
		Object details = authentication.getPrincipal();
		if (details != null && details.getClass().getName().endsWith("DevUserDetails")) {
			try {
				Object inner = details.getClass().getMethod("principal").invoke(details);
				if (inner instanceof EdamPrincipal principal) {
					return Optional.of(principal);
				}
			}
			catch (ReflectiveOperationException ignored) {
				// Fall through: no principal means no tenant, which fails closed.
			}
		}
		return Optional.empty();
	}

	/** True when the principal may act for the given institution. */
	public boolean isMemberOf(String institutionCode) {
		return currentPrincipal().map(principal -> principal.belongsTo(institutionCode)).orElse(false);
	}

	/** A resolved tenant selection: stable code plus the surrogate id rows store. */
	public record InstitutionSelection(String code, Long id) {
	}

}
