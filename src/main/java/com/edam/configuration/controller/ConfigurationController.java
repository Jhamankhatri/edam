package com.edam.configuration.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.api.ApiPaths;
import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.common.security.PublicEndpoint;
import com.edam.configuration.dto.ConfigurationDtos;
import com.edam.configuration.dto.ConfigurationDtos.BulkSettingsUpdateRequest;
import com.edam.configuration.dto.ConfigurationDtos.FeatureFlagResponse;
import com.edam.configuration.dto.ConfigurationDtos.PublicConfigurationResponse;
import com.edam.configuration.dto.ConfigurationDtos.SaveFeatureFlagRequest;
import com.edam.configuration.dto.ConfigurationDtos.SaveSettingRequest;
import com.edam.configuration.dto.ConfigurationDtos.SaveThemeRequest;
import com.edam.configuration.dto.ConfigurationDtos.SettingResponse;
import com.edam.configuration.dto.ConfigurationDtos.ThemeResponse;
import com.edam.configuration.service.ApplicationSettingService;
import com.edam.configuration.service.FeatureFlagService;
import com.edam.configuration.service.PublicConfigurationService;
import com.edam.configuration.service.ThemeService;

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
 * Branding, themes, typed application settings and feature flags (Phase 4B).
 *
 * <p>All write endpoints require {@code configuration:manage}. Reads of admin-only data
 * require {@code configuration:view}. The {@code /public} endpoint requires no auth and
 * returns only the safe subset needed by the login page and app shell.
 */
