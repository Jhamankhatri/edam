package com.edam.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The principal is the single object every authorization decision reads.
 *
 * <p>These tests concentrate on the two ways that model can fail silently: a permission leaking into a
 * part of the organization it was not granted for, and a scope being treated as wider than it is. Both
 * are "everything still works" failures — the application runs, approvals work, and records are visible
 * to the wrong department.
 */
class EdamPrincipalTest {

	private static final UUID ENGINEERING = UUID.randomUUID();

	private static final UUID FINANCE = UUID.randomUUID();

	private static final UUID KARACHI_BRANCH = UUID.randomUUID();

	private static final UUID HQ = UUID.randomUUID();

	private static EdamPrincipal user(String displayName, Set<String> permissions, Set<DataScope> scopes) {
		return new EdamPrincipal(UUID.randomUUID(), "EMP-1001", "a.khan", displayName,
				Set.of("REQUESTER", "DEPARTMENT_HEAD"), permissions, scopes);
	}

	private static EdamPrincipal departmentHead() {
		return user("Ayesha Khan", Set.of(Permissions.REQUEST_VIEW_SCOPE, Permissions.APPROVAL_ACT),
				Set.of(new DataScope(DataScope.Type.DEPARTMENT, ENGINEERING)));
	}

	private static EdamPrincipal installationAdministrator() {
		return user("Bilal Ahmed",
				Set.of(Permissions.ORGANIZATION_MANAGE, Permissions.EXPENSE_CONFIGURATION_MANAGE),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
	}

	@Nested
	@DisplayName("capabilities (what the user may do)")
	class Capabilities {

		@Test
		@DisplayName("a held permission is reported, an unheld one is not")
		void reportsHeldPermissions() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.hasPermission(Permissions.APPROVAL_ACT)).isTrue();
			assertThat(principal.hasPermission(Permissions.AUDIT_EXPORT)).isFalse();
		}

