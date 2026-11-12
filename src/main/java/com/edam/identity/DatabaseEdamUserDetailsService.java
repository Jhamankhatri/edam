package com.edam.identity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.EdamUserDetailsService;
import com.edam.iam.UserMembership;
import com.edam.iam.UserMembershipRepository;
import com.edam.institution.Institution;
import com.edam.institution.InstitutionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Database-backed identity adapter: builds an {@link EdamPrincipal} from persisted memberships.
 *
 * <h2>Position in the authentication seam</h2>
 * BRD OD-24 leaves the identity provider open (bank SSO/OIDC/SAML, MFA, token model). This adapter
 * does <em>not</em> choose one. It fills the existing {@link EdamUserDetailsService} seam with the
 * local membership store, so:
 * <ul>
 *   <li>roles and permissions are institution-scoped real data, not fixtures;</li>
 *   <li>replacing it with an OIDC adapter is a bean swap, not a rewrite;</li>
 *   <li>no password is stored here — credential verification stays with whichever provider is
 *       approved.</li>
 * </ul>
 *
 * <h2>Precedence</h2>
 * Only one identity adapter may be active. This class is a plain component, not a
 * {@code @Configuration}, so it cannot race with another conditional bean definition; the choice is
 * made in one place by {@code IdentityConfiguration}. This also fixes a real startup failure:
 * annotating the class {@code @Configuration} and naming its {@code @Bean} method after the class
 * produced two definitions with the same name.
 *
 * <h2>Multiple memberships</h2>
 * A username may hold memberships in several institutions. All of them are loaded into the
 * principal as a set; nothing assumes a single tenant (IAM-04). A membership whose institution is
 * missing or not operational is skipped, so a suspended tenant cannot be entered by logging in.
 */
public class DatabaseEdamUserDetailsService implements EdamUserDetailsService {

	private static final Logger log = LoggerFactory.getLogger(DatabaseEdamUserDetailsService.class);

	private final UserMembershipRepository memberships;

	private final InstitutionRepository institutions;

	public DatabaseEdamUserDetailsService(UserMembershipRepository memberships,
			InstitutionRepository institutions) {
		this.memberships = memberships;
		this.institutions = institutions;
	}

	@Override
	public EdamUserDetails loadUserByUsername(String username) {
		List<UserMembership> active = this.memberships.findActiveByUsername(username);
		if (active.isEmpty()) {
			throw new UsernameNotFoundException("Unknown user");
		}
		Map<Long, Institution> byId = new LinkedHashMap<>();
		this.institutions.findAll().forEach(institution -> byId.put(institution.getId(), institution));

		Map<String, Set<String>> rolesByInstitution = new LinkedHashMap<>();
		Map<String, Set<String>> permissionsByInstitution = new LinkedHashMap<>();
		Set<String> institutionCodes = new LinkedHashSet<>();
		String displayName = null;
		String employeeId = null;
		for (UserMembership membership : active) {
			Institution institution = byId.get(membership.getInstitutionId());
			if (institution == null || !institution.isOperational()) {
				log.debug("identity.membership_skipped reason=institution_not_operational institutionId={}",
						membership.getInstitutionId());
				continue;
			}
			String code = institution.getCode();
			rolesByInstitution.computeIfAbsent(code, key -> new LinkedHashSet<>())
				.addAll(membership.roleCodes());
			permissionsByInstitution.computeIfAbsent(code, key -> new LinkedHashSet<>())
				.addAll(membership.permissionCodes());
			institutionCodes.add(code);
			if (displayName == null && membership.getDisplayName() != null) {
				displayName = membership.getDisplayName();
			}
			if (employeeId == null) {
				employeeId = membership.getEmployeeId();
			}
		}
		if (institutionCodes.isEmpty()) {
			throw new UsernameNotFoundException("Unknown user");
		}
		EdamPrincipal principal = new EdamPrincipal(employeeId, username,
				displayName == null ? username : displayName, rolesByInstitution, permissionsByInstitution,
				institutionCodes);
		// No credential: this adapter identifies, it does not authenticate. A provider that needs a
		// password must obtain it from the approved identity source.
		return new EdamUserDetails(principal, null);
	}

}
