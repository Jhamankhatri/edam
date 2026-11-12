package com.edam.common.health;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.security.CurrentUserContext;
import com.edam.institution.api.InstitutionBrandingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthCheckController.class)
class HealthCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EdamDatabaseHealthIndicator databaseHealthIndicator;

    @MockitoBean
    private InstitutionBrandingService brandingService;

    @MockitoBean
    private CurrentUserContext currentUserContext;

    @Test
    void returnsOkWhenDatabaseHealthIsUp() throws Exception {
        when(databaseHealthIndicator.health()).thenReturn(Health.up().build());

        mockMvc.perform(get("/api/v1/health")
                        .header(CorrelationIdProvider.CORRELATION_ID_HEADER, "health-corr-1"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdProvider.CORRELATION_ID_HEADER, "health-corr-1"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.correlationId").value("health-corr-1"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database").value("UP"));
    }

    @Test
    void returnsServiceUnavailableWhenDatabaseHealthIsDown() throws Exception {
        when(databaseHealthIndicator.health()).thenReturn(Health.down().build());

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.data.status").value("DEGRADED"))
                .andExpect(jsonPath("$.data.database").value("DOWN"));
    }
}
