package com.edam.common.tenant;

import java.io.IOException;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.institution.Institution;
import com.edam.institution.InstitutionRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the request's institution and binds it, with its id, for the remainder of the request.
 *
 * <h2>Why a filter and not a controller argument</h2>
 * Every service call needs the tenant id, and passing it explicitly through each layer is exactly
 * how a parameter gets dropped. Binding it once per request means the value services read is the
 * value this filter validated.
 *
 * <h2>Resolution rules (BRD §8)</h2>
 * <ol>
 *   <li>The {@code X-Institution} header, if present, must name an institution the authenticated
 *       principal is a <em>verified member</em> of. Anything else is rejected with
 *       {@code 403 TENANT_NOT_SELECTED}/{@code ACCESS_DENIED} before any business code runs. A
 *       header is a <em>selection</em>, never a grant.</li>
 *   <li>With no header, a single-membership principal resolves to that institution.</li>
 *   <li>A multi-institution principal with no header resolves to <em>nothing</em>; endpoints that
 *       need a tenant fail closed with a message telling the caller to select one. The filter does
 *       not fail the request globally, because identity and health endpoints legitimately have no
 *       tenant.</li>
 *   <li>Anonymous requests bind nothing.</li>
 * </ol>
 *
 * <p>The binding is always cleared in {@code finally}: this is a thread-pool thread, and a leaked
 * tenant would be read by the next request served on it.
 */
public class TenantContextFilter extends OncePerRequestFilter {

	/** Header the client uses to select among institutions it is a member of. */
	public static final String INSTITUTION_HEADER = "X-Institution";

	private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

	private final InstitutionContext institutionContext;

	private final InstitutionRepository institutions;

	public TenantContextFilter(InstitutionContext institutionContext, InstitutionRepository institutions) {
		this.institutionContext = institutionContext;
		this.institutions = institutions;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		try {
			EdamPrincipal principal = currentPrincipal();
			if (principal != null) {
				String requested = request.getHeader(INSTITUTION_HEADER);
				if (requested != null && !requested.isBlank()) {
					String code = requested.trim().toLowerCase(java.util.Locale.ROOT);
					if (!principal.belongsTo(code)) {
						// Do not disclose whether the institution exists.
						log.warn("security.tenant_rejected reason=not_a_member user={} institution={}",
								principal.getUsername(), code);
						response.sendError(HttpServletResponse.SC_FORBIDDEN,
								"You are not a member of the selected institution.");
						return;
					}
					resolveId(code).ifPresentOrElse(id -> this.institutionContext.bind(code, id),
							() -> this.institutionContext.bind(code));
				}
				else if (principal.institutionCodes().size() == 1) {
					String code = principal.institutionCodes().iterator().next();
					resolveId(code).ifPresentOrElse(id -> this.institutionContext.bind(code, id),
							() -> this.institutionContext.bind(code));
				}
				// else: multi-institution with no explicit selection — nothing is bound on purpose.
			}
			chain.doFilter(request, response);
		}
		finally {
			this.institutionContext.clear();
		}
	}

	private java.util.Optional<Long> resolveId(String code) {
		return this.institutions.findByCode(code).filter(Institution::isActive).map(Institution::getId);
	}

	private static EdamPrincipal currentPrincipal() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		return (authentication.getPrincipal() instanceof EdamPrincipal principal) ? principal : null;
	}

}
