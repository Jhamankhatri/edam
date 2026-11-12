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

		@Override
		public void addInterceptors(InterceptorRegistry registry) {
			// modelCapturingInterceptor() is static so this call returns the same singleton the
			// container manages; a non-static @Bean method here would create a second instance
			// and the assertion (which autowires the bean) would observe an empty capture.
			registry.addInterceptor(modelCapturingInterceptor())
				.addPathPatterns("/**")
				.order(Ordered.HIGHEST_PRECEDENCE);
		}

		@org.springframework.context.annotation.Bean
		static ModelCapturingInterceptor modelCapturingInterceptor() {
			return new ModelCapturingInterceptor();
		}

	}
}
