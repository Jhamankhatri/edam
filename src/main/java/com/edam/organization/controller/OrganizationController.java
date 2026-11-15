package com.edam.organization.controller;

import java.util.List;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.organization.dto.OrganizationDtos.BranchResponse;
import com.edam.organization.dto.OrganizationDtos.CreateOrganizationProfileRequest;
import com.edam.organization.dto.OrganizationDtos.CreateOrganizationUnitRequest;
import com.edam.organization.dto.OrganizationDtos.DepartmentResponse;
import com.edam.organization.dto.OrganizationDtos.OrganizationProfileResponse;
import com.edam.organization.dto.OrganizationDtos.OrganizationUnitNode;
import com.edam.organization.dto.OrganizationDtos.OrganizationUnitResponse;
import com.edam.organization.dto.OrganizationDtos.OrganizationUnitTypeResponse;
import com.edam.organization.dto.OrganizationDtos.SaveBranchRequest;
import com.edam.organization.dto.OrganizationDtos.SaveDepartmentRequest;
import com.edam.organization.dto.OrganizationDtos.UpdateOrganizationProfileRequest;
import com.edam.organization.dto.OrganizationDtos.UpdateOrganizationUnitRequest;
import com.edam.organization.service.OrganizationProfileService;
import com.edam.organization.service.OrganizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Organization structure and profile (Module A).
 *
 * <h2>Permission split</h2>
 * Reads require {@code organization:view}, writes {@code organization:manage}. The profile is separated
 * to {@code configuration:*}: changing the bank's timezone is a configuration concern, not an
 * organizational one, and conflating them would mean a department administrator could adjust how every
 * timestamp in the system is rendered.
 *
 * <h2>Deactivation instead of DELETE</h2>
 * There is no delete mapping for units, departments or branches. Those rows are referenced by historical
 * requests and audit records, so removing one would either destroy history or fail on a foreign key. The
 * {@code /activation} endpoints deactivate instead, which is the operation a bank actually performs when
 * a department is merged away.
 *
 * <h2>Why these controllers are thin</h2>
 * Each method validates input (declaratively), delegates, and wraps the result. No controller touches a
 * repository: the service owns the transaction, the invariants and the audit record.
 */
