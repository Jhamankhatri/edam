package com.edam.institution.internal;

import com.edam.common.security.CurrentUserContext;
import com.edam.common.security.InstitutionContext;
import com.edam.common.validation.HexColorValidator;
import com.edam.config.EdamProperties;
import com.edam.institution.api.InstitutionBrandingService;
import com.edam.institution.api.InstitutionBrandingView;
import com.edam.institution.domain.Institution;
import com.edam.institution.domain.InstitutionBranding;
import com.edam.institution.domain.InstitutionSetting;
import com.edam.institution.domain.InstitutionStatus;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves active institution presentation and configuration settings from PostgreSQL,
 * falling back cleanly to externalized {@link EdamProperties} defaults when no tenant record is bound.
 */
@Service
@Transactional(readOnly = true)
public class InstitutionConfigServiceImpl implements InstitutionBrandingService {

    private final InstitutionRepository institutionRepository;
    private final InstitutionBrandingRepository brandingRepository;
    private final InstitutionSettingRepository settingRepository;
    private final CurrentUserContext currentUserContext;
    private final EdamProperties edamProperties;

    public InstitutionConfigServiceImpl(
            InstitutionRepository institutionRepository,
            InstitutionBrandingRepository brandingRepository,
            InstitutionSettingRepository settingRepository,
            CurrentUserContext currentUserContext,
            EdamProperties edamProperties) {
        this.institutionRepository = institutionRepository;
        this.brandingRepository = brandingRepository;
        this.settingRepository = settingRepository;
        this.currentUserContext = currentUserContext;
        this.edamProperties = edamProperties;
    }

    @Override
    public InstitutionBrandingView getActiveBranding() {
        Optional<InstitutionContext> activeContext = currentUserContext.getInstitutionContext();
        if (activeContext.isPresent() && activeContext.get().institutionCode() != null) {
            return getBrandingByCode(activeContext.get().institutionCode());
        }
        return getBrandingByCode(edamProperties.getDefaultInstitution().getCode());
    }

    @Override
    public InstitutionBrandingView getBrandingByCode(String institutionCode) {
        if (institutionCode == null || institutionCode.isBlank()) {
            return defaultFallbackView();
        }
        return institutionRepository
                .findByCodeIgnoreCaseAndStatus(institutionCode.trim(), InstitutionStatus.ACTIVE)
                .map(this::toBrandingView)
                .orElseGet(this::defaultFallbackView);
    }

    @Override
    public InstitutionBrandingView getFallbackBranding() {
        return defaultFallbackView();
    }

    @Override
    public Optional<String> getSettingValue(Long institutionId, String settingKey) {
        if (institutionId == null || settingKey == null || settingKey.isBlank()) {
            return Optional.empty();
        }
        return settingRepository
                .findByInstitutionIdAndSettingKey(institutionId, settingKey.trim())
                .map(InstitutionSetting::getSettingValue);
    }

    private InstitutionBrandingView toBrandingView(Institution institution) {
        EdamProperties.DefaultInstitution defaults = edamProperties.getDefaultInstitution();
        Optional<InstitutionBranding> brandingOpt = brandingRepository.findByInstitutionId(institution.getId());
        if (brandingOpt.isEmpty()) {
            return new InstitutionBrandingView(
                    institution.getCode(),
                    institution.getName(),
                    institution.getShortName(),
                    defaults.getProductTitle(),
                    defaults.getLogoPath(),
                    defaults.getFaviconPath(),
                    HexColorValidator.sanitizeOrDefault(defaults.getPrimaryColor(), "#1B365D"),
                    HexColorValidator.sanitizeOrDefault(defaults.getSecondaryColor(), "#334E68"),
                    HexColorValidator.sanitizeOrDefault(defaults.getAccentColor(), "#0F766E"),
                    defaults.getThemeMode(),
                    defaults.getFooterText(),
                    institution.getDefaultCurrencyCode(),
                    institution.getLocale(),
                    institution.getTimeZone());
        }

        InstitutionBranding branding = brandingOpt.get();
        return new InstitutionBrandingView(
                institution.getCode(),
                institution.getName(),
                institution.getShortName(),
                branding.getProductTitle(),
                sanitizePath(branding.getLogoPath(), defaults.getLogoPath()),
                sanitizePath(branding.getFaviconPath(), defaults.getFaviconPath()),
                HexColorValidator.sanitizeOrDefault(branding.getPrimaryColor(), defaults.getPrimaryColor()),
                HexColorValidator.sanitizeOrDefault(branding.getSecondaryColor(), defaults.getSecondaryColor()),
                HexColorValidator.sanitizeOrDefault(branding.getAccentColor(), defaults.getAccentColor()),
                branding.getThemeMode().name(),
                branding.getFooterText(),
                institution.getDefaultCurrencyCode(),
                institution.getLocale(),
                institution.getTimeZone());
    }

    private InstitutionBrandingView defaultFallbackView() {
        EdamProperties.DefaultInstitution defaults = edamProperties.getDefaultInstitution();
        return new InstitutionBrandingView(
                defaults.getCode(),
                defaults.getName(),
                defaults.getShortName(),
                defaults.getProductTitle(),
                defaults.getLogoPath(),
                defaults.getFaviconPath(),
                HexColorValidator.sanitizeOrDefault(defaults.getPrimaryColor(), "#1B365D"),
                HexColorValidator.sanitizeOrDefault(defaults.getSecondaryColor(), "#334E68"),
                HexColorValidator.sanitizeOrDefault(defaults.getAccentColor(), "#0F766E"),
                defaults.getThemeMode(),
                defaults.getFooterText(),
                defaults.getDefaultCurrencyCode(),
                defaults.getLocale(),
                defaults.getTimeZone());
    }

    private String sanitizePath(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        String trimmed = candidate.trim();
        if (trimmed.startsWith("/") && !trimmed.startsWith("//") && !trimmed.contains("..")) {
            return trimmed;
        }
        return fallback;
    }
}
