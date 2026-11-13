package com.edam.identity.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.identity.dto.AuthDtos.ChangePasswordRequest;
import com.edam.identity.dto.AuthDtos.CurrentUserResponse;
import com.edam.identity.dto.AuthDtos.DataScopeView;
import com.edam.identity.dto.AuthDtos.NeutralResponse;
import com.edam.identity.dto.AuthDtos.PasswordPolicyView;
import com.edam.identity.model.LoginAttempt;
import com.edam.identity.model.PasswordResetToken;
import com.edam.identity.repository.LoginAttemptRepository;
import com.edam.identity.repository.PasswordResetTokenRepository;
import com.edam.organization.model.Branch;
import com.edam.organization.model.Department;
import com.edam.organization.repository.BranchRepository;
import com.edam.organization.repository.DepartmentRepository;
import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.model.UserDataScope;
import com.edam.usermanagement.repository.UserDataScopeRepository;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication and self-service credential management (Module C).
 *
 * <h2>Threats this class is written against</h2>
 * <ol>
 *   <li><strong>Account enumeration.</strong> Every failure path — unknown user, wrong password,
 *       inactive account, locked account — produces the same code and message to the caller.
 *       Distinguishing them turns the login form into a user directory.</li>
 *   <li><strong>Credential stuffing / brute force.</strong> Consecutive failures lock the account for a
 *       bounded window. The lockout is recorded on the user row so the decision is O(1) at sign-in.</li>
 *   <li><strong>Timing analysis.</strong> When no user matches, a hash is still computed against a dummy
 *       credential. Returning early would make "user exists" measurably faster than "user does not".</li>
 *   <li><strong>Session fixation.</strong> Handled by Spring Security at the filter level
 *       ({@code migrateSession}); this class never establishes a session itself.</li>
 *   <li><strong>Reset-token leakage.</strong> Only the SHA-256 of a reset token is stored; the raw value
 *       exists only in the message handed to the user.</li>
 * </ol>
 *
 * <h2>Why authentication is not done through the AuthenticationManager</h2>
 * The application needs more than a yes/no: it must distinguish "locked" from "bad credentials" for its
 * own audit record, must persist the attempt, and must reset the failure counter. Doing that in a filter
 * would spread the rule across two layers. The password comparison itself is still
 * {@link PasswordEncoder}, so the hashing algorithm is unchanged and single-sourced.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/**
	 * A syntactically valid BCrypt hash of a random value, used only to burn the same CPU time when no
	 * user matches. It is not a credential for any account and cannot authenticate anything.
	 */
	private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final UserRepository users;

	private final RoleRepository roles;

	private final UserDataScopeRepository dataScopes;

	private final DepartmentRepository departments;

	private final BranchRepository branches;

	private final LoginAttemptRepository loginAttempts;

	private final PasswordResetTokenRepository resetTokens;

	private final PasswordEncoder passwordEncoder;

	private final PasswordPolicy passwordPolicy;

	private final SecureTokens tokens;

	private final AuditRecorder audit;

	private final LoginAttemptRecorder attemptRecorder;

	private final Duration resetTokenTtl;

	public AuthService(UserRepository users, RoleRepository roles, UserDataScopeRepository dataScopes,
			DepartmentRepository departments, BranchRepository branches, LoginAttemptRepository loginAttempts,
			PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
			SecureTokens tokens, AuditRecorder audit, LoginAttemptRecorder attemptRecorder,
			@Value("${edam.security.password-reset.ttl:PT30M}") Duration resetTokenTtl) {
		this.users = users;
		this.roles = roles;
		this.dataScopes = dataScopes;
		this.departments = departments;
		this.branches = branches;
		this.loginAttempts = loginAttempts;
		this.resetTokens = resetTokens;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.tokens = tokens;
		this.audit = audit;
		this.attemptRecorder = attemptRecorder;
		this.resetTokenTtl = resetTokenTtl;
	}

	// ---------------------------------------------------------------------------------------------
	// Sign in
	// ---------------------------------------------------------------------------------------------

	/**
	 * Verifies credentials and returns the user on success.
	 *
	 * <p>Runs in its own transaction ({@code REQUIRES_NEW}) so that a failure still persists the attempt
	 * and the incremented counter. If it joined the caller's transaction, the very rollback triggered by
	 * the failure would erase the evidence of it — and the lockout would never engage.
	 *
	 * @param username the presented login name
	 * @param presentedPassword the presented password
	 * @param ipAddress the caller's address, for the evidence record
	 * @return the authenticated user
	 * @throws BusinessException {@code INVALID_CREDENTIALS} or {@code ACCOUNT_LOCKED}
	 */
	/**
	 * Verifies credentials and returns the user on success.
	 *
	 * <h2>Transaction design, and the bug it prevents</h2>
	 * This method is <strong>not</strong> transactional. That looks wrong until you follow the failure
	 * path: it must persist an incremented failure counter and then throw. A {@code BusinessException} is
	 * a runtime exception, so any transaction around this method would be marked rollback-only and would
	 * discard that counter — the threshold would never be reached and the lockout would never engage.
	 *
	 * <p>The evidence and the counter are therefore written by {@link LoginAttemptRecorder} in an
	 * independent, committed transaction, and only afterwards is the caller rejected. The successful path
	 * saves the user (clearing the counter, stamping the last sign-in) in its own short transaction.
	 *
	 * @param username the presented login name
	 * @param presentedPassword the presented password
	 * @param ipAddress the caller's address, for the evidence record
	 * @return the authenticated user
	 * @throws BusinessException {@code INVALID_CREDENTIALS} or {@code ACCOUNT_LOCKED}
	 */
	public User authenticate(String username, String presentedPassword, String ipAddress) {
		Instant now = Instant.now();
		String normalised = (username == null) ? "" : username.trim();

		Optional<User> maybeUser = this.users.findByUsernameIgnoreCase(normalised);
		if (maybeUser.isEmpty()) {
			// Burn comparable CPU so an unknown username is not measurably faster than a wrong password.
			this.passwordEncoder.matches(presentedPassword == null ? "" : presentedPassword, DUMMY_HASH);
			this.attemptRecorder.recordFailure(null, normalised, ipAddress, "unknown_user", now);
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		User user = maybeUser.get();

		if (user.isLocked(now)) {
			// The lockout message is distinguishable from bad credentials *only* for a correct password:
			// telling an attacker that a real account is locked would confirm it exists.
			boolean passwordCorrect = user.hasLocalCredential()
					&& this.passwordEncoder.matches(presentedPassword, user.getPasswordHash());
			this.attemptRecorder.recordFailure(user.getId(), normalised, ipAddress, "account_locked", now);
			if (passwordCorrect) {
				throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
			}
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		boolean passwordMatches = user.hasLocalCredential() && presentedPassword != null
				&& this.passwordEncoder.matches(presentedPassword, user.getPasswordHash());

		if (!passwordMatches) {
			// Committed independently, so this cannot be rolled back by the rejection that follows.
			this.attemptRecorder.recordFailure(user.getId(), normalised, ipAddress, "bad_password", now);
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		// Correct password but the account was deactivated. Reported as invalid credentials: whether an
		// account is disabled is not information an unauthenticated caller is entitled to.
		if (!user.isActive()) {
			this.attemptRecorder.recordFailure(user.getId(), normalised, ipAddress, "inactive_account", now);
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		// One committed transaction clears the counter, stamps the sign-in and records the evidence.
		this.attemptRecorder.recordSuccess(user.getId(), normalised, ipAddress, now);
		user.clearFailedLogins();
		user.setLastLoginAt(now);
		this.audit.record(AuditAction.LOGIN_SUCCESS, "User", user.getId(),
				"Signed in from " + (ipAddress == null ? "unknown address" : ipAddress));
		log.info("auth.login_success userId={} username={}", user.getId(), user.getUsername());
		return user;
	}

	// ---------------------------------------------------------------------------------------------
	// Current user
	// ---------------------------------------------------------------------------------------------

	/** Builds the self-description used by the client after sign-in. */
	@Transactional(readOnly = true)
	public CurrentUserResponse currentUser(User user) {
		List<Role> activeRoles = this.roles.findActiveRolesForUser(user.getId());
		Set<String> roleCodes = new LinkedHashSet<>();
		Set<String> permissions = new LinkedHashSet<>();
		for (Role role : activeRoles) {
			roleCodes.add(role.getCode());
			permissions.addAll(role.permissionCodes());
		}

		List<DataScopeView> scopes = new ArrayList<>();
		for (UserDataScope scope : this.dataScopes.findByUserId(user.getId())) {
			scopes.add(new DataScopeView(scope.getScopeType().name(), scope.getScopeReferenceId(),
					describeScope(scope)));
		}

		String departmentName = (user.getDepartmentId() == null) ? null
				: this.departments.findById(user.getDepartmentId()).map(Department::getName).orElse(null);
		String branchName = (user.getBranchId() == null) ? null
				: this.branches.findById(user.getBranchId()).map(Branch::getName).orElse(null);

		boolean installationScope = scopes.stream().anyMatch(scope -> "ALL".equals(scope.type()));

		return new CurrentUserResponse(user.getId(), user.getUsername(), user.getEmployeeCode(), user.displayName(),
				user.getEmail(), user.getDesignation(), user.getDepartmentId(), departmentName, user.getBranchId(),
				branchName, List.copyOf(roleCodes), List.copyOf(permissions), scopes, installationScope,
				user.getPasswordChangedAt() == null, user.getLastLoginAt(),
				new PasswordPolicyView(this.passwordPolicy.describe().minimumLength(),
						this.passwordPolicy.describe().maximumLength(),
						this.passwordPolicy.describe().requireUppercase(),
						this.passwordPolicy.describe().requireLowercase(),
						this.passwordPolicy.describe().requireDigit(),
						this.passwordPolicy.describe().requireSpecial()));
	}

	private String describeScope(UserDataScope scope) {
		if (scope.getScopeReferenceId() == null) {
			return (scope.getScopeType() == com.edam.common.security.DataScope.Type.ALL) ? "Entire installation"
					: "Own records only";
		}
		return switch (scope.getScopeType()) {
			case DEPARTMENT -> this.departments.findById(scope.getScopeReferenceId()).map(Department::getName)
				.orElse(null);
			case BRANCH -> this.branches.findById(scope.getScopeReferenceId()).map(Branch::getName).orElse(null);
			default -> null;
		};
	}

	// ---------------------------------------------------------------------------------------------
	// Change password
	// ---------------------------------------------------------------------------------------------

	/**
	 * Changes the caller's own password.
	 *
	 * <p>Requires the current password: without it, anyone who found an unlocked browser could change the
	 * credential and lock the real owner out of their account permanently.
	 */
	@Transactional
	public void changePassword(User user, ChangePasswordRequest request) {
		if (!user.hasLocalCredential()
				|| !this.passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			// Same code as a failed sign-in so a stolen session cannot be used to probe for the password.
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "The current password is incorrect.");
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw new BusinessException(ErrorCode.WEAK_PASSWORD,
					"The new password must be different from the current password.");
		}
		this.passwordPolicy.validate(request.newPassword(), user);
		user.changePassword(this.passwordEncoder.encode(request.newPassword()), Instant.now());
		this.users.save(user);
		// Outstanding reset links were issued for the old credential and must not survive it.
		this.resetTokens.invalidateOutstanding(user.getId(), Instant.now());
		this.audit.record(AuditAction.UPDATE, "User", user.getId(), "Changed own password");
		log.info("auth.password_changed userId={}", user.getId());
	}

	// ---------------------------------------------------------------------------------------------
	// Forgot / reset password
	// ---------------------------------------------------------------------------------------------

	/**
	 * Issues a reset grant, if the address belongs to an active user.
	 *
	 * <h2>Enumeration defence</h2>
	 * The return value is identical in every case, including when no account exists, and no exception is
	 * thrown. The caller cannot learn from this endpoint whether an address is registered — which is why
	 * it returns a {@link NeutralResponse} rather than a result object.
	 *
	 * <p>The raw token is returned to the <em>service</em> caller so the notification layer can deliver
	 * it. It is never returned over HTTP and never logged.
	 *
	 * @return the raw reset token when a grant was issued, or empty when it was not
	 */
	@Transactional
	public Optional<String> forgotPassword(String email) {
		Instant now = Instant.now();
		Optional<User> maybeUser = this.users.findByEmailIgnoreCase(email).filter(User::isActive);
		if (maybeUser.isEmpty()) {
			// Deliberately silent. Any observable difference here is an enumeration oracle.
			log.debug("auth.reset_requested_for_unknown_address");
			return Optional.empty();
		}
		User user = maybeUser.get();
		if (!user.hasLocalCredential()) {
			// The account authenticates through an external provider; a local reset would be meaningless
			// and would create a credential the identity provider does not know about.
			log.info("auth.reset_skipped reason=no_local_credential userId={}", user.getId());
			return Optional.empty();
		}
		// Any earlier grant dies when a new one is issued, so only the newest link works.
		this.resetTokens.invalidateOutstanding(user.getId(), now);
		String rawToken = this.tokens.generate();
		this.resetTokens.save(new PasswordResetToken(user.getId(), this.tokens.hash(rawToken), now,
				now.plus(this.resetTokenTtl)));
		this.audit.record(AuditAction.UPDATE, "User", user.getId(), "Password reset requested");
		log.info("auth.reset_issued userId={}", user.getId());
		return Optional.of(rawToken);
	}

	/** The constant message returned by the forgot-password endpoint for every outcome. */
	public static NeutralResponse neutralResetMessage() {
		return new NeutralResponse(
				"If that address belongs to an active account, password reset instructions have been sent.");
	}

	/**
	 * Redeems a reset token and sets a new password.
	 *
	 * <p>The token is looked up by hash, so the raw value is never stored or compared by value. An
	 * unknown, used or expired token yields one indistinguishable error.
	 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		Instant now = Instant.now();
		PasswordResetToken grant = this.resetTokens.findByTokenHash(this.tokens.hash(rawToken))
			.filter(candidate -> candidate.isRedeemable(now))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));

		User user = this.users.findById(grant.getUserId())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_RESET_TOKEN));
		if (!user.isActive()) {
			throw new BusinessException(ErrorCode.INVALID_RESET_TOKEN);
		}
		this.passwordPolicy.validate(newPassword, user);
		user.changePassword(this.passwordEncoder.encode(newPassword), now);
		this.users.save(user);
		grant.markUsed(now);
		this.resetTokens.save(grant);
		// A completed reset invalidates every other outstanding grant for the account.
		this.resetTokens.invalidateOutstanding(user.getId(), now);
		this.audit.record(AuditAction.UPDATE, "User", user.getId(), "Password reset completed");
		log.info("auth.reset_completed userId={}", user.getId());
	}

	// ---------------------------------------------------------------------------------------------
	// Administrator-driven password reset
	// ---------------------------------------------------------------------------------------------

	/**
	 * Issues a reset grant on behalf of an administrator.
	 *
	 * <p>Exists so the user-management module can trigger the same secure flow rather than inventing a
	 * second one. The administrator never sees or sets the password; they only cause a link to be sent.
	 */
	@Transactional
	public Optional<String> issueAdministrativeReset(UUID userId) {
		User user = this.users.findById(userId)
			.orElseThrow(() -> new com.edam.common.exception.ResourceNotFoundException("User", userId));
		Instant now = Instant.now();
		this.resetTokens.invalidateOutstanding(user.getId(), now);
		String rawToken = this.tokens.generate();
		this.resetTokens.save(new PasswordResetToken(user.getId(), this.tokens.hash(rawToken), now,
				now.plus(this.resetTokenTtl)));
		this.audit.record(AuditAction.UPDATE, "User", user.getId(), "Password reset issued by an administrator");
		return Optional.of(rawToken);
	}

	/** Loads a user by id, for the current-user endpoints. */
	@Transactional(readOnly = true)
	public User findUser(UUID userId) {
		return this.users.findById(userId)
			.orElseThrow(() -> new com.edam.common.exception.ResourceNotFoundException("User", userId));
	}

	/**
	 * Builds the security principal for an authenticated user.
	 *
	 * <p>Delegates to the same computation the identity adapter uses — active roles, their permissions,
	 * and the user's data scopes — so a session created by the login controller carries exactly the
	 * authorities a later request would load from the database. Duplicating that logic here would allow
	 * the two to drift, and a drifted principal is a privilege bug.
	 */
	@Transactional(readOnly = true)
	public org.springframework.security.core.userdetails.UserDetails buildPrincipal(User user) {
		List<Role> activeRoles = this.roles.findActiveRolesForUser(user.getId());
		Set<String> roleCodes = new LinkedHashSet<>();
		Set<String> permissions = new LinkedHashSet<>();
		for (Role role : activeRoles) {
			roleCodes.add(role.getCode());
			permissions.addAll(role.permissionCodes());
		}
		Set<com.edam.common.security.DataScope> scopes = new LinkedHashSet<>();
		for (UserDataScope row : this.dataScopes.findByUserId(user.getId())) {
			scopes.add(row.toDataScope());
		}
		return new com.edam.common.security.EdamPrincipal(user.getId(), user.getEmployeeCode(), user.getUsername(),
				user.displayName(), roleCodes, permissions, scopes);
	}

	/** Normalises a login name for storage and lookup; usernames are matched case-insensitively. */
	public static String normaliseUsername(String username) {
		return (username == null) ? null : username.trim().toLowerCase(Locale.ROOT);
	}

}
