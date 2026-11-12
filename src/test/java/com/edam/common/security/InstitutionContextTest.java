package com.edam.common.security;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tenant resolution must fail closed. Guessing an institution would risk showing one
 * bank's data to a user acting for another.
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
				Set.of()));

		assertThat(this.context.currentInstitution()).contains("albaraka");
		assertThat(this.context.requireInstitution()).isEqualTo("albaraka");
	}

	@Test
	@DisplayName("a multi-membership user must select explicitly instead of defaulting")
	void multiMembershipFailsClosed() {
		authenticate(new EdamPrincipal("emp-1", "admin", "Admin",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")), Set.of()));

		assertThat(this.context.currentInstitution()).isEmpty();
		assertThatThrownBy(this.context::requireInstitution).isInstanceOf(com.edam.common.exception.BusinessException.class)
			.hasMessageContaining("Select the institution");
	}

	@Test
	@DisplayName("an explicit selection wins for multi-membership users")
	void explicitSelectionWins() {
		authenticate(new EdamPrincipal("emp-1", "admin", "Admin",
				Map.of("albaraka", Set.of("ADMINISTRATOR"), "demo-bank", Set.of("ADMINISTRATOR")), Set.of()));

		this.context.bind("demo-bank");

		assertThat(this.context.requireInstitution()).isEqualTo("demo-bank");
		assertThat(this.context.isMemberOf("albaraka")).isTrue();
		assertThat(this.context.isMemberOf("other-bank")).isFalse();
	}

	@Test
	@DisplayName("clearing the context removes the explicit selection")
	void clearRemovesSelection() {
		this.context.bind("demo-bank");
		this.context.clear();

		assertThat(this.context.currentInstitution()).isEmpty();
	}

}
