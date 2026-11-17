package com.edam.integrations.adapter;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import com.edam.common.api.ErrorCode;
import com.edam.common.exception.BusinessException;
import com.edam.integrations.model.IntegrationConfig.AuthType;
import com.edam.integrations.service.adapters.*;

import org.springframework.boot.test.context.TestComponent;

/**
 * Minimal HTTP "ping" adapter used ONLY in tests. Verifies the adapter SPI, secret
 * plumbing, connection testing, execution, retries, and redaction. It is not included
 * in production code. It sends a single GET request to {@code endpointUrl} and treats
 * HTTP 2xx as success; any other status / network error becomes a retryable failure.
 */
@TestComponent
public class StubHttpAdapter implements IntegrationAdapter {

	public static final String TYPE = "STUB_HTTP_TEST";
	public static final String OP_PING = "ping";

	private final HttpClient client = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	@Override public String providerType() { return TYPE; }
	@Override public String displayName() { return "Stub HTTP (test-only)"; }
	@Override public Set<AuthType> supportedAuthModes() { return Set.of(AuthType.NONE, AuthType.BEARER, AuthType.BASIC, AuthType.API_KEY); }
	@Override public Set<String> supportedOperations() { return Set.of(OP_PING); }
	@Override public boolean requiresEndpointUrl() { return true; }

	@Override
	public void validateConfiguration(Map<String, Object> config, AuthType authMode, String endpointUrl) {
		if (endpointUrl == null || !endpointUrl.startsWith("https://")) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Stub HTTP requires https:// endpointUrl");
		}
	}

	@Override
	public ConnectionTestResult testConnection(AdapterContext ctx, Duration connectTimeout, Duration readTimeout) {
		return request(ctx, connectTimeout, readTimeout, "TEST");
	}

	@Override
	public ExecutionResult execute(AdapterContext ctx, String operation, String idempotencyKey) {
		ConnectionTestResult r = request(ctx, Duration.ofSeconds(4), Duration.ofSeconds(8), operation);
		if (r.success()) {
			return ExecutionResult.succeeded("stub-" + java.util.UUID.randomUUID(), Map.of("latencyMs", r.respondedInMs()));
		}
		return ExecutionResult.failed("NETWORK" .equals(r.diagnosticCode()) || "TIMEOUT".equals(r.diagnosticCode()),
				r.diagnosticCode(), r.message(), Map.of("latencyMs", r.respondedInMs()));
	}

	private ConnectionTestResult request(AdapterContext ctx, Duration connectTimeout, Duration readTimeout, String op) {
		long start = System.currentTimeMillis();
		try {
			HttpRequest.Builder b = HttpRequest.newBuilder()
					.uri(URI.create(ctx.endpointUrl()))
					.timeout(readTimeout)
					.header("User-Agent", "edam-stub-http/1.0")
					.GET();
			applyAuth(b, ctx);
			HttpResponse<Void> resp = client.send(b.build(), HttpResponse.BodyHandlers.discarding());
			int dur = (int) (System.currentTimeMillis() - start);
			if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
				return ConnectionTestResult.ok(dur);
			}
			return ConnectionTestResult.failure("HTTP_" + resp.statusCode(), "Provider returned HTTP " + resp.statusCode(), dur);
		} catch (java.net.SocketTimeoutException e) {
			return ConnectionTestResult.failure("TIMEOUT", "Request timed out after " + readTimeout, (int)(System.currentTimeMillis()-start));
		} catch (java.io.IOException e) {
			// Covers HttpTimeoutException, ConnectException, UnknownHostException, and generic I/O.
			return ConnectionTestResult.failure("NETWORK", "Network error: " + sanitize(e.getMessage()), (int)(System.currentTimeMillis()-start));
		} catch (IllegalArgumentException | URISyntaxException e) {
			return ConnectionTestResult.failure("BAD_URL", "Malformed endpoint URL.", null);
		}
	}

	private static void applyAuth(HttpRequest.Builder b, AdapterContext ctx) {
		if (ctx.secret() == null) return;
		switch (ctx.authType()) {
			case BEARER -> b.header("Authorization", "Bearer " + ctx.secret());
			case BASIC -> {
				String token = Base64.getEncoder().encodeToString(("user:" + ctx.secret()).getBytes(StandardCharsets.UTF_8));
				b.header("Authorization", "Basic " + token);
			}
			case API_KEY -> b.header("X-Api-Key", ctx.secret());
			default -> {}
		}
	}

	private static String sanitize(String m) {
		if (m == null) return null;
		return m.replaceAll("(?i)(authorization|x-api-key|token|secret|password)[^\\s]*\\s*[:=]?\\s*\\S+", "***");
	}
}
