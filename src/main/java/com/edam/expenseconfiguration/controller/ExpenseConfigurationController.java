package com.edam.expenseconfiguration.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.CategoryResponse;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.LookupResponse;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SaveCategoryRequest;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SaveLookupRequest;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SaveSubcategoryRequest;
import com.edam.expenseconfiguration.dto.ExpenseConfigurationDtos.SubcategoryResponse;
import com.edam.expenseconfiguration.service.ExpenseConfigurationService;

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
 * The expense catalogue (Module E).
 *
 * <h2>Why requesters can read this and only administrators can write it</h2>
 * A requester must see the categories to raise a request, so read access uses
 * {@code expense-configuration:view}. Writing changes how every future request is classified and routed,
 * so it requires {@code expense-configuration:manage} — and, because the requirement's model is explicit
 * that capability is not reach, a managing user still needs a data scope covering the records they touch.
 *
 * <h2>Natures and request types are separate resources</h2>
 * They are small vocabularies but not "lookup values": a nature and a request type each carry distinct
 * semantics in the delegation matrix, and folding them into one catch-all table would hide that. Separate
 * endpoints keep the contract explicit.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/expense-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Expense configuration",
		description = "Categories, subcategories, natures and administrative request types")
public class ExpenseConfigurationController {

	private final ExpenseConfigurationService service;

	public ExpenseConfigurationController(ExpenseConfigurationService service) {
		this.service = service;
	}

	// ------------------------------------------------------------------
	// Categories
	// ------------------------------------------------------------------

	@GetMapping("/categories")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_VIEW)
	@Operation(summary = "List categories with their subcategories")
	public ApiResponse<List<CategoryResponse>> categories(@RequestParam(defaultValue = "false") boolean includeInactive,
			HttpServletRequest request) {
		return ok(this.service.listCategories(includeInactive), request);
	}

	@PostMapping("/categories")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Create a category")
	public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody SaveCategoryRequest body,
			HttpServletRequest request) {
		return created(this.service.createCategory(body), request);
	}

	@PutMapping("/categories/{id}")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Update a category",
			description = "The expense type of an existing category cannot change; see the service contract.")
	public ApiResponse<CategoryResponse> updateCategory(@PathVariable UUID id,
			@Valid @RequestBody SaveCategoryRequest body, HttpServletRequest request) {
		return ok(this.service.updateCategory(id, body), request);
	}

	@PostMapping("/categories/{id}/activation")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a category")
	public ApiResponse<CategoryResponse> setCategoryActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.service.setCategoryActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Subcategories
	// ------------------------------------------------------------------

	@PostMapping("/categories/{categoryId}/subcategories")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Add a subcategory to a category")
	public ResponseEntity<ApiResponse<SubcategoryResponse>> createSubcategory(@PathVariable UUID categoryId,
			@Valid @RequestBody SaveSubcategoryRequest body, HttpServletRequest request) {
		return created(this.service.createSubcategory(categoryId, body), request);
	}

	@PutMapping("/subcategories/{id}")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Update a subcategory")
	public ApiResponse<SubcategoryResponse> updateSubcategory(@PathVariable UUID id,
			@Valid @RequestBody SaveSubcategoryRequest body, HttpServletRequest request) {
		return ok(this.service.updateSubcategory(id, body), request);
	}

	@PostMapping("/subcategories/{id}/activation")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a subcategory")
	public ApiResponse<SubcategoryResponse> setSubcategoryActive(@PathVariable UUID id,
			@RequestParam boolean active, HttpServletRequest request) {
		return ok(this.service.setSubcategoryActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Natures
	// ------------------------------------------------------------------

	@GetMapping("/natures")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_VIEW)
	@Operation(summary = "List expense natures")
	public ApiResponse<List<LookupResponse>> natures(HttpServletRequest request) {
		return ok(this.service.listNatures(), request);
	}

	@PostMapping("/natures")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Create an expense nature")
	public ResponseEntity<ApiResponse<LookupResponse>> createNature(@Valid @RequestBody SaveLookupRequest body,
			HttpServletRequest request) {
		return created(this.service.createNature(body), request);
	}

	@PostMapping("/natures/{id}/activation")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a nature")
	public ApiResponse<LookupResponse> setNatureActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.service.setNatureActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Request types
	// ------------------------------------------------------------------

	@GetMapping("/request-types")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_VIEW)
	@Operation(summary = "List administrative request types")
	public ApiResponse<List<LookupResponse>> requestTypes(HttpServletRequest request) {
		return ok(this.service.listRequestTypes(), request);
	}

	@PostMapping("/request-types")
	@EdamPermission(Permissions.EXPENSE_CONFIGURATION_MANAGE)
	@Operation(summary = "Create a request type")
	public ResponseEntity<ApiResponse<LookupResponse>> createRequestType(@Valid @RequestBody SaveLookupRequest body,
			HttpServletRequest request) {
		return created(this.service.createRequestType(body), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

	private static <T> ResponseEntity<ApiResponse<T>> created(T data, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(data, CorrelationId.current(), request.getRequestURI()));
	}

}
