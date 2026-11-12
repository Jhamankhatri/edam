package com.edam.common.security;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Institution membership and authority derivation are security-critical: a user who belongs
 * to two institutions must never be treated as a single-tenant user, and permissions must be
 * distinguishable from institution-scoped roles.
 */
class EdamPrincipalTest {

	private static EdamPrincipal administratorOfTwoInstitutions() {
		return new EdamPrincipal("emp-1", "admin", "Administrator",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")),
				Set.of(Permissions.CONFIGURATION_MANAGE, Permissions.REQUEST_CREATE));
	}

	@Test
	@DisplayName("exposes every institution membership instead of assuming one")
	void exposesAllInstitutions() {
		assertThat(administratorOfTwoInstitutions().institutionCodes()).containsExactlyInAnyOrder("albaraka",
				"demo-bank");
	}

	@Test
	@DisplayName("scopes roles to their institution in the granted authorities")
	void scopesRolesToInstitution() {
		Set<String> authorities = administratorOfTwoInstitutions().getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.collect(java.util.stream.Collectors.toSet());

		assertThat(authorities).contains("ROLE_albaraka:ADMINISTRATOR", "ROLE_demo-bank:ADMINISTRATOR",
				"PERM_configuration:manage", "PERM_request:create");
		assertThat(authorities).noneMatch(authority -> authority.equals("ROLE_ADMINISTRATOR"));
	}

	@Test
	@DisplayName("membership check is exact and null-safe")
	void membershipIsExact() {
		EdamPrincipal principal = administratorOfTwoInstitutions();

		assertThat(principal.belongsTo("albaraka")).isTrue();
		assertThat(principal.belongsTo("other-bank")).isFalse();
		assertThat(principal.belongsTo(null)).isFalse();
	}

	@Test
	@DisplayName("principal never carries credential material")
	void neverCarriesPassword() {
		EdamPrincipal principal = administratorOfTwoInstitutions();

		assertThat(principal.getPassword()).isNull();
		assertThat(principal.isEnabled()).isTrue();
		assertThat(principal.isAccountNonLocked()).isTrue();
	}

	@Test
	@DisplayName("defensive copies prevent mutation of the security-critical maps")
	void isImmutable() {
		Map<String, Set<String>> mutableRoles = new java.util.HashMap<>();
		mutableRoles.put("albaraka", Set.of("ADMINISTRATOR"));
		EdamPrincipal principal = new EdamPrincipal("emp-1", "admin", "Admin", mutableRoles, Set.of());

		mutableRoles.put("demo-bank", Set.of("ADMINISTRATOR"));

		assertThat(principal.institutionCodes()).containsExactly("albaraka");
	}

	@Test
	@DisplayName("a user with no institution membership has no tenant and no roles")
	void toleratesEmptyMembership() {
		EdamPrincipal principal = new EdamPrincipal("emp-2", "nobody", "Nobody", null, null);

		assertThat(principal.institutionCodes()).isEmpty();
		assertThat(principal.getAuthorities()).isEmpty();
	}

	@Test
	@DisplayName("rolesIn returns an empty set for an unknown institution")
	void rolesInUnknownInstitutionIsEmpty() {
		assertThat(administratorOfTwoInstitutions().rolesIn("other-bank")).isEmpty();
	}

}
