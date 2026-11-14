package com.edam.organization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the organization module (Modules A, B).
 */
public final class OrganizationDtos {

	private OrganizationDtos() {
	}

	// ------------------------------------------------------------------
	// Organization profile (singleton)
	// ------------------------------------------------------------------

	@Schema(name = "OrganizationProfileResponse", description = "The installation's own identity and contact details")
	public record OrganizationProfileResponse(
			UUID id,
			String organizationName,
			String displayName,
			String legalName,
			String organizationCode,
			String contactEmail,
			String contactPhone,
			String website,
			String supportEmail,
			String addressLine1,
			String addressLine2,
			String addressCity,
			String addressState,
			String addressPostalCode,
			String addressCountry,
			UUID logoAssetId,
			UUID faviconAssetId,
			String timezone,
			String dateFormat,
			UUID defaultCurrencyId,
			String defaultCurrencyCode,
			Instant createdAt,
			Instant updatedAt) {
	}

	@Schema(name = "CreateOrganizationProfileRequest",
			description = "Creates the singleton organization profile. Fails if a profile already exists.")
	public record CreateOrganizationProfileRequest(
			@jakarta.validation.constraints.NotBlank @Size(max = 200) String organizationName,
			@Size(max = 200) String displayName,
			@Size(max = 300) String legalName,
			@Size(max = 60) @Pattern(regexp = "^[A-Z0-9_-]{2,30}$",
					message = "Organization code must be 2-30 uppercase letters/digits/underscore/dash") String organizationCode,
			@Email @Size(max = 255) String contactEmail,
			@Size(max = 40) String contactPhone,
			@Size(max = 255) @Pattern(regexp = "^https?://.+", message = "Website must start with http:// or https://") String website,
			@Email @Size(max = 255) String supportEmail,
			@Size(max = 300) String addressLine1,
			@Size(max = 300) String addressLine2,
			@Size(max = 120) String addressCity,
			@Size(max = 120) String addressState,
			@Size(max = 30) String addressPostalCode,
			@Size(max = 100) String addressCountry,
			UUID logoAssetId,
			UUID faviconAssetId,
			@Size(max = 60) String timezone,
			@Size(max = 40) String dateFormat,
			UUID defaultCurrencyId) {
	}

	@Schema(name = "UpdateOrganizationProfileRequest",
			description = "Partial update; only non-null fields are applied.")
	public record UpdateOrganizationProfileRequest(
			@Size(max = 200) String organizationName,
			@Size(max = 200) String displayName,
			@Size(max = 300) String legalName,
			@Size(max = 60) @Pattern(regexp = "^[A-Z0-9_-]{2,30}$",
					message = "Organization code must be 2-30 uppercase letters/digits/underscore/dash") String organizationCode,
			@Email @Size(max = 255) String contactEmail,
			@Size(max = 40) String contactPhone,
			@Size(max = 255) @Pattern(regexp = "^https?://.+", message = "Website must start with http:// or https://") String website,
			@Email @Size(max = 255) String supportEmail,
			@Size(max = 300) String addressLine1,
			@Size(max = 300) String addressLine2,
			@Size(max = 120) String addressCity,
			@Size(max = 120) String addressState,
			@Size(max = 30) String addressPostalCode,
			@Size(max = 100) String addressCountry,
			UUID logoAssetId,
			UUID faviconAssetId,
			@Size(max = 60) String timezone,
			@Size(max = 40) String dateFormat,
			UUID defaultCurrencyId) {
	}

	// ------------------------------------------------------------------
	// Organization units
	// ------------------------------------------------------------------

	@Schema(name = "OrganizationUnitResponse")
	public record OrganizationUnitResponse(UUID id, UUID parentUnitId, UUID unitTypeId, String unitTypeCode,
			String unitTypeName, String code, String name, String description, boolean active, int depth) {
	}

	@Schema(name = "OrganizationUnitNode")
	public record OrganizationUnitNode(OrganizationUnitResponse unit, List<OrganizationUnitNode> children) {
	}

	@Schema(name = "CreateOrganizationUnitRequest")
	public record CreateOrganizationUnitRequest(
			@jakarta.validation.constraints.NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
					message = "Code may contain letters, digits, dots, hyphens and underscores") String code,
			@jakarta.validation.constraints.NotBlank @Size(max = 150) String name,
			@Size(max = 500) String description,
			UUID unitTypeId,
			UUID parentUnitId) {
	}

	@Schema(name = "UpdateOrganizationUnitRequest")
	public record UpdateOrganizationUnitRequest(
			@Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@Size(max = 150) String name,
			@Size(max = 500) String description,
			UUID parentUnitId) {
	}

	@Schema(name = "OrganizationUnitTypeResponse")
	public record OrganizationUnitTypeResponse(UUID id, String code, String name, String description) {
	}

	// ------------------------------------------------------------------
	// Departments
	// ------------------------------------------------------------------

	@Schema(name = "DepartmentResponse")
	public record DepartmentResponse(UUID id, UUID unitId, String code, String name, String description, boolean active) {
	}

	@Schema(name = "SaveDepartmentRequest")
	public record SaveDepartmentRequest(
			@jakarta.validation.constraints.NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@jakarta.validation.constraints.NotBlank @Size(max = 150) String name,
			@Size(max = 500) String description,
			UUID parentUnitId,
			UUID headUserId) {
	}

	// ------------------------------------------------------------------
	// Branches
	// ------------------------------------------------------------------

	@Schema(name = "BranchResponse")
	public record BranchResponse(UUID id, UUID unitId, String code, String name, String description, boolean active) {
	}

	@Schema(name = "SaveBranchRequest")
	public record SaveBranchRequest(
			@jakarta.validation.constraints.NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@jakarta.validation.constraints.NotBlank @Size(max = 150) String name,
			@Size(max = 500) String description,
			UUID parentUnitId,
			@Size(max = 500) String address) {
	}

}
