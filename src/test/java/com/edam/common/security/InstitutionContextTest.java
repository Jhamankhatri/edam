package com.edam.common.security;

import java.util.Map;
import java.util.Set;

import com.edam.common.exception.BusinessException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant resolution must fail closed. Guessing an institution would risk showing one bank's data to
 * a user acting for another, and silently returning "no tenant" — the previous behaviour — becomes a
 * cross-tenant read the moment tenant-owned tables exist.
 */
class InstitutionContextTest {

	private final InstitutionContext context = new InstitutionContext();

	@AfterEach
	void tearDown() {
		this.context.clear();
		SecurityContextHolder.clearContext();
	}

	private void authenticate(EdamPrincipal principal) {
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
					principal.getAuthorities()));
	}

	@Test
	@DisplayName("anonymous requests have no institution")
	void anonymousHasNone() {
		assertThat(this.context.currentInstitution()).isEmpty();
		assertThat(this.context.currentPrincipal()).isEmpty();
	}

	@Test
	@DisplayName("a single-membership user resolves implicitly")
	void singleMembershipResolves() {
		authenticate(new EdamPrincipal("emp-1", "approver", "Approver", Map.of("albaraka", Set.of("APPROVER")),
				Map.of("albaraka", Set.of(Permissions.APPROVAL_ACT)), Set.of("albaraka")));

		assertThat(this.context.currentInstitution()).contains("albaraka");
		assertThat(this.context.requireInstitution()).isEqualTo("albaraka");
	}

	@Test
	@DisplayName("a multi-membership user must select explicitly instead of defaulting")
	void multiMembershipFailsClosed() {
		authenticate(new EdamPrincipal("emp-1", "admin", "Admin",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")),
				Map.of(), Set.of("albaraka", "demo-bank")));

		assertThat(this.context.currentInstitution()).isEmpty();
		assertThatThrownBy(this.context::requireInstitution).isInstanceOf(BusinessException.class)
			.hasMessageContaining("Select the institution");
	}

	@Test
	@DisplayName("an explicit selection wins for multi-membership users")
	void explicitSelectionWins() {
		authenticate(new EdamPrincipal("emp-1", "admin", "Admin",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")),
				Map.of(), Set.of("albaraka", "demo-bank")));

		this.context.bind("demo-bank", 7L);

		assertThat(this.context.requireInstitution()).isEqualTo("demo-bank");
		assertThat(this.context.requireInstitutionId()).isEqualTo(7L);
		assertThat(this.context.isMemberOf("albaraka")).isTrue();
		assertThat(this.context.isMemberOf("other-bank")).isFalse();
	}

	@Test
	@DisplayName("the institution id is what tenant-owned rows receive, and it is never guessed")
	void institutionIdRequiresResolution() {
		// A code without a resolved id must not silently become "the" tenant for writes.
		this.context.bind("demo-bank");

		assertThat(this.context.currentInstitution()).contains("demo-bank");
		assertThat(this.context.currentInstitutionId()).isEmpty();
		assertThatThrownBy(this.context::requireInstitutionId).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("a selection for a non-member institution is still refused by authorization")
	void membershipIsCheckedSeparately() {
		authenticate(new EdamPrincipal("emp-1", "requester", "Requester", Map.of("albaraka", Set.of("REQUESTER")),
				Map.of("albaraka", Set.of(Permissions.REQUEST_VIEW_OWN)), Set.of("albaraka")));

		// Binding is not an authorization decision; the caller must still prove membership.
		this.context.bind("demo-bank", 7L);

		assertThat(this.context.isMemberOf("demo-bank")).isFalse();
		assertThat(this.context.currentPrincipal().orElseThrow().hasPermissionIn("demo-bank",
				Permissions.REQUEST_VIEW_OWN)).isFalse();
	}

	@Test
	@DisplayName("clearing the context removes the explicit selection")
	void clearRemovesSelection() {
		this.context.bind("demo-bank", 1L);
		this.context.clear();

		assertThat(this.context.currentInstitution()).isEmpty();
		assertThat(this.context.selection()).isEmpty();
	}

	@Test
	@DisplayName("institution codes are normalised so header casing cannot create a phantom tenant")
	void codesAreNormalised() {
		this.context.bind("Demo-Bank", 3L);

		assertThat(this.context.requireInstitution()).isEqualTo("demo-bank");
	}

}
