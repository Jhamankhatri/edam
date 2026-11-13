package com.edam.audit.controller;

import java.util.UUID;

import com.edam.audit.dto.AuditDtos.TimelinePage;
import com.edam.audit.service.AuditReadService;
import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * request read endpoints: {@code request:view-own} at the controller, with service-layer
 * enforcement of department/branch/installation scopes via
 * {@code ExpenseRequestService.isVisible}.
 *
 * <h2>Pagination</h2>
 * The timeline uses keyset pagination with an opaque {@code cursor}. The first request omits the
 * cursor; subsequent requests pass the {@code nextCursor} returned by the previous page. The
 * cursor fixes an upper bound ({@code boundedAt}) so new events arriving mid-browse do not shift
 * pages or produce duplicates.
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
					+ "deterministic chronological order. Pagination is keyset-based: pass the opaque "
					+ "nextCursor from the previous response. Out-of-scope requests are reported as not found.")
	public ApiResponse<TimelinePage> timeline(@PathVariable UUID id,
			@RequestParam(required = false) @Parameter(description = "Page size, capped at 200.") Integer size,
			@RequestParam(required = false) @Parameter(
					description = "Opaque continuation cursor from the previous response.") String cursor,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ApiResponse.success(
				this.service.requestTimeline(id, size, cursor, caller),
				CorrelationId.current(), request.getRequestURI());
	}

}
