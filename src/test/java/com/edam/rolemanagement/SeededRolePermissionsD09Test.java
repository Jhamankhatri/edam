package com.edam.rolemanagement;

import java.util.List;
import java.util.Set;

import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-09 regression test: every seeded role that holds the wider
 * {@code request:view-scope} or {@code request:view-all} permission must also
 * hold {@code request:view-own}. The controller aspect enforces an exact-match
 * permission check, so relying on an implicit hierarchy would 403 these roles.
 * Service-layer visibility still enforces department/branch/installation scope,
 * so granting the base permission does not widen data access.
 */
@SpringBootTest
class SeededRolePermissionsD09Test extends AbstractPostgreSqlIntegrationTest {

	private static final String VIEW_OWN = "request:view-own";
	private static final String VIEW_SCOPE = "request:view-scope";
	private static final String VIEW_ALL = "request:view-all";

	// Roles seeded in V5/V12 with widened scope that must also hold request:view-own after V28.
	private static final Set<String> WIDER_ROLES = Set.of(
			"DEPARTMENT_HEAD",
			"RECOMMENDING_AUTHORITY",
			"EXPENSE_COMMITTEE_MEMBER",
			"CEO",
			"CFO",
			"HEAD_OF_ADMIN_GSD",
			"REPORT_ADMINISTRATOR",
			"SYSTEM_ADMINISTRATOR");

	@Autowired
	private RoleRepository roles;

	@Test
	void rolesWithWidenedRequestViewAlsoHoldViewOwn() {
		List<Role> seeded = this.roles.findAll();
		assertThat(seeded).isNotEmpty();

		for (Role role : seeded) {
			Set<String> codes = role.permissionCodes();
			boolean wider = codes.contains(VIEW_SCOPE) || codes.contains(VIEW_ALL);
			if (wider) {
				assertThat(codes)
						.as("Role %s holds request:view-scope/view-all but is missing request:view-own "
								+ "(Flyway V28 should have granted it; D-09).", role.getCode())
						.contains(VIEW_OWN);
			}
		}
	}

	@Test
	void knownWiderRolesExistAndCarryExpectedPermissions() {
		for (String code : WIDER_ROLES) {
			Role role = this.roles.findByCode(code)
					.orElseThrow(() -> new AssertionError("Expected seeded role missing: " + code));
			Set<String> codes = role.permissionCodes();
			assertThat(codes).as("role %s must have request:view-own", code).contains(VIEW_OWN);
		}
	}
}
