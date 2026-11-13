package com.edam.expenseconfiguration.dto;

import java.util.List;
import java.util.UUID;

import com.edam.expenseconfiguration.model.ExpenseCategory.ExpenseType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the expense catalogue (Module E).
 *
 * <p>The delegation matrix has its own DTOs in {@code com.edam.delegation.dto}. They were initially
 * declared here, which made two packages own the same wire format; keeping them with the module that
 * resolves them is what lets the matrix change without touching the catalogue's contract.
 */
public final class ExpenseConfigurationDtos {

	private ExpenseConfigurationDtos() {
	}

	// ------------------------------------------------------------------
	// Categories and subcategories
	// ------------------------------------------------------------------

	@Schema(name = "SubcategoryResponse")
	public record SubcategoryResponse(UUID id, UUID categoryId, String code, String name, String description,
			boolean active) {
	}

	/**
	 * A category with its subcategories.
	 *
	 * <p>Nested rather than flat because the two are always read together, and a client that had to make one
	 * call per category to build a request form would issue dozens of requests for a single screen.
	 */
	@Schema(name = "CategoryResponse")
	public record CategoryResponse(UUID id, String code, String name, String description,
			ExpenseType expenseType, boolean active, List<SubcategoryResponse> subcategories) {
	}

	@Schema(name = "SaveCategoryRequest")
	public record SaveCategoryRequest(
			@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
			@NotNull ExpenseType expenseType) {
	}

	@Schema(name = "SaveSubcategoryRequest")
	public record SaveSubcategoryRequest(
			@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {
	}

	// ------------------------------------------------------------------
	// Natures and request types
	// ------------------------------------------------------------------

	@Schema(name = "LookupResponse", description = "A code/name pair from a configurable vocabulary")
	public record LookupResponse(UUID id, String code, String name, String description, boolean active) {
	}

	@Schema(name = "SaveLookupRequest")
	public record SaveLookupRequest(
			@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {
	}

}
