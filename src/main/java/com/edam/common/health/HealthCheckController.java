package com.edam.common.health;

import com.edam.common.api.ApiResponse;
import com.edam.common.logging.CorrelationIdProvider;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Standardized application health endpoint complementing Spring Boot Actuator {@code /actuator/health}.
 * Never exposes JDBC URLs, credentials, or internal environment topology.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthCheckController {

    private final EdamDatabaseHealthIndicator databaseHealthIndicator;
    private final String applicationName;
    private final String applicationVersion;

    public HealthCheckController(
            EdamDatabaseHealthIndicator databaseHealthIndicator,
            @Value("${spring.application.name:edam}") String applicationName,
            ObjectProvider<BuildProperties> buildPropertiesProvider) {
        this.databaseHealthIndicator = databaseHealthIndicator;
        this.applicationName = applicationName;
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        this.applicationVersion = buildProperties != null ? buildProperties.getVersion() : "0.1.0-SNAPSHOT";
    }

    @GetMapping
    public ResponseEntity<ApiResponse<HealthSummaryResponse>> checkHealth() {
        Health dbHealth = databaseHealthIndicator.health();
        boolean dbUp = dbHealth != null && Status.UP.equals(dbHealth.getStatus());

        HttpStatus httpStatus = dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        String overallStatus = dbUp ? "UP" : "DEGRADED";
        String dbStatus = dbUp ? "UP" : "DOWN";
        String correlationId = CorrelationIdProvider.currentOrNew();

        HealthSummaryResponse payload = new HealthSummaryResponse(
                overallStatus,
                applicationName,
                applicationVersion,
                dbStatus,
                Instant.now());

        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.of(dbUp, httpStatus.value(), correlationId, "Health status evaluated", payload));
    }
}
