package com.edam.testsupport;

import java.util.Collections;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Test interceptor that records the model a view would receive.
 *
 * <p>Spring Boot cannot render JSPs inside its test container: the response body of a
 * JSP-backed page is empty in {@code MockMvc}. The model contents are still fully computed by
 * the production {@code HandlerInterceptor} chain, because
 * {@code DispatcherServlet#doDispatch} applies {@code postHandle} before view rendering. This
 * interceptor captures that model so a test can assert on it without a servlet container.
 *
 * <p>It is registered by {@code ViewModelContractTest} only, with the highest precedence, and
 * therefore sees the model exactly as the JSP would.
 */
public final class ModelCapturingInterceptor implements HandlerInterceptor {

	private volatile Map<String, Object> model = Collections.emptyMap();

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			ModelAndView modelAndView) {
		this.model = (modelAndView != null) ? Map.copyOf(modelAndView.getModelMap()) : Collections.emptyMap();
	}

	public Map<String, Object> model() {
		return this.model;
	}

}
