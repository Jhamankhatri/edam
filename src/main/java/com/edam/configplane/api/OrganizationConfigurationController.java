package com.edam.configplane.api;

import java.util.List;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.configplane.ExpenseNature;
import com.edam.configplane.api.ConfigurationDtos.CreateCommitteeLevelRequest;
import com.edam.configplane.api.ConfigurationDtos.CreateCommitteeMemberRequest;
import com.edam.configplane.api.ConfigurationDtos.CreateCurrencyRequest;
import com.edam.configplane.api.ConfigurationDtos.CreateRecommendingAuthorityRequest;
import com.edam.configplane.api.ConfigurationDtos.CommitteeLevelResponse;
import com.edam.configplane.api.ConfigurationDtos.CommitteeMemberResponse;
import com.edam.configplane.api.ConfigurationDtos.CurrencyResponse;
import com.edam.configplane.api.ConfigurationDtos.DepartmentHeadLimitResponse;
import com.edam.configplane.api.ConfigurationDtos.ExceedingLimitResponse;
import com.edam.configplane.api.ConfigurationDtos.RecommendingAuthorityResponse;
import com.edam.configplane.api.ConfigurationDtos.UpdateCommitteeLevelRequest;
import com.edam.configplane.api.ConfigurationDtos.UpdateCurrencyRequest;
import com.edam.configplane.api.ConfigurationDtos.UpdateDepartmentHeadLimitRequest;
import com.edam.configplane.api.ConfigurationDtos.UpdateRecommendingAuthorityRequest;
import com.edam.configplane.api.ConfigurationDtos.UpsertExceedingLimitRequest;
import com.edam.configplane.authority.RecommendingAuthority;
import com.edam.configplane.authority.RecommendingAuthorityService;
import com.edam.configplane.committee.CommitteeDesignation;
import com.edam.configplane.committee.CommitteeLevel;
import com.edam.configplane.committee.CommitteeMember;
import com.edam.configplane.committee.CommitteeService;
import com.edam.configplane.currency.Currency;
import com.edam.configplane.currency.CurrencyService;
import com.edam.configplane.limit.DepartmentHeadLimit;
import com.edam.configplane.limit.DepartmentHeadLimitService;
import com.edam.configplane.limit.ExceedingLimit;
import com.edam.configplane.limit.ExceedingLimitService;

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
 * Currencies, limits, authorities and the Expense Committee (BRD FM-08, FM-10..FM-13).
 *
 * <h2>One controller per configuration segment, not per entity</h2>
 * These are the "organisational" half of the configuration plane: who approves, in what currency,
 * and up to what amount. They are grouped because they share a permission and an audience (the
 * institution administrator), and because a client configures them together. The delegation matrix
 * has its own controller because it carries the routing explanation endpoint.
 */
@RestController
@RequestMapping(path = "/api/v1/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Configuration — Organisation",
		description = "Currencies, limits, recommending authorities and the Expense Committee")
public class OrganizationConfigurationController {

	private final CurrencyService currencies;

	private final ExceedingLimitService exceedingLimits;

	private final DepartmentHeadLimitService departmentHeadLimits;

	private final RecommendingAuthorityService authorities;

	private final CommitteeService committee;

	public OrganizationConfigurationController(CurrencyService currencies, ExceedingLimitService exceedingLimits,
			DepartmentHeadLimitService departmentHeadLimits, RecommendingAuthorityService authorities,
			CommitteeService committee) {
		this.currencies = currencies;
		this.exceedingLimits = exceedingLimits;
		this.departmentHeadLimits = departmentHeadLimits;
		this.authorities = authorities;
		this.committee = committee;
	}

	// ------------------------------------------------------------------
	// Currencies (FM-11)
	// ------------------------------------------------------------------

