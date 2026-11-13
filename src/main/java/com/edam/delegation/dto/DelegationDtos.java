package com.edam.delegation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the delegation matrix (Module E).
 *
 * <p>{@code approverType} is a <em>string</em> on the way in and out. Binding it to the enum directly would
 * turn an unknown value into a framework deserialisation error that names no accepted values; the service
 * validates it and reports the closed list, which is what an administrator needs when a chain fails to save.
 */
public final class DelegationDtos {

	private DelegationDtos() {
	}

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

	@Schema(name = "SaveApproverStepRequest")
	public record SaveApproverStepRequest(@NotNull String approverType, UUID requiredRoleId,
			@Size(max = 64) String departmentScope, boolean mandatory) {
	}

	@Schema(name = "SaveDelegationRuleRequest")
	public record SaveDelegationRuleRequest(@NotNull UUID categoryId, UUID subcategoryId, @NotNull UUID natureId,
			UUID requestTypeId,
			@DecimalMin(value = "0.0", message = "must not be negative") BigDecimal minimumAmount,
			@DecimalMin(value = "0.0", message = "must not be negative") BigDecimal maximumAmount,
			@NotNull UUID currencyId, Instant effectiveFrom, Instant effectiveTo,
			@NotEmpty(message = "an approval chain needs at least one step") @Valid List<SaveApproverStepRequest> steps) {
	}

	@Schema(name = "DelegationResolutionResponse")
	public record DelegationResolutionResponse(UUID ruleId, String bandDescription, int stepCount,
			List<ApproverStepResponse> steps, String message) {
	}

}
