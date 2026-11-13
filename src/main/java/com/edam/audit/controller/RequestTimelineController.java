package com.edam.audit.controller;

import java.util.UUID;

import com.edam.audit.dto.AuditDtos.TimelineEntry;
import com.edam.audit.service.AuditReadService;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Request activity timeline (BRD §26.2 / AUD-01).
 *
 * <p>Lives under the {@code /expense-requests} path because it is a sub-resource of a request,
 * but it is implemented in the audit module because it merges audit events with workflow
 * actions — neither module alone can produce it. The authorization model mirrors the existing
 * request read endpoints: {@code request:view-own} at the controller (ownership may suffice),
 * with service-layer enforcement of department/branch/installation scopes via
 * {@code ExpenseRequestService.isVisible}.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/expense-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Expense requests")
public class RequestTimelineController {

	private final AuditReadService service;

	public RequestTimelineController(AuditReadService service) {
		this.service = service;
	}

	@GetMapping("/{id}/activity")
	@EdamPermission(Permissions.REQUEST_VIEW_OWN)
	@Operation(summary = "Activity timeline for a request",
			description = "Merges audit events (creation, updates, submission, cancellation) with workflow "
					+ "actions (approve, reject, revert, forward, escalate) and task assignments, in "
					+ "deterministic chronological order. Out-of-scope requests are reported as not found.")
	public ApiResponse<PageResponse<TimelineEntry>> timeline(@PathVariable UUID id,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ApiResponse.success(
				this.service.requestTimeline(id, PageQuery.of(page, size, null), caller),
				CorrelationId.current(), request.getRequestURI());
	}

}
