package com.edam.common.health;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Verifies PostgreSQL connectivity using a lightweight read-only probe without exposing
 * database hostnames, credentials, or exception internals in health responses.
 */
@Component("edamDatabase")
public class EdamDatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(EdamDatabaseHealthIndicator.class);
    private static final String VALIDATION_QUERY = "SELECT 1";

    private final DataSource dataSource;

    public EdamDatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(VALIDATION_QUERY);
                ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return Health.up()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("reachable", true)
                        .build();
            }
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("reachable", false)
                    .build();
        } catch (SQLException ex) {
            log.error("PostgreSQL health probe failed", ex);
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("reachable", false)
                    .build();
        }
    }
}
