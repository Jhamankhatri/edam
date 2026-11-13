package com.edam.approvallimits.controller;

import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.ApprovalLimitResponse;
import com.edam.approvallimits.dto.ApprovalLimitDtos.AuthorityCheck;
import com.edam.approvallimits.dto.ApprovalLimitDtos.AuthorityCheckRequest;
import com.edam.approvallimits.dto.ApprovalLimitDtos.SaveApprovalLimitRequest;
import com.edam.approvallimits.service.ApprovalLimitService;
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
 * Department and personal approval limits (Module D).
 *
 * <h2>The authority check is a preview, not a decision</h2>
 * {@code POST /approval-limits/authority-check} answers "could anyone approve this amount, and who?" It
 * reads the configured limits and applies the documented resolution rules. It changes nothing and it does
 * not route: once Phase 3's engine exists, this is the same resolution the engine will consult, exposed so
 * an administrator can verify their configuration before a request depends on it.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/approval-limits", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Approval limits", description = "Department and personal approval ceilings, and authority resolution")
public class ApprovalLimitController {

	private final ApprovalLimitService service;

	public ApprovalLimitController(ApprovalLimitService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.APPROVAL_LIMIT_VIEW)
	@Operation(summary = "List approval limits, optionally for one department")
	public ApiResponse<List<ApprovalLimitResponse>> list(@RequestParam(required = false) UUID departmentId,
			HttpServletRequest request) {
		return ok(this.service.list(departmentId), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.APPROVAL_LIMIT_VIEW)
	@Operation(summary = "Get one approval limit")
	public ApiResponse<ApprovalLimitResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.service.get(id), request);
	}

	@PostMapping
	@EdamPermission(Permissions.APPROVAL_LIMIT_MANAGE)
	@Operation(summary = "Create an approval limit",
			description = "Omit userId for a department-wide limit; supply it for a personal limit. Overlapping "
					+ "active periods for the same authority are refused.")
	public ResponseEntity<ApiResponse<ApprovalLimitResponse>> create(@Valid @RequestBody SaveApprovalLimitRequest body,
			HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ok(this.service.create(body), request));
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.APPROVAL_LIMIT_MANAGE)
	@Operation(summary = "Change a limit's amount or close its period",
			description = "The department, holder, currency and classification are not editable: a different "
					+ "authority is a different limit.")
	public ApiResponse<ApprovalLimitResponse> update(@PathVariable UUID id,
			@Valid @RequestBody SaveApprovalLimitRequest body, HttpServletRequest request) {
		return ok(this.service.update(id, body), request);
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.APPROVAL_LIMIT_MANAGE)
	@Operation(summary = "Activate or deactivate a limit")
	public ApiResponse<ApprovalLimitResponse> setActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.service.setActive(id, active), request);
	}

	@PostMapping("/authority-check")
	@EdamPermission(Permissions.APPROVAL_LIMIT_VIEW)
	@Operation(summary = "Check who could approve an amount in a department",
			description = "Read-only preview of the resolution Phase 3 will use. It does not route anything.")
	public ApiResponse<AuthorityCheck> checkAuthority(@Valid @RequestBody AuthorityCheckRequest body,
			HttpServletRequest request) {
		return ok(this.service.checkAuthority(body.departmentId(), body.expenseType(), body.currencyId(),
				body.amount(), body.candidateUserIds() == null ? List.of() : body.candidateUserIds()), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
