package com.edam.common.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

	private final CorrelationIdFilter filter = new CorrelationIdFilter();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	@Test
	@DisplayName("echoes the inbound correlation id and exposes it on the response")
	void propagatesInboundId() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard");
		request.addHeader(CorrelationId.HEADER, "550e8400-e29b-41d4-a716-446655440000");
		MockHttpServletResponse response = new MockHttpServletResponse();
		String[] mdcDuringChain = new String[1];
		FilterChain chain = (req, res) -> mdcDuringChain[0] = MDC.get(CorrelationId.MDC_KEY);

		this.filter.doFilter(request, response, chain);

		assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
		assertThat(mdcDuringChain[0]).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
	}

	@Test
	@DisplayName("generates an id when the caller supplies none")
	void generatesWhenAbsent() throws ServletException, IOException {
		MockHttpServletResponse response = new MockHttpServletResponse();

		this.filter.doFilter(new MockHttpServletRequest("GET", "/dashboard"), response, (req, res) -> {
		});

		assertThat(response.getHeader(CorrelationId.HEADER)).hasSize(36);
	}

	@Test
	@DisplayName("clears the MDC after the request so pooled threads cannot leak context")
	void clearsMdcAfterRequest() throws ServletException, IOException {
		this.filter.doFilter(new MockHttpServletRequest("GET", "/dashboard"), new MockHttpServletResponse(),
				(req, res) -> {
				});

		assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
	}

	@Test
	@DisplayName("clears the MDC even when the chain throws")
	void clearsMdcOnFailure() {
		MockHttpServletResponse response = new MockHttpServletResponse();

		try {
			this.filter.doFilter(new MockHttpServletRequest("GET", "/dashboard"), response, (req, res) -> {
				throw new IllegalStateException("boom");
			});
		}
		catch (Exception ex) {
			// expected
		}

		assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
	}

}
