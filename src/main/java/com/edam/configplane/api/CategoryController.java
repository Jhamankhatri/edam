package com.edam.configplane.api;

import java.util.List;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.configplane.api.ConfigurationDtos.CategoryResponse;
import com.edam.configplane.api.ConfigurationDtos.CreateCategoryRequest;
import com.edam.configplane.api.ConfigurationDtos.CreateSubcategoryRequest;
import com.edam.configplane.api.ConfigurationDtos.SubcategoryResponse;
import com.edam.configplane.api.ConfigurationDtos.UpdateCategoryRequest;
import com.edam.configplane.api.ConfigurationDtos.UpdateSubcategoryRequest;
import com.edam.configplane.category.CategoryService;
import com.edam.configplane.category.ExpenseCategory;
import com.edam.configplane.category.ExpenseSubcategory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * Expense category and sub-category administration (BRD FM-07).
 *
 * <h2>Permission model</h2>
 * Reading configuration requires {@code configuration:view}; changing it requires
 * {@code configuration:manage}. Both are evaluated <em>in the caller's institution</em> by
 * {@code RequirePermissionAspect}, so an administrator of one bank cannot read or modify another
 * bank's matrix even though the URL shape is identical.
 *
 * <h2>Why every response is a DTO</h2>
 * Entities carry {@code institutionId}, audit columns and lazy associations. Serialising them would
 * leak tenant structure and couple the wire format to the persistence model, so the contract is an
 * explicit record (BRD API-01).
 */
@RestController
@RequestMapping(path = "/api/v1/configuration/categories", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Configuration — Categories", description = "Expense categories and sub-categories (FM-07)")
public class CategoryController {

	private final CategoryService categories;

	public CategoryController(CategoryService categories) {
		this.categories = categories;
	}

	@GetMapping
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List expense categories")
	public ApiResponse<List<CategoryResponse>> list(@RequestParam(defaultValue = "true") boolean activeOnly,
			HttpServletRequest request) {
		List<CategoryResponse> rows = this.categories.listCategories(activeOnly)
			.stream()
			.map(CategoryController::toResponse)
			.toList();
		return ApiResponse.success(rows, CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/{id}")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Read one expense category")
	public ApiResponse<CategoryResponse> get(@PathVariable Long id, HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.categories.getCategory(id)), CorrelationId.current(),
				request.getRequestURI());
	}

	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create an expense category")
	public ApiResponse<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest body,
			HttpServletRequest request) {
		ExpenseCategory created = this.categories.createCategory(body.name(), body.description(),
				body.displayOrder());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update an expense category")
	public ApiResponse<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest body,
			HttpServletRequest request) {
		ExpenseCategory updated = this.categories.updateCategory(id, body.name(), body.description(),
				body.displayOrder());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate an expense category")
	public ApiResponse<CategoryResponse> setActive(@PathVariable Long id, @RequestParam boolean active,
			HttpServletRequest request) {
		ExpenseCategory updated = this.categories.setCategoryActive(id, active);
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	/**
	 * Delete is refused when the category has sub-categories; the business message says so and the
	 * foreign key is the backstop.
	 */
	@DeleteMapping("/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Delete an unused expense category")
	public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) {
		this.categories.deleteCategory(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Sub-categories
	// ------------------------------------------------------------------

	@GetMapping("/{categoryId}/subcategories")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List the sub-categories of a category")
	public ApiResponse<List<SubcategoryResponse>> listSubcategories(@PathVariable Long categoryId,
			@RequestParam(defaultValue = "true") boolean activeOnly, HttpServletRequest request) {
		List<SubcategoryResponse> rows = this.categories.listSubcategories(categoryId, activeOnly)
			.stream()
			.map(CategoryController::toResponse)
			.toList();
		return ApiResponse.success(rows, CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/{categoryId}/subcategories", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create a sub-category")
	public ApiResponse<SubcategoryResponse> createSubcategory(@PathVariable Long categoryId,
			@Valid @RequestBody CreateSubcategoryRequest body, HttpServletRequest request) {
		ExpenseSubcategory created = this.categories.createSubcategory(categoryId, body.name(), body.description(),
				body.displayOrder());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/subcategories/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update a sub-category")
	public ApiResponse<SubcategoryResponse> updateSubcategory(@PathVariable Long id,
			@Valid @RequestBody UpdateSubcategoryRequest body, HttpServletRequest request) {
		ExpenseSubcategory updated = this.categories.updateSubcategory(id, body.name(), body.description(),
				body.displayOrder());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/subcategories/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a sub-category")
	public ApiResponse<SubcategoryResponse> setSubcategoryActive(@PathVariable Long id, @RequestParam boolean active,
			HttpServletRequest request) {
		ExpenseSubcategory updated = this.categories.setSubcategoryActive(id, active);
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@DeleteMapping("/subcategories/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Delete a sub-category")
	public ApiResponse<Void> deleteSubcategory(@PathVariable Long id, HttpServletRequest request) {
		this.categories.deleteSubcategory(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	static CategoryResponse toResponse(ExpenseCategory category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
				category.isActive(), category.getDisplayOrder(), 0, category.getUpdatedAt(),
				category.getVersion());
	}

	static CategoryResponse toResponse(ExpenseCategory category, int subcategoryCount) {
		return new CategoryResponse(category.getId(), category.getName(), category.getDescription(),
				category.isActive(), category.getDisplayOrder(), subcategoryCount, category.getUpdatedAt(),
				category.getVersion());
	}

	static SubcategoryResponse toResponse(ExpenseSubcategory subcategory) {
		return new SubcategoryResponse(subcategory.getId(),
				subcategory.getCategory() == null ? null : subcategory.getCategory().getId(),
				subcategory.getName(), subcategory.getDescription(), subcategory.isActive(),
				subcategory.getDisplayOrder(), subcategory.getUpdatedAt(), subcategory.getVersion());
	}

}
