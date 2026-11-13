package com.edam.identity.controller;

import java.util.Optional;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.PublicEndpoint;
import com.edam.identity.dto.AuthDtos.BootstrapRequest;
import com.edam.identity.dto.AuthDtos.BootstrapResponse;
import com.edam.identity.dto.AuthDtos.ChangePasswordRequest;
import com.edam.identity.dto.AuthDtos.CsrfTokenResponse;
import com.edam.identity.dto.AuthDtos.CurrentUserResponse;
import com.edam.identity.dto.AuthDtos.ForgotPasswordRequest;
import com.edam.identity.dto.AuthDtos.InstallationStatusResponse;
import com.edam.identity.dto.AuthDtos.LoginRequest;
import com.edam.identity.dto.AuthDtos.NeutralResponse;
import com.edam.identity.dto.AuthDtos.ResetPasswordRequest;
import com.edam.identity.service.AuthService;
import com.edam.identity.service.InstallationBootstrapService;
import com.edam.usermanagement.model.User;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Browser-facing authentication (Module C).
 *
 * <h2>Why the session is established here and not by a filter</h2>
 * Spring Security's form login authenticates a URL-encoded POST and answers with a 302. A React client
 * receives a redirect it cannot follow usefully and a cookie it cannot read. This controller accepts
 * JSON, performs the same verification through the same {@code PasswordEncoder}, and then writes the
 * session explicitly via {@link SecurityContextRepository}.
 *
 * <p>Writing the context explicitly is the part that makes the session actually persist. Setting
 * {@code SecurityContextHolder} alone is not enough: with {@code IF_REQUIRED} sessions and no
 * {@code SecurityContextPersistenceFilter} having run a save, the context would be discarded at the end
 * of the request and the next call would be anonymous.
 *
 * <h2>CSRF</h2>
 * The token is issued as a {@code XSRF-TOKEN} cookie readable by JavaScript
 * ({@code CookieCsrfTokenRepository.withHttpOnlyFalse()}, configured in {@code SecurityConfig}), and is
 * also returned by {@code GET /auth/csrf} for clients that prefer an explicit fetch. The header to echo
 * it in is {@code X-XSRF-TOKEN}. CSRF is never disabled.
 *
 * <h2>What is public, and why</h2>
 * Login, logout, CSRF, installation status, bootstrap and the reset endpoints must be reachable without
 * a session — by definition. Each carries a {@code @PublicEndpoint} reason because the architecture test
 * demands one, which is exactly the kind of forcing function that keeps an accidental
 * {@code permitAll()} from appearing unnoticed.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "Sign in, sign out, current user, password and installation setup")
public class AuthController {

	private final AuthService auth;

	private final InstallationBootstrapService bootstrap;

	private final SecurityContextRepository securityContextRepository;

	public AuthController(AuthService auth, InstallationBootstrapService bootstrap,
			SecurityContextRepository securityContextRepository) {
		this.auth = auth;
		this.bootstrap = bootstrap;
		this.securityContextRepository = securityContextRepository;
	}

