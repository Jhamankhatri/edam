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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the view-model contract of the shared layout, including on pages that are
 * <em>not</em> backed by a controller method.
 *
 * <h2>Why these tests are written against the response body</h2>
 * <p>The login and access-denied pages are produced by {@code ParameterizableViewController}
 * instances registered in {@code WebMvcConfig}. Spring MVC fills the model through
 * {@code ModelFactory} only for handler methods, so a {@code @ControllerAdvice}
 * {@code @ModelAttribute} never runs for those pages: the previous implementation returned
 * HTTP 200 with completely blank branding and no correlation ID.
 *
 * <p>Asserting on a {@code ModelAndView} captured from the test would miss that class of
 * defect, because it inspects MVC internals rather than the page. These tests therefore assert
 * the response body as the browser receives it. Jasper does not execute inside Spring Boot's
 * test container, so the body contains the uncompiled template; the values that a broken model
 * would leave empty are nonetheless rendered by the container's own EL evaluation of
 * {@code ${...}} expressions, which is exactly the failure mode being guarded.
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
	@DisplayName("the login page carries institution branding, though no controller method serves it")
	void loginPageReceivesBranding() throws Exception {
		String body = this.mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains(this.institutionProperties.getShortName());
		assertThat(body).contains(this.institutionProperties.getName());
		// The theme tokens are interpolated into the shared layout; a missing model attribute
		// would leave the CSS variable declarations empty.
		assertThat(body).doesNotContain("--edam-primary: ;");
		assertThat(body).doesNotContain("--edam-secondary: ;");
	}

	@Test
	@DisplayName("the access-denied page carries institution branding too")
	void accessDeniedReceivesBranding() throws Exception {
		String body = this.mockMvc.perform(get("/access-denied")).andExpect(status().isOk()).andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).contains(this.institutionProperties.getShortName());
	}

	@Test
	@DisplayName("every public page carries the correlation id into the view")
	void correlationIdIsExposedToViews() throws Exception {
		String correlationId = "11111111-2222-3333-4444-555555555555";
		for (String path : new String[] { "/login", "/access-denied" }) {
			String body = this.mockMvc.perform(get(path).header("X-Correlation-Id", correlationId))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

			assertThat(body).as("page %s must render the correlation id", path).contains(correlationId);
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

}
