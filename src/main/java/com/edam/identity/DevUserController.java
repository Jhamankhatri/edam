package com.edam.identity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPrincipal;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development aid: shows the authenticated principal's institution memberships and
 * permissions so that the multi-institution model can be inspected without a database
 * browser. Available only in the {@code local} profile. It exposes no credentials.
 */
@RestController
@RequestMapping(path = "/api/v1/dev/me", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(prefix = "edam.security.dev-users", name = "enabled", havingValue = "true")
public class DevUserController {

	@GetMapping
	public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal EdamPrincipal principal,
			HttpServletRequest request) {
		Map<String, Object> data = new LinkedHashMap<>();
		if (principal != null) {
			data.put("employeeId", principal.employeeId());
			data.put("displayName", principal.displayName());
			data.put("institutions", principal.institutionCodes());
			data.put("rolesByInstitution", principal.institutionRoles());
			data.put("permissions", principal.permissions().stream().sorted().toList());
		}
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/permissions")
	public ApiResponse<List<String>> permissions(@AuthenticationPrincipal EdamPrincipal principal,
			HttpServletRequest request) {
		List<String> permissions = (principal == null) ? List.of()
				: principal.permissions().stream().sorted().toList();
		return ApiResponse.success(permissions, CorrelationId.current(), request.getRequestURI());
	}

}
