package com.edam.integrations.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Request/response shapes for the integration-foundation API (Phase 4H).
 *
 * <p>All DTOs explicitly omit {@code encryptedSecret} and other credential material. Secrets
 * travel only in write requests (create/update) and never come back in responses; update
 * uses a tri-state {@code secretAction} so that omitted secrets preserve the existing value.
 */
public final class IntegrationDtos {

	private IntegrationDtos() {}

	public enum AuthMode { NONE, BASIC, BEARER, API_KEY, OAUTH2, CLIENT_CERTIFICATE, LDAP_BIND }

	public enum SecretAction { SET, CLEAR, KEEP }

	public enum ConnectionState { UNKNOWN, UNCONFIGURED, ACTIVE, INACTIVE, FAILED, TESTING }

	public enum ExecutionState { PENDING, RUNNING, SUCCEEDED, FAILED, RETRY_PENDING, CANCELLED }

	// ---------- Create / Update ----------

	public record CreateIntegrationRequest(
			String code,
			String name,
			String providerType,
			String description,
			String endpointUrl,
			AuthMode authMode,
			String username,
			String secret,
			SecretAction secretAction,
			Map<String, Object> configuration
	) {}

	public record UpdateIntegrationRequest(
			String name,
			String description,
			String endpointUrl,
			AuthMode authMode,
			String username,
			String secret,
			SecretAction secretAction,
			Map<String, Object> configuration,
			Long expectedVersion
	) {}

	// ---------- Responses ----------

	public record IntegrationSummary(
			UUID id, String code, String name, String providerType, String description,
			String endpointUrl, AuthMode authMode, boolean active, boolean archived,
			ConnectionState connectionStatus,
			Instant lastTestAt, Boolean lastTestSuccess, String lastTestMessage,
			Integer lastTestDurationMs, Instant connectionCheckedAt,
			Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
			long version, boolean hasSecret, int secretVersion
	) {}

	public record IntegrationDetail(
			UUID id, String code, String name, String providerType, String description,
			String endpointUrl, AuthMode authMode, String username,
			Map<String, Object> configuration, boolean active, boolean archived,
			ConnectionState connectionStatus,
			Instant lastTestAt, Boolean lastTestSuccess, String lastTestMessage,
			Integer lastTestDurationMs, Instant connectionCheckedAt,
			Instant createdAt, String createdBy, Instant updatedAt, String updatedBy,
			long version, boolean hasSecret, int secretVersion
	) {}

	public record ProviderCatalogueEntry(
			String type, String name, List<String> authModes,
			List<String> operations, boolean requiresEndpointUrl
	) {}

	public record ConnectionTestResponse(
			boolean success, ConnectionState resultingState, String message,
			Integer respondedInMs, String diagnosticCode, Instant testedAt
	) {}

	// ---------- Executions ----------

	public record ExecuteRequest(String operation, String idempotencyKey, Map<String, Object> requestMetadata) {}

	public record RetryRequest(Long expectedVersion) {}

	public record ExecutionSummary(
			UUID id, UUID integrationConfigId, String operation, String idempotencyKey,
			String externalReference, ExecutionState status, int attemptCount, int maxAttempts,
			Instant startedAt, Instant completedAt, Instant nextRetryAt, boolean retryable,
			String errorCode, String errorMessage, UUID triggeredBy,
			Instant createdAt, Instant updatedAt, long version
	) {}

	public record ExecutionDetail(
			UUID id, UUID integrationConfigId, String operation, String idempotencyKey,
			String externalReference, ExecutionState status, int attemptCount, int maxAttempts,
			Instant startedAt, Instant completedAt, Instant nextRetryAt, boolean retryable,
			Map<String, Object> requestMetadata, Map<String, Object> responseMetadata,
			String errorCode, String errorMessage, UUID triggeredBy,
			Instant createdAt, Instant updatedAt, long version
	) {}
}