		@Test
		@DisplayName("a null or unknown permission is denied rather than throwing")
		void deniesUnknownPermission() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.hasPermission(null)).isFalse();
			assertThat(principal.hasPermission("made-up:permission")).isFalse();
		}

		@Test
		@DisplayName("hasAllPermissions requires every listed capability")
		void requiresAllPermissions() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.hasAllPermissions(Permissions.APPROVAL_ACT, Permissions.REQUEST_VIEW_SCOPE))
				.isTrue();
			assertThat(principal.hasAllPermissions(Permissions.APPROVAL_ACT, Permissions.AUDIT_VIEW)).isFalse();
		}

		@Test
		@DisplayName("authorities are permissions only, never role codes")
		void authoritiesArePermissionsNotRoles() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.getAuthorities()).extracting("authority")
				.containsExactlyInAnyOrder(Permissions.REQUEST_VIEW_SCOPE, Permissions.APPROVAL_ACT)
				.doesNotContain("DEPARTMENT_HEAD", "REQUESTER");
		}
	}

	@Nested
	@DisplayName("data scopes (where those capabilities apply)")
	class Scopes {

		@Test
		@DisplayName("a department-scoped user is in scope for that department and no other")
		void departmentScopeIsNarrow() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.scopesToDepartment(ENGINEERING)).isTrue();
			assertThat(principal.scopesToDepartment(FINANCE)).isFalse();
			assertThat(principal.hasInstallationScope()).isFalse();
		}

		@Test
		@DisplayName("a department scope does not grant branch or organization-unit access")
		void differentScopeKindsDoNotCross() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.scopesToBranch(KARACHI_BRANCH)).isFalse();
			assertThat(principal.scopesToOrganizationUnit(HQ)).isFalse();
		}

		@Test
		@DisplayName("the ALL scope covers every kind of organizational unit")
		void allScopeCoversEverything() {
			EdamPrincipal principal = installationAdministrator();
			assertThat(principal.hasInstallationScope()).isTrue();
			assertThat(principal.scopesToDepartment(ENGINEERING)).isTrue();
			assertThat(principal.scopesToDepartment(FINANCE)).isTrue();
			assertThat(principal.scopesToBranch(KARACHI_BRANCH)).isTrue();
			assertThat(principal.scopesToOrganizationUnit(HQ)).isTrue();
		}

		@Test
		@DisplayName("ALL scope confers no capability that the user does not hold")
		void allScopeDoesNotAddPermissions() {
			EdamPrincipal principal = installationAdministrator();
			assertThat(principal.hasPermission(Permissions.ORGANIZATION_MANAGE)).isTrue();
			assertThat(principal.hasPermission(Permissions.APPROVAL_ACT)).isFalse();
			assertThat(principal.hasPermission(Permissions.AUDIT_VIEW)).isFalse();
		}

		@Test
		@DisplayName("a null reference never matches a scope")
		void nullReferenceIsNotInScope() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.scopesToDepartment(null)).isFalse();
			assertThat(principal.scopesToBranch(null)).isFalse();
			assertThat(principal.scopesToOrganizationUnit(null)).isFalse();
		}

		@Test
		@DisplayName("a self-scoped user does not hold installation scope")
		void selfScopeIsNotInstallationScope() {
			EdamPrincipal principal = user("Sana Malik", Set.of(Permissions.REQUEST_VIEW_OWN),
					Set.of(new DataScope(DataScope.Type.SELF, null)));
			assertThat(principal.isSelfScopedOnly()).isTrue();
			assertThat(principal.hasInstallationScope()).isFalse();
			assertThat(principal.scopesToDepartment(ENGINEERING)).isFalse();
		}

		@Test
		@DisplayName("a user whose roles grant permissions but who has no scope is install-wide-nowhere")
		void unscopedUserHasNoOrganizationalAccess() {
			EdamPrincipal principal = user("Unscoped", Set.of(Permissions.EXPENSE_CONFIGURATION_MANAGE), Set.of());
			assertThat(principal.hasPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)).isTrue();
			assertThat(principal.hasInstallationScope()).isFalse();
			assertThat(principal.scopesToDepartment(ENGINEERING)).isFalse();
			assertThat(principal.isSelfScopedOnly()).isFalse();
		}
	}

	@Nested
	@DisplayName("construction invariants")
	class Construction {

		@Test
		@DisplayName("a reference-bearing scope without a reference is rejected")
		void referenceRequired() {
			assertThatThrownBy(() -> new DataScope(DataScope.Type.DEPARTMENT, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("requires a reference id");
		}

		@Test
		@DisplayName("ALL and SELF must not carry a reference, so a wrong one cannot linger")
		void referenceForbidden() {
			assertThatThrownBy(() -> new DataScope(DataScope.Type.ALL, ENGINEERING))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new DataScope(DataScope.Type.SELF, ENGINEERING))
				.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("null collections are tolerated and become empty, never null")
		void toleratesNullCollections() {
			EdamPrincipal principal = new EdamPrincipal(UUID.randomUUID(), null, "x", "X", null, null, null);
			assertThat(principal.roleCodes()).isEmpty();
			assertThat(principal.permissions()).isEmpty();
			assertThat(principal.dataScopes()).isEmpty();
			assertThat(principal.hasInstallationScope()).isFalse();
		}

		@Test
		@DisplayName("the principal carries no password and never writes one in toString")
		void carriesNoCredential() {
			EdamPrincipal principal = departmentHead();
			assertThat(principal.getPassword()).isNull();
			assertThat(principal.toString()).doesNotContain("password").contains("a.khan");
		}
	}

	@Test
	@DisplayName("permissionsByModule groups codes by their module prefix")
	void groupsPermissionsByModule() {
		EdamPrincipal principal = user("Grouped",
				Set.of(Permissions.REQUEST_CREATE, Permissions.REQUEST_SUBMIT, Permissions.AUDIT_VIEW),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
		assertThat(principal.permissionsByModule()).containsKeys("request", "audit");
		assertThat(principal.permissionsByModule().get("request"))
			.containsExactlyInAnyOrder(Permissions.REQUEST_CREATE, Permissions.REQUEST_SUBMIT);
	}

}
