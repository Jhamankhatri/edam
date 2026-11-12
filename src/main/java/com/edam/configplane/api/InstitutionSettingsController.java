package com.edam.configplane.api;

import java.util.List;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.configplane.api.ConfigurationDtos.FeatureFlagResponse;
import com.edam.configplane.api.ConfigurationDtos.InstitutionSettingsResponse;
import com.edam.configplane.api.ConfigurationDtos.UpdateFeatureFlagRequest;
import com.edam.configplane.api.ConfigurationDtos.UpdateInstitutionSettingsRequest;
import com.edam.configplane.feature.FeatureFlagService;
import com.edam.institution.Institution;
import com.edam.institution.InstitutionSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Institution settings and feature flags (BRD INS-01..04).
 *
 * <h2>Scope</h2>
 * This controller administers <em>the caller's own institution</em>. Creating a new institution is a
 * platform provisioning operation (BRD §21) and is intentionally absent: it needs roles, a currency,
 * departments and an administrator, so exposing a bare "create institution" endpoint would let a
 * half-provisioned tenant exist.
 */
@RestController
@RequestMapping(path = "/api/v1/institution", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Institution", description = "Institution settings and feature flags (INS-01..04)")
public class InstitutionSettingsController {

	private final InstitutionSettingsService settings;

	private final FeatureFlagService featureFlags;

	public InstitutionSettingsController(InstitutionSettingsService settings, FeatureFlagService featureFlags) {
		this.settings = settings;
		this.featureFlags = featureFlags;
	}

	@GetMapping("/settings")
	@EdamPermission(Permissions.INSTITUTION_VIEW)
	@Operation(summary = "Read the current institution's settings")
	public ApiResponse<InstitutionSettingsResponse> get(HttpServletRequest request) {
		return ApiResponse.success(toResponse(this.settings.current()), CorrelationId.current(),
				request.getRequestURI());
	}

	@PutMapping(path = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.INSTITUTION_ADMINISTER)
	@Operation(summary = "Update the current institution's settings")
	public ApiResponse<InstitutionSettingsResponse> update(@Valid @RequestBody UpdateInstitutionSettingsRequest body,
			HttpServletRequest request) {
		Institution updated = this.settings.update(body.name(), body.shortName(), body.displayName(),
				body.baseCurrency(), body.primaryColor(), body.secondaryColor(), body.accentColor(),
				body.languageTag(), body.timeZone(), body.supportEmail(), body.dateFormat(), body.numberFormat());
		return ApiResponse.success(toResponse(updated), CorrelationId.current(), request.getRequestURI());
	}

	@GetMapping("/feature-flags")
	@EdamPermission(Permissions.INSTITUTION_VIEW)
	@Operation(summary = "List feature flags with their effective values")
	public ApiResponse<List<FeatureFlagResponse>> listFlags(HttpServletRequest request) {
		List<FeatureFlagResponse> flags = this.featureFlags.list()
			.stream()
			.map(state -> new FeatureFlagResponse(state.key().name(), state.key().displayName(),
					state.key().description(), state.enabled(), state.key().configurable(), state.updatedAt()))
			.toList();
		return ApiResponse.success(flags, CorrelationId.current(), request.getRequestURI());
	}

	@PutMapping(path = "/feature-flags/{key}", consumes = MediaType.APPLICATION_JSON_VALUE)
	@EdamPermission(Permissions.INSTITUTION_FEATURE_MANAGE)
	@Operation(summary = "Set a feature flag for the current institution")
	public ApiResponse<FeatureFlagResponse> setFlag(@PathVariable String key,
			@Valid @RequestBody UpdateFeatureFlagRequest body, HttpServletRequest request) {
		FeatureFlagService.FlagState state = this.featureFlags.update(key, body.enabled());
		return ApiResponse.success(new FeatureFlagResponse(state.key().name(), state.key().displayName(),
				state.key().description(), state.enabled(), state.key().configurable(), state.updatedAt()),
				CorrelationId.current(), request.getRequestURI());
	}

	static InstitutionSettingsResponse toResponse(Institution institution) {
		return new InstitutionSettingsResponse(institution.getId(), institution.getCode(), institution.getName(),
				institution.getShortName(), institution.getBaseCurrency(), institution.getPrimaryColor(),
				institution.getSecondaryColor(), institution.getAccentColor(), institution.getLanguageTag(),
				institution.getTimeZone(), institution.getDepartmentHeadLimit(), institution.isActive(),
				institution.getDisplayName(), institution.getSupportEmail(), institution.getDateFormat(),
				institution.getNumberFormat(), institution.getStatus());
	}

}
