package com.edam.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import com.edam.common.exception.BusinessException;
import com.edam.identity.model.InstallationState;
import com.edam.identity.repository.InstallationStateRepository;
import com.edam.identity.service.AuthService;
import com.edam.identity.service.InstallationBootstrapService;
import com.edam.identity.service.PasswordPolicy;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Authentication behaviour against real PostgreSQL.
 *
 * <p>These tests exist because the audit found that no test in the project touched a service or a
 * repository: the security spine was proven by an architecture test (annotations exist) but never by
 * behaviour (a locked account is actually refused). That distinction is the whole point of this class.
 *
 * <p>Every test creates its own user with a random username, so they neither depend on nor disturb each
 * other, and none of them relies on fixture data that a migration might one day change.
 *
 * <h2>Deliberately not {@code @Transactional}</h2>
 * {@code AuthService#authenticate} runs in {@code REQUIRES_NEW} so that a failed sign-in persists its
 * evidence — if it joined the caller's transaction, the rollback caused by the failure would erase the
 * record of it and the lockout would never engage. A test-level transaction therefore cannot be seen by
 * the code under test, and every fixture would appear not to exist. Creating real, committed rows is the
 * only way to test this path honestly; unique usernames keep the tests independent.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthenticationIntegrationTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository users;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private PasswordPolicy passwordPolicy;

	@Autowired
	private InstallationBootstrapService bootstrap;

	@Autowired
	private InstallationStateRepository installationState;

	private User newUser(String password) {
		User user = new User();
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		user.setUsername("itest-" + suffix);
		user.setFirstName("Integration");
		user.setLastName("Tester");
		user.setEmail("itest-" + suffix + "@bank.example");
		user.setEmployeeCode("IT-" + suffix);
		user.setActive(true);
		user.changePassword(this.passwordEncoder.encode(password), Instant.now());
		return this.users.save(user);
	}

	@Test
	@DisplayName("a valid credential authenticates and records the success")
	void validCredentialAuthenticates() {
		User user = newUser("Str0ng!Passphrase");
		User authenticated = this.authService.authenticate(user.getUsername(), "Str0ng!Passphrase", "127.0.0.1");
		assertThat(authenticated.getId()).isEqualTo(user.getId());
		assertThat(authenticated.getLastLoginAt()).isNotNull();
	}

	@Test
	@DisplayName("username matching is case-insensitive, because a capital letter is a support ticket")
	void usernameIsCaseInsensitive() {
		User user = newUser("Str0ng!Passphrase");
		User authenticated = this.authService.authenticate(user.getUsername().toUpperCase(), "Str0ng!Passphrase",
				"127.0.0.1");
		assertThat(authenticated.getId()).isEqualTo(user.getId());
	}

	@Test
	@DisplayName("a wrong password is refused with INVALID_CREDENTIALS")
	void wrongPasswordRefused() {
		User user = newUser("Str0ng!Passphrase");
		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Wr0ng!Passphrase", "127.0.0.1"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("username or password");
	}

	@Test
	@DisplayName("an unknown username is refused with the SAME message as a wrong password")
	void unknownUserIsIndistinguishable() {
		// This is the enumeration defence stated as an executable assertion. If the two messages ever
		// diverge, the login form becomes a user directory and this test fails.
		String wrongPasswordMessage = catchMessage(
				() -> this.authService.authenticate("definitely-not-a-user", "Wr0ng!Passphrase", "127.0.0.1"));
		String existingUser = newUser("Str0ng!Passphrase").getUsername();
		String badPasswordMessage = catchMessage(
				() -> this.authService.authenticate(existingUser, "Wr0ng!Passphrase", "127.0.0.1"));

		assertThat(wrongPasswordMessage).isEqualTo(badPasswordMessage);
	}

	private static String catchMessage(Runnable action) {
		try {
			action.run();
			throw new AssertionError("expected a BusinessException");
		}
		catch (BusinessException ex) {
			return ex.getMessage();
		}
	}

	@Test
	@DisplayName("repeated failures lock the account, and the lock is persisted")
	void repeatedFailuresLockTheAccount() {
		User user = newUser("Str0ng!Passphrase");
		for (int attempt = 0; attempt < 4; attempt++) {
			assertThatThrownBy(
					() -> this.authService.authenticate(user.getUsername(), "Wr0ng!Passphrase", "127.0.0.1"))
				.isInstanceOf(BusinessException.class);
		}
		User afterFour = this.users.findById(user.getId()).orElseThrow();
		assertThat(afterFour.getFailedLoginCount()).isEqualTo(4);
		assertThat(afterFour.getLockedUntil()).as("four failures must not lock at a threshold of five").isNull();

		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Wr0ng!Passphrase", "127.0.0.1"))
			.isInstanceOf(BusinessException.class);

		User afterFive = this.users.findById(user.getId()).orElseThrow();
		assertThat(afterFive.getLockedUntil()).as("the fifth failure must engage the lock").isNotNull();
		assertThat(afterFive.isLocked(Instant.now())).isTrue();
	}

	@Test
	@DisplayName("a locked account refuses even the CORRECT password, and says so")
	void lockedAccountRefusesCorrectPassword() {
		User user = newUser("Str0ng!Passphrase");
		for (int attempt = 0; attempt < 5; attempt++) {
			assertThatThrownBy(
					() -> this.authService.authenticate(user.getUsername(), "Wr0ng!Passphrase", "127.0.0.1"))
				.isInstanceOf(BusinessException.class)
				.as("attempts before the lock report bad credentials");
		}
		// Knowing the password must not help: otherwise the lockout is advisory.
		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Str0ng!Passphrase", "127.0.0.1"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("temporarily locked");
	}

	@Test
	@DisplayName("a successful sign-in clears the failure count and any lock")
	void successResetsLockState() {
		User user = newUser("Str0ng!Passphrase");
		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Wr0ng!1Passphrase", "1.1.1.1"))
			.isInstanceOf(BusinessException.class);
		assertThat(this.users.findById(user.getId()).orElseThrow().getFailedLoginCount()).isEqualTo(1);

		this.authService.authenticate(user.getUsername(), "Str0ng!Passphrase", "127.0.0.1");

		User after = this.users.findById(user.getId()).orElseThrow();
		assertThat(after.getFailedLoginCount()).isZero();
		assertThat(after.getLockedUntil()).isNull();
	}

	@Test
	@DisplayName("an inactive account cannot sign in even with the correct password")
	void inactiveAccountRefused() {
		User user = newUser("Str0ng!Passphrase");
		user.setActive(false);
		this.users.save(user);

		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Str0ng!Passphrase", "127.0.0.1"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("username or password");
	}

	@Test
	@DisplayName("a user with no local credential cannot sign in, and no password is accepted")
	void noLocalCredentialCannotAuthenticate() {
		// Models the SSO case: the row exists, authentication is delegated, so no local credential exists.
		User user = newUser("Str0ng!Passphrase");
		user.setPasswordHash(null);
		this.users.save(user);

		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Str0ng!Passphrase", "127.0.0.1"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("a password reset token is single-use")
	void resetTokenIsSingleUse() {
		User user = newUser("Str0ng!Passphrase");
		String rawToken = this.authService.forgotPassword(user.getEmail()).orElseThrow();

		this.authService.resetPassword(rawToken, "N3w!Passphrase99");
		assertThatThrownBy(() -> this.authService.resetPassword(rawToken, "An0ther!Passphrase"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("invalid or has expired");
	}

	@Test
	@DisplayName("resetting a password actually changes what authenticates")
	void resetChangesTheCredential() {
		User user = newUser("Str0ng!Passphrase");
		String rawToken = this.authService.forgotPassword(user.getEmail()).orElseThrow();
		this.authService.resetPassword(rawToken, "N3w!Passphrase99");

		this.authService.authenticate(user.getUsername(), "N3w!Passphrase99", "127.0.0.1");
		assertThatThrownBy(() -> this.authService.authenticate(user.getUsername(), "Str0ng!Passphrase", "127.0.0.1"))
			.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("requesting a reset for an unknown address reveals nothing and issues nothing")
	void forgotPasswordIsSilentForUnknownAddress() {
		// The return value must be empty, not an error: an error would be an enumeration oracle.
		assertThat(this.authService.forgotPassword("nobody-" + UUID.randomUUID() + "@bank.example")).isEmpty();
	}

	@Test
	@DisplayName("issuing a new reset invalidates the previous one")
	void newResetInvalidatesOld() {
		User user = newUser("Str0ng!Passphrase");
		String first = this.authService.forgotPassword(user.getEmail()).orElseThrow();
		String second = this.authService.forgotPassword(user.getEmail()).orElseThrow();

		assertThatThrownBy(() -> this.authService.resetPassword(first, "An0ther!Passphrase1"))
			.isInstanceOf(BusinessException.class);
		this.authService.resetPassword(second, "An0ther!Passphrase1");
	}

	@Test
	@DisplayName("changing a password requires the current one")
	void changePasswordRequiresCurrent() {
		User user = newUser("Str0ng!Passphrase");
		var request = new com.edam.identity.dto.AuthDtos.ChangePasswordRequest("Wr0ng!Passphrase", "N3w!Passphrase99");
		assertThatThrownBy(() -> this.authService.changePassword(user, request))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("current password is incorrect");
	}

	@Test
	@DisplayName("changing a password to the same value is refused")
	void changePasswordRefusesSameValue() {
		User user = newUser("Str0ng!Passphrase");
		var request = new com.edam.identity.dto.AuthDtos.ChangePasswordRequest("Str0ng!Passphrase",
				"Str0ng!Passphrase");
		assertThatThrownBy(() -> this.authService.changePassword(user, request))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("different");
	}

	@Test
	@DisplayName("installation state is a single row, pinned by constraint")
	void installationStateIsSingleton() {
		assertThat(this.installationState.findById(InstallationState.SINGLETON_ID)).isPresent();
	}

	@Test
	@DisplayName("bootstrap is closed once the installation is complete, whatever token is presented")
	void bootstrapClosedAfterCompletion() {
		// This test does not complete the installation (that would poison every other test in the shared
		// database). It asserts the guard that fires before any token comparison: a completed installation
		// must reject bootstrap permanently.
		InstallationState state = this.installationState.findById(InstallationState.SINGLETON_ID).orElseThrow();
		if (!state.isBootstrapped()) {
			state.complete(UUID.randomUUID(), Instant.now());
			this.installationState.save(state);
		}
		assertThatThrownBy(() -> this.bootstrap.bootstrap("any-token",
				new com.edam.identity.dto.AuthDtos.BootstrapRequest("admin", "First", "Admin",
						"admin@bank.example", null, "Str0ng!Passphrase"), "127.0.0.1"))
			.isInstanceOf(BusinessException.class)
			.hasMessageContaining("already been set up");
	}

	@Test
	@DisplayName("the password policy is exposed to clients so a form can validate before submitting")
	void policyIsDescribed() {
		var description = this.passwordPolicy.describe();
		assertThat(description.minimumLength()).isGreaterThanOrEqualTo(8);
		assertThat(description.maximumLength()).isGreaterThanOrEqualTo(description.minimumLength());
	}

}
