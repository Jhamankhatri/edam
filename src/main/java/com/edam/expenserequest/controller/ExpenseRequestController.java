package com.edam.expenserequest.controller;

import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.api.PageQuery;
import com.edam.common.api.PageResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.expenserequest.dto.ExpenseRequestDtos.CancelRequest;
import com.edam.expenserequest.dto.ExpenseRequestDtos.CreateRequest;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestResponse;
import com.edam.expenserequest.dto.ExpenseRequestDtos.RequestSummary;
import com.edam.expenserequest.dto.ExpenseRequestDtos.UpdateRequest;
import com.edam.expenserequest.model.ExpenseRequest;
import com.edam.expenserequest.service.ExpenseRequestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OPEX and CAPEX request management (Module G).
 *
 * <h2>Two permissions per operation, deliberately</h2>
 * Each endpoint declares the permission for the <em>operation</em> ({@code request:create},
 * {@code request:submit}, …), and the service enforces authority over the <em>specific request</em> —
 * authorship, editable state, data scope. The endpoint check alone would be wrong in the permissive
 * direction: {@code request:submit} means "may submit requests", not "may submit anyone's".
 *
 * <h2>Reading is split three ways</h2>
 * {@code request:view-own}, {@code request:view-scope} and {@code request:view-all} exist as separate
 * codes in the catalogue. They are all satisfied by {@code request:view-own} here because the actual
 * discrimination is the data scope applied in the query, not the permission: a user permitted to see their
 * department's requests holds a department data scope. Requiring {@code request:view-all} for the register
 * would lock out every ordinary requester from their own list.
 *
 * <h2>No delete, no direct state flip</h2>
 * There is no {@code DELETE} and no endpoint that sets a status field. Cancellation is the only withdrawal,
 * and approval outcomes arrive from the workflow engine — never from a client-supplied status, which would
 * let a requester approve their own request by typing the right word.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/expense-requests", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Expense requests", description = "OPEX and CAPEX request creation, registers, submission and cancellation")
public class ExpenseRequestController {

	private final ExpenseRequestService service;

	public ExpenseRequestController(ExpenseRequestService service) {
		this.service = service;
	}

	// ---------------------------------------------------------------------------------------------
	// Read
	// ---------------------------------------------------------------------------------------------

	@GetMapping
	@EdamPermission(Permissions.REQUEST_VIEW_OWN)
	@Operation(summary = "List requests visible to the caller",
			description = "Scoped to the caller's data scopes. Set mineOnly=true for the 'My Request' register.")
	public ApiResponse<PageResponse<RequestSummary>> list(@RequestParam(required = false) String search,
			@RequestParam(required = false) ExpenseRequest.Status status,
			@RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID categoryId,
			@RequestParam(defaultValue = "false") boolean mineOnly, @RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size, @RequestParam(required = false) String sort,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.list(search, status, departmentId, categoryId, mineOnly, PageQuery.of(page, size, sort),
				caller), request);
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.REQUEST_VIEW_OWN)
	@Operation(summary = "Get one request with its detail, allocations, items and quotations",
			description = "A request outside the caller's scope is reported as not found rather than forbidden.")
	public ApiResponse<RequestResponse> get(@PathVariable UUID id, @AuthenticationPrincipal EdamPrincipal caller,
			HttpServletRequest request) {
		return ok(this.service.get(id, caller), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Create and edit
	// ---------------------------------------------------------------------------------------------

	@PostMapping
	@EdamPermission(Permissions.REQUEST_CREATE)
	@Operation(summary = "Create a request as a draft",
			description = "The request number is allocated from the installation's sequence. A draft is checked "
					+ "for internal consistency only; completeness is enforced at submission.")
	public ResponseEntity<ApiResponse<RequestResponse>> create(@Valid @RequestBody CreateRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ok(this.service.create(body, caller), request));
	}

	@PutMapping("/{id}")
	@EdamPermission(Permissions.REQUEST_EDIT)
	@Operation(summary = "Replace an editable request",
			description = "Only the requester may change a request, and only while it is a draft or was returned "
					+ "for correction.")
	public ApiResponse<RequestResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.update(id, body, caller), request);
	}

	// ---------------------------------------------------------------------------------------------
	// Lifecycle
	// ---------------------------------------------------------------------------------------------

	@PostMapping("/{id}/submit")
	@EdamPermission(Permissions.REQUEST_SUBMIT)
	@Operation(summary = "Submit a request for approval",
			description = "Applies every conditional rule for the request's classification. Submission does not "
					+ "choose approvers: routing belongs to the workflow engine.")
	public ApiResponse<RequestResponse> submit(@PathVariable UUID id,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.submit(id, caller), request);
	}

	@PostMapping("/{id}/cancel")
	@EdamPermission(Permissions.REQUEST_CANCEL)
	@Operation(summary = "Withdraw a request before a decision",
			description = "Only the requester may cancel. An approved request cannot be cancelled; reversing it "
					+ "is a separate operation.")
	public ApiResponse<RequestResponse> cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest body,
			@AuthenticationPrincipal EdamPrincipal caller, HttpServletRequest request) {
		return ok(this.service.cancel(id, body.reason(), caller), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

}
