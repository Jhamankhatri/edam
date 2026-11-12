package com.edam.web;


import java.util.Arrays;

import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Temporary diagnostic: reports which interceptors the MVC infrastructure actually applies and
 * what a view-controller dispatch produces. Emits its findings as a notice annotation so the CI
 * build stays green while the information is collected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterceptorDiagnosticsTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private org.springframework.context.ApplicationContext context;

	@Test
	void dumpDispatcherState() {
		StringBuilder report = new StringBuilder();

		report.append("allMappings=")
			.append(context.getBeansOfType(HandlerMapping.class).entrySet().stream()
				.map(entry -> entry.getKey() + ":" + entry.getValue().getClass().getSimpleName())
				.toList())
			.append(" | ");

		try {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
			for (var entry : context.getBeansOfType(HandlerMapping.class).entrySet()) {
				HandlerExecutionChain chain = entry.getValue().getHandler(request);
				if (chain != null) {
					Object handler = chain.getHandler();
					report.append(entry.getKey()).append("=>handler=").append(handler.getClass().getSimpleName())
						.append(", interceptors=").append(Arrays.toString(chain.getInterceptors())).append("; ");
				}
			}
		}
		catch (Exception ex) {
			report.append("lookupFailed=").append(ex.getClass().getSimpleName()).append("; ");
		}

		MvcResult result = this.mockMvc.perform(get("/login")).andReturn();
		report.append("resultHandler=").append(result.getHandler());
		report.append(" | view=")
			.append((result.getModelAndView() != null) ? result.getModelAndView().getViewName() : null);
		report.append(" | model=")
			.append((result.getModelAndView() != null) ? result.getModelAndView().getModelMap() : null);

		System.out.println("::notice title=INTERCEPTOR_DIAG::" + report);
	}

}
