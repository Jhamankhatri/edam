package com.edam.web;

import java.util.Map;
import java.util.Set;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security and API-contract behaviour exercised through the MVC stack with real
 * authentication objects.
 *
 * <p>JSP bodies are intentionally not asserted here: Spring Boot cannot render JSP views
 * inside an embedded test container. View rendering is verified against the packaged
 * executable WAR, which is the same artifact that is deployed (see docs/VERIFICATION.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebLayerSecurityTest extends AbstractPostgreSqlIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	private static EdamPrincipal principalWith(String... permissions) {
		return new EdamPrincipal("emp-1", "admin", "Administrator", Map.of("demo-bank", Set.of("ADMINISTRATOR")),
				Set.of(permissions));
	}

	@Test
	@DisplayName("an anonymous browser request is redirected to the login page")
	void anonymousIsRedirectedToLogin() throws Exception {
		this.mockMvc.perform(get("/dashboard"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("http://localhost/login"));
	}

	@Test
	@DisplayName("an anonymous API request receives the JSON 401 envelope, never a redirect")
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
		this.mockMvc
			.perform(get("/api/v1/system/info").with(user(principalWith(Permissions.REPORTING_VIEW)))
				.accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.application").value("edam"))
			.andExpect(jsonPath("$.data.status").value("UP"))
			.andExpect(jsonPath("$.correlationId").isNotEmpty())
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("the identity endpoint exposes institution membership as a set")
	void identityExposesInstitutionSet() throws Exception {
		EdamPrincipal multiTenant = new EdamPrincipal("emp-1", "admin", "Administrator",
				Map.of("demo-bank", Set.of("ADMINISTRATOR"), "albaraka", Set.of("ADMINISTRATOR")), Set.of());

		this.mockMvc.perform(get("/api/v1/dev/me").with(user(multiTenant)).accept(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.institutions.length()").value(2))
			.andExpect(jsonPath("$.data.rolesByInstitution").isMap());
	}

	@Test
	@DisplayName("security headers are applied to HTML responses")
	void securityHeaders() throws Exception {
		this.mockMvc.perform(get("/login"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Frame-Options", "DENY"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")));
	}

	@Test
	@DisplayName("a caller-supplied correlation id is echoed verbatim")
	void correlationIdEchoed() throws Exception {
		this.mockMvc.perform(get("/login").header("X-Correlation-Id", "11111111-2222-3333-4444-555555555555"))
			.andExpect(header().string("X-Correlation-Id", "11111111-2222-3333-4444-555555555555"));
	}

	@Test
	@DisplayName("a malicious correlation id is replaced, never reflected into headers or logs")
	void correlationIdIsSanitized() throws Exception {
		MvcResult result = this.mockMvc
			.perform(get("/login").header("X-Correlation-Id", "not-a-uuid<script>alert(1)</script>"))
			.andExpect(status().isOk())
			.andReturn();

		String header = result.getResponse().getHeader("X-Correlation-Id");
		assertThat(header).hasSize(36).doesNotContain("<script>");
	}

	@Test
	@DisplayName("the error dispatch target is reachable, so failures render instead of redirecting")
	void errorEndpointIsReachable() throws Exception {
		this.mockMvc.perform(get("/error").with(user(principalWith())))
			.andExpect(status().is5xxServerError())
			.andExpect(header().doesNotExist("Location"));
	}

	@Test
	@DisplayName("CSRF protection is enabled for state-changing browser requests")
	void csrfIsEnforced() throws Exception {
		this.mockMvc.perform(post("/logout").with(user(principalWith())))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("the access-denied page is publicly reachable so a 403 can be explained")
	void accessDeniedPageIsPublic() throws Exception {
		this.mockMvc.perform(get("/access-denied")).andExpect(status().isOk());
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
