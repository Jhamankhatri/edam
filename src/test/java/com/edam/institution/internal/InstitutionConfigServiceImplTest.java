package com.edam.institution.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.edam.common.security.CurrentUserContext;
import com.edam.common.security.InstitutionContext;
import com.edam.common.security.InstitutionContextHolder;
import com.edam.config.EdamProperties;
import com.edam.institution.api.InstitutionBrandingView;
import com.edam.institution.domain.Institution;
import com.edam.institution.domain.InstitutionBranding;
import com.edam.institution.domain.InstitutionSetting;
import com.edam.institution.domain.InstitutionStatus;
import com.edam.institution.domain.ThemeMode;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InstitutionConfigServiceImplTest {

    private InstitutionRepository institutionRepository;
    private InstitutionBrandingRepository brandingRepository;
    private InstitutionSettingRepository settingRepository;
    private EdamProperties edamProperties;
    private InstitutionConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        institutionRepository = mock(InstitutionRepository.class);
        brandingRepository = mock(InstitutionBrandingRepository.class);
        settingRepository = mock(InstitutionSettingRepository.class);
        edamProperties = new EdamProperties();
        service = new InstitutionConfigServiceImpl(
                institutionRepository,
                brandingRepository,
                settingRepository,
                new CurrentUserContext(),
                edamProperties);
    }

    @AfterEach
    void tearDown() {
        InstitutionContextHolder.clear();
    }

    @Test
    void returnsExternalizedDefaultsWhenNoDatabaseInstitutionExists() {
        when(institutionRepository.findByCodeIgnoreCaseAndStatus("PLATFORM", InstitutionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        InstitutionBrandingView view = service.getActiveBranding();

        assertThat(view.institutionCode()).isEqualTo("PLATFORM");
        assertThat(view.primaryColor()).isEqualTo("#1B365D");
        assertThat(view.defaultCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void resolvesTenantSpecificBrandingAndSanitizesUnsafeColorOrPathValues() {
        Institution institution = new Institution(
                "FIN_ALPHA",
                "Alpha Financial Corp",
                "Alpha",
                "Alpha Financial Corp Ltd",
                InstitutionStatus.ACTIVE,
                "PKR",
                "en-PK",
                "Asia/Karachi");
        ReflectionTestUtils.setField(institution, "id", 42L);

        InstitutionBranding branding = new InstitutionBranding(
                institution,
                "Alpha Expense Hub",
                "https://evil.example.com/logo.png",
                "/assets/images/alpha-favicon.svg",
                "red; expression(alert(1))",
                "#224466",
                "#008877",
                ThemeMode.DARK,
                "Alpha Corporate Treasury");

        when(institutionRepository.findByCodeIgnoreCaseAndStatus("FIN_ALPHA", InstitutionStatus.ACTIVE))
                .thenReturn(Optional.of(institution));
        when(brandingRepository.findByInstitutionId(42L)).thenReturn(Optional.of(branding));

        InstitutionContextHolder.set(new InstitutionContext(42L, "FIN_ALPHA", 1L, "user1", Set.of()));

        InstitutionBrandingView view = service.getActiveBranding();

        assertThat(view.institutionCode()).isEqualTo("FIN_ALPHA");
        assertThat(view.name()).isEqualTo("Alpha Financial Corp");
        assertThat(view.defaultCurrencyCode()).isEqualTo("PKR");
        assertThat(view.primaryColor()).isEqualTo("#1B365D");
        assertThat(view.secondaryColor()).isEqualTo("#224466");
        assertThat(view.logoPath()).isEqualTo("/assets/images/edam-logo.svg");
        assertThat(view.faviconPath()).isEqualTo("/assets/images/alpha-favicon.svg");
        assertThat(view.themeMode()).isEqualTo("DARK");
    }

    @Test
    void retrievesInstitutionSettingByKey() {
        Institution institution = new Institution(
                "FIN_BETA",
                "Beta Microfinance",
                "Beta",
                "Beta Microfinance",
                InstitutionStatus.ACTIVE,
                "USD",
                "en",
                "UTC");
        ReflectionTestUtils.setField(institution, "id", 77L);
        InstitutionSetting setting = new InstitutionSetting(
                institution,
                "workflow.dual_control",
                "true",
                "BOOLEAN",
                "Requires dual control approval");
        when(settingRepository.findByInstitutionIdAndSettingKey(77L, "workflow.dual_control"))
                .thenReturn(Optional.of(setting));

        assertThat(service.getSettingValue(77L, "workflow.dual_control")).contains("true");
    }
}
