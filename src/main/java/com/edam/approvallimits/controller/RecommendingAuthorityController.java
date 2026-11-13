package com.edam.approvallimits.controller;

import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.RecommendingAuthorityResponse;
import com.edam.approvallimits.dto.ApprovalLimitDtos.ResolvedAuthority;
import com.edam.approvallimits.dto.ApprovalLimitDtos.SaveRecommendingAuthorityRequest;
import com.edam.approvallimits.service.RecommendingAuthorityService;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recommending authorities (Module D).
 *
 * <h2>Why the resolution endpoint matters</h2>
 * {@code GET /recommending-authorities/resolved} answers the question the workflow engine asks when a step is
 * designated {@code RECOMMENDING_AUTHORITY}: who must recommend this department's request, for this
 * classification, today? Before this module, that designation resolved to nobody. Exposing the resolution
 * lets an administrator confirm a department is correctly staffed <em>before</em> a request reaches that step
 * and stalls.
 *
 * <p>The response distinguishes "no authority configured" from "the configured person is inactive", because
 * the two need different fixes and a generic "unresolved" would send the administrator looking in the wrong
 * place.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/recommending-authorities", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Recommending authorities",
		description = "Designated recommenders per department, and effective-dated resolution")
public class RecommendingAuthorityController {

	private final RecommendingAuthorityService service;

	public RecommendingAuthorityController(RecommendingAuthorityService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.RECOMMENDING_AUTHORITY_VIEW)
	@Operation(summary = "List recommending authorities, optionally for one department")
	public ApiResponse<List<RecommendingAuthorityResponse>> list(@RequestParam(required = false) UUID departmentId,
			HttpServletRequest request) {
		return ok(this.service.list(departmentId), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.RECOMMENDING_AUTHORITY_VIEW)
	@Operation(summary = "Get one recommending-authority designation")
	public ApiResponse<RecommendingAuthorityResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.service.get(id), request);
	}

	@PostMapping
	@EdamPermission(Permissions.RECOMMENDING_AUTHORITY_MANAGE)
	@Operation(summary = "Designate a recommending authority",
			description = "The person must belong to the department. One active authority per department and "
					+ "classification over any period.")
	public ResponseEntity<ApiResponse<RecommendingAuthorityResponse>> create(
			@Valid @RequestBody SaveRecommendingAuthorityRequest body, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ok(this.service.create(body), request));
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.RECOMMENDING_AUTHORITY_MANAGE)
	@Operation(summary = "Transfer a designation to another person, or close its period")
	public ApiResponse<RecommendingAuthorityResponse> update(@PathVariable UUID id,
			@Valid @RequestBody SaveRecommendingAuthorityRequest body, HttpServletRequest request) {
		return ok(this.service.update(id, body), request);
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.RECOMMENDING_AUTHORITY_MANAGE)
	@Operation(summary = "Activate or deactivate a designation")
	public ApiResponse<RecommendingAuthorityResponse> setActive(@PathVariable UUID id,
			@RequestParam boolean active, HttpServletRequest request) {
		return ok(this.service.setActive(id, active), request);
	}

	@GetMapping("/resolved")
	@EdamPermission(Permissions.RECOMMENDING_AUTHORITY_VIEW)
	@Operation(summary = "Resolve the authority for a department and classification",
			description = "The same lookup the workflow engine performs. resolution is CONFIGURED, "
					+ "NONE_CONFIGURED or CONFIGURED_USER_INACTIVE.")
	public ApiResponse<ResolvedAuthority> resolved(@RequestParam UUID departmentId,
			@RequestParam(defaultValue = "OPEX") String expenseType, HttpServletRequest request) {
		return ok(this.service.explain(departmentId, expenseType, java.time.Instant.now()), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
