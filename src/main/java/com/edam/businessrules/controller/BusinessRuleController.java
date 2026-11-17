package com.edam.businessrules.controller;

import java.util.List;
import java.util.UUID;

import com.edam.businessrules.dto.RuleDtos;
import com.edam.businessrules.service.BusinessRuleService;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Secured REST endpoints for business-rule-set and rule lifecycle. The controller only
 * maps HTTP to service calls; no business logic or SQL lives here.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/business-rules", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Business Rules", description = "Versioned, declarative business-rule management")
public class BusinessRuleController {

	private final BusinessRuleService service;

	public BusinessRuleController(BusinessRuleService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.RULE_VIEW)
	@Operation(summary = "List rule sets")
	public ApiResponse<List<RuleDtos.RuleSetSummary>> list(@AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.listRuleSets(), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.RULE_VIEW)
	@Operation(summary = "Get a rule set with its active/draft version and rules")
	public ApiResponse<RuleDtos.RuleVersionDetail> get(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.getRuleSet(id), request);
	}

	@PostMapping
	@EdamPermission(Permissions.RULE_MANAGE)
	@Operation(summary = "Create a new rule set (seeds an empty DRAFT v1)")
	public ApiResponse<RuleDtos.RuleSetSummary> create(@Valid @RequestBody RuleDtos.CreateRuleSetRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.createRuleSet(body, caller), request);
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.RULE_MANAGE)
	@Operation(summary = "Update rule-set metadata or active flag")
	public ApiResponse<RuleDtos.RuleSetSummary> update(@PathVariable UUID id,
			@Valid @RequestBody RuleDtos.UpdateRuleSetRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.updateRuleSet(id, body, caller), request);
	}

	@PostMapping("/{id}/drafts")
	@EdamPermission(Permissions.RULE_MANAGE)
	@Operation(summary = "Create a new DRAFT version if none exists")
	public ApiResponse<RuleDtos.RuleVersionDetail> newDraft(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.newDraft(id, caller), request);
	}

	@PostMapping("/{id}/publish")
	@EdamPermission(Permissions.RULE_PUBLISH)
	@Operation(summary = "Validate and publish the current DRAFT version, retiring the previous PUBLISHED version")
	public ApiResponse<RuleDtos.RuleVersionDetail> publish(@PathVariable UUID id,
			@RequestBody(required = false) RuleDtos.PublishRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.publish(id, body, caller), request);
	}

	@PostMapping("/{id}/retire")
	@EdamPermission(Permissions.RULE_PUBLISH)
	@Operation(summary = "Retire the currently PUBLISHED version")
	public ApiResponse<RuleDtos.RuleVersionDetail> retire(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.retirePublished(id, caller), request);
	}

	@PutMapping("/versions/{versionId}/rules")
	@EdamPermission(Permissions.RULE_MANAGE)
	@Operation(summary = "Create or update a rule inside a DRAFT version")
	public ApiResponse<RuleDtos.RuleDetail> upsertRule(@PathVariable UUID versionId,
			@Valid @RequestBody RuleDtos.RuleUpsertRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.upsertRule(versionId, body, caller), request);
	}

	@DeleteMapping("/rules/{ruleId}")
	@EdamPermission(Permissions.RULE_MANAGE)
	@Operation(summary = "Delete a rule from a DRAFT version")
	public ApiResponse<Void> deleteRule(@PathVariable UUID ruleId,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		this.service.deleteRule(ruleId, caller);
		return ok(null, request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}
}
