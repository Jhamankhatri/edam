package com.edam.web;

import java.util.Arrays;

import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Temporary diagnostic: reports which interceptors the MVC infrastructure actually applies and
 * what a view-controller dispatch produces. Deleted once the interceptor contract is green.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InterceptorDiagnosticsTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void dumpDispatcherState() throws Exception {
		StringBuilder report = new StringBuilder();
		report.append("interceptors=").append(Arrays.toString(this.handlerMapping.getInterceptors())).append(" | ");
		Object chain = this.handlerMapping.getHandler(new org.springframework.mock.web.MockHttpServletRequest("GET", "/login"));
		report.append("loginChain=").append(chain);
		if (chain instanceof HandlerExecutionChain executionChain) {
			report.append(" chainInterceptors=").append(Arrays.toString(executionChain.getInterceptors()));
		}
		report.append(" | ");
		MvcResult result = this.mockMvc.perform(get("/login")).andReturn();
		Object handler = result.getHandler();
		report.append("resultHandler=").append(handler);
		report.append(" | view=").append((result.getModelAndView() != null) ? result.getModelAndView().getViewName() : null);
		report.append(" | model=").append((result.getModelAndView() != null) ? result.getModelAndView().getModelMap() : null);
		report.append(" | flash=").append(result.getFlashMap());
		throw new AssertionError(report.toString());
	}

}
