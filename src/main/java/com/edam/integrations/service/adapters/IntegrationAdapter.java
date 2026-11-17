package com.edam.integrations.service.adapters;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import com.edam.integrations.model.IntegrationConfig.AuthType;

/**
 * Provider-specific adapter contract.
 *
 * <p>Each concrete adapter implements one integration {@link #providerType()}. Adding a
 * provider requires adding a class that implements this interface and registering it in
 * {@link IntegrationAdapterRegistry}; no unrelated module needs to change.
 *
 * <p>Adapters MUST:
 * <ul>
 *   <li>Validate their own configuration schema ({@link #validateConfiguration(Map, AuthType)}).</li>
 *   <li>Perform connection tests using a provider-defined success predicate (not just "HTTP 200"
 *       unless the provider contract specifies that).</li>
 *   <li>Never perform side effects during a connection test.</li>
 *   <li>Apply explicit connection/read timeouts.</li>
 *   <li>Return sanitized diagnostics via {@link ConnectionTestResult#message()} — never leak credentials.</li>
 *   <li>Bound and redact response payloads in execution metadata.</li>
 * </ul>
 */
public interface IntegrationAdapter {

	/** Stable identifier (matches {@code integration_configs.integration_type}). */
	String providerType();

	/** Human-readable provider name for catalogues. */
	String displayName();

	/** Authentication modes this adapter supports; used to validate create/update payloads. */
	Set<AuthType> supportedAuthModes();

	/** Operations this adapter supports (e.g. "ping", "push-notification", "sync-staff"). */
	Set<String> supportedOperations();

	/** Whether this adapter requires an {@code endpoint_url}. */
	default boolean requiresEndpointUrl() { return true; }

	/**
	 * Validate the adapter-specific configuration JSON (decoded to a Map). Throws
	 * {@link com.edam.common.exception.BusinessException} with {@code VALIDATION_FAILED}
	 * for invalid values.
	 */
	void validateConfiguration(Map<String, Object> config, AuthType authMode, String endpointUrl);

	/**
	 * Execute a connection test against a configured integration. MUST NOT cause side effects
	 * on the provider. Implementations MUST apply {@code connectTimeout} / {@code readTimeout}
	 * and return a structured result rather than throwing for ordinary network failures.
	 */
	ConnectionTestResult testConnection(AdapterContext ctx, Duration connectTimeout, Duration readTimeout);

	/**
	 * Execute an operation. {@code operation} is guaranteed to be in {@link #supportedOperations()}.
	 * Implementations MUST treat {@code secret} as opaque (never log it, never place it in
	 * URLs/query strings, never echo back in {@link ExecutionResult#responseMetadata()}).
	 */
	ExecutionResult execute(AdapterContext ctx, String operation, String idempotencyKey);
}