@RestController
@RequestMapping(path = ApiPaths.V1, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Configuration", description = "Themes, application settings and feature flags")
public class ConfigurationController {

	private final ThemeService themes;
	private final ApplicationSettingService settings;
	private final FeatureFlagService flags;
	private final PublicConfigurationService publicConfig;

	public ConfigurationController(ThemeService themes, ApplicationSettingService settings,
			FeatureFlagService flags, PublicConfigurationService publicConfig) {
		this.themes = themes;
		this.settings = settings;
		this.flags = flags;
		this.publicConfig = publicConfig;
	}

	// ------------------------------------------------------------------
	// Public (unauthenticated)
	// ------------------------------------------------------------------

	@GetMapping("/public/configuration")
	@PublicEndpoint(reason = "Login screen and application shell must be able to read branding (name, colours, locale) before the user signs in. Whitelist-only; no secrets exposed.")
	@Operation(summary = "Safe subset of branding and configuration for the login page and app shell",
			description = "No authentication required. Never exposes secrets, roles or internal settings.")
	public ApiResponse<PublicConfigurationResponse> publicConfiguration(HttpServletRequest request) {
		return ok(this.publicConfig.getPublicConfiguration(), request);
	}

	// ------------------------------------------------------------------
	// Themes
	// ------------------------------------------------------------------

	@GetMapping("/themes")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List themes")
	public ApiResponse<List<ThemeResponse>> listThemes(
			@RequestParam(defaultValue = "false") boolean includeInactive, HttpServletRequest request) {
		return ok(this.themes.list(includeInactive), request);
	}

	@GetMapping("/themes/active")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "The currently-active default theme")
	public ApiResponse<ThemeResponse> activeTheme(HttpServletRequest request) {
		return ok(this.themes.activeDefault(), request);
	}

	@GetMapping("/themes/{id}")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Get one theme by id")
	public ApiResponse<ThemeResponse> getTheme(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.themes.get(id), request);
	}

	@PostMapping("/themes")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create a new theme")
	public ResponseEntity<ApiResponse<ThemeResponse>> createTheme(
			@Valid @RequestBody SaveThemeRequest body, HttpServletRequest request) {
		return created(this.themes.create(body), request);
	}

	@PutMapping("/themes/{id}")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update a theme")
	public ApiResponse<ThemeResponse> updateTheme(@PathVariable UUID id,
			@Valid @RequestBody SaveThemeRequest body, HttpServletRequest request) {
		return ok(this.themes.update(id, body), request);
	}

	@PostMapping("/themes/{id}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Activate or deactivate a theme")
	public ApiResponse<ThemeResponse> setThemeActive(@PathVariable UUID id,
			@RequestParam boolean active, HttpServletRequest request) {
		return ok(active ? this.themes.activate(id) : this.themes.deactivate(id), request);
	}

	@PostMapping("/themes/{id}/default")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Promote a theme to the installation default")
	public ApiResponse<ThemeResponse> setDefaultTheme(@PathVariable UUID id, HttpServletRequest request) {
		return ok(this.themes.setDefault(id), request);
	}

	// ------------------------------------------------------------------
	// Application settings
	// ------------------------------------------------------------------

	@GetMapping("/settings")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List all active application settings")
	public ApiResponse<List<SettingResponse>> listSettings(HttpServletRequest request) {
		return ok(this.settings.listAll(), request);
	}

	@GetMapping("/settings/categories/{category}")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List active settings in a category")
	public ApiResponse<List<SettingResponse>> listByCategory(@PathVariable String category, HttpServletRequest request) {
		return ok(this.settings.listByCategory(category), request);
	}

	@GetMapping("/settings/{key}")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Get a single setting by key")
	public ApiResponse<SettingResponse> getSetting(@PathVariable String key, HttpServletRequest request) {
		return ok(this.settings.get(key), request);
	}

	@PostMapping("/settings")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create or update a single setting")
	public ResponseEntity<ApiResponse<SettingResponse>> saveSetting(
			@Valid @RequestBody SaveSettingRequest body, HttpServletRequest request) {
		// If the key exists we return 200; if new we return 201.
		boolean isNew = this.settings.isNewKey(body.key());
		SettingResponse saved = this.settings.createOrUpdate(body);
		return isNew
				? created(saved, request)
				: ResponseEntity.ok(ApiResponse.success(saved, CorrelationId.current(), request.getRequestURI()));
	}

	@PatchMapping("/settings/bulk")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Update multiple settings atomically in one transaction",
			description = "All supplied keys must already exist and be editable; no partial updates.")
	public ApiResponse<List<SettingResponse>> bulkUpdateSettings(
			@Valid @RequestBody BulkSettingsUpdateRequest body, HttpServletRequest request) {
		return ok(this.settings.bulkUpdate(body), request);
	}

	// ------------------------------------------------------------------
	// Feature flags
	// ------------------------------------------------------------------

	@GetMapping("/feature-flags")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "List all feature flags")
	public ApiResponse<List<FeatureFlagResponse>> listFlags(HttpServletRequest request) {
		return ok(this.flags.list(), request);
	}

	@GetMapping("/feature-flags/{key}")
	@EdamPermission(Permissions.CONFIGURATION_VIEW)
	@Operation(summary = "Get one feature flag")
	public ApiResponse<FeatureFlagResponse> getFlag(@PathVariable String key, HttpServletRequest request) {
		return ok(this.flags.get(key), request);
	}

	@PostMapping("/feature-flags")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Create or update a feature flag")
	public ResponseEntity<ApiResponse<FeatureFlagResponse>> saveFlag(
			@Valid @RequestBody SaveFeatureFlagRequest body, HttpServletRequest request) {
		boolean isNew = this.flags.isNewKey(body.key());
		FeatureFlagResponse saved = this.flags.save(body);
		return isNew ? created(saved, request)
				: ResponseEntity.ok(ApiResponse.success(saved, CorrelationId.current(), request.getRequestURI()));
	}

	@PostMapping("/feature-flags/{key}/activation")
	@EdamPermission(Permissions.CONFIGURATION_MANAGE)
	@Operation(summary = "Enable or disable a feature flag")
	public ApiResponse<FeatureFlagResponse> setFlagEnabled(@PathVariable String key,
			@RequestParam boolean enabled, HttpServletRequest request) {
		return ok(this.flags.setEnabled(key, enabled), request);
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	private static <T> ApiResponse<T> ok(T data, HttpServletRequest req) {
		return ApiResponse.success(data, CorrelationId.current(), req.getRequestURI());
	}

	private static <T> ResponseEntity<ApiResponse<T>> created(T data, HttpServletRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(data, CorrelationId.current(), req.getRequestURI()));
	}
}
