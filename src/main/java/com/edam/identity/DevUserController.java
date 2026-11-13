package com.edam.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development aid: shows the caller's own identity, effective permissions and data scopes.
 *
 * <p>This exists so that an authorization problem can be diagnosed without a database browser —
 * "why is this button hidden" is almost always answered by "the role is granted but the data scope is
 * missing". It is registered only when {@code edam.security.dev-users.enabled=true}, which is set by
 * the {@code local} profile alone.
 *
 * <p>It exposes no credential and no other user's data: everything in the response is derived from the
 * caller's own principal.
 */
@RestController
@RequestMapping(path = "/api/v1/dev/me", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "edam.security.dev-users", name = "enabled", havingValue = "true")
public class DevUserController {

	/**
	 * The caller's own identity.
	 *
	 * <p>Deliberately not permission-gated: a user with no roles still has to be able to see who they
	 * are and why nothing is available to them, otherwise a misconfigured account is indistinguishable
	 * from an application fault.
	 */
	@GetMapping
	@com.edam.common.security.PublicEndpoint(
			reason = "Returns only the caller's own identity; a user with no roles must still see it")
	public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal EdamPrincipal principal,
			HttpServletRequest request) {
		Map<String, Object> data = new LinkedHashMap<>();
		if (principal != null) {
			data.put("userId", principal.userId());
			data.put("employeeCode", principal.employeeCode());
			data.put("username", principal.getUsername());
			data.put("displayName", principal.displayName());
			data.put("roles", principal.roleCodes().stream().sorted().toList());
			data.put("permissions", principal.permissions().stream().sorted().toList());
			data.put("installationScope", principal.hasInstallationScope());
			data.put("dataScopes", principal.dataScopes()
				.stream()
				.map(scope -> Map.of("type", scope.type().name(),
						"referenceId", scope.referenceId() == null ? "" : scope.referenceId().toString()))
				.toList());
		}
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/permissions")
	@com.edam.common.security.PublicEndpoint(reason = "The caller's own effective permission codes only")
	public ApiResponse<List<String>> permissions(@AuthenticationPrincipal EdamPrincipal principal,
			HttpServletRequest request) {
		List<String> permissions = (principal == null) ? List.of()
				: principal.permissions().stream().sorted().toList();
		return ApiResponse.success(permissions, CorrelationId.current(), request.getRequestURI());
	}

}
