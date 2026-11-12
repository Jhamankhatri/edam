package com.edam.common.security;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Institution membership, tenant-scoped permissions and authority derivation are security-critical.
 *
 * <p>These tests exist because the first version of this class carried a single flat permission set,
 * which made a permission granted in one institution effective in every institution (audit defect
 * D-1). The regression tests below fail if that model ever returns.
 */
class EdamPrincipalTest {

	private static EdamPrincipal administratorOfTwoInstitutions() {
		return new EdamPrincipal("emp-1", "admin", "Administrator",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")),
				Map.of("albaraka", Set.of(Permissions.CONFIGURATION_MANAGE, Permissions.REQUEST_CREATE),
						"demo-bank", Set.of(Permissions.REQUEST_VIEW_OWN)),
				Set.of("albaraka", "demo-bank"));
	}

	@Test
	@DisplayName("exposes every institution membership instead of assuming one")
	void exposesAllInstitutions() {
		assertThat(administratorOfTwoInstitutions().institutionCodes()).containsExactlyInAnyOrder("albaraka",
				"demo-bank");
	}

	@Test
	@DisplayName("scopes roles AND permissions to their institution in the granted authorities")
	void scopesAuthoritiesToInstitution() {
		Set<String> authorities = administratorOfTwoInstitutions().getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.collect(java.util.stream.Collectors.toSet());

		assertThat(authorities).contains("ROLE_albaraka:ADMINISTRATOR", "ROLE_demo-bank:ADMINISTRATOR",
				"PERM_albaraka:configuration:manage", "PERM_albaraka:request:create",
				"PERM_demo-bank:request:view-own");
		// No unscoped authority may exist: either would be a cross-tenant grant.
		assertThat(authorities).noneMatch(authority -> authority.equals("ROLE_ADMINISTRATOR"));
		assertThat(authorities).noneMatch(authority -> authority.startsWith("PERM_")
				&& !authority.startsWith("PERM_albaraka:") && !authority.startsWith("PERM_demo-bank:"));
	}

	@Test
	@DisplayName("REGRESSION D-1: a permission held in one institution is not effective in another")
	void permissionDoesNotCrossTenants() {
		EdamPrincipal principal = administratorOfTwoInstitutions();

		assertThat(principal.hasPermissionIn("albaraka", Permissions.CONFIGURATION_MANAGE)).isTrue();
		assertThat(principal.hasPermissionIn("demo-bank", Permissions.CONFIGURATION_MANAGE)).isFalse();
		// And the union view, which exists for read models only, must not be used for a decision.
		assertThat(principal.permissions()).contains(Permissions.CONFIGURATION_MANAGE);
	}

	@Test
	@DisplayName("permission checks fail closed for unknown or null institutions")
	void permissionCheckFailsClosed() {
		EdamPrincipal principal = administratorOfTwoInstitutions();

		assertThat(principal.hasPermissionIn("other-bank", Permissions.CONFIGURATION_MANAGE)).isFalse();
		assertThat(principal.hasPermissionIn(null, Permissions.CONFIGURATION_MANAGE)).isFalse();
		assertThat(principal.hasPermissionIn("albaraka", null)).isFalse();
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
		EdamPrincipal principal = new EdamPrincipal("emp-1", "admin", "Admin", mutableRoles, Map.of("albaraka",
				Set.of(Permissions.APPROVAL_ACT)), Set.of("albaraka"));

		mutableRoles.put("demo-bank", Set.of("ADMINISTRATOR"));

		assertThat(principal.institutionCodes()).containsExactly("albaraka");
		assertThat(principal.belongsTo("demo-bank")).isFalse();
	}

	@Test
	@DisplayName("a user with no institution membership has no tenant and no roles")
	void toleratesEmptyMembership() {
		EdamPrincipal principal = new EdamPrincipal("emp-2", "nobody", "Nobody", null, null, null);

		assertThat(principal.institutionCodes()).isEmpty();
		assertThat(principal.getAuthorities()).isEmpty();
		assertThat(principal.permissions()).isEmpty();
	}

	@Test
	@DisplayName("rolesIn and permissionsIn return empty sets for an unknown institution")
	void unknownInstitutionIsEmpty() {
		assertThat(administratorOfTwoInstitutions().rolesIn("other-bank")).isEmpty();
		assertThat(administratorOfTwoInstitutions().permissionsIn("other-bank")).isEmpty();
	}

	@Test
	@DisplayName("the encoded authority helper matches the authority actually granted")
	void authorityEncodingIsConsistent() {
		String expected = EdamPrincipal.permissionAuthority("albaraka", Permissions.CONFIGURATION_MANAGE);

		assertThat(administratorOfTwoInstitutions().getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)).contains(expected);
	}

}
