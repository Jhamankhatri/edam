package com.edam.expenseconfiguration.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.expenseconfiguration.model.ExpenseCategory.ExpenseType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for expense configuration and the delegation matrix (Module E).
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

	// ------------------------------------------------------------------
	// Delegation matrix
	// ------------------------------------------------------------------

	/**
	 * One approval step as configured.
	 *
	 * <p>{@code requiredRoleId} is present only for a {@code ROLE} step, and {@code roleCode} is resolved for
	 * display so an operator can read the chain without a second lookup.
	 */
	@Schema(name = "ApproverStepResponse")
	public record ApproverStepResponse(short stepOrder, String approverType, UUID requiredRoleId, String roleCode,
			String departmentScope, boolean mandatory) {
	}

	@Schema(name = "DelegationRuleResponse")
	public record DelegationRuleResponse(UUID id, UUID categoryId, String categoryName, UUID subcategoryId,
			String subcategoryName, UUID natureId, String natureName, UUID requestTypeId, String requestTypeName,
			BigDecimal minimumAmount, BigDecimal maximumAmount, UUID currencyId, String currencyCode,
			Instant effectiveFrom, Instant effectiveTo, boolean active, List<ApproverStepResponse> steps) {
	}

	/**
	 * Step definition on a write.
	 *
	 * <p>{@code approverType} is a string, validated in the service against the closed enum, rather than the
	 * enum itself: a JSON binding failure on an unknown constant produces a message from the framework that
	 * does not tell the caller which values are accepted.
	 */
	@Schema(name = "SaveApproverStepRequest")
	public record SaveApproverStepRequest(@NotNull String approverType, UUID requiredRoleId,
			@Size(max = 64) String departmentScope, boolean mandatory) {
	}

	@Schema(name = "SaveDelegationRuleRequest")
	public record SaveDelegationRuleRequest(@NotNull UUID categoryId, UUID subcategoryId, @NotNull UUID natureId,
			UUID requestTypeId,
			@NotNull @DecimalMin(value = "0.0", message = "must not be negative") BigDecimal minimumAmount,
			@DecimalMin(value = "0.0", message = "must not be negative") BigDecimal maximumAmount,
			@NotNull UUID currencyId, Instant effectiveFrom, Instant effectiveTo,
			@NotEmpty(message = "an approval chain needs at least one step") @Valid List<SaveApproverStepRequest> steps) {
	}

	@Schema(name = "DelegationResolutionResponse",
			description = "The route that would apply to a hypothetical request, for configuration testing")
	public record DelegationResolutionResponse(UUID ruleId, String bandDescription, int stepCount,
			List<ApproverStepResponse> steps, String message) {
	}

}
