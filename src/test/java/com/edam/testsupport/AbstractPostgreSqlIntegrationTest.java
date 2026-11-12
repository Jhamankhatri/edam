package com.edam.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for tests that need the real PostgreSQL schema.
 *
 * <p>Supplies datasource coordinates from a shared container. Every subclass therefore runs
 * against the same Flyway-managed schema, which keeps migration times low while still
 * exercising the production dialect.
 */
public abstract class AbstractPostgreSqlIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		var container = PostgreSqlTestContainer.getInstance();
		registry.add("spring.datasource.url", container::getJdbcUrl);
		registry.add("spring.datasource.username", container::getUsername);
		registry.add("spring.datasource.password", container::getPassword);
	}

}
