package com.edam.approvallimits.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.dto.ApprovalLimitDtos.ExceedingLimitRuleResponse;
import com.edam.approvallimits.dto.ApprovalLimitDtos.RuleEvaluation;
import com.edam.approvallimits.dto.ApprovalLimitDtos.SaveExceedingLimitRuleRequest;
import com.edam.approvallimits.service.ExceedingLimitRuleService;
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
 * Exceeding-limit rules (Module D).
 *
 * <h2>Every response says the rule is not enforced</h2>
 * The BRD leaves the consequence of exceeding a limit undecided (line 765, and OD-P2-03 here). The
 * reference implementation stores an escalation type and acts on none of it. Rather than hide that, every
 * representation carries {@code enforced = false} together with a note explaining why, so nobody configures
 * a {@code BLOCK} rule believing that submissions will be blocked.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/exceeding-limit-rules", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Exceeding-limit rules",
		description = "Threshold configuration and evaluation. Consequences are not applied to routing yet.")
public class ExceedingLimitRuleController {

	private final ExceedingLimitRuleService service;

	public ExceedingLimitRuleController(ExceedingLimitRuleService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.EXCEEDING_LIMIT_VIEW)
	@Operation(summary = "List exceeding-limit rules")
	public ApiResponse<List<ExceedingLimitRuleResponse>> list(
			@RequestParam(defaultValue = "false") boolean includeInactive, HttpServletRequest request) {
		return ok(this.service.list(includeInactive), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.EXCEEDING_LIMIT_VIEW)
	@Operation(summary = "Get one exceeding-limit rule")
	public ApiResponse<ExceedingLimitRuleResponse> get(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.service.get(id), request);
	}

	@PostMapping
	@EdamPermission(Permissions.EXCEEDING_LIMIT_MANAGE)
	@Operation(summary = "Create an exceeding-limit rule",
			description = "One active rule per nature, classification and currency; overlapping thresholds are "
					+ "refused because the outcome would otherwise depend on row order.")
	public ResponseEntity<ApiResponse<ExceedingLimitRuleResponse>> create(
			@Valid @RequestBody SaveExceedingLimitRuleRequest body, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ok(this.service.create(body), request));
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.EXCEEDING_LIMIT_MANAGE)
	@Operation(summary = "Change an exceeding-limit rule")
	public ApiResponse<ExceedingLimitRuleResponse> update(@PathVariable UUID id,
			@Valid @RequestBody SaveExceedingLimitRuleRequest body, HttpServletRequest request) {
		return ok(this.service.update(id, body), request);
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.EXCEEDING_LIMIT_MANAGE)
	@Operation(summary = "Activate or deactivate a rule")
	public ApiResponse<ExceedingLimitRuleResponse> setActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.service.setActive(id, active), request);
	}

	@GetMapping("/evaluation")
	@EdamPermission(Permissions.EXCEEDING_LIMIT_VIEW)
	@Operation(summary = "Evaluate the configured rules against a hypothetical amount",
			description = "Answers which thresholds the amount would cross. Nothing is routed and nothing is "
					+ "saved; the response states that no consequence is applied yet.")
	public ApiResponse<RuleEvaluation> evaluate(@RequestParam String expenseType, @RequestParam UUID natureId,
			@RequestParam UUID currencyId, @RequestParam BigDecimal amount, HttpServletRequest request) {
		return ok(this.service.evaluate(expenseType, natureId, currencyId, amount), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
