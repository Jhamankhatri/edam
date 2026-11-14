package com.edam.configuration.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response DTOs for Phase 4B branding/theme/settings/feature-flag endpoints
 * plus the public-config endpoint.
 */
public final class ConfigurationDtos {

	private ConfigurationDtos() {}

	// ------------------------------------------------------------------
	// Themes
	// ------------------------------------------------------------------

	@Schema(name = "ThemeTokenRequest")
	public record ThemeTokenRequest(
			@NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,99}$") String key,
			@NotBlank @Size(max = 500) String value,
			@NotNull ThemeTokenType type) {}

	@Schema(name = "ThemeTokenType")
	public enum ThemeTokenType { COLOUR, FONT, SPACING, RADIUS, OTHER }

	@Schema(name = "ThemeResponse")
	public record ThemeResponse(
			UUID id, String name, String description, String themeType,
			boolean active, boolean isDefault,
			String primaryColor, String secondaryColor, String accentColor,
			String backgroundColor, String surfaceColor, String textColor, String headingColor,
			String navBgColor, String navTextColor,
			String buttonPrimaryColor, String buttonPrimaryText,
			String successColor, String warningColor, String errorColor, String infoColor,
			Map<String, String> tokens,
			Instant createdAt, Instant updatedAt) {}

	@Schema(name = "SaveThemeRequest")
	public record SaveThemeRequest(
			@NotBlank @Size(max = 100) String name,
			@Size(max = 1000) String description,
			@NotNull ThemeType themeType,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$", message = "Colour must be #rgb, #rrggbb or #rrggbbaa") String primaryColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String secondaryColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String accentColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String backgroundColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String surfaceColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String textColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String headingColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String navBgColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String navTextColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String buttonPrimaryColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String buttonPrimaryText,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String successColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String warningColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String errorColor,
			@Pattern(regexp = "^$|^#[0-9a-fA-F]{3,8}$") String infoColor,
			@Valid List<ThemeTokenRequest> tokens) {
	}

	@Schema(name = "ThemeType")
	public enum ThemeType { LIGHT, DARK }

	// ------------------------------------------------------------------
	// Application settings
	// ------------------------------------------------------------------

	@Schema(name = "SettingResponse")
	public record SettingResponse(
			UUID id, String key, String displayName, String description, String category,
			String type, Object value, Object defaultValue, boolean active, boolean editable,
			Instant updatedAt) {}

	@Schema(name = "SaveSettingRequest")
	public record SaveSettingRequest(
			@NotBlank @Size(max = 150) @Pattern(regexp = "^[a-z][a-z0-9._-]{1,149}$") String key,
			@Size(max = 200) String displayName,
			@Size(max = 2000) String description,
			@NotBlank @Size(max = 80) String category,
			@NotNull SettingType type,
			Object value,
			Object defaultValue,
			Boolean active,
			Boolean editable) {}

	@Schema(name = "BulkSettingsUpdateRequest")
	public record BulkSettingsUpdateRequest(@NotNull Map<String, Object> values) {}

	@Schema(name = "SettingType")
	public enum SettingType { STRING, BOOLEAN, INTEGER, DECIMAL, JSON, DATE }

	// ------------------------------------------------------------------
	// Feature flags
	// ------------------------------------------------------------------

	@Schema(name = "FeatureFlagResponse")
	public record FeatureFlagResponse(
			UUID id, String key, String displayName, String description,
			boolean enabled, Object configuration, Instant updatedAt) {}

	@Schema(name = "SaveFeatureFlagRequest")
	public record SaveFeatureFlagRequest(
			@NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Z_][A-Z0-9_]{1,99}$") String key,
			@NotBlank @Size(max = 150) String displayName,
			@Size(max = 2000) String description,
			Boolean enabled,
			Object configuration) {}

	// ------------------------------------------------------------------
	// Public config (safe subset for login/app shell)
	// ------------------------------------------------------------------

	@Schema(name = "PublicConfigurationResponse", description = "Safe subset of configuration exposed to unauthenticated callers")
	public record PublicConfigurationResponse(
			String applicationName,
			String organizationDisplayName,
			String organizationName,
			String supportEmail,
			String logoAssetId,
			String faviconAssetId,
			String defaultLocale,
			String defaultTimeZone,
			String dateFormat,
			String defaultCurrencyCode,
			String activeThemeName,
			String activeThemeType,
			Map<String, String> themeTokens) {}
}
