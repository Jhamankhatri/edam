package com.edam.audit.controller;

import java.time.Instant;
import java.util.UUID;

import com.edam.audit.dto.AuditDtos;
import com.edam.audit.dto.AuditDtos.AuditEventDetail;
import com.edam.audit.dto.AuditDtos.AuditEventSummary;
import com.edam.audit.service.AuditReadService;
import com.edam.audit.service.AuditReadService.SearchCriteria;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.audit.AuditAction;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit search and event detail (Module K, AUD-01 / AUD-02 read side).
 *
 * <p>Endpoints here are read-only and do not themselves generate audit rows — an auditor browsing
 * history must not drown the trail with their own reads. {@code audit:export} (AUD-03) is a
 * separate endpoint and is not implemented in this step.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/audit-events", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Audit", description = "Searchable audit history (AUD-01, AUD-02). Export is a separate permission.")
public class AuditController {

	private final AuditReadService service;

	public AuditController(AuditReadService service) {
		this.service = service;
	}

	@GetMapping
	@EdamPermission(Permissions.AUDIT_VIEW)
	@Operation(summary = "Search audit events",
			description = "Bounded paginated search. The default window is the last 24 hours; a single request may "
					+ "span at most 31 days. List rows do not include before/after values; retrieve a single "
					+ "event for the diff.")
	public ApiResponse<PageResponse<AuditEventSummary>> search(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Parameter(
					description = "Window start (inclusive, ISO-8601 instant). Defaults to 24 hours ago.") Instant from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) @Parameter(
					description = "Window end (exclusive, ISO-8601 instant). Defaults to now.") Instant to,
			@RequestParam(required = false) @Parameter(description = "Actor user id filter.") UUID actorId,
			@RequestParam(required = false) @Parameter(
					description = "Actor username substring filter (case-insensitive).") String actorName,
			@RequestParam(required = false) @Parameter(description = "Entity/module type, e.g. ExpenseRequest.") String entityType,
			@RequestParam(required = false) @Parameter(description = "Entity id filter (exact match).") String entityId,
			@RequestParam(required = false) @Parameter(description = "Action filter (exact enum name).") AuditAction action,
			@RequestParam(required = false) @Parameter(description = "Correlation id filter (exact match).") String correlationId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		SearchCriteria criteria = new SearchCriteria(from, to, actorId, actorName, entityType, entityId,
				action, correlationId);
		return ApiResponse.success(this.service.search(criteria, PageQuery.of(page, size, null), caller),
				CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.AUDIT_VIEW)
	@Operation(summary = "One audit event with its redacted before/after diff",
			description = "For events concerning an expense request, the caller must also be able to read that "
					+ "request (audit:view alone does not let an auditor read another user's request detail).")
	public ApiResponse<AuditEventDetail> get(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ApiResponse.success(this.service.detail(id, caller), CorrelationId.current(),
				request.getRequestURI());
	}

}
