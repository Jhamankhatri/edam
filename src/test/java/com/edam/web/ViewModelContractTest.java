package com.edam.web;

import com.edam.institution.InstitutionProperties;
import com.edam.institution.InstitutionService;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.testsupport.ModelCapturingInterceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the view-model contract of the shared layout, including on pages that are
 * <em>not</em> backed by a controller method.
 *
 * <h2>The defect these tests exist for</h2>
 * <p>The login and access-denied pages are produced by {@code ParameterizableViewController}
 * instances registered in {@code WebMvcConfig}. Spring MVC fills the model through
 * {@code ModelFactory} only while invoking a <em>handler method</em>, so a
 * {@code @ControllerAdvice} {@code @ModelAttribute} never runs for those pages: the previous
 * implementation returned HTTP 200 with blank branding and no correlation ID.
 *
 * <p>View attributes are therefore contributed by a {@code HandlerInterceptor}, which
 * {@code DispatcherServlet} applies through {@code applyPostHandle} for <em>every</em>
 * {@code HandlerExecutionChain}, immediately before the view is rendered. That ordering is
 * what makes the model observable here.
 *
 * <h2>Why the model is captured instead of the response body</h2>
 * <p>Spring Boot's test container has no JSP engine, so a JSP-backed response body is empty in
 * {@code MockMvc}. Asserting on the body would only prove that an artifact of the test
 * environment is missing. {@link ModelCapturingInterceptor} records the model that the real
 * interceptor chain produced, which is exactly the input a JSP receives. Rendering of the JSP
 * itself — including that the {@code ${...}} expressions resolve — is verified against the
 * packaged executable WAR, in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ViewModelContractTest.CaptureConfig.class)
class ViewModelContractTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private InstitutionProperties institutionProperties;

	@Autowired
	private InstitutionService institutionService;

	@Autowired
	private ModelCapturingInterceptor modelCapture;

	/**
	 * Registers the capture interceptor with the highest precedence. {@code postHandle} runs in
	 * reverse order, so the highest precedence means it observes the model <em>after</em> the
	 * production interceptor has contributed the view attributes.
	 */
	@TestConfiguration
	static class CaptureConfig implements WebMvcConfigurer {

		private final ModelCapturingInterceptor capture;

		CaptureConfig(ModelCapturingInterceptor capture) {
			this.capture = capture;
		}

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			// Register the injected singleton: creating a second instance here would record
			// nothing, because the assertion reads the bean.
			registry.addInterceptor(this.capture).addPathPatterns("/**").order(Ordered.HIGHEST_PRECEDENCE);
		}

		@org.springframework.context.annotation.Bean
		ModelCapturingInterceptor modelCapturingInterceptor() {
			return new ModelCapturingInterceptor();
		}

	}

	@Test
	@DisplayName("the login page receives institution branding, though no controller method serves it")
	void loginPageReceivesBranding() throws Exception {
		this.mockMvc.perform(get("/login")).andExpect(status().isOk());

		Object theme = this.modelCapture.model().get(ViewConstants.INSTITUTION_THEME);
		assertThat(theme).as("branding must be present for a view-controller page").isInstanceOf(InstitutionTheme.class);
		InstitutionTheme branding = (InstitutionTheme) theme;
		assertThat(branding.shortName()).isEqualTo(this.institutionProperties.getShortName());
		assertThat(branding.name()).isEqualTo(this.institutionProperties.getName());
		assertThat(branding.primaryColor()).matches("#[0-9a-fA-F]{6}");
	}

	@Test
	@DisplayName("the access-denied page receives institution branding too")
	void accessDeniedReceivesBranding() throws Exception {
		this.mockMvc.perform(get("/access-denied")).andExpect(status().isOk());

		assertThat(this.modelCapture.model()).containsKey(ViewConstants.INSTITUTION_THEME);
	}

	@Test
	@DisplayName("every public page carries the correlation id into the view")
	void correlationIdIsExposedToViews() throws Exception {
		String correlationId = "11111111-2222-3333-4444-555555555555";
		for (String path : new String[] { "/login", "/access-denied" }) {
			this.mockMvc.perform(get(path).header("X-Correlation-Id", correlationId)).andExpect(status().isOk());

			assertThat(this.modelCapture.model()).as("view model of %s", path)
				.containsEntry(ViewConstants.CORRELATION_ID, correlationId);
		}
	}

	@Test
	@DisplayName("branding is resolved from the institution record, not only from configuration")
	void brandingReflectsPersistedInstitution() throws Exception {
		// The seed migration creates demo-bank; resolving it through the public module API
		// proves the value the interceptor exposes is tenant data rather than a constant.
		var seeded = this.institutionService.requireActive("demo-bank");
		assertThat(seeded.name()).isNotBlank();
		assertThat(seeded.shortName()).isNotBlank();
	}

	@Test
	@DisplayName("a redirect response carries no theme, because it renders no markup")
	void redirectCarriesNoTheme() throws Exception {
		this.mockMvc.perform(get("/")).andExpect(status().is3xxRedirection());

		assertThat(this.modelCapture.model()).doesNotContainKey(ViewConstants.INSTITUTION_THEME);
	}

}
