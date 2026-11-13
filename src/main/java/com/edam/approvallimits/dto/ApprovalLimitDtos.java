package com.edam.approvallimits.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.edam.approvallimits.model.DepartmentApprovalLimit.ExpenseType;
import com.edam.approvallimits.model.ExceedingLimitRule.EscalationType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request and response shapes for approval limits, exceeding-limit rules and recommending authorities
 * (Module D).
 *
 * <h2>Why these are separate records rather than one per resource</h2>
 * Each pair of request/response records is nested in this holder so that the three closely related
 * configuration resources stay readable together and a reviewer can see, in one file, that a limit and a
 * recommending authority use the same classification vocabulary. They are still separate types, so a
 * controller cannot accidentally accept a rule where a limit was expected.
 */
public final class ApprovalLimitDtos {

	private ApprovalLimitDtos() {
	}

	// ---------------------------------------------------------------------------------------------
	// Approval limits
	// ---------------------------------------------------------------------------------------------

	/**
	 * Creates or edits a limit.
	 *
	 * <p>{@code userId} is optional on purpose: absent means a department-wide limit, present means a
	 * personal one. The distinction is the design, so it is expressed by nullability rather than a separate
	 * endpoint.
	 */
	public record SaveApprovalLimitRequest(@NotNull UUID departmentId, UUID userId,
			@NotNull ExpenseType expenseType, @NotNull UUID currencyId,
			@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal approvalLimit,
			Instant effectiveFrom, Instant effectiveTo) {
	}

	public record ApprovalLimitResponse(UUID id, UUID departmentId, String departmentName, UUID userId,
			String userDisplayName, String expenseType, UUID currencyId, BigDecimal approvalLimit,
			Instant effectiveFrom, Instant effectiveTo, boolean active) {
	}

	/**
	 * The ceiling that actually applies, and which authority it came from.
	 *
	 * <p>{@code basis} says {@code PERSONAL} or {@code DEPARTMENT} so an administrator can see <em>why</em>
	 * a figure applies. Reporting only the number would leave a dispute ("my limit is higher") unexplained.
	 */
	public record EffectiveLimit(UUID departmentId, UUID userId, BigDecimal limit, UUID currencyId,
			String expenseType, UUID sourceLimitId, String basis) {
	}

	/** Whether any of the candidate approvers is authorised for an amount. */
	public record AuthorityCheck(UUID departmentId, String expenseType, UUID currencyId, BigDecimal amount,
			List<UUID> permittedUserIds, List<String> reasons) {
	}

	/** What the caller asks to check: an amount in a department, optionally for named people. */
	public record AuthorityCheckRequest(@NotNull UUID departmentId, @NotBlank String expenseType,
			@NotNull UUID currencyId, @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal amount,
			List<UUID> candidateUserIds) {
	}

	// ---------------------------------------------------------------------------------------------
	// Exceeding-limit rules
	// ---------------------------------------------------------------------------------------------

	public record SaveExceedingLimitRuleRequest(@NotNull ExpenseType expenseType, @NotNull UUID natureId,
			@NotNull UUID currencyId,
			@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 4) BigDecimal thresholdAmount,
			@NotNull EscalationType escalationType) {
	}

	/**
	 * A rule as stored, plus whether it is acted upon.
	 *
	 * <p>{@code enforced} is always {@code false} in Phase 2 and is not a property of the row. The BRD
	 * leaves the consequence of exceeding a limit undecided (OD-P2-03), so the API reports the bank's
	 * stated intent while stating plainly that no routing change follows from it. Omitting the marker
	 * would let an administrator believe a BLOCK rule blocks something.
	 */
	public record ExceedingLimitRuleResponse(UUID id, String expenseType, UUID natureId, String natureName,
			UUID currencyId, BigDecimal thresholdAmount, String escalationType, boolean active, boolean enforced,
			String enforcementNote) {
	}

	/** The outcome of evaluating rules against a hypothetical request. */
	public record RuleEvaluation(UUID natureId, UUID currencyId, String expenseType, BigDecimal amount,
			List<ExceedingLimitRuleResponse> triggered, String consequenceNote) {
	}

	// ---------------------------------------------------------------------------------------------
	// Recommending authorities
	// ---------------------------------------------------------------------------------------------

	public record SaveRecommendingAuthorityRequest(@NotNull UUID departmentId, @NotNull UUID userId,
			@NotNull ExpenseType expenseType, Instant effectiveFrom, Instant effectiveTo) {
	}

	public record RecommendingAuthorityResponse(UUID id, UUID departmentId, String departmentName, UUID userId,
			String userDisplayName, String expenseType, Instant effectiveFrom, Instant effectiveTo,
			boolean active) {
	}

	/** The authority resolved for a department on a date, or an explanation of why there is none. */
	public record ResolvedAuthority(UUID departmentId, String expenseType, Instant at, UUID userId,
			String userDisplayName, String resolution) {
	}

	/** Free-text rationale is capped by the audit trail's tolerance for it, not by a column here. */
	public record DecisionNote(@NotBlank @Size(max = 500) String note) {
	}

}
