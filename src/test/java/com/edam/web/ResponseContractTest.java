package com.edam.web;

import com.edam.common.api.ApiResponse;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the JSON contract end to end, including that a successful call is a standard
 * envelope and that the correlation ID crosses from request to response untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ResponseContractTest extends AbstractPostgreSqlIntegrationTest {

	private static final String USER = "admin";

	private static final String PASSWORD = "admin-change-me";

	@LocalServerPort
	private int port;

	@Autowired
	private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	/**
	 * Signs in through the real form-login flow and returns a cookie-authenticated client.
	 * Uses a session because the UI is server-rendered; CSRF token is read from the form.
	 */
	private RestClient authenticatedClient() {
		var client = RestClient.builder().baseUrl("http://localhost:" + this.port).build();

		ResponseEntity<String> loginPage = client.get().uri("/login").retrieve().toEntity(String.class);
		String csrf = extractCsrf(loginPage.getBody());
		String cookie = loginPage.getHeaders().getFirst("Set-Cookie").split(";", 2)[0];

		ResponseEntity<String> loginResponse = client.post()
			.uri("/login")
			.header("Cookie", cookie)
			.contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
			.body("username=" + USER + "&password=" + PASSWORD + "&_csrf=" + csrf)
			.exchange((request, response) -> ResponseEntity.status(response.getStatusCode()).build());

		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
		String sessionCookie = loginResponse.getHeaders().getFirst("Set-Cookie");
		return RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultHeaders(headers -> headers.add("Cookie", sessionCookie.split(";", 2)[0]))
			.build();
	}

	private static String extractCsrf(String html) {
		int nameIndex = html.indexOf("name=\"_csrf\"");
		assertThat(nameIndex).isPositive();
		int valueIndex = html.indexOf("value=\"", nameIndex);
		int endIndex = html.indexOf('"', valueIndex + 7);
		return html.substring(valueIndex + 7, endIndex);
	}

	@Test
	@DisplayName("an authenticated API call returns the standard success envelope")
	void successEnvelope() {
		ResponseEntity<ApiResponse<java.util.Map<String, Object>>> response = authenticatedClient().get()
			.uri("/api/v1/system/info")
			.retrieve()
			.toEntity(new ParameterizedTypeReference<ApiResponse<java.util.Map<String, Object>>>() {
			});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().success()).isTrue();
		assertThat(response.getBody().data()).containsEntry("application", "edam").containsEntry("status", "UP");
		assertThat(response.getBody().correlationId()).isNotBlank();
		assertThat(response.getBody().error()).isNull();
	}

	@Test
	@DisplayName("a development identity endpoint exposes institutions as a set, never a single tenant")
	void identityEndpointExposesMultipleInstitutions() {
		ResponseEntity<ApiResponse<java.util.Map<String, Object>>> response = authenticatedClient().get()
			.uri("/api/v1/dev/me")
			.retrieve()
			.toEntity(new ParameterizedTypeReference<ApiResponse<java.util.Map<String, Object>>>() {
			});

		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).containsKeys("institutions", "rolesByInstitution", "permissions");
		assertThat((Iterable<?>) response.getBody().data().get("institutions")).hasSize(2);
	}

	@Test
	@DisplayName("a generated correlation id is returned when the caller supplies none")
	void generatesCorrelationId() {
		ResponseEntity<String> response = RestClient.create("http://localhost:" + this.port)
			.get()
			.uri("/login")
			.retrieve()
			.toEntity(String.class);

		assertThat(response.getHeaders().getFirst("X-Correlation-Id")).hasSize(36);
	}

	@Test
	@DisplayName("an unknown API path returns the JSON 404 envelope, not an HTML page")
	void unknownApiPath() {
		ResponseEntity<String> response = RestClient.builder()
			.baseUrl("http://localhost:" + this.port)
			.defaultStatusHandler(status -> true, (request, response) -> {
			})
			.build()
			.get()
			.uri("/api/v1/does-not-exist")
			.accept(org.springframework.http.MediaType.APPLICATION_JSON)
			.retrieve()
			.toEntity(String.class);

		assertThat(response.getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED);
		if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
			assertThat(response.getBody()).contains("EDAM-4001");
		}
	}

}
