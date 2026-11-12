package com.edam.common.security;

import org.springframework.security.core.userdetails.UserDetailsService;

/**
 * Authentication seam for EDAM.
 *
 * <p>The BRD states that authentication is inherited from the client's portal and that no
 * reusable identity integration is specified (OD-11). The foundation therefore must not
 * invent an authentication protocol. Instead it depends on this abstraction; a later phase
 * supplies a local, LDAP, OIDC or SAML implementation without changing the authorization
 * model or any controller.
 *
 * <p>Implementations <strong>must</strong> return an {@link EdamPrincipal} as the
 * {@link org.springframework.security.core.Authentication#getPrincipal() principal}, so
 * that {@code InstitutionContext} can resolve tenant membership. This contract is verified
 * in {@code EdamPrincipalContractTest} rather than expressed as a static type, because
 * {@link UserDetailsService} fixes the return type.
 */
public interface EdamUserDetailsService extends UserDetailsService {

}
