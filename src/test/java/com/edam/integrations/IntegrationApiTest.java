package com.edam.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import com.edam.common.security.DataScope;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.Permissions;
import com.edam.integrations.crypto.IntegrationSecretCrypto;
import com.edam.integrations.model.IntegrationConfig;
import com.edam.integrations.model.IntegrationConfig.AuthType;
import com.edam.integrations.model.IntegrationExecution;
import com.edam.integrations.repository.IntegrationConfigRepository;
import com.edam.integrations.repository.IntegrationExecutionRepository;
import com.edam.integrations.service.IntegrationService;
import com.edam.integrations.dto.IntegrationDtos.ExecuteRequest;
import com.edam.integrations.service.IntegrationExecutionWorker;
import com.edam.integrations.service.adapters.*;
import com.edam.testsupport.AbstractPostgreSqlIntegrationTest;
import com.edam.usermanagement.model.User;
import com.edam.usermanagement.repository.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
	"EDAM_INTEGRATION_MASTER_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
	"edam.integrations.worker.enabled=true",
	"edam.integrations.worker.delay-ms=999999999"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationApiTest.StubAdapterConfig.class)
@DisplayName("Integrations: configuration lifecycle, secret encryption, connection testing, executions")
class IntegrationApiTest extends AbstractPostgreSqlIntegrationTest {

	private static final String BASE = "/api/v1/integrations";

	@Autowired private MockMvc mockMvc;
	@Autowired private JsonMapper json;
	@Autowired private UserRepository users;
	@Autowired private IntegrationConfigRepository configs;
	@Autowired private IntegrationExecutionRepository executions;
	@Autowired private IntegrationSecretCrypto crypto;
	@Autowired private IntegrationExecutionWorker worker;
	@Autowired private IntegrationService service;
	@Autowired private JdbcTemplate jdbc;

	private User admin;

	@BeforeEach
	void setup() {
		this.admin = users.findAll().stream().findFirst().orElseGet(() ->
			com.edam.testsupport.RequestFixtures.user(this.users, null));
		// Clean up rows created by this test class. audit_events is append-only
		// (trigger-enforced), so do NOT attempt to delete audit rows; filter by
		// the unique test-prefix code in assertions instead.
		jdbc.execute("DELETE FROM integration_executions WHERE integration_config_id IN (SELECT id FROM integration_configs WHERE integration_code LIKE 's-%')");
		jdbc.execute("DELETE FROM integration_configs WHERE integration_code LIKE 's-%'");
	}

