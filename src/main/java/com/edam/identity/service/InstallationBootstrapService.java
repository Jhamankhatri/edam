package com.edam.identity.service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.identity.dto.AuthDtos.BootstrapRequest;
import com.edam.identity.dto.AuthDtos.BootstrapResponse;
import com.edam.identity.dto.AuthDtos.InstallationStatusResponse;
import com.edam.identity.model.InstallationState;
import com.edam.identity.repository.InstallationStateRepository;
import com.edam.rolemanagement.model.Role;
import com.edam.rolemanagement.model.UserRole;
import com.edam.rolemanagement.repository.RoleRepository;
import com.edam.rolemanagement.repository.UserRoleRepository;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.model.UserDataScope;
import com.edam.usermanagement.repository.UserDataScopeRepository;
import com.edam.usermanagement.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-time setup of the first administrator (Module C).
 *
 * <h2>The problem this solves</h2>
 * A freshly deployed installation has an empty {@code users} table and must not ship a default
 * credential — a committed {@code admin/admin} is a known password in source control, in every backup
 * and in every container image ever built. Nor can the first administrator be seeded by a migration:
 * a migration runs identically on every environment, so any credential it creates is known to everyone
 * who can read the repository.
 *
 * <h2>The chosen mechanism</h2>
 * The operator supplies a one-time installation token, out of band, in the {@code X-Installation-Token}
 * header. The expected value comes from {@code EDAM_INSTALLATION_TOKEN}, and is held in memory only.
 *
 * <p>Three properties make this safe rather than merely convenient:
 * <ol>
 *   <li><strong>No token configured means bootstrap is closed</strong>, not open. A deployment that
 *       forgets to set the variable cannot be set up by an anonymous caller; it fails closed and says so
 *       in the log.</li>
 *   <li><strong>The token dies with the installation.</strong> Once {@code installation_state} is
 *       complete, the header is rejected permanently, whatever its value. There is no "re-setup"
 *       path.</li>
 *   <li><strong>Comparison is constant-time.</strong> A byte-by-byte comparison leaks the length of the
 *       shared prefix through timing, which over many attempts is enough to recover the token.</li>
 * </ol>
 *
 * <h2>What the administrator receives</h2>
 * The installation-wide system administrator role from the V12 catalogue, and a single {@code ALL}
 * data scope. That combination is what "first administrator" means in this product: every capability,
 * everywhere. It is granted to exactly one account, once, and every subsequent administrator is created
 * through the user-management API with a narrower role and scope by choice.
 */
@Service
public class InstallationBootstrapService {

	private static final Logger log = LoggerFactory.getLogger(InstallationBootstrapService.class);

	/** The catalogue role a first administrator receives. Installed by V12. */
	private static final String BOOTSTRAP_ROLE_CODE = "SYSTEM_ADMINISTRATOR";

	/** Fallback role code if a deployment renamed the system role; still a catalogue role. */
	private static final String BOOTSTRAP_ROLE_FALLBACK = "BANK_ADMINISTRATOR";

	private final InstallationStateRepository installationState;

	private final UserRepository users;

	private final RoleRepository roles;

	private final UserRoleRepository userRoles;

	private final UserDataScopeRepository dataScopes;

	private final PasswordEncoder passwordEncoder;

	private final PasswordPolicy passwordPolicy;

	private final AuditRecorder audit;

	/** The expected token, or {@code null} when bootstrap is closed. */
	private final String expectedToken;

	public InstallationBootstrapService(InstallationStateRepository installationState, UserRepository users,
			RoleRepository roles, UserRoleRepository userRoles, UserDataScopeRepository dataScopes,
			PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, AuditRecorder audit,
			@Value("${edam.installation.bootstrap-token:}") String expectedToken) {
		this.installationState = installationState;
		this.users = users;
		this.roles = roles;
		this.userRoles = userRoles;
		this.dataScopes = dataScopes;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.audit = audit;
		this.expectedToken = (expectedToken == null || expectedToken.isBlank()) ? null : expectedToken;
		if (this.expectedToken == null) {
			log.info("installation.bootstrap closed reason=no_installation_token_configured");
		}
	}

	/**
	 * Whether the installation is complete.
	 *
	 * <p>Read from a single row so it is O(1) and cannot be confused by a partially-created user set.
	 */
	@Transactional(readOnly = true)
	public InstallationStatusResponse status() {
		InstallationState state = requireState();
		return new InstallationStatusResponse(state.isBootstrapped(), state.getBootstrappedAt());
	}

