package com.edam.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanupMdc() {
        MDC.clear();
    }

    @Test
    void preservesValidIncomingCorrelationIdAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        request.addHeader(CorrelationIdProvider.CORRELATION_ID_HEADER, "corr-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, res) -> {
            assertThat(MDC.get(CorrelationIdProvider.MDC_CORRELATION_ID)).isEqualTo("corr-12345");
            assertThat(MDC.get(CorrelationIdProvider.MDC_REQUEST_METHOD)).isEqualTo("GET");
            assertThat(MDC.get(CorrelationIdProvider.MDC_REQUEST_PATH)).isEqualTo("/api/v1/health");
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdProvider.CORRELATION_ID_HEADER)).isEqualTo("corr-12345");
        assertThat(MDC.get(CorrelationIdProvider.MDC_CORRELATION_ID)).isNull();
    }

    @Test
    void replacesInvalidIncomingCorrelationIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.addHeader(CorrelationIdProvider.CORRELATION_ID_HEADER, "bad\r\ninjection");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            String active = MDC.get(CorrelationIdProvider.MDC_CORRELATION_ID);
            assertThat(active).isNotBlank().doesNotContain("bad");
        });

        String header = response.getHeader(CorrelationIdProvider.CORRELATION_ID_HEADER);
        assertThat(header).isNotBlank().matches("^[0-9a-fA-F-]{36}$");
        assertThat(MDC.get(CorrelationIdProvider.MDC_CORRELATION_ID)).isNull();
    }
}