	private EdamPrincipal adminUser() {
		return new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.INTEGRATION_VIEW, Permissions.INTEGRATION_MANAGE,
					Permissions.INTEGRATION_EXECUTE, Permissions.INTEGRATION_TEST),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
	}
	private EdamPrincipal viewer() {
		return new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.INTEGRATION_VIEW),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
	}
	private EdamPrincipal unauthed() {
		return new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of(), Set.of(), Set.of());
	}

	@Test @DisplayName("catalogue returns registered provider types (stub is registered via @TestConfiguration)")
	void providerCatalogue() throws Exception {
		mockMvc.perform(get(BASE + "/providers").with(user(adminUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data[?(@.type=='TEST_STUB')]").exists());
	}

	@Test @DisplayName("crypto is fail-closed: a short/blank key is rejected")
	void cryptoFailClosed() {
		Assertions.assertThrows(IllegalStateException.class,
			() -> new IntegrationSecretCrypto("abcd"));
		Assertions.assertDoesNotThrow(() -> new IntegrationSecretCrypto(
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
	}

	@Test @DisplayName("create + get + list + activate/deactivate/archive lifecycle; secret never returned")
	void lifecycleSecretNotExposed() throws Exception {
		String id = createStub("https://example.com/", "super-secret-token");
		String resp = mockMvc.perform(get(BASE + "/" + id).with(user(adminUser())))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		JsonNode detail = json.readTree(resp).path("data");
		assertThat(detail.path("hasSecret").asBoolean()).isTrue();
		assertThat(detail.has("secret")).isFalse();
		String cipher = jdbc.queryForObject(
			"select encrypted_secret from integration_configs where id = ?", String.class, UUID.fromString(id));
		assertThat(cipher).doesNotContain("super-secret-token");
		assertThat(crypto.decrypt(cipher)).isEqualTo("super-secret-token");

		mockMvc.perform(get(BASE).with(user(viewer())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].hasSecret").value(true));

		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(true));
		mockMvc.perform(post(BASE + "/" + id + "/deactivate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.active").value(false));
		mockMvc.perform(delete(BASE + "/" + id).with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.archived").value(true));

		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().is4xxClientError());
		mockMvc.perform(get(BASE).with(user(unauthed())))
			.andExpect(status().isForbidden());
	}

	@Test @DisplayName("update without secret preserves existing value; CLEAR removes it when auth=NONE")
	void secretPreservationAndClear() throws Exception {
		String id = createStub("https://example.com/", "original-secret");
		String before = jdbc.queryForObject(
			"select encrypted_secret from integration_configs where id = ?", String.class, UUID.fromString(id));
		mockMvc.perform(put(BASE + "/" + id).with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("name", "Renamed"))))
			.andExpect(status().isOk());
		String after = jdbc.queryForObject(
			"select encrypted_secret from integration_configs where id = ?", String.class, UUID.fromString(id));
		assertThat(after).isEqualTo(before);

		mockMvc.perform(put(BASE + "/" + id).with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("secret", "new-secret", "secretAction", "SET"))))
			.andExpect(status().isOk());
		String rotated = jdbc.queryForObject(
			"select encrypted_secret from integration_configs where id = ?", String.class, UUID.fromString(id));
		assertThat(rotated).isNotEqualTo(before);
		assertThat(crypto.decrypt(rotated)).isEqualTo("new-secret");

		mockMvc.perform(put(BASE + "/" + id).with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("secretAction", "CLEAR", "authMode", "NONE"))))
			.andExpect(status().isOk());
		String cleared = jdbc.queryForObject(
			"select encrypted_secret from integration_configs where id = ?", String.class, UUID.fromString(id));
		assertThat(cleared).isNull();
	}

	@Test @DisplayName("connection test against a local HTTP server (fails because adapter requires https)")
	void httpsRequiredEnforced() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/health", ex -> {
			byte[] body = "OK".getBytes(StandardCharsets.UTF_8);
			ex.sendResponseHeaders(200, body.length);
			ex.getResponseBody().write(body); ex.getResponseBody().close();
		});
		server.start();
		try {
			int port = server.getAddress().getPort();
			mockMvc.perform(post(BASE).with(csrf()).with(user(adminUser()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(json.writeValueAsString(Map.of(
						"code", "stub-plain-" + System.nanoTime(),
						"name", "Plain", "providerType", "TEST_STUB",
						"endpointUrl", "http://127.0.0.1:" + port + "/health",
						"authMode", "NONE"))))
				.andExpect(status().is4xxClientError());
		} finally {
			server.stop(0);
		}
	}

	@Test @DisplayName("connection test on non-routable host returns FAILED (safe failure)")
	void connectionTestFailureState() throws Exception {
		String bad = createStub("https://example.com/", null);
		mockMvc.perform(post(BASE + "/" + bad + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		mockMvc.perform(post(BASE + "/" + bad + "/test").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.success").value(false))
			.andExpect(jsonPath("$.data.resultingState").value("FAILED"));
	}

	@Test @DisplayName("idempotent submissions collapse to a single execution")
	void idempotencyReplays() throws Exception {
		String id = createStub("https://example.com/", null);
		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		String body = json.writeValueAsString(Map.of("operation", "ping", "idempotencyKey", "abc-123"));
		String r1 = mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String eid = json.readTree(r1).path("data").path("id").asText();
		String r2 = mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(json.readTree(r2).path("data").path("id").asText()).isEqualTo(eid);
	}

	@Test @DisplayName("submissions without idempotency key enqueue separate executions (not collapsed)")
	void nullIdempotencyKeysDoNotCollide() throws Exception {
		String id = createStub("https://example.com/", null);
		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		String body = json.writeValueAsString(Map.of("operation", "ping"));
		String r1 = mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String r2 = mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String id1 = json.readTree(r1).path("data").path("id").asText();
		String id2 = json.readTree(r2).path("data").path("id").asText();
		assertThat(id1).isNotBlank();
		assertThat(id2).isNotBlank();
		assertThat(id1).isNotEqualTo(id2);
	}

	@Test @DisplayName("concurrent same-key submissions collapse to one execution (REQUIRES_NEW insert)")
	void concurrentSameKeyCollapses() throws Exception {
		String id = createStub("https://example.com/", null);
		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		String key = "concurrent-" + UUID.randomUUID();
		final UUID configId = UUID.fromString(id);
		final int threads = 8;
		java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
		java.util.List<java.util.concurrent.Future<UUID>> futures = new java.util.ArrayList<>();
		for (int i = 0; i < threads; i++) {
			futures.add(pool.submit(() -> {
				start.await();
				// Hit the service directly to avoid MockMvc/CSRF thread-safety noise; each
				// call opens its own transaction via Spring's proxy (same as the HTTP path).
				return service.submitExecution(adminUser(), configId,
					new ExecuteRequest("ping", key, Map.of())).id();
			}));
		}
		start.countDown();
		java.util.Set<UUID> returnedIds = new java.util.HashSet<>();
		java.util.List<Throwable> failures = new java.util.ArrayList<>();
		for (var f : futures) {
			try { returnedIds.add(f.get()); }
			catch (Exception ex) { failures.add(ex.getCause() != null ? ex.getCause() : ex); }
		}
		pool.shutdown();
		assertThat(failures).as("concurrent submit exceptions: %s", failures).isEmpty();
		// All threads must see the same winning execution id.
		assertThat(returnedIds).hasSize(1);
		long rows = jdbc.queryForObject(
			"select count(*) from integration_executions "
			+ "where integration_config_id = ?::uuid and operation_type = 'ping' and idempotency_key = ?",
			Long.class, configId, key);
		// Exactly one execution row for this (config, op, key) regardless of thread count.
		assertThat(rows).isEqualTo(1);
	}

	@Test @DisplayName("worker claim -> process transitions to RETRY_PENDING/FAILED, audit never contains secret")
	void workerClaimFailAndAuditRedaction() throws Exception {
		String uniqueSecret = "worker-secret-" + UUID.randomUUID();
		String id = createStub("https://example.com/", uniqueSecret);
		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		String r = mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("operation", "ping", "idempotencyKey", "worker-" + UUID.randomUUID()))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		UUID eid = UUID.fromString(json.readTree(r).path("data").path("id").asText());
		// sweep() claims ONE pending execution and calls processOne() on it via
		// REQUIRES_NEW; after sweep returns the row should be terminal/retrying.
		worker.sweep();
		IntegrationExecution done = executions.findById(eid).orElseThrow();
		assertThat(done.getStatus()).isIn(IntegrationExecution.ExecutionStatus.RETRY_PENDING,
			IntegrationExecution.ExecutionStatus.FAILED,
			IntegrationExecution.ExecutionStatus.RUNNING);
		// Drive it to completion explicitly if sweep left it in RUNNING (claim
		// committed before processOne got scheduled in this direct-call flow).
		if (done.getStatus() == IntegrationExecution.ExecutionStatus.RUNNING) {
			worker.processOne(eid);
			done = executions.findById(eid).orElseThrow();
			assertThat(done.getStatus()).isIn(IntegrationExecution.ExecutionStatus.RETRY_PENDING,
				IntegrationExecution.ExecutionStatus.FAILED);
		}
		Integer secretLeaks = jdbc.queryForObject(
			"select count(*) from audit_events where entity_type in ('IntegrationConfig','IntegrationExecution') "
			+ "and (new_values::text like ? or old_values::text like ?)",
			Integer.class, "%" + uniqueSecret + "%", "%" + uniqueSecret + "%");
		assertThat(secretLeaks).isEqualTo(0);
	}

	@Test @DisplayName("unsupported provider type is rejected at create")
	void unsupportedProviderRejected() throws Exception {
		mockMvc.perform(post(BASE).with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of(
					"code", "nope", "name", "nope",
					"providerType", "NOPE_PROVIDER",
					"authMode", "NONE"))))
			.andExpect(status().is4xxClientError());
	}

	@Test @DisplayName("deactivated integration blocks execution submission")
	void deactivatedBlocksExecution() throws Exception {
		String id = createStub("https://example.com/", null);
		mockMvc.perform(post(BASE + "/" + id + "/deactivate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("operation", "ping"))))
			.andExpect(status().is4xxClientError());
	}

	@Test @DisplayName("INTEGRATION_TEST and INTEGRATION_EXECUTE are enforced separately")
	void fineGrainedPermissionsEnforced() throws Exception {
		String id = createStub("https://example.com/", null);
		mockMvc.perform(post(BASE + "/" + id + "/activate").with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		// VIEW-only user may not test or execute.
		mockMvc.perform(post(BASE + "/" + id + "/test").with(csrf()).with(user(viewer())))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(viewer()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("operation", "ping"))))
			.andExpect(status().isForbidden());
		// TEST-only user may test but not execute.
		EdamPrincipal tester = new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.INTEGRATION_VIEW, Permissions.INTEGRATION_TEST),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
		mockMvc.perform(post(BASE + "/" + id + "/test").with(csrf()).with(user(tester)))
			.andExpect(status().isOk());
		mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(tester))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("operation", "ping"))))
			.andExpect(status().isForbidden());
		// EXECUTE-only user may execute (and retry) but not test.
		EdamPrincipal runner = new EdamPrincipal(this.admin.getId(), this.admin.getEmployeeCode(),
				this.admin.getUsername(), this.admin.displayName(), Set.of("ADMIN"),
				Set.of(Permissions.INTEGRATION_VIEW, Permissions.INTEGRATION_EXECUTE),
				Set.of(new DataScope(DataScope.Type.ALL, null)));
		mockMvc.perform(post(BASE + "/" + id + "/test").with(csrf()).with(user(runner)))
			.andExpect(status().isForbidden());
		String execResp = mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(runner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("operation", "ping"))))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String execId = json.readTree(execResp).path("data").path("id").asText();
		// TEST-only user cannot retry; EXECUTE user can.
		mockMvc.perform(post(BASE + "/executions/" + execId + "/retry").with(csrf()).with(user(tester))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden());
		mockMvc.perform(post(BASE + "/executions/" + execId + "/retry").with(csrf()).with(user(runner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk());
	}

	@Test @DisplayName("archived integration blocks both test and execution")
	void archivedBlocksTestAndExecute() throws Exception {
		String id = createStub("https://example.com/", null);
		mockMvc.perform(delete(BASE + "/" + id).with(csrf()).with(user(adminUser())))
			.andExpect(status().isOk());
		mockMvc.perform(post(BASE + "/" + id + "/test").with(csrf()).with(user(adminUser())))
			.andExpect(status().is4xxClientError());
		mockMvc.perform(post(BASE + "/" + id + "/executions").with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("operation", "ping"))))
			.andExpect(status().is4xxClientError());
	}

	private String createStub(String url, String secret) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("code", "s-" + UUID.randomUUID().toString().substring(0, 8));
		body.put("name", "stub");
		body.put("providerType", "TEST_STUB");
		body.put("endpointUrl", url);
		body.put("authMode", secret == null ? "NONE" : "BEARER");
		if (secret != null) { body.put("secret", secret); body.put("secretAction", "SET"); }
		String resp = mockMvc.perform(post(BASE).with(csrf()).with(user(adminUser()))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(body)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return json.readTree(resp).path("data").path("id").asText();
	}

	// ---- Test-only adapter registered via @TestConfiguration so the production catalogue has
	// ---- zero built-in providers (foundation only) while tests exercise the SPI end-to-end.
	@TestConfiguration
	static class StubAdapterConfig {
		@Bean
		IntegrationAdapter testStubAdapter() {
			return new IntegrationAdapter() {
				@Override public String providerType() { return "TEST_STUB"; }
				@Override public String displayName() { return "Test Stub"; }
				@Override public Set<AuthType> supportedAuthModes() {
					return Set.of(AuthType.NONE, AuthType.BEARER, AuthType.BASIC, AuthType.API_KEY);
				}
				@Override public Set<String> supportedOperations() { return Set.of("ping"); }
				@Override public boolean requiresEndpointUrl() { return true; }
				@Override public void validateConfiguration(Map<String, Object> config, AuthType authMode, String endpointUrl) {
					if (endpointUrl == null || !endpointUrl.startsWith("https://")) {
						throw new com.edam.common.exception.BusinessException(
							com.edam.common.api.ErrorCode.VALIDATION_FAILED,
							"TEST_STUB requires https:// endpointUrl");
					}
				}
				@Override public ConnectionTestResult testConnection(AdapterContext ctx, Duration connectTimeout, Duration readTimeout) {
					// Intentionally always fails — we are validating plumbing, not a provider.
					return ConnectionTestResult.failure("NOOP", "Stub adapter does not dial out in tests.", 0);
				}
				@Override public ExecutionResult execute(AdapterContext ctx, String operation, String idempotencyKey) {
					return ExecutionResult.failed(true, "NOOP", "Stub execution always fails for test", null);
				}
			};
		}
	}
}
