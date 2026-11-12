package com.edam.configplane.api;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.configplane.ExpenseNature;
import com.edam.configplane.api.ConfigurationDtos.CreateDelegationRuleRequest;
import com.edam.configplane.api.ConfigurationDtos.DelegationRuleResponse;
import com.edam.configplane.api.ConfigurationDtos.RoutingResolutionResponse;
import com.edam.configplane.api.ConfigurationDtos.UpdateDelegationRuleRequest;
import com.edam.configplane.category.ApproverType;
import com.edam.configplane.category.DelegationMatrixService;
import com.edam.configplane.category.DelegationRule;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expense Delegation Matrix administration and routing explanation (BRD FM-09, §8.6).
 *
 * <h2>Why a resolution endpoint exists</h2>
 * The matrix decides who must approve an amount. BRD FM-06 and §8.6 require that decision to be
 * explainable: a requester must be able to see which route their request will take, and an auditor
 * must be able to reconstruct why. Without this endpoint the only record of the decision would be a
 * log line, which is not an acceptable answer to "who approved this and on what authority".
 */
@RestController
@RequestMapping(path = "/api/v1/configuration/delegation-matrix", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Configuration — Delegation Matrix",
		description = "Amount bands and approver routing (FM-09)")
public class DelegationMatrixController {

	private final DelegationMatrixService matrix;

	public DelegationMatrixController(DelegationMatrixService matrix) {
		this.matrix = matrix;
	}

	@GetMapping
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List delegation rules")
	public ApiResponse<List<DelegationRuleResponse>> list(@RequestParam(defaultValue = "true") boolean activeOnly,
			HttpServletRequest request) {
		return ApiResponse.success(this.matrix.list(activeOnly).stream().map(DelegationMatrixController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Read one delegation rule")
	public ApiResponse<DelegationRuleResponse> get(@PathVariable Long id, HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.matrix.get(id)), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create a delegation rule")
	public ApiResponse<DelegationRuleResponse> create(@Valid @RequestBody CreateDelegationRuleRequest body,
			HttpServletRequest request) {
		DelegationRule created = this.matrix.create(ExpenseNature.valueOf(body.expenseNature()), body.categoryId(),
				body.subcategoryId(), body.amountFrom(), body.amountTo(), body.committeeLevelId(),
				toApproverTypes(body.approvers()), body.recommendingAuthorityIds());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update a delegation rule")
	public ApiResponse<DelegationRuleResponse> update(@PathVariable Long id,
			@Valid @RequestBody UpdateDelegationRuleRequest body, HttpServletRequest request) {
		DelegationRule updated = this.matrix.update(id, body.categoryId(), body.subcategoryId(), body.amountFrom(),
				body.amountTo(), body.committeeLevelId(), toApproverTypes(body.approvers()),
				body.recommendingAuthorityIds());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a delegation rule")
	public ApiResponse<DelegationRuleResponse> setActive(@PathVariable Long id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.matrix.setActive(id, active)), CorrelationId.current(),
				request.getRequestURI());
	}

	@DeleteMapping("/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Delete a delegation rule")
	public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
		this.matrix.delete(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	/**
	 * Explain which rule governs an amount, without creating a request.
	 *
	 * <p>Read-only and side-effect free. A refusal is a normal answer with
	 * {@code resolved = false} and a reason, not an error, because "no rule covers this amount" is
	 * information the administrator needs to see while configuring the matrix.
	 */
	@GetMapping("/resolution")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Explain the routing decision for an amount")
	public ApiResponse<RoutingResolutionResponse> resolve(@RequestParam Long categoryId,
			@RequestParam(required = false) Long subcategoryId,
			@RequestParam String expenseNature,
			@RequestParam BigDecimal amount, HttpServletRequest request) {
		var resolution = this.matrix.resolve(ExpenseNature.valueOf(expenseNature), categoryId, subcategoryId, amount);
		return ApiResponse.success(resolution, CorrelationId.current(), request.getRequestURI());
	}

	static Set<ApproverType> toApproverTypes(Set<String> approvers) {
		Set<ApproverType> types = new LinkedHashSet<>();
		for (String approver : approvers) {
			try {
				types.add(ApproverType.valueOf(approver));
			}
			catch (IllegalArgumentException ex) {
				throw new com.edam.common.exception.ValidationFailedException("approvers",
						"Unknown approver type: " + approver);
			}
		}
		return types;
	}

	static DelegationRuleResponse toResponse(DelegationRule rule) {
		Set<String> approvers = rule.getApproverTypes().stream().map(Enum::name)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		List<Long> authorityIds = rule.getRecommendingAuthorities()
			.stream()
			.map(com.edam.configplane.authority.RecommendingAuthority::getId)
			.sorted()
			.toList();
		return new DelegationRuleResponse(rule.getId(), rule.getCategory().getId(), rule.getCategory().getName(),
				rule.getSubcategory() == null ? null : rule.getSubcategory().getId(),
				rule.getSubcategory() == null ? null : rule.getSubcategory().getName(),
				rule.getExpenseNature().name(), rule.getAmountFrom(), rule.getAmountTo(),
				rule.getCommitteeLevel() == null ? null : rule.getCommitteeLevel().getId(), approvers, authorityIds,
				rule.isActive(), rule.getUpdatedAt(), rule.getVersion());
	}

}
