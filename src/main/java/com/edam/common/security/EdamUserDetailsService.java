package com.edam.common.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Authentication seam for EDAM.
 *
 * <p>The BRD states that authentication is inherited from the client's portal and that no
 * reusable identity integration is specified (OD-11). The foundation therefore must not
 * invent an authentication protocol. Instead it depends on this abstraction; a later phase
 * supplies a local, LDAP, OIDC or SAML implementation without changing the authorization
 * model or any controller.
 *
 * <p>Implementations must return an {@link EdamPrincipal} as the
 * {@link UserDetailsService#loadUserByUsername(String)} subject so that
 * {@code InstitutionContext} can resolve tenant membership.
 */
public interface EdamUserDetailsService extends UserDetailsService {

	@Override
	EdamPrincipal loadUserByUsername(String username) throws UsernameNotFoundException;

}