	/**
	 * Creates the first administrator and closes the installation.
	 *
	 * @param presentedToken the value of {@code X-Installation-Token}
	 * @param request the administrator to create
	 * @return the created administrator, without any credential material
	 * @throws BusinessException when the installation is closed, the token is wrong, or setup is disabled
	 */
	@Transactional
	public BootstrapResponse bootstrap(String presentedToken, BootstrapRequest request, String ipAddress) {
		InstallationState state = requireState();

		if (state.isBootstrapped()) {
			// Rejected before any token check: once installed, the endpoint is dead regardless of secrets.
			throw new BusinessException(ErrorCode.INSTALLATION_ALREADY_COMPLETED);
		}
		if (this.expectedToken == null) {
			// Fail closed. An operator who did not configure a token must set one deliberately.
			throw new BusinessException(ErrorCode.INSTALLATION_NOT_BOOTSTRAPPED,
					"Installation setup is disabled: no installation token is configured for this deployment.");
		}
		if (!constantTimeEquals(presentedToken, this.expectedToken)) {
			state.recordFailedAttempt(Instant.now());
			this.installationState.save(state);
			log.warn("installation.bootstrap_rejected reason=invalid_token ip={}", ipAddress);
			// Same code as an unconfigured deployment: an attacker learns nothing about why.
			throw new BusinessException(ErrorCode.INSTALLATION_NOT_BOOTSTRAPPED, "The installation token is not valid.");
		}

		// Validate everything before writing anything, so a rejected request leaves no partial installation.
		this.passwordPolicy.validate(request.password(), null);
		String username = AuthService.normaliseUsername(request.username());
		if (username == null || username.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A username is required.");
		}
		if (this.users.existsByUsernameIgnoreCase(username)) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That username is already in use.");
		}
		if (this.users.existsByEmailIgnoreCase(request.email())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "That e-mail address is already in use.");
		}

		Role role = resolveBootstrapRole();

		Instant now = Instant.now();
		User administrator = new User();
		administrator.setUsername(username);
		administrator.setFirstName(request.firstName().trim());
		administrator.setLastName(request.lastName().trim());
		administrator.setEmail(request.email().trim());
		administrator.setEmployeeCode(blankToNull(request.employeeCode()));
		administrator.setActive(true);
		administrator.changePassword(this.passwordEncoder.encode(request.password()), now);
		User saved = this.users.save(administrator);

		this.userRoles.save(new UserRole(saved.getId(), role.getId(), null, null));
		// Installation-wide scope: the first administrator's role is meaningless without it, because an
		// unscoped user is denied record access by design.
		this.dataScopes.save(new UserDataScope(saved.getId(), new com.edam.common.security.DataScope(
				com.edam.common.security.DataScope.Type.ALL, null), "installation-bootstrap"));

		state.complete(saved.getId(), now);
		this.installationState.save(state);

		this.audit.record(AuditAction.CREATE, "User", saved.getId(), "First administrator created by installation bootstrap");
		log.info("installation.bootstrap_completed adminUserId={} username={} role={}", saved.getId(),
				saved.getUsername(), role.getCode());

		return new BootstrapResponse(saved.getId(), saved.getUsername(), saved.displayName(),
				List.of(role.getCode()), now);
	}

	/**
	 * Resolves the role the first administrator receives.
	 *
	 * <p>Both candidates are catalogue roles installed by migration. Falling back keeps bootstrap working
	 * if a deployment renamed one; failing outright would make a renamed role look like a broken install.
	 */
	private Role resolveBootstrapRole() {
		List<String> candidates = new ArrayList<>(List.of(BOOTSTRAP_ROLE_CODE, BOOTSTRAP_ROLE_FALLBACK));
		for (String code : candidates) {
			var found = this.roles.findByCode(code);
			if (found.isPresent()) {
				return found.get();
			}
		}
		throw new BusinessException(ErrorCode.INVALID_CONFIGURATION,
				"The permission catalogue is incomplete: no bootstrap administrator role is installed.");
	}

	private InstallationState requireState() {
		return this.installationState.findById(InstallationState.SINGLETON_ID)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CONFIGURATION,
					"The installation state row is missing; run the database migrations."));
	}

	/**
	 * Length-safe, constant-time comparison.
	 *
	 * <p>{@link MessageDigest#isEqual} is constant-time only for equal-length inputs, and returning early
	 * on a length mismatch would leak the token's length. Both sides are therefore hashed first, which
	 * makes the comparison input-independent.
	 */
	private static boolean constantTimeEquals(String presented, String expected) {
		if (presented == null || expected == null) {
			return false;
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] a = digest.digest(presented.getBytes(StandardCharsets.UTF_8));
			byte[] b = digest.digest(expected.getBytes(StandardCharsets.UTF_8));
			return MessageDigest.isEqual(a, b);
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}

}
