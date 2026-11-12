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

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <h2>Multiple memberships</h2>
 * A username may hold memberships in several institutions. All of them are loaded into the
 * principal as a set; nothing assumes a single tenant (IAM-04). A membership whose institution is
 * missing or not {@code ACTIVE} is skipped, so a deactivated tenant cannot be entered by logging
 * in.
 *
 * <h2>Failure behaviour</h2>
 * Unknown users raise {@link UsernameNotFoundException}. The message is uniform and never reveals
 * whether an account exists.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "edam.security.local-identity", name = "enabled", havingValue = "true",
		matchIfMissing = true)
public class DatabaseEdamUserDetailsService {

	@Bean
	@ConditionalOnMissingBean(EdamUserDetailsService.class)
	EdamUserDetailsService databaseEdamUserDetailsService(UserMembershipRepository memberships,
			InstitutionRepository institutions) {
		return username -> {
			List<UserMembership> active = memberships.findActiveByUsername(username);
			if (active.isEmpty()) {
				throw new UsernameNotFoundException("Unknown user");
			}
			Map<Long, Institution> byId = new LinkedHashMap<>();
			institutions.findAll()
				.forEach(institution -> byId.put(institution.getId(), institution));

			Map<String, Set<String>> rolesByInstitution = new LinkedHashMap<>();
			Map<String, Set<String>> permissionsByInstitution = new LinkedHashMap<>();
			Set<String> institutionCodes = new LinkedHashSet<>();
			String displayName = null;
			String employeeId = null;
			for (UserMembership membership : active) {
				Institution institution = byId.get(membership.getInstitutionId());
				if (institution == null || !institution.isActive()) {
					// Deactivated or missing tenant: membership is not usable.
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
			return new DevUserDetails(new EdamPrincipal(employeeId, username,
					displayName == null ? username : displayName, rolesByInstitution, permissionsByInstitution,
					institutionCodes), null);
		};
	}

}
