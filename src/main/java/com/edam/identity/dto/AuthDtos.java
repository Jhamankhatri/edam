package com.edam.identity.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Authentication API contract (Module C).
 *
 * <h2>Why there is no token in any response</h2>
 * Authentication is session-based, so the credential is the {@code JSESSIONID} cookie, set by the
 * container with {@code HttpOnly}. Returning a token as well would create a second, weaker credential
 * that JavaScript could read and exfiltrate — the cookie must be the only one.
 */
public final class AuthDtos {

	private AuthDtos() {
	}

	/** Sign-in request. Never logged. */
	public record LoginRequest(@NotBlank @Size(max = 100) String username,
			@NotBlank @Size(max = 200) String password) {
	}

	/**
	 * The authenticated caller's own identity, roles and effective permissions.
	 *
	 * <p>This is the bootstrap call a React app makes after login: one round trip supplies everything
	 * needed to decide which navigation items and actions to render. It is a convenience for the UI,
	 * never a security control — every endpoint re-checks the permission server-side.
	 */
	public record CurrentUserResponse(UUID userId, String username, String employeeCode, String displayName,
			String email, String designation, UUID departmentId, String departmentName, UUID branchId,
			String branchName, List<String> roles, List<String> permissions, List<DataScopeView> dataScopes,
			boolean installationScope, boolean mustChangePassword, Instant lastLoginAt,
			PasswordPolicyView passwordPolicy) {
	}

	/** One data scope, flattened for the client. */
	public record DataScopeView(String type, UUID referenceId, String referenceName) {
	}

	/** The password policy, so a client can validate before submitting. */
	public record PasswordPolicyView(int minimumLength, int maximumLength, boolean requireUppercase,
			boolean requireLowercase, boolean requireDigit, boolean requireSpecial) {
	}

	/** Change one's own password. */
	public record ChangePasswordRequest(@NotBlank @Size(max = 200) String currentPassword,
			@NotBlank @Size(max = 200) String newPassword) {
	}

	/**
	 * Request a reset link.
	 *
	 * <p>The response is identical whether or not the address exists — that is the whole point. See
	 * {@code AuthService#forgotPassword}.
	 */
	public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 255) String email) {
	}

	/** Redeem a reset token. */
	public record ResetPasswordRequest(@NotBlank @Size(max = 400) String token,
			@NotBlank @Size(max = 200) String newPassword) {
	}

	/**
	 * The result of a request that must not reveal whether an account exists.
	 *
	 * <p>{@code message} is constant for every outcome. No field distinguishes "sent" from "no such
	 * account", because that difference is an account-enumeration oracle.
	 */
	public record NeutralResponse(String message) {
	}

	/**
	 * Installation status, readable before authentication.
	 *
	 * <p>A React app must know whether to show the login form or the first-run setup form, and it must
	 * learn that before it has any session. This endpoint therefore exposes exactly one bit of
	 * information and nothing that could help an attacker: not the admin's name, not the user count.
	 */
	public record InstallationStatusResponse(boolean bootstrapped, Instant bootstrappedAt) {
	}

	/**
	 * First-administrator setup.
	 *
	 * <p>Authorised by a one-time installation token held by the operator, never by a credential in
	 * the source tree. The token is supplied out of band (environment variable or printed once at
	 * startup) and is not part of this request body — it travels in a header so it cannot end up in a
	 * URL, browser history or access log.
	 */
	public record BootstrapRequest(@NotBlank @Size(max = 100) String username,
			@NotBlank @Size(max = 100) String firstName, @NotBlank @Size(max = 100) String lastName,
			@NotBlank @Email @Size(max = 255) String email, @Size(max = 50) String employeeCode,
			@NotBlank @Size(max = 200) String password) {
	}

	/** The administrator created by bootstrap. Deliberately carries no credential. */
	public record BootstrapResponse(UUID userId, String username, String displayName, List<String> roles,
			Instant bootstrappedAt) {
	}

	/**
	 * CSRF token for the client to echo in {@code X-XSRF-TOKEN}.
	 *
	 * <p>Also readable from a non-HttpOnly cookie, but returned here as well so a client that cannot
	 * read cookies (or prefers not to) can still obtain it.
	 */
	public record CsrfTokenResponse(String headerName, String parameterName, String token) {
	}

}
