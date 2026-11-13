package com.edam.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import com.edam.common.exception.BusinessException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The context is the only source of "who is acting". These tests pin the fail-closed behaviour: no
 * authentication, an anonymous token, or a principal of the wrong type must all yield "no principal"
 * rather than a guessed one.
 */
class InstitutionContextTest {

	private final InstitutionContext context = new InstitutionContext();

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	private EdamPrincipal principal() {
		return new EdamPrincipal(UUID.randomUUID(), "EMP-1", "a.khan", "Ayesha Khan", Set.of("REQUESTER"),
				Set.of(Permissions.REQUEST_VIEW_OWN), Set.of(new DataScope(DataScope.Type.SELF, null)));
	}

	@Test
	@DisplayName("an unauthenticated request has no principal")
	void anonymousHasNoPrincipal() {
		assertThat(this.context.currentPrincipal()).isEmpty();
		assertThat(this.context.isAuthenticated()).isFalse();
	}

	@Test
	@DisplayName("an anonymous authentication token is not a principal, even though it is 'authenticated'")
	void anonymousTokenIsRejected() {
		AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken("key", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
		SecurityContextHolder.getContext().setAuthentication(anonymous);

		assertThat(this.context.currentPrincipal()).isEmpty();
		assertThat(this.context.isAuthenticated()).isFalse();
	}

	@Test
	@DisplayName("an authenticated EdamPrincipal is resolved with its scopes intact")
	void resolvesAuthenticatedPrincipal() {
		EdamPrincipal principal = principal();
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

		assertThat(this.context.currentPrincipal()).contains(principal);
		assertThat(this.context.principal().username()).isEqualTo("a.khan");
		assertThat(this.context.isAuthenticated()).isTrue();
	}

	@Test
	@DisplayName("a foreign principal type is rejected instead of being coerced")
	void foreignPrincipalTypeIsRejected() {
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken("a-string-principal", null,
					AuthorityUtils.NO_AUTHORITIES));

		assertThat(this.context.currentPrincipal()).isEmpty();
	}

	@Test
	@DisplayName("requiring a principal without one fails closed with UNAUTHENTICATED")
	void requirePrincipalFailsClosed() {
		assertThatThrownBy(() -> this.context.principal()).isInstanceOf(BusinessException.class)
			.isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.getErrorCode()).isEqualTo(com.edam.common.api.ErrorCode.UNAUTHENTICATED));
	}

}
