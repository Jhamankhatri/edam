package com.edam.workflow.controller;

import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestResponse;
import com.edam.workflow.dto.ApprovalDtos.ActionResponse;
import com.edam.workflow.dto.ApprovalDtos.ApprovalCaseFile;
import com.edam.workflow.dto.ApprovalDtos.ApprovalTaskSummary;
import com.edam.workflow.dto.ApprovalDtos.ApproveRequest;
import com.edam.workflow.dto.ApprovalDtos.ForwardRequest;
import com.edam.workflow.dto.ApprovalDtos.RejectRequest;
import com.edam.workflow.dto.ApprovalDtos.RevertRequest;
import com.edam.workflow.service.ApprovalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The approval inbox and the four approval actions (Module I, BRD 7.3 and 8.5).
 *
 * <h2>{@code approval:view} and {@code approval:act} are two permissions on purpose</h2>
 * Reading the inbox is a normal part of being an approver, manager or auditor. Acting is not: it changes a
 * financial decision. Splitting them lets a delegation matrix be granted read access to approvals without
 * gaining the ability to approve.
 *
 * <h2>What the permission here does <em>not</em> mean</h2>
 * {@code approval:act} means "may act on approvals in general". The service additionally requires an open
 * task assigned to the caller. This is the BRD's explicit rule that a role alone does not confer approval
 * authority over a particular request, and it is why no endpoint here takes a user id or an approver field
 * from the client.
 *
 * <h2>Remarks are validated at two layers</h2>
 * {@code @NotBlank} on the request body rejects an empty remark before any business logic runs, which gives
 * the client a field-level error. The service repeats the check because a future caller may not come through
 * this controller, and because the BRD's requirement is about the decision, not about HTTP.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/approvals", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Approvals", description = "The approval inbox, case files and approve, reject, revert and forward")
public class ApprovalController {

	private static final Sort INBOX_SORT = Sort.by(Sort.Direction.DESC, "assignedAt");

	private final ApprovalService service;

	public ApprovalController(ApprovalService service) {
		this.service = service;
	}

	// ---------------------------------------------------------------------------------------------
	// Inbox
	// ---------------------------------------------------------------------------------------------

	@GetMapping
	@EdamPermission(Permissions.APPROVAL_VIEW)
	@Operation(summary = "My pending approvals",
			description = "Open tasks addressed to the caller directly or to a role they currently hold, newest "
					+ "first. A role-addressed task is visible to every current holder, and acting on it is still "
					+ "recorded against the individual who acted.")
	public ApiResponse<PageResponse<ApprovalTaskSummary>> inbox(@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.inbox(caller, PageQuery.of(page, size, null).toPageable(INBOX_SORT)), request);
	}

	@GetMapping("/count")
	@EdamPermission(Permissions.APPROVAL_VIEW)
	@Operation(summary = "How many approvals await the caller")
	public ApiResponse<Long> count(@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.inboxCount(caller), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Case file
	// ---------------------------------------------------------------------------------------------

	@GetMapping("/tasks/{taskId}")
	@EdamPermission(Permissions.APPROVAL_VIEW)
	@Operation(summary = "The case file for one approval task",
			description = "The request, the workflow's position, per-step progress and the decision history. "
					+ "Also reports which actions the caller may take and why not, so a UI can render the panel "
					+ "from the server's decision rather than re-deriving the rules.")
	public ApiResponse<ApprovalCaseFile> caseFile(@PathVariable UUID taskId,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.caseFile(taskId, caller), request);
	}

	@GetMapping("/tasks/{taskId}/request")
	@EdamPermission(Permissions.APPROVAL_VIEW)
	@Operation(summary = "The expense request behind an approval task",
			description = "Read-only, and scoped by the Phase 2 visibility rules: an approver sees the request "
					+ "they were assigned, not every request.")
	public ApiResponse<RequestResponse> requestFor(@PathVariable UUID taskId,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.requestForTask(taskId, caller), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Actions
	// ---------------------------------------------------------------------------------------------

	@PostMapping("/tasks/{taskId}/approve")
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Approve an assigned task",
			description = "Advances the workflow when the step's quorum is met. Remarks are required (BRD 8.5).")
	public ApiResponse<ActionResponse> approve(@PathVariable UUID taskId,
			@Valid @RequestBody ApproveRequest body, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.approve(taskId, body.remarks(), caller), request);
	}

	@PostMapping("/tasks/{taskId}/reject")
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Reject an assigned task",
			description = "Ends the request unless the workflow's published revision declares another target. "
					+ "Remarks are required (BRD 8.5).")
	public ApiResponse<ActionResponse> reject(@PathVariable UUID taskId, @Valid @RequestBody RejectRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.reject(taskId, body.remarks(), caller), request);
	}

	@PostMapping("/tasks/{taskId}/revert")
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Revert an assigned task",
			description = "Returns the request for correction. Remarks are required (BRD 8.5). The destination "
					+ "is the revision's declared revert target, or its initial state.")
	public ApiResponse<ActionResponse> revert(@PathVariable UUID taskId, @Valid @RequestBody RevertRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.revert(taskId, body.remarks(), caller), request);
	}

	@PostMapping("/tasks/{taskId}/forward")
	@EdamPermission(Permissions.APPROVAL_ACT)
	@Operation(summary = "Forward an assigned task to another user",
			description = "Closes the caller's task and opens an equivalent one for the recipient in the same "
					+ "step, so the delegate's approval counts toward the same quorum. A recipient and remarks are "
					+ "both required (BRD 8.5).")
	public ApiResponse<ActionResponse> forward(@PathVariable UUID taskId, @Valid @RequestBody ForwardRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.forward(taskId, body.recipientUserId(), body.remarks(), caller), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
