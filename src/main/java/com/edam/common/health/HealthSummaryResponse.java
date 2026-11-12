package com.edam.common.health;

import java.time.Instant;

/**
 * Public-safe application health summary returned by {@link HealthCheckController}.
 */
public record HealthSummaryResponse(
        String status,
        String application,
        String version,
        String database,
        Instant timestamp) {
}