	@PostMapping("/login")
	@PublicEndpoint(reason = "Sign-in cannot require an existing session")
	@Operation(summary = "Sign in with a username and password",
			description = "Establishes a server-side session and sets the JSESSIONID cookie. "
					+ "Returns the caller's profile, roles and effective permissions.")
	public ApiResponse<CurrentUserResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
			HttpServletResponse response) {
		User user = this.auth.authenticate(body.username(), body.password(), clientIp(request));
		persistAuthentication(user, request, response);
		return ApiResponse.success(this.auth.currentUser(user), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/logout")
	@PublicEndpoint(reason = "Logout must succeed even when the session has already expired")
	@Operation(summary = "Sign out and invalidate the session")
	public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
		// Invalidate rather than only clear the context: an invalidated session id cannot be replayed,
		// whereas a cleared holder leaves a still-valid session on the server.
		var session = request.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/me")
	@Operation(summary = "The authenticated caller's profile, roles, permissions and data scopes")
	public ApiResponse<CurrentUserResponse> currentUser(HttpServletRequest request) {
		User user = requireCurrentUser();
		return ApiResponse.success(this.auth.currentUser(user), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/csrf")
	@PublicEndpoint(reason = "A new client has no token yet; issuing one grants no access")
	@Operation(summary = "Obtain a CSRF token",
			description = "The token must be sent back in the X-XSRF-TOKEN header on every state-changing request.")
	public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest request, HttpServletResponse response) {
		// Reading the attribute is what forces Spring Security to generate and write the cookie.
		Object token = request.getAttribute("_csrf");
		if (!(token instanceof org.springframework.security.web.csrf.CsrfToken csrfToken)) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CSRF token is not available");
		}
		return ApiResponse.success(
				new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken()),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/change-password")
	@Operation(summary = "Change your own password",
			description = "Requires the current password. Returns 200 with no body on success.")
	public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
			HttpServletRequest request) {
		this.auth.changePassword(requireCurrentUser(), body);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/forgot-password")
	@PublicEndpoint(reason = "A user who cannot sign in must still be able to request a reset")
	@Operation(summary = "Request a password reset",
			description = "Always returns the same message, whether or not the address is registered.")
	public ApiResponse<NeutralResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body,
			HttpServletRequest request) {
		Optional<String> rawToken = this.auth.forgotPassword(body.email());
		// The token is handed to the notification layer; nothing about its presence is observable here.
		rawToken.ifPresent(token -> org.slf4j.LoggerFactory.getLogger(AuthController.class)
			.debug("auth.reset_token_issued delivery_pending=true"));
		return ApiResponse.success(AuthService.neutralResetMessage(), CorrelationId.current(),
				request.getRequestURI());
	}

	@PostMapping("/reset-password")
	@PublicEndpoint(reason = "Redeeming a reset token is how a locked-out user regains access")
	@Operation(summary = "Set a new password using a reset token")
	public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
		this.auth.resetPassword(body.token(), body.newPassword());
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/installation")
	@PublicEndpoint(reason = "A client must know whether to show sign-in or first-run setup")
	@Operation(summary = "Whether this installation has been set up")
	public ApiResponse<InstallationStatusResponse> installationStatus(HttpServletRequest request) {
		return ApiResponse.success(this.bootstrap.status(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/bootstrap")
	@PublicEndpoint(reason = "The first administrator does not exist yet, so cannot authenticate")
	@Operation(summary = "Create the first administrator",
			description = "Requires the one-time installation token in the X-Installation-Token header. "
					+ "Permanently closes once completed.")
	public ResponseEntity<ApiResponse<BootstrapResponse>> bootstrap(
			@RequestHeader(name = "X-Installation-Token", required = false) String installationToken,
			@Valid @RequestBody BootstrapRequest body, HttpServletRequest request) {
		BootstrapResponse created = this.bootstrap.bootstrap(installationToken, body, clientIp(request));
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(created, CorrelationId.current(), request.getRequestURI()));
	}

	// ---------------------------------------------------------------------------------------------
	// Session plumbing
	// ---------------------------------------------------------------------------------------------

	/**
	 * Writes the authenticated context to the session.
	 *
	 * <p>The principal stored is the same {@code EdamPrincipal} the filter chain builds from the
	 * identity adapter, so a session established here is indistinguishable from one established any
	 * other way — which matters for every downstream authorization decision.
	 */
	private void persistAuthentication(User user, HttpServletRequest request, HttpServletResponse response) {
		UserDetails details = this.auth.buildPrincipal(user);
		UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken
			.authenticated(details, null, details.getAuthorities());
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		// Creates the session, migrates any pre-existing one, and registers it for saving.
		this.securityContextRepository.saveContext(context, request, response);
	}

	private User requireCurrentUser() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof com.edam.common.security.EdamPrincipal principal)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
		}
		return this.auth.findUser(principal.userId());
	}

	private static String clientIp(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

}
