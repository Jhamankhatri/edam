package com.edam.expense.api;

import java.util.List;

import com.edam.approval.ApprovalTask;
import com.edam.approval.ApprovalTaskRepository;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.configplane.ExpenseNature;
import com.edam.expense.ExpenseRequest;
import com.edam.expense.ExpenseRequestService;
import com.edam.expense.RequestHistoryEntry;
import com.edam.expense.RequestState;
import com.edam.expense.api.RequestDtos.ApprovalView;
import com.edam.expense.api.RequestDtos.CancelRequest;
import com.edam.expense.api.RequestDtos.CreateExpenseRequest;
import com.edam.expense.api.RequestDtos.ExpenseRequestResponse;
import com.edam.expense.api.RequestDtos.RequestCaseFileResponse;
import com.edam.expense.api.RequestDtos.RequestHistoryResponse;
import com.edam.expense.api.RequestDtos.ReverseRequest;
import com.edam.expense.api.RequestDtos.SubmitRequest;
import com.edam.expense.api.RequestDtos.UpdateExpenseRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
 * Expense request API (BRD FM-01..FM-06).
 *
 * <h2>Authorization shape</h2>
 * Every method carries an institution-scoped permission. Creating needs {@code request:create};
 * reading the list requires {@code request:view-own} and is automatically narrowed to the caller's own
 * requests unless they hold {@code request:view-all}; reading one request additionally passes through
 * the service's ownership/scope check. A permission alone never grants sight of someone else's request.
 */
@RestController
@RequestMapping(path = "/api/v1/requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Expense Requests", description = "Create, submit, track and review requests (FM-01..FM-06)")
public class ExpenseRequestController {

	private final ExpenseRequestService service;

	private final ApprovalTaskRepository tasks;

	public ExpenseRequestController(ExpenseRequestService service, ApprovalTaskRepository tasks) {
		this.service = service;
		this.tasks = tasks;
	}

