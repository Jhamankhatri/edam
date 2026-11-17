package com.edam.integrations.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.integrations.dto.IntegrationDtos.*;
import com.edam.integrations.service.IntegrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for integration configuration management, connection testing and execution
 * tracking (Phase 4H).
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/integrations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Integrations", description = "Integration configuration, connection testing and execution tracking")
public class IntegrationController {

	private final IntegrationService service;

	public IntegrationController(IntegrationService service) {
		this.service = service;
	}

	private static <T> ApiResponse<T> ok(T body, HttpServletRequest r) {
		return ApiResponse.success(body, CorrelationId.current(), r.getRequestURI());
	}

	@GetMapping("/providers")
	@EdamPermission(Permissions.INTEGRATION_VIEW)
	@Operation(summary = "List supported integration provider types")
	public ApiResponse<List<ProviderCatalogueEntry>> providers(HttpServletRequest r) {
		return ok(service.providerCatalogue(), r);
	}

	@GetMapping
	@EdamPermission(Permissions.INTEGRATION_VIEW)
	@Operation(summary = "List integration configurations")
	public ApiResponse<List<IntegrationSummary>> list(HttpServletRequest r) {
		return ok(service.list(), r);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.INTEGRATION_VIEW)
	@Operation(summary = "Integration configuration detail (secrets never returned)")
	public ApiResponse<IntegrationDetail> detail(@PathVariable UUID id, HttpServletRequest r) {
		return ok(service.detail(id), r);
	}

	@PostMapping
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Create an integration configuration")
	public ApiResponse<IntegrationDetail> create(@RequestBody CreateIntegrationRequest req,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.create(caller, req), r);
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Update an integration configuration. Omitting secret preserves existing value.")
	public ApiResponse<IntegrationDetail> update(@PathVariable UUID id,
			@RequestBody UpdateIntegrationRequest req,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.update(caller, id, req), r);
	}

	@PostMapping("/{id}/activate")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Activate an integration")
	public ApiResponse<IntegrationDetail> activate(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.activate(caller, id, true), r);
	}

	@PostMapping("/{id}/deactivate")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Deactivate an integration (prevents new executions, retains history)")
	public ApiResponse<IntegrationDetail> deactivate(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.activate(caller, id, false), r);
	}

	@DeleteMapping("/{id}")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Archive an integration (soft-delete, execution history retained)")
	public ApiResponse<IntegrationDetail> archive(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.archive(caller, id), r);
	}

	@PostMapping("/{id}/test")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Test an integration connection (no side effects on provider)")
	public ApiResponse<ConnectionTestResponse> test(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.testConnection(caller, id), r);
	}

	@PostMapping("/{id}/executions")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Enqueue an execution")
	public ApiResponse<ExecutionSummary> execute(@PathVariable UUID id,
			@RequestBody ExecuteRequest req,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.submitExecution(caller, id, req), r);
	}

	@GetMapping("/{id}/executions")
	@EdamPermission(Permissions.INTEGRATION_VIEW)
	@Operation(summary = "Paginated execution history")
	public ApiResponse<Page<ExecutionSummary>> history(@PathVariable UUID id,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "25") int size,
			HttpServletRequest r) {
		return ok(service.executionHistory(id, status, page, size), r);
	}

	@GetMapping("/executions/{execId}")
	@EdamPermission(Permissions.INTEGRATION_VIEW)
	@Operation(summary = "Execution detail")
	public ApiResponse<ExecutionDetail> executionDetail(@PathVariable UUID execId, HttpServletRequest r) {
		return ok(service.executionDetail(execId), r);
	}

	@PostMapping("/executions/{execId}/retry")
	@EdamPermission(Permissions.INTEGRATION_MANAGE)
	@Operation(summary = "Retry an eligible failed execution")
	public ApiResponse<ExecutionSummary> retry(@PathVariable UUID execId,
			@RequestBody(required = false) RetryRequest req,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest r) {
		return ok(service.retry(caller, execId, req), r);
	}
}
