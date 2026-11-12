package com.edam.config;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edam.common.security.CurrentUserContext;
import com.edam.common.security.EdamUserPrincipal;
import com.edam.common.security.InstitutionAuthorizationChecker;
import com.edam.common.security.InstitutionContext;
import com.edam.common.security.InstitutionContextHolder;
import com.edam.common.security.PermissionConstants;
import com.edam.institution.api.InstitutionBrandingService;
import com.edam.institution.api.InstitutionBrandingView;
import com.edam.institution.web.InstitutionContextRestController;
import com.edam.institution.web.InstitutionThemeController;
import com.edam.web.FoundationViewController;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    FoundationViewController.class,
    InstitutionContextRestController.class,
    InstitutionThemeController.class
})
@Import(CurrentUserContext.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstitutionBrandingService brandingService;

    @AfterEach
    void clearInstitutionContext() {
        InstitutionContextHolder.clear();
    }

    @Test
    void unauthenticatedBrowserRequestRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unauthenticatedApiRequestReturns401JsonEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EDAM-AUTH-401"));
    }

    @Test
    void publicLoginAndThemeCssAreAccessibleWithoutAuthentication() throws Exception {
        when(brandingService.getActiveBranding()).thenReturn(sampleBranding("PLATFORM"));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/WEB-INF/jsp/auth/login.jsp"));

        mockMvc.perform(get("/assets/theme/institution-theme.css"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("--edam-primary: #1B365D;")));
    }

    @Test
    void authenticatedUserWithMultiInstitutionMembershipCanSwitchAllowedInstitution() throws Exception {
        EdamUserPrincipal principal = multiInstitutionPrincipal();
        when(brandingService.getActiveBranding()).thenReturn(sampleBranding("INST_B"));

        mockMvc.perform(get("/api/v1/institutions/current")
                        .header("X-Institution-Code", "INST_B")
                        .with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.institutionCode").value("INST_B"));
    }

    @Test
    void rejectsUnauthorizedInstitutionContextSwitchWith403Json() throws Exception {
        EdamUserPrincipal principal = multiInstitutionPrincipal();

        mockMvc.perform(get("/api/v1/institutions/current")
                        .header("X-Institution-Code", "INST_UNAUTHORIZED")
                        .with(user(principal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EDAM-SEC-403"));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mockMvc.perform(post("/logout").with(user(multiInstitutionPrincipal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorizationCheckerEvaluatesPermissionsStrictlyWithinActiveInstitution() {
        InstitutionAuthorizationChecker checker = new InstitutionAuthorizationChecker(new CurrentUserContext());
        InstitutionContextHolder.set(new InstitutionContext(
                10L,
                "INST_A",
                100L,
                "alice",
                Set.of(PermissionConstants.INSTITUTION_CONFIG_VIEW)));

        org.assertj.core.api.Assertions.assertThat(
                        checker.hasPermission(PermissionConstants.INSTITUTION_CONFIG_VIEW))
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(
                        checker.hasPermission(PermissionConstants.AUDIT_LOG_VIEW))
                .isFalse();
    }

    private EdamUserPrincipal multiInstitutionPrincipal() {
        return new EdamUserPrincipal(
                100L,
                "alice",
                "Alice Example",
                "{bcrypt}ignored",
                true,
                true,
                "INST_A",
                Map.of("INST_A", 1L, "INST_B", 2L),
                Map.of(
                        "INST_A", Set.of(PermissionConstants.INSTITUTION_CONFIG_VIEW),
                        "INST_B", Set.of(PermissionConstants.AUDIT_LOG_VIEW)),
                List.of(new SimpleGrantedAuthority("AUTHENTICATED_USER")));
    }

    private InstitutionBrandingView sampleBranding(String code) {
        return new InstitutionBrandingView(
                code,
                "Sample Financial Institution",
                code,
                "EDAM Platform",
                "/assets/images/edam-logo.svg",
                "/assets/images/edam-logo.svg",
                "#1B365D",
                "#334E68",
                "#0F766E",
                "LIGHT",
                "Sample Footer",
                "PKR",
                "en",
                "UTC");
    }
}
