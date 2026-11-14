package com.edam.configuration.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.configuration.dto.ConfigurationDtos.PublicConfigurationResponse;
import com.edam.configuration.dto.ConfigurationDtos.ThemeResponse;
import com.edam.masterdata.model.Currency;
import com.edam.masterdata.repository.CurrencyRepository;
import com.edam.organization.model.OrganizationProfile;
import com.edam.organization.repository.OrganizationProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, unauthenticated-safe configuration for the login page and app shell.
 *
 * <p>This service never returns secrets, internal settings, role information, user data
 * or feature flags whose exposure would reveal unreleased functionality. It is consumed
 * by the future React app before authentication.
 */
@Service
public class PublicConfigurationService {

	private final OrganizationProfileRepository profiles;
	private final ThemeService themes;
	private final ApplicationSettingService settings;
	private final CurrencyRepository currencies;

	public PublicConfigurationService(OrganizationProfileRepository profiles, ThemeService themes,
			ApplicationSettingService settings, CurrencyRepository currencies) {
		this.profiles = profiles;
		this.themes = themes;
		this.settings = settings;
		this.currencies = currencies;
	}

	@Transactional(readOnly = true)
	public PublicConfigurationResponse getPublicConfiguration() {
		OrganizationProfile p = this.profiles.findFirstByActiveTrue().orElse(null);
		Map<String, Object> s = this.settings.publicSafeValues();

		String appName = stringVal(s.get("app.displayName"), "EDAM");
		String defaultLocale = stringVal(s.get("app.defaultLocale"), "en-GB");
		String dateFormat = stringVal(s.get("app.dateFormat"), p == null ? "dd MMM yyyy" : p.getDateFormat());
		String timeZone = stringVal(s.get("app.timeZone"), p == null ? "Asia/Karachi" : p.getTimezone());
		String defaultCurrencyCode = null;
		if (p != null && p.getDefaultCurrencyId() != null) {
			defaultCurrencyCode = this.currencies.findById(p.getDefaultCurrencyId())
				.map(Currency::getCode).orElse(null);
		}
		if (defaultCurrencyCode == null) defaultCurrencyCode = stringVal(s.get("app.defaultCurrency"), null);

		String orgName = p == null ? null : p.getOrganizationName();
		String orgDisplayName = p == null ? appName : (p.getDisplayName() != null ? p.getDisplayName() : p.getOrganizationName());
		String supportEmail = p == null ? null : p.getSupportEmail();
		String logoAssetId = p == null ? null : (p.getLogoAssetId() == null ? null : p.getLogoAssetId().toString());
		String faviconAssetId = p == null ? null : (p.getFaviconAssetId() == null ? null : p.getFaviconAssetId().toString());

		ThemeResponse activeTheme;
		try { activeTheme = this.themes.activeDefault(); }
		catch (BusinessException be) { activeTheme = null; }

		String themeName = activeTheme == null ? null : activeTheme.name();
		String themeType = activeTheme == null ? null : activeTheme.themeType();
		Map<String, String> tokens = new LinkedHashMap<>();
		if (activeTheme != null) {
			putIfPresent(tokens, "primary", activeTheme.primaryColor());
			putIfPresent(tokens, "secondary", activeTheme.secondaryColor());
			putIfPresent(tokens, "accent", activeTheme.accentColor());
			putIfPresent(tokens, "background", activeTheme.backgroundColor());
			putIfPresent(tokens, "surface", activeTheme.surfaceColor());
			putIfPresent(tokens, "text", activeTheme.textColor());
			putIfPresent(tokens, "heading", activeTheme.headingColor());
			putIfPresent(tokens, "navBg", activeTheme.navBgColor());
			putIfPresent(tokens, "navText", activeTheme.navTextColor());
			putIfPresent(tokens, "buttonPrimary", activeTheme.buttonPrimaryColor());
			putIfPresent(tokens, "buttonPrimaryText", activeTheme.buttonPrimaryText());
			putIfPresent(tokens, "success", activeTheme.successColor());
			putIfPresent(tokens, "warning", activeTheme.warningColor());
			putIfPresent(tokens, "error", activeTheme.errorColor());
			putIfPresent(tokens, "info", activeTheme.infoColor());
			if (activeTheme.tokens() != null) tokens.putAll(activeTheme.tokens());
		}

		return new PublicConfigurationResponse(appName, orgDisplayName, orgName, supportEmail,
				logoAssetId, faviconAssetId, defaultLocale, timeZone, dateFormat, defaultCurrencyCode,
				themeName, themeType, Map.copyOf(tokens));
	}

	private static String stringVal(Object v, String dflt) {
		if (v == null) return dflt;
		String s = v.toString();
		return s.isBlank() ? dflt : s;
	}

	private static void putIfPresent(Map<String, String> m, String key, String val) {
		if (val != null && !val.isBlank()) m.put(key, val);
	}
}
