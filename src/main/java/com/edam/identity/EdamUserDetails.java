package com.edam.identity;

import java.util.Collection;

import com.edam.common.security.EdamPrincipal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * {@link UserDetails} wrapper that pairs the application {@link EdamPrincipal} with an optional
 * encoded credential, so that both identity adapters can return the same type.
 *
 * <h2>Why a wrapper at all</h2>
 * Spring Security's contract requires {@code getPassword()}, while {@link EdamPrincipal} deliberately
 * carries no credential material. Keeping the password in a separate, short-lived object means the
 * principal placed in the security context can never leak a hash when it is serialised, logged or
 * put in a session.
 *
 * <p>The database adapter returns this wrapper with a {@code null} password: it identifies a user
 * from stored memberships but does not verify credentials, because BRD OD-24 leaves the identity
 * provider (and therefore the credential model) to the bank. {@link #toString()} deliberately omits
 * the hash in both cases.
 */
public final class EdamUserDetails implements UserDetails {

	private final transient EdamPrincipal principal;

	private final transient String encodedPassword;

	public EdamUserDetails(EdamPrincipal principal, String encodedPassword) {
		this.principal = principal;
		this.encodedPassword = encodedPassword;
	}

	/** The application principal placed in the security context after authentication. */
	public EdamPrincipal principal() {
		return this.principal;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return this.principal.getAuthorities();
	}

	@Override
	public String getPassword() {
		return this.encodedPassword;
	}

	@Override
	public String getUsername() {
		return this.principal.getUsername();
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String toString() {
		return "EdamUserDetails[" + this.principal.username() + "]";
	}

}
