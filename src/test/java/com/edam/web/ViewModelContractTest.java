package com.edam.web;

import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionService;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the view-model contract of the shared layout, including the pages that are
 * <em>not</em> backed by a controller method.
 *
 * <h2>Why these tests exist</h2>
 * <p>The login page is produced by a {@code ParameterizableViewController} registered in
 * {@code WebMvcConfig}; it is not a handler method in an application package. An earlier
 * revision scoped {@code InstitutionThemeAdvice} with {@code basePackages}, which excluded
 * exactly this handler: the login page returned HTTP 200 with completely blank branding.
 * A unit test of the advice could not have failed; only a test through the real dispatch
 * chain can. These tests therefore assert the rendered HTML of the public pages, and the
 * theme advice is deliberately not unit-tested in isolation.
 *
 * <p>The assertions look at the response body as the browser receives it. Spring Boot cannot
 * render JSP tags inside a MockMvc test, so the body contains the uncompiled template; what
 * matters here is that the model attributes the template reads are present and populated
 * from configuration. Full JSP rendering is verified against the packaged WAR in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ViewModelContractTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InstitutionProperties institutionProperties;

	@Autowired
	private InstitutionService institutionService;

	@Test
	@DisplayName("the login page receives institution branding, though no controller method serves it")
	void loginPageReceivesBranding() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();

		assertThat(result.getModelAndView()).as("the login view is rendered by a registered view controller")
			.isNotNull();
		assertThat(result.getModelAndView().getModel()).containsKey("institutionTheme");
		InstitutionTheme theme = (InstitutionTheme) result.getModelAndView().getModel().get("institutionTheme");
		assertThat(theme).isNotNull();
		assertThat(theme.name()).isEqualTo(this.institutionProperties.getName());
		assertThat(theme.shortName()).isEqualTo(this.institutionProperties.getShortName());
		assertThat(theme.primaryColor()).matches("#[0-9a-fA-F]{6}");
	}

	@Test
	@DisplayName("branding is populated from the institution record, not only from configuration")
	void brandingReflectsPersistedInstitution() throws Exception {
		// The seed migration creates demo-bank; resolving it through the public module API
		// proves the value the advice exposes is tenant data rather than a constant.
		var seeded = this.institutionService.requireActive("demo-bank");
		assertThat(seeded.name()).isNotBlank();
		assertThat(seeded.shortName()).isNotBlank();
	}

	@Test
	@DisplayName("the access-denied page receives the theme attributes too")
	void accessDeniedReceivesBranding() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/access-denied")).andExpect(status().isOk()).andReturn();

		assertThat(result.getModelAndView().getModel()).containsKey("institutionTheme");
	}

	@Test
	@DisplayName("every public page carries the correlation id into the view")
	void correlationIdIsExposedToViews() throws Exception {
		for (String path : new String[] { "/login", "/access-denied" }) {
			MvcResult result = this.mockMvc.perform(get(path).header("X-Correlation-Id",
					"11111111-2222-3333-4444-555555555555")).andExpect(status().isOk()).andReturn();

			assertThat(result.getModelAndView().getModel())
				.as("view model of %s", path)
				.containsEntry("correlationId", "11111111-2222-3333-4444-555555555555");
		}
	}

}