	@GetMapping("/currencies")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List currencies")
	public ApiResponse<List<CurrencyResponse>> listCurrencies(@RequestParam(defaultValue = "true") boolean activeOnly,
			HttpServletRequest request) {
		return ApiResponse.success(this.currencies.list(activeOnly).stream().map(OrganizationConfigurationController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/currencies", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Add a currency")
	public ApiResponse<CurrencyResponse> createCurrency(@Valid @RequestBody CreateCurrencyRequest body,
			HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.currencies.create(body.code(), body.name(), body.rateToBase())),
				CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/currencies/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update a currency's name and rate (the code is immutable)")
	public ApiResponse<CurrencyResponse> updateCurrency(@PathVariable Long id,
			@Valid @RequestBody UpdateCurrencyRequest body, HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.currencies.update(id, body.name(), body.rateToBase())),
				CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/currencies/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a currency")
	public ApiResponse<CurrencyResponse> setCurrencyActive(@PathVariable Long id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.currencies.setActive(id, active)), CorrelationId.current(),
				request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Exceeding limits (FM-13)
	// ------------------------------------------------------------------

	@GetMapping("/exceeding-limits")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List the exceeding limits per expense nature")
	public ApiResponse<List<ExceedingLimitResponse>> listExceedingLimits(HttpServletRequest request) {
		return ApiResponse.success(this.exceedingLimits.list()
			.stream()
			.map(OrganizationConfigurationController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/exceeding-limits", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Set the exceeding limit for an expense nature")
	public ApiResponse<ExceedingLimitResponse> upsertExceedingLimit(
			@Valid @RequestBody UpsertExceedingLimitRequest body, HttpServletRequest request) {
		ExceedingLimit saved = this.exceedingLimits.upsert(ExpenseNature.valueOf(body.expenseNature()), body.amount(),
				body.description(), body.active());
		return ApiResponse.success(toResponse(saved), CorrelationId.current(), request.getRequestURI());
	}

	@DeleteMapping("/exceeding-limits/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Remove an exceeding limit")
	public ApiResponse<Void> deleteExceedingLimit(@PathVariable Long id, HttpServletRequest request) {
		this.exceedingLimits.delete(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Department-head general delegation (FM-10)
	// ------------------------------------------------------------------

	@GetMapping("/department-head-limit")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Read the department-head general delegation (single record)")
	public ApiResponse<DepartmentHeadLimitResponse> getDepartmentHeadLimit(HttpServletRequest request) {
		return ApiResponse.success(this.departmentHeadLimits.find()
			.map(OrganizationConfigurationController::toResponse)
			.orElseGet(OrganizationConfigurationController::notConfigured), CorrelationId.current(),
				request.getRequestURI());
	}

	@PutMapping(path = "/department-head-limit", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create or update the department-head general delegation")
	public ApiResponse<DepartmentHeadLimitResponse> updateDepartmentHeadLimit(
			@Valid @RequestBody UpdateDepartmentHeadLimitRequest body, HttpServletRequest request) {
		DepartmentHeadLimit saved = this.departmentHeadLimits.upsert(body.amount(), body.description(), body.active());
		return ApiResponse.success(toResponse(saved), CorrelationId.current(), request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Recommending authorities (FM-12)
	// ------------------------------------------------------------------

	@GetMapping("/recommending-authorities")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List recommending authorities")
	public ApiResponse<List<RecommendingAuthorityResponse>> listAuthorities(
			@RequestParam(defaultValue = "true") boolean activeOnly, HttpServletRequest request) {
		return ApiResponse.success(this.authorities.list(activeOnly)
			.stream()
			.map(OrganizationConfigurationController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/recommending-authorities", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create a recommending authority")
	public ApiResponse<RecommendingAuthorityResponse> createAuthority(
			@Valid @RequestBody CreateRecommendingAuthorityRequest body, HttpServletRequest request) {
		RecommendingAuthority created = this.authorities.create(body.title(), body.departmentCode(),
				body.employeeId(), body.employeeDisplayName());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	/** The title is immutable; this endpoint re-assigns the holder, which is the real operation. */
	@PutMapping(path = "/recommending-authorities/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Re-assign the holder of a recommending authority")
	public ApiResponse<RecommendingAuthorityResponse> reassignAuthority(@PathVariable Long id,
			@Valid @RequestBody UpdateRecommendingAuthorityRequest body, HttpServletRequest request) {
		RecommendingAuthority updated = this.authorities.reassign(id, body.departmentCode(), body.employeeId(),
				body.employeeDisplayName());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/recommending-authorities/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a recommending authority")
	public ApiResponse<RecommendingAuthorityResponse> setAuthorityActive(@PathVariable Long id,
			@RequestParam boolean active, HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.authorities.setActive(id, active)), CorrelationId.current(),
				request.getRequestURI());
	}

	@DeleteMapping("/recommending-authorities/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Delete an unreferenced recommending authority")
	public ApiResponse<Void> deleteAuthority(@PathVariable Long id, HttpServletRequest request) {
		this.authorities.delete(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Expense Committee members (FM-08)
	// ------------------------------------------------------------------

	@GetMapping("/committee/members")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List committee members")
	public ApiResponse<List<CommitteeMemberResponse>> listMembers(
			@RequestParam(defaultValue = "true") boolean activeOnly, HttpServletRequest request) {
		return ApiResponse.success(this.committee.listMembers(activeOnly)
			.stream()
			.map(OrganizationConfigurationController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/committee/members", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Add a committee member")
	public ApiResponse<CommitteeMemberResponse> createMember(@Valid @RequestBody CreateCommitteeMemberRequest body,
			HttpServletRequest request) {
		CommitteeMember created = this.committee.createMember(body.employeeId(), body.displayName(),
				CommitteeDesignation.valueOf(body.designation()), body.designationLabel());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/committee/members/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a committee member")
	public ApiResponse<CommitteeMemberResponse> setMemberActive(@PathVariable Long id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.committee.setMemberActive(id, active)), CorrelationId.current(),
				request.getRequestURI());
	}

	@DeleteMapping("/committee/members/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Delete a committee member who is on no level")
	public ApiResponse<Void> deleteMember(@PathVariable Long id, HttpServletRequest request) {
		this.committee.deleteMember(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Expense Committee levels (FM-08)
	// ------------------------------------------------------------------

	@GetMapping("/committee/levels")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List committee levels")
	public ApiResponse<List<CommitteeLevelResponse>> listLevels(@RequestParam(defaultValue = "true") boolean activeOnly,
			HttpServletRequest request) {
		return ApiResponse.success(this.committee.listLevels(activeOnly)
			.stream()
			.map(OrganizationConfigurationController::toResponse)
			.toList(), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping(path = "/committee/levels", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create a committee level")
	public ApiResponse<CommitteeLevelResponse> createLevel(@Valid @RequestBody CreateCommitteeLevelRequest body,
			HttpServletRequest request) {
		CommitteeLevel created = this.committee.createLevel(body.name(), body.amountFrom(), body.amountTo(),
				body.minimumApprovals(), body.requiredMemberCount(), Boolean.TRUE.equals(body.requiresCeo()),
				Boolean.TRUE.equals(body.requiresCfo()), body.memberIds());
		return ApiResponse.success(toResponse(created), CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/committee/levels/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update a committee level")
	public ApiResponse<CommitteeLevelResponse> updateLevel(@PathVariable Long id,
			@Valid @RequestBody UpdateCommitteeLevelRequest body, HttpServletRequest request) {
		CommitteeLevel updated = this.committee.updateLevel(id, body.name(), body.amountFrom(), body.amountTo(),
				body.minimumApprovals(), body.requiredMemberCount(), Boolean.TRUE.equals(body.requiresCeo()),
				Boolean.TRUE.equals(body.requiresCfo()), body.memberIds());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@PostMapping("/committee/levels/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a committee level")
	public ApiResponse<CommitteeLevelResponse> setLevelActive(@PathVariable Long id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.committee.setLevelActive(id, active)), CorrelationId.current(),
				request.getRequestURI());
	}

	@DeleteMapping("/committee/levels/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Delete a committee level")
	public ApiResponse<Void> deleteLevel(@PathVariable Long id, HttpServletRequest request) {
		this.committee.deleteLevel(id);
		return ApiResponse.success(CorrelationId.current(), request.getRequestURI());
	}

	// ------------------------------------------------------------------
	// Mapping
	// ------------------------------------------------------------------

	static CurrencyResponse toResponse(Currency currency) {
		return new CurrencyResponse(currency.getId(), currency.getCode(), currency.getName(),
				currency.getRateToBase(), currency.isActive(), currency.getUpdatedAt(), currency.getVersion());
	}

	static ExceedingLimitResponse toResponse(ExceedingLimit limit) {
		return new ExceedingLimitResponse(limit.getId(), limit.getExpenseNature().name(), limit.getAmount(),
				limit.getDescription(), limit.isActive(), "NOT_ENFORCED", limit.getUpdatedAt(), limit.getVersion());
	}

	static DepartmentHeadLimitResponse toResponse(DepartmentHeadLimit limit) {
		return new DepartmentHeadLimitResponse(limit.getId(), limit.getAmount(), limit.getDescription(),
				limit.isActive(), true, limit.getUpdatedAt(), limit.getVersion());
	}

	/** Explicit "never configured" answer; the routing fallback then applies (BRD OD-06). */
	static DepartmentHeadLimitResponse notConfigured() {
		return new DepartmentHeadLimitResponse(null, null, "No general delegation record is configured. "
				+ "The platform fallback amount applies until one is created.", false, false, null, 0L);
	}

	static RecommendingAuthorityResponse toResponse(RecommendingAuthority authority) {
		return new RecommendingAuthorityResponse(authority.getId(), authority.getTitle(),
				authority.getDepartmentCode(), authority.getEmployeeId(), authority.getEmployeeDisplayName(),
				authority.isActive(), authority.getUpdatedAt(), authority.getVersion());
	}

	static CommitteeMemberResponse toResponse(CommitteeMember member) {
		return new CommitteeMemberResponse(member.getId(), member.getEmployeeId(), member.getDisplayName(),
				member.getDesignation().name(), member.getDesignationLabel(), member.isActive(),
				member.getUpdatedAt(), member.getVersion());
	}

	static CommitteeLevelResponse toResponse(CommitteeLevel level) {
		List<Long> memberIds = level.getMembers().stream().map(CommitteeMember::getId).sorted().toList();
		return new CommitteeLevelResponse(level.getId(), level.getName(), level.getAmountFrom(), level.getAmountTo(),
				level.getMinimumApprovals(), level.getRequiredMemberCount(), level.isRequiresCeo(),
				level.isRequiresCfo(), memberIds, level.isActive(), level.getUpdatedAt(), level.getVersion());
	}

}
