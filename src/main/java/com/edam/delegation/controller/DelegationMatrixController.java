package com.edam.delegation.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.delegation.dto.DelegationDtos.DelegationResolutionResponse;
import com.edam.delegation.dto.DelegationDtos.DelegationRuleResponse;
import com.edam.delegation.dto.DelegationDtos.SaveDelegationRuleRequest;
import com.edam.delegation.service.DelegationMatrixService;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The delegation matrix (Module E).
 *
 * <h2>The preview endpoint is the reason this controller matters</h2>
 * Configuring an approval chain is the highest-consequence administrative action in EDAM: a wrong band
 * routes a large payment to a junior approver. {@code POST /delegation-matrix/preview} answers "what would
 * approve this?" using the same resolver the request module uses, so an administrator can verify a change
 * before a single real request depends on it.
 *
 * <h2>No update mapping</h2>
 * A rule that has been in force is closed and replaced, never edited. Editing would change which chain
 * approved a past request with no evidence that the edit happened.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/delegation-matrix", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Delegation matrix", description = "Approval routing rules by classification and amount band")
public class DelegationMatrixController {

	private final DelegationMatrixService matrix;

	public DelegationMatrixController(DelegationMatrixService matrix) {
		this.matrix = matrix;
	}

	@GetMapping
	@EdamPermission(Permissions.DELEGATION_VIEW)
	@Operation(summary = "List delegation rules, optionally for one category")
	public ApiResponse<List<DelegationRuleResponse>> list(@RequestParam(required = false) UUID categoryId,
			@RequestParam(defaultValue = "false") boolean includeInactive, HttpServletRequest request) {
		return ok(this.matrix.list(categoryId, includeInactive), request);
	}

	@PostMapping
	@EdamPermission(Permissions.DELEGATION_MANAGE)
	@Operation(summary = "Create a delegation rule with its approval chain",
			description = "Overlapping active rules for the same classification and amount are refused by a "
					+ "database exclusion constraint.")
	public ResponseEntity<ApiResponse<DelegationRuleResponse>> create(
			@Valid @RequestBody SaveDelegationRuleRequest body, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(this.matrix.create(body), CorrelationId.current(), request.getRequestURI()));
	}

	@PostMapping("/{id}/close")
	@EdamPermission(Permissions.DELEGATION_MANAGE)
	@Operation(summary = "Close a rule's effective period",
			description = "The rule stops applying from now and remains readable for historical routes.")
	public ApiResponse<DelegationRuleResponse> close(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.matrix.close(id), request);
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.DELEGATION_MANAGE)
	@Operation(summary = "Activate or deactivate a rule")
	public ApiResponse<DelegationRuleResponse> setActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.matrix.setActive(id, active), request);
	}

	@PostMapping("/preview")
	@EdamPermission(Permissions.DELEGATION_VIEW)
	@Operation(summary = "Preview the approval chain for a hypothetical request",
			description = "Uses the same resolver as request submission, so the preview cannot disagree with "
					+ "what submission would do.")
	public ApiResponse<DelegationResolutionResponse> preview(@RequestParam UUID categoryId,
			@RequestParam(required = false) UUID subcategoryId, @RequestParam UUID natureId,
			@RequestParam(required = false) UUID requestTypeId, @RequestParam UUID currencyId,
			@RequestParam BigDecimal amount, HttpServletRequest request) {
		return ok(this.matrix.preview(categoryId, subcategoryId, natureId, requestTypeId, currencyId, amount),
				request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
