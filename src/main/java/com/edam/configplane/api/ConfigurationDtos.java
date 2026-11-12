package com.edam.configplane.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response contracts for the configuration plane (BRD FM-07..FM-13).
 *
 * <h2>Why request records are nested here</h2>
 * The API contract is a unit: a controller, its request shapes and its response shapes change
 * together. Grouping them keeps the wire format reviewable in one file and makes it obvious when a
 * validation rule and a database constraint drift apart. Each request record documents the BRD rule
 * it implements.
 *
 * <h2>What responses never contain</h2>
 * No institution id, no audit columns, no internal ids the client did not already know, and no
 * secrets. Responses are the contract the future React client codes against (BRD API-01).
 */
public final class ConfigurationDtos {

	private ConfigurationDtos() {
	}

	// ------------------------------------------------------------------
	// Categories (FM-07)
	// ------------------------------------------------------------------

	/** Full category representation. */
	public record CategoryResponse(Long id, String name, String description, boolean active, int displayOrder,
			int subcategoryCount, Instant updatedAt, long version) {
	}

	public record CreateCategoryRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			@Min(0) @Max(9999) Integer displayOrder) {
	}

	public record UpdateCategoryRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			@Min(0) @Max(9999) Integer displayOrder) {
	}

	/** Sub-category representation; {@code categoryId} is echoed for client convenience. */
	public record SubcategoryResponse(Long id, Long categoryId, String name, String description, boolean active,
			int displayOrder, Instant updatedAt, long version) {
	}

	public record CreateSubcategoryRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			@Min(0) @Max(9999) Integer displayOrder) {
	}

	public record UpdateSubcategoryRequest(
			@NotBlank @Size(max = 120) String name,
			@Size(max = 500) String description,
			@Min(0) @Max(9999) Integer displayOrder) {
	}

	// ------------------------------------------------------------------
	// Delegation matrix (FM-09, §8.6)
	// ------------------------------------------------------------------

	/**
	 * One band of the delegation matrix.
	 *
	 * <p>{@code amountTo} null means unbounded and is only valid on the highest band; the service
	 * rejects a gap or an overlap rather than silently choosing one.
	 */
	public record DelegationRuleResponse(Long id, Long categoryId, String categoryName, Long subcategoryId,
			String subcategoryName, String expenseNature, BigDecimal amountFrom, BigDecimal amountTo,
			Long committeeLevelId, Set<String> approvers, List<Long> recommendingAuthorityIds, boolean active,
			Instant updatedAt, long version) {
	}

	public record CreateDelegationRuleRequest(@NotNull Long categoryId, Long subcategoryId,
			@NotBlank @Pattern(regexp = "OPEX|CAPEX", message = "must be OPEX or CAPEX") String expenseNature,
			@NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal amountFrom,
			@DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amountTo,
			@NotEmpty Set<@NotBlank String> approvers, List<Long> recommendingAuthorityIds, Long committeeLevelId) {
	}

	public record UpdateDelegationRuleRequest(@NotNull Long categoryId, Long subcategoryId,
			@NotBlank @Pattern(regexp = "OPEX|CAPEX", message = "must be OPEX or CAPEX") String expenseNature,
			@NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal amountFrom,
			@DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amountTo,
			@NotEmpty Set<@NotBlank String> approvers, List<Long> recommendingAuthorityIds, Long committeeLevelId) {
	}

	/**
	 * The router's answer for one request: the rule that applies, or a refusal.
	 *
	 * <p>Exposed as a read-only endpoint because the BRD requires routing to be explainable to the
	 * requester and to auditors; without this the only way to see the decision is to read logs.
	 */
	public record RoutingResolutionResponse(boolean resolved, Long ruleId, Long categoryId, Long subcategoryId,
			String expenseNature, BigDecimal amount, String matchedBandDescription, Set<String> approvers,
			List<Long> recommendingAuthorityIds, Long committeeLevelId, String refusalReason) {
	}

	// ------------------------------------------------------------------
	// Exceeding limits (FM-13)
	// ------------------------------------------------------------------

	/**
	 * Threshold above which a nature is "exceeding".
	 *
	 * <p>Deliberately data only. BRD FM-13 leaves whether exceeding the limit changes routing open
	 * (OD-27), so no workflow code consults these values. The response exposes
	 * {@code routingEffect = "NOT_ENFORCED"} to state that plainly rather than let a client infer
	 * enforcement.
	 */
	public record ExceedingLimitResponse(Long id, String expenseNature, BigDecimal amount, String description,
			boolean active, String routingEffect, Instant updatedAt, long version) {
	}

	public record UpsertExceedingLimitRequest(
			@NotBlank @Pattern(regexp = "OPEX|CAPEX", message = "must be OPEX or CAPEX") String expenseNature,
			@NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
			@Size(max = 255) String description, Boolean active) {
	}

	// ------------------------------------------------------------------
	// Department-head general delegation (FM-10)
	// ------------------------------------------------------------------

	public record DepartmentHeadLimitResponse(Long id, BigDecimal amount, String description, boolean active,
			boolean configured, Instant updatedAt, long version) {
	}

	public record UpdateDepartmentHeadLimitRequest(
			@NotNull @DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
			@Size(max = 500) String description, Boolean active) {
	}

	// ------------------------------------------------------------------
	// Currencies (FM-11)
	// ------------------------------------------------------------------

	public record CurrencyResponse(Long id, String code, String name, BigDecimal rateToBase, boolean active,
			Instant updatedAt, long version) {
	}

	public record CreateCurrencyRequest(
			@NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code") String code,
			@NotBlank @Size(max = 100) String name,
			@NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal rateToBase) {
	}

	/** The code is immutable: a currency's identity must not change under recorded amounts. */
	public record UpdateCurrencyRequest(@NotBlank @Size(max = 100) String name,
			@NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal rateToBase) {
	}

	// ------------------------------------------------------------------
	// Recommending authorities (FM-12)
	// ------------------------------------------------------------------

	public record RecommendingAuthorityResponse(Long id, String title, String departmentCode, String employeeId,
			String employeeDisplayName, boolean active, Instant updatedAt, long version) {
	}

	public record CreateRecommendingAuthorityRequest(
			@NotBlank @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9 ]+",
					message = "only letters, digits and spaces are permitted") String title,
			@NotBlank @Size(max = 64) String departmentCode, @NotBlank @Size(max = 64) String employeeId,
			@Size(max = 200) String employeeDisplayName) {
	}

	public record UpdateRecommendingAuthorityRequest(@NotBlank @Size(max = 200) @Pattern(regexp = "[A-Za-z0-9 ]+",
			message = "only letters, digits and spaces are permitted") String title,
			@NotBlank @Size(max = 64) String departmentCode, @Size(max = 200) String employeeDisplayName) {
	}

	// ------------------------------------------------------------------
	// Expense Committee (FM-08)
	// ------------------------------------------------------------------

	public record CommitteeMemberResponse(Long id, String employeeId, String displayName, String designation,
			String designationLabel, boolean active, Instant updatedAt, long version) {
	}

	public record CreateCommitteeMemberRequest(@NotBlank @Size(max = 64) String employeeId,
			@NotBlank @Size(max = 200) String displayName,
			@NotBlank @Pattern(regexp = "CEO|CFO|HEAD_OF_ADMIN_GSD|MEMBER",
					message = "must be CEO, CFO, HEAD_OF_ADMIN_GSD or MEMBER") String designation,
			@Size(max = 120) String designationLabel) {
	}

	public record CommitteeLevelResponse(Long id, String name, BigDecimal amountFrom, BigDecimal amountTo,
			int minimumApprovals, int requiredMemberCount, boolean requiresCeo, boolean requiresCfo,
			List<Long> memberIds, boolean active, Instant updatedAt, long version) {
	}

	public record CreateCommitteeLevelRequest(
			@NotBlank @Size(max = 120) String name,
			@NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal amountFrom,
			@DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amountTo,
			@Min(1) int minimumApprovals,
			@Min(1) @Max(10) int requiredMemberCount,
			Boolean requiresCeo, Boolean requiresCfo, @NotEmpty List<Long> memberIds) {
	}

	public record UpdateCommitteeLevelRequest(
			@NotBlank @Size(max = 120) String name,
			@NotNull @DecimalMin(value = "0.00") @Digits(integer = 17, fraction = 2) BigDecimal amountFrom,
			@DecimalMin(value = "0.01") @Digits(integer = 17, fraction = 2) BigDecimal amountTo,
			@Min(1) int minimumApprovals,
			@Min(1) @Max(10) int requiredMemberCount,
			Boolean requiresCeo, Boolean requiresCfo, @NotEmpty List<Long> memberIds) {
	}

	// ------------------------------------------------------------------
	// Institution settings and feature flags (INS-01..04)
	// ------------------------------------------------------------------

	public record InstitutionSettingsResponse(Long id, String code, String name, String shortName,
			String baseCurrency, String primaryColor, String secondaryColor, String accentColor, String languageTag,
			String timeZone, BigDecimal departmentHeadLimit, boolean active, String displayName,
			String supportEmail, String dateFormat, String numberFormat, String status) {
	}

	public record UpdateInstitutionSettingsRequest(@NotBlank @Size(max = 200) String name,
			@NotBlank @Size(max = 40) String shortName, @Size(max = 200) String displayName,
			@Pattern(regexp = "[A-Z]{3}", message = "must be an ISO-4217 code") String baseCurrency,
			@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour") String primaryColor,
			@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour") String secondaryColor,
			@Pattern(regexp = "#[0-9a-fA-F]{6}", message = "must be a #rrggbb colour") String accentColor,
			@Size(max = 20) String languageTag, @Size(max = 60) String timeZone,
			@Size(max = 200) String supportEmail, @Size(max = 40) String dateFormat,
			@Size(max = 40) String numberFormat) {
	}

	/** One feature flag for one institution (INS-04). */
	public record FeatureFlagResponse(String key, String name, String description, boolean enabled,
			boolean configurable, Instant updatedAt) {
	}

	public record UpdateFeatureFlagRequest(@NotNull Boolean enabled) {
	}

}
