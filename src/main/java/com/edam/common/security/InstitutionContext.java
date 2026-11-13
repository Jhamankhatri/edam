package com.edam.common.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the acting principal for the current request.
 *
 * <h2>Why this class shrank</h2>
 * It used to resolve a <em>tenant</em>, because the earlier model allowed one deployment to serve
 * several banks. The requirement models the opposite: {@code organization_profile} is explicitly
 * "the purchasing bank's own configuration; it is not a multi-bank registry", and one EDAM
 * installation serves one bank.
 *
 * <p>With that established, tenant resolution is not merely unnecessary — it is a liability. Every
 * per-request tenant binding, header check and fail-closed branch was a place where a wrong value
 * could scope a query. The organization is now a property of the installation, and access is narrowed
 * per user by {@link DataScope} instead.
 *
 * <p>The class keeps its name and a stable API so that the security spine, audit recorder and
 * controllers did not need rewriting; {@link #principal()} is the only concept they need.
 */
@Component
public class InstitutionContext {

	/** The authenticated principal for the current request, if any. */
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

	/**
	 * The authenticated principal, or a failure.
	 *
	 * <p>There is no "anonymous but allowed" path here on purpose: a service that needs an identity and
	 * cannot find one has a bug, and silently proceeding would mean acting as nobody.
	 */
	public EdamPrincipal principal() {
		return currentPrincipal().orElseThrow(() -> new com.edam.common.exception.BusinessException(
				com.edam.common.api.ErrorCode.UNAUTHENTICATED));
	}

	/** True when a request is authenticated. */
	public boolean isAuthenticated() {
		return currentPrincipal().isPresent();
	}

}
