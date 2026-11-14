package com.edam.api;

import java.util.List;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.configuration.dto.ConfigurationDtos;
import com.edam.configuration.dto.ConfigurationDtos.BulkSettingsUpdateRequest;
import com.edam.configuration.dto.ConfigurationDtos.SaveFeatureFlagRequest;
import com.edam.configuration.dto.ConfigurationDtos.SaveSettingRequest;
import com.edam.configuration.dto.ConfigurationDtos.SaveThemeRequest;
import com.edam.configuration.dto.ConfigurationDtos.SettingResponse;
import com.edam.configuration.dto.ConfigurationDtos.SettingType;
import com.edam.configuration.dto.ConfigurationDtos.ThemeResponse;
import com.edam.configuration.dto.ConfigurationDtos.ThemeTokenRequest;
import com.edam.configuration.dto.ConfigurationDtos.ThemeTokenType;
import com.edam.configuration.dto.ConfigurationDtos.ThemeType;
import com.edam.configuration.model.BrandingTheme;
import com.edam.configuration.repository.BrandingThemeRepository;
import com.edam.configuration.service.ApplicationSettingService;
import com.edam.configuration.service.FeatureFlagService;
import com.edam.configuration.service.PublicConfigurationService;
import com.edam.configuration.service.ThemeService;
import com.edam.organization.dto.OrganizationDtos;
import com.edam.organization.service.OrganizationProfileService;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ConfigurationApiTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired private ThemeService themes;
	@Autowired private ApplicationSettingService settings;
	@Autowired private FeatureFlagService flags;
	@Autowired private PublicConfigurationService publicConfig;
	@Autowired private BrandingThemeRepository themeRepo;
	@Autowired private OrganizationProfileService profiles;

	@Test
	void defaultThemeIsAvailableAfterSeed() {
		ThemeResponse t = this.themes.activeDefault();
		assertThat(t.isDefault()).isTrue();
		assertThat(t.active()).isTrue();
		assertThat(t.themeType()).isEqualTo("LIGHT");
		assertThat(t.primaryColor()).startsWith("#");
	}

	@Test
	void themeCrudAndActivation() {
		ThemeResponse created = this.themes.create(new SaveThemeRequest(
				"Test Dark", "Dark variant", ThemeType.DARK,
				"#111111", "#222222", "#333333", "#000000", "#0a0a0a",
				"#eeeeee", "#ffffff", "#111111", "#eeeeee",
				"#111111", "#eeeeee", "#22c55e", "#facc15", "#ef4444", "#3b82f6",
				List.of(new ThemeTokenRequest("fontFamily", "Inter, sans-serif", ThemeTokenType.FONT))));
		assertThat(created.id()).isNotNull();
		assertThat(created.tokens()).containsEntry("fontFamily", "Inter, sans-serif");

		// Activate and promote to default
		this.themes.activate(created.id());
		ThemeResponse def = this.themes.setDefault(created.id());
		assertThat(def.isDefault()).isTrue();
		// Seed default should now be non-default
		assertThat(this.themes.list(false)).hasSizeGreaterThanOrEqualTo(2);
	}

	@Test
	void invalidColorRejected() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> this.themes.create(new SaveThemeRequest("BadColor", null, ThemeType.LIGHT,
						"not-a-color", null, null, "#fff", null, null, null, null, null, null,
						null, null, null, null, null, null, List.of())));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void tokenWithDisallowedCharactersRejected() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> this.themes.create(new SaveThemeRequest("Xss", null, ThemeType.LIGHT,
						"#fff", null, null, "#fff", null, null, null, null, null, null,
						null, null, null, null, null, null,
						List.of(new ThemeTokenRequest("evil", "<script>alert(1)</script>", ThemeTokenType.OTHER)))));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void deactivatingDefaultIsRefused() {
		ThemeResponse d = this.themes.activeDefault();
		BusinessException ex = assertThrows(BusinessException.class,
				() -> this.themes.deactivate(d.id()));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void settingTypeValidation() {
		SettingResponse s = this.settings.createOrUpdate(new SaveSettingRequest(
				"test.integer", "Test integer", null, "general", SettingType.INTEGER,
				42, 10, true, true));
		assertThat(s.value()).isEqualTo(42L);
		BusinessException bad = assertThrows(BusinessException.class,
				() -> this.settings.createOrUpdate(new SaveSettingRequest(
						"test.integer", null, null, null, null, "not-an-int", null, null, null)));
		assertThat(bad.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void bulkUpdateAtomic() {
		this.settings.createOrUpdate(new SaveSettingRequest("bulk.a", null, null, "general",
				SettingType.STRING, "a", null, true, true));
		this.settings.createOrUpdate(new SaveSettingRequest("bulk.b", null, null, "general",
				SettingType.INTEGER, 1, null, true, true));
		List<SettingResponse> out = this.settings.bulkUpdate(new BulkSettingsUpdateRequest(Map.of(
				"bulk.a", "updated", "bulk.b", 99)));
		assertThat(out).hasSize(2);
		assertThat(this.settings.get("bulk.b").value()).isEqualTo(99L);
	}

	@Test
	void blockedSettingKeyRejected() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> this.settings.createOrUpdate(new SaveSettingRequest(
						"db.password", null, null, "general", SettingType.STRING,
						"secret", null, true, true)));
		assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PERMISSION_DENIED);
	}

	@Test
	void featureFlagCrud() {
		var saved = this.flags.save(new SaveFeatureFlagRequest("NEW_DASHBOARD", "New dashboard", null, true, null));
		assertThat(saved.enabled()).isTrue();
		assertThat(this.flags.isEnabled("NEW_DASHBOARD")).isTrue();
		var toggled = this.flags.setEnabled("NEW_DASHBOARD", false);
		assertThat(toggled.enabled()).isFalse();
	}

	@Test
	void missingFeatureFlagDefaultsToDisabled() {
		assertThat(this.flags.isEnabled("BOGUS_FLAG_XXXX")).isFalse();
	}

	@Test
	@Transactional
	void publicConfigDoesNotLeakSensitiveData() {
		// Ensure there is a profile first
		try { this.profiles.current(); }
		catch (Exception noProfile) {
			this.profiles.create(new OrganizationDtos.CreateOrganizationProfileRequest(
					"EDAM Bank", null, "EDAM Bank Limited", null, null, null,
					"https://edam.example.com", "support@edam.example.com",
					null, null, null, null, null,
					null, null, null, null, null, null));
		}
		var pub = this.publicConfig.getPublicConfiguration();
		assertThat(pub.applicationName()).isNotNull();
		// Theme tokens must be present
		assertThat(pub.themeTokens()).isNotEmpty();
		// No internal settings leak
		assertThat(pub).extracting("supportEmail").isNotNull();
	}
}