	@GetMapping
	@EdamPermission(Permissions.REQUEST_VIEW_OWN)
	@Operation(summary = "Search requests the caller may see")
	public ApiResponse<PageResponse<ExpenseRequestResponse>> list(
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size,
			@RequestParam(required = false) String sort, @RequestParam(required = false) String state,
			@RequestParam(required = false) String expenseNature, @RequestParam(required = false) String department,
			@RequestParam(required = false) String q, @RequestParam(defaultValue = "true") boolean ownOnly,
			HttpServletRequest request) {
		PageQuery query = PageQuery.of(page, size, sort);
		// Sorting is restricted to columns the query exposes; an arbitrary property would reach the
		// persistence layer and fail at runtime instead of at validation.
		var pageable = query.toPageable(Sort.by(Sort.Order.desc("createdAt")));
		var result = this.service.search(parseState(state), parseNature(expenseNature), department, q, ownOnly,
				pageable);
		return ApiResponse.success(PageResponse.of(result, ExpenseRequestController::toResponse),
				CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.REQUEST_VIEW_OWN)
	@Operation(summary = "Read one request")
	public ApiResponse<ExpenseRequestResponse> get(@PathVariable Long id, HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.service.get(id)), CorrelationId.current(),
				request.getRequestURI());
	}

	@GetMapping("/{id}/case-file")
	@EdamPermission(Permissions.REQUEST_VIEW_OWN)
	@Operation(summary = "Read the request with its history and approvals")
	public ApiResponse<RequestCaseFileResponse> caseFile(@PathVariable Long id, HttpServletRequest request) {
		ExpenseRequest expenseRequest = this.service.get(id);
		List<RequestHistoryResponse> history = this.service.history(id)
			.stream()
			.map(ExpenseRequestController::toResponse)
			.toList();
		List<ApprovalView> approvals = this.tasks
			.findByInstitutionIdAndRequestIdOrderBySequenceNumberAsc(expenseRequest.getInstitutionId(), id)
			.stream()
			.map(task -> toView(task, false))
			.toList();
		return ApiResponse.success(new RequestCaseFileResponse(toResponse(expenseRequest), history, approvals),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.REQUEST_CREATE)
	@Operation(summary = "Create an expense request")
	public ApiResponse<ExpenseRequestResponse> create(@Valid @RequestBody CreateExpenseRequest body,
			HttpServletRequest request) {
		ExpenseRequest created = this.service.create(ExpenseNature.valueOf(body.expenseNature()), body.categoryId(),
				body.subcategoryId(), body.title(), body.description(), body.amount(), body.currencyCode(),
				body.branchCode());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.REQUEST_EDIT_RETURNED)
	@Operation(summary = "Edit a draft or returned request")
	public ApiResponse<ExpenseRequestResponse> update(@PathVariable Long id,
			@Valid @RequestBody UpdateExpenseRequest body, HttpServletRequest request) {
		ExpenseRequest updated = this.service.update(id, body.categoryId(), body.subcategoryId(), body.description(),
				body.amount(), body.currencyCode(), ExpenseNature.valueOf(body.expenseNature()), body.branchCode());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/submit")
	@EdamPermission(Permissions.REQUEST_SUBMIT)
	@Operation(summary = "Submit a request into its workflow")
	public ApiResponse<ExpenseRequestResponse> submit(@PathVariable Long id, @Valid @RequestBody SubmitRequest body,
			HttpServletRequest request) {
		ExpenseRequest submitted = this.service.submit(id, body.workflowDefinitionId());
		return ApiResponse.success(toResponse(submitted), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/cancel")
	@EdamPermission(Permissions.REQUEST_CANCEL)
	@Operation(summary = "Cancel a request before it is approved")
	public ApiResponse<ExpenseRequestResponse> cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest body,
			HttpServletRequest request) {
		ExpenseRequest cancelled = this.service.cancel(id, body.reason());
		return ApiResponse.success(toResponse(cancelled), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/reverse")
	@EdamPermission(Permissions.REQUEST_CANCEL)
	@Operation(summary = "Reverse an approved request, preserving its history")
	public ApiResponse<ExpenseRequestResponse> reverse(@PathVariable Long id, @Valid @RequestBody ReverseRequest body,
			HttpServletRequest request) {
		ExpenseRequest reversed = this.service.reverse(id, body.reason());
		return ApiResponse.success(toResponse(reversed), CorrelationId.current(), request.getRequestURI());
	}

	private static RequestState parseState(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return RequestState.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new com.edam.common.exception.ValidationFailedException("state",
					"Unknown request state: " + value);
		}
	}

	private static ExpenseNature parseNature(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return ExpenseNature.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new com.edam.common.exception.ValidationFailedException("expenseNature",
					"Unknown expense nature: " + value);
		}
	}

	static ExpenseRequestResponse toResponse(ExpenseRequest expenseRequest) {
		return new ExpenseRequestResponse(expenseRequest.getId(), expenseRequest.getRequestNumber(),
				expenseRequest.getTitle(), expenseRequest.getDescription(), expenseRequest.getAmount(),
				expenseRequest.getCurrencyCode(), expenseRequest.getAmountInBase(),
				expenseRequest.getExpenseNature().name(),
				expenseRequest.getCategory() == null ? null : expenseRequest.getCategory().getId(),
				expenseRequest.getCategory() == null ? null : expenseRequest.getCategory().getName(),
				expenseRequest.getSubcategory() == null ? null : expenseRequest.getSubcategory().getId(),
				expenseRequest.getSubcategory() == null ? null : expenseRequest.getSubcategory().getName(),
				expenseRequest.getDepartmentCode(), expenseRequest.getBranchCode(),
				expenseRequest.getRequesterEmployeeId(), expenseRequest.getRequesterUsername(),
				expenseRequest.getRequesterDisplayName(), expenseRequest.getState().name(),
				expenseRequest.getCurrentStepCode(),
				expenseRequest.getWorkflowDefinition() == null ? null
						: expenseRequest.getWorkflowDefinition().getId(),
				expenseRequest.getWorkflowDefinition() == null ? null
						: expenseRequest.getWorkflowDefinition().getCode(),
				expenseRequest.getWorkflowVersion() == null ? null
						: expenseRequest.getWorkflowVersion().getVersionNumber(),
				expenseRequest.getSubmittedAt(), expenseRequest.getFinalizedAt(),
				expenseRequest.getCancellationReason(), expenseRequest.getCreatedAt(),
				expenseRequest.getUpdatedAt(), expenseRequest.getVersion());
	}

	static RequestHistoryResponse toResponse(RequestHistoryEntry entry) {
		return new RequestHistoryResponse(entry.getSequenceNumber(), entry.getAction(), entry.getFromState(),
				entry.getToState(), entry.getActorEmployeeId(), entry.getActorUsername(), entry.getComment(),
				entry.getOccurredAt(), entry.getCorrelationId());
	}

	static ApprovalView toView(ApprovalTask task, boolean actionable) {
		return new ApprovalView(task.getId(), task.getStepCode(), task.getStepLabel(), task.getApproverRole(),
				task.getSequenceNumber(), task.getStatus().name(), task.getAssignedEmployeeId(),
				task.getAssignedDepartment(), task.getActedByUsername(), task.getActedAt(),
				task.getDecisionComment(), actionable);
	}

}
