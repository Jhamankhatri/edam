package com.edam.identity;

import java.util.Collection;

import com.edam.common.security.EdamPrincipal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Development-only {@link UserDetails} that pairs the application {@link EdamPrincipal}
 * with an encoded credential for {@code DaoAuthenticationProvider}.
 *
 * <p>Production identity adapters authenticate externally and construct an
 * {@link EdamPrincipal} directly, so this wrapper never appears in the production model.
 * {@link #toString()} deliberately omits the password hash.
 */
public final class DevUserDetails implements UserDetails {

	private final transient EdamPrincipal principal;

	private final transient String encodedPassword;

	DevUserDetails(EdamPrincipal principal, String encodedPassword) {
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
		return "DevUserDetails[" + this.principal.username() + "]";
	}

}
