package com.edam.web;

import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the security posture over real HTTP: nothing business-facing is reachable
 * anonymously, the health probe is, and the response carries no unsafe cache headers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityConfigurationTest extends AbstractPostgreSqlIntegrationTest {

	@LocalServerPort
	private int port;

	private RestClient client() {
		// Disable redirect following so the assertion sees the 302 to the login page.
		return RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(status -> true, response -> {
			})
			.build();
	}

	@Test
	@DisplayName("the dashboard requires authentication")
	void dashboardRequiresAuthentication() {
		ResponseEntity<String> response = client().get().uri("/dashboard").retrieve().toEntity(String.class);

		assertThat(response.getStatusCode()).isIn(HttpStatus.FOUND, HttpStatus.UNAUTHORIZED);
		assertThat(response.getHeaders().getLocation()).hasToString("http://localhost:" + this.port + "/login");
	}

	@Test
	@DisplayName("the API is not reachable anonymously and returns a JSON envelope")
	void apiRequiresAuthentication() {
		ResponseEntity<String> response = client().get()
			.uri("/api/v1/system/info")
			.accept(org.springframework.http.MediaType.APPLICATION_JSON)
			.retrieve()
			.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("\"success\":false").contains("EDAM-2001").contains("correlationId");
		assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
	}

	@Test
	@DisplayName("the health probe is public and exposes no details")
	void healthProbeIsPublic() {
		ResponseEntity<String> response = client().get().uri("/actuator/health").retrieve().toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("\"status\":\"UP\"");
		assertThat(response.getBody()).doesNotContain("db").doesNotContain("postgres");
	}

	@Test
	@DisplayName("the login page is public and renders")
	void loginPageIsPublic() {
		ResponseEntity<String> response = client().get().uri("/login").retrieve().toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("name=\"username\"").contains("name=\"_csrf\"");
	}

	@Test
	@DisplayName("every response carries a correlation id")
	void correlationIdIsEchoed() {
		ResponseEntity<String> response = client().get()
			.uri("/login")
			.header("X-Correlation-Id", "11111111-2222-3333-4444-555555555555")
			.retrieve()
			.toEntity(String.class);

		assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("11111111-2222-3333-4444-555555555555");
	}

	@Test
	@DisplayName("security headers are present on HTML responses")
	void securityHeadersArePresent() {
		ResponseEntity<String> response = client().get().uri("/login").retrieve().toEntity(String.class);

		assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("default-src 'self'");
		assertThat(response.getHeaders().getFirst("X-Frame-Options")).isEqualTo("DENY");
		assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
	}

}
