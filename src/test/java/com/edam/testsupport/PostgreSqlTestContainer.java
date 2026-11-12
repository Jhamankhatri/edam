package com.edam.testsupport;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared PostgreSQL container for integration tests.
 *
 * <p>Tests run against real PostgreSQL rather than H2 so that Flyway migrations, partial
 * indexes, regular-expression CHECK constraints, {@code TIMESTAMPTZ} and identity columns
 * are validated exactly as they will run in production. An H2 approximation would hide
 * precisely the dialect problems this foundation must catch.
 *
 * <p>The container is a singleton started once per JVM and never stopped explicitly;
 * Testcontainers' Ryuk reaper removes it when the JVM exits.
 */
public final class PostgreSqlTestContainer {

	private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18-alpine");

	private static final PostgreSQLContainer INSTANCE = new PostgreSQLContainer(IMAGE)
		.withDatabaseName("edam_test")
		.withUsername("edam_test")
		.withPassword("edam_test_only");

	private PostgreSqlTestContainer() {
	}

	public static PostgreSQLContainer getInstance() {
		if (!INSTANCE.isRunning()) {
			INSTANCE.start();
		}
		return INSTANCE;
	}

}