@RestController
@RequestMapping(path = ApiPaths.V1 + "/organization", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Organization", description = "Organization hierarchy, departments, branches and the bank profile")
public class OrganizationController {

	private final OrganizationService organization;

	private final OrganizationProfileService profile;

	public OrganizationController(OrganizationService organization, OrganizationProfileService profile) {
		this.organization = organization;
		this.profile = profile;
	}

	// ------------------------------------------------------------------
	// Profile
	// ------------------------------------------------------------------

	@GetMapping("/profile")
	@EdamPermission(Permissions.ORGANIZATION_VIEW)
	@Operation(summary = "Read the organization profile")
	public ApiResponse<OrganizationProfileResponse> getProfile(HttpServletRequest request) {
		return ok(this.profile.current(), request);
	}

	@PostMapping("/profile")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create the organization profile",
			description = "Bootstraps the singleton profile. Fails if a profile already exists.")
	public ResponseEntity<ApiResponse<OrganizationProfileResponse>> createProfile(
			@Valid @RequestBody CreateOrganizationProfileRequest body, HttpServletRequest request) {
		return created(this.profile.create(body), request);
	}

	@PatchMapping("/profile")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update organization profile settings",
			description = "Partial update: only the supplied fields are applied.")
	public ApiResponse<OrganizationProfileResponse> updateProfile(
			@Valid @RequestBody UpdateOrganizationProfileRequest request, HttpServletRequest httpRequest) {
		return ok(this.profile.update(request), httpRequest);
	}

	// ------------------------------------------------------------------
	// Unit types and hierarchy
	// ------------------------------------------------------------------

	@GetMapping("/unit-types")
	@EdamPermission(Permissions.ORGANIZATION_VIEW)
	@Operation(summary = "List the organization unit type vocabulary")
	public ApiResponse<List<OrganizationUnitTypeResponse>> unitTypes(HttpServletRequest request) {
		return ok(this.organization.listUnitTypes(), request);
	}

	@GetMapping("/units")
	@EdamPermission(Permissions.ORGANIZATION_VIEW)
	@Operation(summary = "List active organization units as a flat list")
	public ApiResponse<List<OrganizationUnitResponse>> units(HttpServletRequest request) {
		return ok(this.organization.listUnits(), request);
	}

	@GetMapping("/units/tree")
	@EdamPermission(Permissions.ORGANIZATION_VIEW)
	@Operation(summary = "The organization hierarchy as a tree",
			description = "Built from the self-referencing parent relationship; depth is data, not fixed.")
	public ApiResponse<List<OrganizationUnitNode>> tree(HttpServletRequest request) {
		return ok(this.organization.tree(), request);
	}

	@PostMapping("/units")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Create an organization unit")
	public ResponseEntity<ApiResponse<OrganizationUnitResponse>> createUnit(
			@Valid @RequestBody CreateOrganizationUnitRequest body, HttpServletRequest request) {
		return created(this.organization.createUnit(body), request);
	}

	@PutMapping("/units/{id}")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Rename or re-parent an organization unit",
			description = "Re-parenting is refused when it would create a cycle.")
	public ApiResponse<OrganizationUnitResponse> updateUnit(@PathVariable UUID id,
			@Valid @RequestBody UpdateOrganizationUnitRequest body, HttpServletRequest request) {
		return ok(this.organization.updateUnit(id, body), request);
	}

	@PostMapping("/units/{id}/activation")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Activate or deactivate an organization unit")
	public ApiResponse<OrganizationUnitResponse> setUnitActive(@PathVariable UUID id,
			@RequestParam boolean active, HttpServletRequest request) {
		return ok(this.organization.setUnitActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Departments
	// ------------------------------------------------------------------

	@GetMapping("/departments")
	@EdamPermission(Permissions.ORGANIZATION_VIEW)
	@Operation(summary = "List active departments")
	public ApiResponse<List<DepartmentResponse>> departments(HttpServletRequest request) {
		return ok(this.organization.listDepartments(), request);
	}

	@PostMapping("/departments")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Create a department",
			description = "The backing organization unit is created in the same transaction.")
	public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(
			@Valid @RequestBody SaveDepartmentRequest body, HttpServletRequest request) {
		return created(this.organization.createDepartment(body), request);
	}

	@PutMapping("/departments/{id}")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Update a department")
	public ApiResponse<DepartmentResponse> updateDepartment(@PathVariable UUID id,
			@Valid @RequestBody SaveDepartmentRequest body, HttpServletRequest request) {
		return ok(this.organization.updateDepartment(id, body), request);
	}

	@PostMapping("/departments/{id}/activation")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Activate or deactivate a department")
	public ApiResponse<DepartmentResponse> setDepartmentActive(@PathVariable UUID id,
			@RequestParam boolean active, HttpServletRequest request) {
		return ok(this.organization.setDepartmentActive(id, active), request);
	}

	// ------------------------------------------------------------------
	// Branches
	// ------------------------------------------------------------------

	@GetMapping("/branches")
	@EdamPermission(Permissions.ORGANIZATION_VIEW)
	@Operation(summary = "List active branches")
	public ApiResponse<List<BranchResponse>> branches(HttpServletRequest request) {
		return ok(this.organization.listBranches(), request);
	}

	@PostMapping("/branches")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Create a branch")
	public ResponseEntity<ApiResponse<BranchResponse>> createBranch(@Valid @RequestBody SaveBranchRequest body,
			HttpServletRequest request) {
		return created(this.organization.createBranch(body), request);
	}

	@PutMapping("/branches/{id}")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Update a branch")
	public ApiResponse<BranchResponse> updateBranch(@PathVariable UUID id, @Valid @RequestBody SaveBranchRequest body,
			HttpServletRequest request) {
		return ok(this.organization.updateBranch(id, body), request);
	}

	@PostMapping("/branches/{id}/activation")
	@EdamPermission(Permissions.ORGANIZATION_MANAGE)
	@Operation(summary = "Activate or deactivate a branch")
	public ApiResponse<BranchResponse> setBranchActive(@PathVariable UUID id, @RequestParam boolean active,
			HttpServletRequest request) {
		return ok(this.organization.setBranchActive(id, active), request);
	}

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest request) {
		return ApiResponse.success(data, CorrelationId.current(), request.getRequestURI());
	}

	private static <T> ResponseEntity<ApiResponse<T>> created(T data, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(data, CorrelationId.current(), request.getRequestURI()));
	}

}
