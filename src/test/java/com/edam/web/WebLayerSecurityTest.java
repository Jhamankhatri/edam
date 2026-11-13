package com.edam.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security and API-contract behaviour exercised through the MVC stack with real authentication objects.
 *
 * <p>Scope note: EDAM is now backend-only, so these tests assert the JSON contract and the security
 * spine rather than rendered pages. Anything that used to assert a JSP body is gone with the view layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebLayerSecurityTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private static EdamPrincipal principalWith(String... permissions) {
		return new EdamPrincipal(UUID.randomUUID(), "EMP-1", "admin", "Administrator", Set.of("SYSTEM_ADMINISTRATOR"),
				Set.of(permissions), Set.of(new DataScope(DataScope.Type.ALL, null)));
	}

	@Test
	@DisplayName("an anonymous request to a protected API receives the JSON 401 envelope")
	void anonymousApiGetsJson401() throws Exception {
		this.mockMvc.perform(get("/api/v1/system/info").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isUnauthorized())
			.andExpect(header().exists("X-Correlation-Id"))
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("EDAM-2001"))
			.andExpect(jsonPath("$.correlationId").isNotEmpty())
			.andExpect(jsonPath("$.path").value("/api/v1/system/info"));
	}

	@Test
	@DisplayName("the health probe is public and reveals nothing about the database")
	void healthProbeIsPublic() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("\"status\":\"UP\"")
			.doesNotContain("\"db\"")
			.doesNotContain("postgres");
	}

	@Test
	@DisplayName("an authenticated API call returns the standard success envelope")
	void authenticatedApiSuccess() throws Exception {
		this.mockMvc.perform(get("/api/v1/system/info").with(user(principalWith(Permissions.REPORT_VIEW)))
			.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.application").value("edam"))
			.andExpect(jsonPath("$.data.status").value("UP"))
			.andExpect(jsonPath("$.correlationId").isNotEmpty())
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("a caller-supplied correlation id is echoed verbatim")
	void correlationIdEchoed() throws Exception {
		this.mockMvc.perform(get("/api/v1/system/info").header("X-Correlation-Id", "11111111-2222-3333-4444-555555555555")
			.with(user(principalWith(Permissions.REPORT_VIEW))))
			.andExpect(header().string("X-Correlation-Id", "11111111-2222-3333-4444-555555555555"));
	}

	@Test
	@DisplayName("a malicious correlation id is replaced, never reflected into headers or logs")
	void correlationIdIsSanitized() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/api/v1/system/info").header("X-Correlation-Id", "not-a-uuid<script>alert(1)</script>")
				.with(user(principalWith(Permissions.REPORT_VIEW))))
			.andExpect(status().isOk())
			.andReturn();

		String header = result.getResponse().getHeader("X-Correlation-Id");
		assertThat(header).hasSize(36).doesNotContain("<script>");
	}

	@Test
	@DisplayName("the container error dispatch answers in the JSON envelope, not a rendered page")
	void errorEndpointIsJson() throws Exception {
		// The container's error path is what a failure before any controller reaches; it must produce the
		// same envelope as every other error, never a rendered page and never a redirect.
		this.mockMvc.perform(get("/error").with(user(principalWith())))
			.andExpect(status().is5xxServerError())
			.andExpect(header().doesNotExist("Location"))
			.andExpect(jsonPath("$.edamEnvelope.success").value(false))
			.andExpect(jsonPath("$.edamEnvelope.error.code").value("EDAM-9001"));
	}

	@Test
	@DisplayName("CSRF is enforced: a state-changing request without a token is rejected")
	void csrfIsEnforced() throws Exception {
		// CSRF is enabled, not disabled, for the browser client. The endpoint under test is the logout
		// API because it is public and mutating: if CSRF were permissive, an anonymous cross-site POST
		// would be processed. 403 is the expected outcome; a 200 would mean the request was allowed
		// through, which is the regression this test exists to catch.
		MvcResult result = this.mockMvc.perform(post("/api/v1/auth/logout")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(403);
	}

	@Test
	@DisplayName("the CSRF cookie is readable by JavaScript, which is what makes the API usable from React")
	void csrfCookieIsReadableByJavaScript() throws Exception {
		// Spring's default CSRF repository marks XSRF-TOKEN HttpOnly, which silently breaks every
		// mutating request from a browser client. This asserts the configured repository writes the
		// cookie without HttpOnly, so a React app can read it and echo it in X-XSRF-TOKEN.
		// The status is asserted first on purpose. Without it, a 500 from the controller and a working
		// endpoint that merely omitted the cookie produce the same message ("no Set-Cookie"), and the
		// two have completely different causes.
		MvcResult result = this.mockMvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();

		List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
		assertThat(setCookies).as("a CSRF token cookie must be issued").anyMatch(cookie -> cookie.startsWith("XSRF-TOKEN="));
		assertThat(setCookies.stream().filter(cookie -> cookie.startsWith("XSRF-TOKEN=")).findFirst().orElse(""))
			.as("the CSRF cookie must not be HttpOnly")
			.doesNotContain("HttpOnly");
	}

	@Test
	@DisplayName("the installation status is readable before authentication")
	void installationStatusIsPublic() throws Exception {
		this.mockMvc.perform(get("/api/v1/auth/installation").accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.bootstrapped").isBoolean());
	}

	@Test
	@DisplayName("an unknown API path fails without leaking internals")
	void unknownApiPathIsSafe() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/api/v1/does-not-exist").with(user(principalWith())).accept(MediaType.APPLICATION_JSON))
			.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(result.getResponse().getStatus()).isBetween(400, 599);
		assertThat(body).doesNotContain("Exception").doesNotContain("java.").doesNotContain("org.springframework");
	}

}
