package com.edam.organization.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the organization module (Modules A, B).
 *
 * <h2>Why responses are records, not entities</h2>
 * Returning a JPA entity would expose every column, couple the wire format to the schema, and let a
 * lazy association throw during serialisation. Each response below is an explicit projection: adding a
 * column to the database changes nothing a client sees until a field is added here deliberately.
 *
 * <h2>Validation lives on the request records</h2>
 * Bean Validation annotations run before the controller body, so a malformed payload never reaches a
 * service. The rules mirror the database CHECK constraints, but both exist: the annotation produces a
 * useful field-level message, and the constraint stops any other writer from bypassing it.
 */
public final class OrganizationDtos {

	private OrganizationDtos() {
	}

	// ------------------------------------------------------------------
	// Organization profile (singleton)
	// ------------------------------------------------------------------

	/** One bank's identity and presentation settings. */
	@Schema(name = "OrganizationProfileResponse", description = "The installation's own identity")
	public record OrganizationProfileResponse(UUID id, String organizationName, String legalName, UUID logoAssetId,
			UUID faviconAssetId, String timezone, String dateFormat, UUID defaultCurrencyId, String defaultCurrencyCode,
			Instant updatedAt) {
	}

	/**
	 * Update command.
	 *
	 * <p>All fields are optional so that a partial update does not require resending the whole profile, and
	 * the service applies only the non-null values. The timezone is validated by the service against
	 * {@code ZoneId.getAvailableZoneIds()} rather than by a pattern: a syntactically plausible zone that
	 * Java cannot resolve would fail later, at report time, far from the mistake.
	 */
	@Schema(name = "UpdateOrganizationProfileRequest")
	public record UpdateOrganizationProfileRequest(
			@Size(max = 200) String organizationName,
			@Size(max = 300) String legalName,
			UUID logoAssetId,
			UUID faviconAssetId,
			@Size(max = 60) String timezone,
			@Size(max = 40) String dateFormat,
			UUID defaultCurrencyId) {
	}

	// ------------------------------------------------------------------
	// Organization units
	// ------------------------------------------------------------------

	/** One node of the hierarchy, with its type resolved for display. */
	@Schema(name = "OrganizationUnitResponse")
	public record OrganizationUnitResponse(UUID id, UUID parentUnitId, UUID unitTypeId, String unitTypeCode,
			String unitTypeName, String code, String name, String description, boolean active, int depth) {
	}

	/** A unit together with its descendants, as returned by the tree endpoint. */
	@Schema(name = "OrganizationUnitNode")
	public record OrganizationUnitNode(OrganizationUnitResponse unit, List<OrganizationUnitNode> children) {
	}

	@Schema(name = "CreateOrganizationUnitRequest")
	public record CreateOrganizationUnitRequest(
			@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$",
					message = "may contain letters, digits, dot, underscore and hyphen only") String code,
			@NotBlank @Size(max = 200) String name,
			@Size(max = 2000) String description,
			@NotNull UUID unitTypeId,
			UUID parentUnitId) {
	}

	@Schema(name = "UpdateOrganizationUnitRequest")
	public record UpdateOrganizationUnitRequest(@NotBlank @Size(max = 200) String name,
			@Size(max = 2000) String description, UUID parentUnitId) {
	}

	/** The unit-type vocabulary, exposed so a client can render the right choices. */
	@Schema(name = "OrganizationUnitTypeResponse")
	public record OrganizationUnitTypeResponse(UUID id, String code, String name, String description) {
	}

	// ------------------------------------------------------------------
	// Departments
	// ------------------------------------------------------------------

	/**
	 * A department.
	 *
	 * <p>{@code headDisplayName} is resolved by the service in a single batch, not by a lazy association:
	 * a department list of fifty rows would otherwise issue fifty extra queries.
	 */
	@Schema(name = "DepartmentResponse")
	public record DepartmentResponse(UUID id, UUID organizationUnitId, String code, String name, UUID headUserId,
			String headDisplayName, boolean active) {
	}

	/**
	 * Create/update command.
	 *
	 * <p>{@code parentUnitId} places the department in the hierarchy. It is optional because a bank may
	 * register departments directly under the head office, which is the root and hence has no parent to
	 * name — the service attaches it to the root when absent.
	 */
	@Schema(name = "SaveDepartmentRequest")
	public record SaveDepartmentRequest(
			@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@NotBlank @Size(max = 200) String name, UUID headUserId, UUID parentUnitId) {
	}

	// ------------------------------------------------------------------
	// Branches
	// ------------------------------------------------------------------

	@Schema(name = "BranchResponse")
	public record BranchResponse(UUID id, UUID organizationUnitId, String code, String name, String address,
			boolean active) {
	}

	@Schema(name = "SaveBranchRequest")
	public record SaveBranchRequest(
			@NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String code,
			@NotBlank @Size(max = 200) String name, @Size(max = 2000) String address, UUID parentUnitId) {
	}

	// ------------------------------------------------------------------
	// Media assets (logo, favicon, attachments)
	// ------------------------------------------------------------------

	/**
	 * A stored binary asset.
	 *
	 * <p>Only metadata is ever returned: the bytes live in Cloudinary and are reached through a
	 * server-generated signed URL, so no API response contains file content or a permanent public link to
	 * an internal document.
	 */
	@Schema(name = "MediaAssetResponse")
	public record MediaAssetResponse(UUID id, String assetType, String fileName, String contentType, long sizeBytes,
			String secureUrl, Instant createdAt) {
	}

	/** Contact details for a vendor or a department, used by the notification templates. */
	@Schema(name = "ContactDetails")
	public record ContactDetails(String contactPerson, @Email String email, String phone) {
	}

}
