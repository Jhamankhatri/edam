package com.edam.testsupport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Guards the packaging contract that makes the JSP UI possible at all.
 *
 * <p>Spring Boot only renders JSPs from an executable WAR, and only when Jasper, the JSTL
 * implementation and the JSP pages are all inside the archive. These assertions run against
 * the repackaged artifact after {@code mvn package} (the surefire binding precedes
 * {@code package}, so the test is skipped when no WAR is present; CI additionally runs a
 * full runtime smoke test of the WAR).
 */
class PackagedWarTest {

	private static final File WAR = new File("target/edam.war");

	@Test
	@DisplayName("the packaged artifact is an executable WAR containing JSPs, Jasper and JSTL")
	void warContainsJspRuntime() throws IOException {
		assumeThat(WAR).as("packaged WAR must exist; run 'mvn package' first").exists();

		try (ZipFile war = new ZipFile(WAR)) {
			List<String> names = Collections.list(war.entries()).stream().map(ZipEntry::getName).toList();

			assertThat(names).as("JSP views").anyMatch(name -> name.startsWith("WEB-INF/jsp/") && name.endsWith(".jsp"));
			assertThat(names).as("Jasper").anyMatch(name -> name.contains("tomcat-embed-jasper"));
			assertThat(names).as("JSTL API").anyMatch(name -> name.contains("jakarta.servlet.jsp.jstl-api"));
			assertThat(names).as("JSTL implementation").anyMatch(name -> name.contains("/jakarta.servlet.jsp.jstl-"));
			assertThat(names).as("Flyway PostgreSQL support").anyMatch(name -> name.contains("flyway-database-postgresql"));
			assertThat(names).as("PostgreSQL driver").anyMatch(name -> name.contains("postgresql-"));
			assertThat(names).as("WAR launcher").anyMatch(name -> name.startsWith("org/springframework/boot/loader/launch/WarLauncher"));
			assertThat(names).as("embedded Tomcat must be present for java -jar").anyMatch(name -> name.contains("tomcat-embed-core"));
		}
	}

	@Test
	@DisplayName("the packaged WAR never contains a test-scoped dependency")
	void warExcludesTestDependencies() throws IOException {
		assumeThat(WAR).exists();

		try (ZipFile war = new ZipFile(WAR)) {
			List<String> libraries = Collections.list(war.entries())
				.stream()
				.map(ZipEntry::getName)
				.filter(name -> name.startsWith("WEB-INF/lib") && name.endsWith(".jar"))
				.toList();

			assertThat(libraries).isNotEmpty()
				.noneMatch(name -> name.contains("testcontainers"))
				.noneMatch(name -> name.contains("junit"))
				.noneMatch(name -> name.contains("mockito"))
				.noneMatch(name -> name.contains("assertj"));
		}
	}

	@Test
	@DisplayName("application.yml is filtered so the build version is resolvable")
	void applicationPropertiesAreFiltered() throws IOException {
		assumeThat(WAR).exists();

		try (ZipFile war = new ZipFile(WAR)) {
			ZipEntry entry = war.getEntry("WEB-INF/classes/application.yml");
			assertThat(entry).as("application.yml must be packaged").isNotNull();
			try (InputStream input = war.getInputStream(entry)) {
				String yml = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
				assertThat(yml).doesNotContain("@project.version@").contains("version: '0.1.0-SNAPSHOT'");
			}
		}
	}

}
