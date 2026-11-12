package com.edam.institution.api;

import java.util.Optional;

/**
 * Public module boundary for resolving active institution branding and configurable settings.
 */
public interface InstitutionBrandingService {

    InstitutionBrandingView getActiveBranding();

    InstitutionBrandingView getBrandingByCode(String institutionCode);

    InstitutionBrandingView getFallbackBranding();

    Optional<String> getSettingValue(Long institutionId, String settingKey);
}
