package com.edam.integrations.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import com.edam.common.api.ErrorCode;
import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.common.security.EdamPrincipal;

import org.springframework.dao.DataIntegrityViolationException;
import com.edam.integrations.crypto.IntegrationSecretCrypto;
import com.edam.integrations.dto.IntegrationDtos;
import com.edam.integrations.dto.IntegrationDtos.*;
import com.edam.integrations.model.IntegrationConfig;
import com.edam.integrations.model.IntegrationConfig.AuthType;
import com.edam.integrations.model.IntegrationConfig.ConnectionStatus;
import com.edam.integrations.model.IntegrationExecution;
import com.edam.integrations.model.IntegrationExecution.ExecutionStatus;
import com.edam.integrations.repository.IntegrationConfigRepository;
import com.edam.integrations.repository.IntegrationExecutionRepository;
import com.edam.integrations.service.adapters.*;
import com.edam.integrations.support.DiagnosticRedactor;
import com.edam.integrations.support.SafeDestination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates integration configuration lifecycle, connection testing and execution
 * submission. All SQL is in repositories; all provider logic is in adapters.
 */
@Service
public class IntegrationService {

	private static final Logger log = LoggerFactory.getLogger(IntegrationService.class);

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
	private static final int MAX_PAGE_SIZE = 100;
	private static final int MAX_CONFIG_BYTES = 16_000;

	private final IntegrationConfigRepository configs;
	private final IntegrationExecutionRepository executions;
	private final IntegrationAdapterRegistry adapters;
	private final IntegrationSecretCrypto crypto;
	private final AuditRecorder audit;
	private final JsonMapper json;

	public IntegrationService(IntegrationConfigRepository configs,
			IntegrationExecutionRepository executions,
			IntegrationAdapterRegistry adapters,
			IntegrationSecretCrypto crypto,
			AuditRecorder audit,
			JsonMapper json) {
		this.configs = configs;
		this.executions = executions;
		this.adapters = adapters;
		this.crypto = crypto;
		this.audit = audit;
		this.json = json;
	}

	// ----------------------------------------------------------------------------------
	// Catalogue
	// ----------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<ProviderCatalogueEntry> providerCatalogue() {
		return adapters.catalogue().stream().map(this::toCatalogueEntry).toList();
	}

	@SuppressWarnings("unchecked")
	private ProviderCatalogueEntry toCatalogueEntry(Map<String, Object> m) {
		return new ProviderCatalogueEntry(
				(String) m.get("type"), (String) m.get("name"),
				(List<String>) m.get("authModes"),
				(List<String>) m.get("operations"),
				(Boolean) m.get("requiresEndpointUrl"));
	}

	// ----------------------------------------------------------------------------------
	// Configuration lifecycle
	// ----------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public List<IntegrationSummary> list() {
		return configs.findByArchivedFalseOrderByCreatedAtDesc().stream().map(this::toSummary).toList();
	}

	@Transactional(readOnly = true)
	public IntegrationDetail detail(UUID id) {
		IntegrationConfig c = loadActiveOrArchived(id);
		return toDetail(c);
	}

	@Transactional
	public IntegrationDetail create(EdamPrincipal caller, CreateIntegrationRequest req) {
		if (req == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Request is required.");
		requireNonBlank(req.code(), "Integration code is required.");
		requireNonBlank(req.name(), "Integration name is required.");
		requireCodeFormat(req.code());
		if (configs.existsByCodeAndArchivedFalse(req.code())) {
			throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
				"An integration with code '" + req.code() + "' already exists.");
		}
		IntegrationAdapter adapter = adapters.require(req.providerType());
		AuthType at = resolveAuthMode(req.authMode(), adapter);
		Map<String, Object> cfg = parseConfiguration(req.configuration());
		validateWithAdapter(adapter, cfg, at, req.endpointUrl());

		IntegrationConfig c = new IntegrationConfig();
		c.setCode(req.code().trim());
		c.setName(req.name().trim());
		c.setProviderType(req.providerType());
		c.setDescription(req.description());
		c.setEndpointUrl(req.endpointUrl());
		c.setAuthenticationType(at);
		c.setConfigurationJson(writeJson(cfg));
		c.setActive(false);
		c.setConnectionStatus(ConnectionStatus.UNCONFIGURED);
		applySecretChange(c, req.secret(), req.secretAction() == null ? SecretAction.SET : req.secretAction(), false);
		applyActor(c, caller, true);
		configs.save(c);
		audit.record(AuditAction.CREATE, "IntegrationConfig", c.getId(),
			"Created integration " + c.getCode() + " (" + c.getProviderType() + ")");
		log.info("integration.created code={} provider={} actor={}", c.getCode(), c.getProviderType(), caller.userId());
		return toDetail(c);
	}

	@Transactional
	public IntegrationDetail update(EdamPrincipal caller, UUID id, UpdateIntegrationRequest req) {
		if (req == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Request is required.");
		IntegrationConfig c = loadActiveOrArchived(id);
		checkVersion(c, req.expectedVersion());

		String newProviderType = c.getProviderType(); // provider type is immutable after create
		IntegrationAdapter adapter = adapters.require(newProviderType);
		AuthType at = req.authMode() == null ? c.getAuthenticationType() : authModeOf(req.authMode());
		if (!adapter.supportedAuthModes().contains(at)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Provider '" + newProviderType + "' does not support auth mode " + at);
		}
		String newEndpoint = req.endpointUrl() != null ? req.endpointUrl() : c.getEndpointUrl();
		Map<String, Object> newCfg = req.configuration() != null ? parseConfiguration(req.configuration()) : readJson(c.getConfigurationJson());
		validateWithAdapter(adapter, newCfg, at, newEndpoint);

		if (req.name() != null && !req.name().isBlank()) c.setName(req.name().trim());
		c.setDescription(req.description());
		c.setEndpointUrl(newEndpoint);
		c.setAuthenticationType(at);
		c.setConfigurationJson(writeJson(newCfg));
		SecretAction action = req.secretAction() == null ? SecretAction.KEEP : req.secretAction();
		applySecretChange(c, req.secret(), action, c.getEncryptedSecret() != null);
		applyActor(c, caller, false);
		configs.save(c);
		audit.record(AuditAction.UPDATE, "IntegrationConfig", c.getId(),
			"Updated integration " + c.getCode());
		log.info("integration.updated code={} actor={}", c.getCode(), caller.userId());
		return toDetail(c);
	}

	@Transactional
	public IntegrationDetail activate(EdamPrincipal caller, UUID id, boolean active) {
		IntegrationConfig c = loadActiveOrArchived(id);
		if (c.isArchived()) throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Archived integrations cannot be activated.");
		if (active && !isConfigured(c)) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
				"Configure the endpoint and credentials before activating this integration.");
		}
		if (active && c.getConnectionStatus() == ConnectionStatus.FAILED) {
			// Allow activation but log: failed means last test failed; operator can still activate.
			log.info("integration.activate_warnings code={} actor={} last_test=FAILED", c.getCode(), caller.userId());
		}
		c.setActive(active);
		applyActor(c, caller, false);
		configs.save(c);
		audit.record(active ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
			"IntegrationConfig", c.getId(),
			(active ? "Activated" : "Deactivated") + " integration " + c.getCode());
		return toDetail(c);
	}

	@Transactional
	public IntegrationDetail archive(EdamPrincipal caller, UUID id) {
		IntegrationConfig c = loadActiveOrArchived(id);
		if (c.isArchived()) return toDetail(c);
		c.setArchived(true);
		c.setActive(false);
		applyActor(c, caller, false);
		configs.save(c);
		audit.record(AuditAction.DELETE, "IntegrationConfig", c.getId(),
			"Archived integration " + c.getCode() + " (history retained)");
		return toDetail(c);
	}

	// ----------------------------------------------------------------------------------
	// Connection testing
	// ----------------------------------------------------------------------------------

	@Transactional
	public ConnectionTestResponse testConnection(EdamPrincipal caller, UUID id) {
		IntegrationConfig c = configs.findWithLockById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Integration not found."));
		if (c.isArchived()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Archived integrations cannot be tested.");
		}
		IntegrationAdapter adapter = adapters.require(c.getProviderType());
		if (!isConfigured(c)) {
			c.setConnectionStatus(ConnectionStatus.UNCONFIGURED);
			c.setConnectionCheckedAt(Instant.now());
			c.setLastTestMessage("Integration is missing required endpoint or credentials.");
			c.setLastTestSuccess(false);
			configs.save(c);
			return new ConnectionTestResponse(false, ConnectionState.UNCONFIGURED,
				c.getLastTestMessage(), null, "UNCONFIGURED", c.getConnectionCheckedAt());
		}
		String secret = decryptSecret(c);
		Map<String, Object> cfg = readJson(c.getConfigurationJson());
		AdapterContext ctx = new AdapterContext(c.getProviderType(), c.getEndpointUrl(),
				c.getAuthenticationType(), null, secret, cfg);

		c.setConnectionStatus(ConnectionStatus.TESTING);
		c.setConnectionCheckedAt(Instant.now());
		configs.save(c);

		audit.record(AuditAction.CREATE, "IntegrationConnectionTest", c.getId(), "Connection test started for " + c.getCode());

		ConnectionTestResult result;
		try {
			result = adapter.testConnection(ctx, CONNECT_TIMEOUT, READ_TIMEOUT);
		} catch (Exception ex) {
			result = ConnectionTestResult.failure("EXCEPTION",
				DiagnosticRedactor.sanitize(ex.getMessage()) != null
					? DiagnosticRedactor.sanitize(ex.getMessage()) : "Test failed with an unexpected error.",
				null);
		}
		Integer dur = result.respondedInMs();
		String msg = DiagnosticRedactor.sanitize(result.message());
		c.setLastTestAt(Instant.now());
		c.setLastTestSuccess(result.success());
		c.setLastTestMessage(msg);
		c.setLastTestDurationMs(dur);
		c.setConnectionStatus(result.success() ? ConnectionStatus.ACTIVE : ConnectionStatus.FAILED);
		configs.save(c);

		audit.record(result.success() ? AuditAction.ACTIVATE : AuditAction.DEACTIVATE,
			"IntegrationConfig", c.getId(),
			"Connection test " + (result.success() ? "succeeded" : "failed")
				+ " code=" + result.diagnosticCode());

		log.info("integration.test code={} ok={} diagnostic={} latencyMs={}",
			c.getCode(), result.success(), result.diagnosticCode(), dur);

		return new ConnectionTestResponse(result.success(),
			result.success() ? ConnectionState.ACTIVE : ConnectionState.FAILED,
			msg, dur, result.diagnosticCode(), c.getLastTestAt());
	}

	// ----------------------------------------------------------------------------------
	// Executions
	// ----------------------------------------------------------------------------------

	@Transactional
	public ExecutionSummary submitExecution(EdamPrincipal caller, UUID configId, ExecuteRequest req) {
		if (req == null || req.operation() == null || req.operation().isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "operation is required.");
		}
		IntegrationConfig c = loadActiveOrArchived(configId);
		if (c.isArchived() || !c.isActive()) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
				"Executions may only be submitted against active integrations.");
		}
		IntegrationAdapter adapter = adapters.require(c.getProviderType());
		if (!adapter.supportedOperations().contains(req.operation())) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Provider '" + c.getProviderType() + "' does not support operation '" + req.operation() + "'.");
		}
		String idemKey = req.idempotencyKey() != null && !req.idempotencyKey().isBlank()
				? req.idempotencyKey().trim() : null;
		if (idemKey != null) {
			Optional<IntegrationExecution> existing = executions
				.findByIdempotencyKeyAndIntegrationConfigIdAndOperationType(idemKey, configId, req.operation());
			if (existing.isPresent()) {
				return toExecSummary(existing.get());
			}
		}

		IntegrationExecution e = new IntegrationExecution();
		e.setIntegrationConfigId(configId);
		e.setOperationType(req.operation());
		e.setIdempotencyKey(idemKey);
		e.setStatus(ExecutionStatus.PENDING);
		e.setAttemptCount(0);
		e.setMaxAttempts(3);
		e.setRetryable(true);
		e.setTriggeredBy(caller == null ? null : caller.userId());
		e.setNextRetryAt(Instant.now());
		Map<String, Object> safeMeta = DiagnosticRedactor.sanitizeMetadata(req.requestMetadata());
		e.setRequestMetadata(writeJson(safeMeta));
		try {
			executions.save(e);
		} catch (DataIntegrityViolationException dup) {
			// Another concurrent request with the same idempotency key beat us to insert;
			// replay the existing row rather than surfacing a constraint error to the caller.
			// Only honoured when a key was supplied (duplicate null-key inserts are legitimate
			// separate executions — see V27 ux_integration_executions_idempotency_key).
			if (idemKey == null) throw dup;
			return executions.findByIdempotencyKeyAndIntegrationConfigIdAndOperationType(
					idemKey, configId, req.operation())
				.map(this::toExecSummary)
				.orElseThrow(() -> dup);
		}
		audit.record(AuditAction.CREATE, "IntegrationExecution", e.getId(),
			"Enqueued operation=" + req.operation() + " integration=" + c.getCode());
		return toExecSummary(e);
	}

	@Transactional
	public ExecutionDetail executionDetail(UUID id) {
		return toExecDetail(executions.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Execution not found.")));
	}

	@Transactional(readOnly = true)
	public Page<ExecutionSummary> executionHistory(UUID configId, String status, int page, int size) {
		int p = Math.max(0, page);
		int s = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
		Pageable pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<IntegrationExecution> raw;
		if (status != null && !status.isBlank()) {
			ExecutionState es;
			try { es = ExecutionState.valueOf(status.toUpperCase()); }
			catch (IllegalArgumentException ex) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown execution status: " + status);
			}
			raw = executions.findByIntegrationConfigIdAndStatusOrderByCreatedAtDesc(configId, toStatus(es), pageable);
		} else {
			raw = executions.findByIntegrationConfigIdOrderByCreatedAtDesc(configId, pageable);
		}
		return raw.map(this::toExecSummary);
	}

	@Transactional
	public ExecutionSummary retry(EdamPrincipal caller, UUID executionId, RetryRequest req) {
		IntegrationExecution e = executions.findById(executionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Execution not found."));
		checkVersion(e, req == null ? null : req.expectedVersion());
		if (e.getStatus() == ExecutionStatus.SUCCEEDED) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Successful executions cannot be retried.");
		}
		if (e.getStatus() == ExecutionStatus.RUNNING) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "Execution is currently running.");
		}
		e.setStatus(ExecutionStatus.RETRY_PENDING);
		e.setRetryable(true);
		e.setNextRetryAt(Instant.now());
		e.setClaimedBy(null); e.setClaimedAt(null); e.setClaimExpiresAt(null);
		e.setErrorMessage(null); e.setErrorCode(null);
		executions.save(e);
		audit.record(AuditAction.UPDATE, "IntegrationExecution", e.getId(), "Retry requested by " + caller.userId());
		return toExecSummary(e);
	}

	// ----------------------------------------------------------------------------------
	// Mapping
	// ----------------------------------------------------------------------------------

	private IntegrationSummary toSummary(IntegrationConfig c) {
		return new IntegrationSummary(c.getId(), c.getCode(), c.getName(), c.getProviderType(),
			c.getDescription(), c.getEndpointUrl(), authModeOf(c.getAuthenticationType()),
			c.isActive(), c.isArchived(), connectionOf(c.getConnectionStatus()),
			c.getLastTestAt(), c.getLastTestSuccess(), c.getLastTestMessage(),
			c.getLastTestDurationMs(), c.getConnectionCheckedAt(),
			c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy(), c.getVersion(),
			c.getEncryptedSecret() != null, c.getSecretVersion());
	}

	private IntegrationDetail toDetail(IntegrationConfig c) {
		return new IntegrationDetail(c.getId(), c.getCode(), c.getName(), c.getProviderType(),
			c.getDescription(), c.getEndpointUrl(), authModeOf(c.getAuthenticationType()),
			null, // username never exposed (we do not store a username column in this phase)
			readJson(c.getConfigurationJson()), c.isActive(), c.isArchived(),
			connectionOf(c.getConnectionStatus()), c.getLastTestAt(), c.getLastTestSuccess(),
			c.getLastTestMessage(), c.getLastTestDurationMs(), c.getConnectionCheckedAt(),
			c.getCreatedAt(), c.getCreatedBy(), c.getUpdatedAt(), c.getUpdatedBy(),
			c.getVersion(), c.getEncryptedSecret() != null, c.getSecretVersion());
	}

	private ExecutionSummary toExecSummary(IntegrationExecution e) {
		return new ExecutionSummary(e.getId(), e.getIntegrationConfigId(), e.getOperationType(),
			e.getIdempotencyKey(), e.getExternalReference(), execStateOf(e.getStatus()),
			e.getAttemptCount(), e.getMaxAttempts(), e.getStartedAt(), e.getCompletedAt(),
			e.getNextRetryAt(), e.isRetryable(), e.getErrorCode(), e.getErrorMessage(),
			e.getTriggeredBy(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
	}

	private ExecutionDetail toExecDetail(IntegrationExecution e) {
		return new ExecutionDetail(e.getId(), e.getIntegrationConfigId(), e.getOperationType(),
			e.getIdempotencyKey(), e.getExternalReference(), execStateOf(e.getStatus()),
			e.getAttemptCount(), e.getMaxAttempts(), e.getStartedAt(), e.getCompletedAt(),
			e.getNextRetryAt(), e.isRetryable(),
			readJson(e.getRequestMetadata()), readJson(e.getResponseMetadata()),
			e.getErrorCode(), e.getErrorMessage(), e.getTriggeredBy(),
			e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
	}

	// ----------------------------------------------------------------------------------
	// Helpers
	// ----------------------------------------------------------------------------------

	private IntegrationConfig loadActiveOrArchived(UUID id) {
		// Allow viewing/updating archived configs for history; the API gates archive-specific ops.
		return configs.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Integration not found."));
	}

	private boolean isConfigured(IntegrationConfig c) {
		IntegrationAdapter a = adapters.find(c.getProviderType()).orElse(null);
		if (a == null) return false;
		boolean needEp = a.requiresEndpointUrl();
		boolean epOk = !needEp || (c.getEndpointUrl() != null && !c.getEndpointUrl().isBlank());
		boolean secretOk = c.getAuthenticationType() == AuthType.NONE || c.getEncryptedSecret() != null;
		return epOk && secretOk;
	}

	private void applySecretChange(IntegrationConfig c, String newSecret, SecretAction action, boolean hasExisting) {
		switch (action) {
			case SET -> {
				if (newSecret == null || newSecret.isEmpty()) {
					if (c.getAuthenticationType() != AuthType.NONE && !hasExisting) {
						throw new BusinessException(ErrorCode.VALIDATION_FAILED,
							"A credential is required for authentication mode " + c.getAuthenticationType());
					}
					return; // nothing to do
				}
				if (!crypto.isAvailable()) {
					throw new BusinessException(ErrorCode.PERSISTENCE_ERROR,
						"Integration master key is not configured; cannot store credentials.");
				}
				c.setEncryptedSecret(crypto.encrypt(newSecret));
				c.setSecretVersion(c.getSecretVersion() + 1);
			}
			case CLEAR -> {
				if (c.getAuthenticationType() != AuthType.NONE) {
					throw new BusinessException(ErrorCode.VALIDATION_FAILED,
						"Cannot clear credentials while authentication mode is " + c.getAuthenticationType());
				}
				c.setEncryptedSecret(null);
			}
			case KEEP -> { /* leave encryptedSecret as-is */ }
		}
	}

	private String decryptSecret(IntegrationConfig c) {
		if (c.getEncryptedSecret() == null) return null;
		if (!crypto.isAvailable()) {
			throw new BusinessException(ErrorCode.PERSISTENCE_ERROR,
				"Integration master key is not configured; cannot read credentials.");
		}
		try {
			return crypto.decrypt(c.getEncryptedSecret());
		} catch (IllegalStateException ex) {
			throw new BusinessException(ErrorCode.PERSISTENCE_ERROR,
				"Stored credential could not be decrypted (master key changed or ciphertext corrupt). Rotate the secret to continue.");
		}
	}

	private void applyActor(IntegrationConfig c, EdamPrincipal caller, boolean create) {
		String by = caller == null ? null : caller.userId().toString();
		if (create) c.setCreatedBy(by);
		c.setUpdatedBy(by);
	}

	private Map<String, Object> parseConfiguration(Map<String, Object> raw) {
		if (raw == null) return new LinkedHashMap<>();
		String json;
		try { json = this.json.writeValueAsString(raw); }
		catch (Exception ex) { throw new BusinessException(ErrorCode.VALIDATION_FAILED, "configuration is not serialisable."); }
		if (json.length() > MAX_CONFIG_BYTES) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"configuration is too large (max " + MAX_CONFIG_BYTES + " bytes).");
		}
		return new LinkedHashMap<>(raw);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readJson(String j) {
		if (j == null || j.isBlank()) return new LinkedHashMap<>();
		try {
			return this.json.readValue(j, LinkedHashMap.class);
		} catch (Exception ex) {
			return new LinkedHashMap<>();
		}
	}

	private String writeJson(Map<String, Object> m) {
		if (m == null || m.isEmpty()) return null;
		try { return this.json.writeValueAsString(m); }
		catch (Exception ex) { throw new BusinessException(ErrorCode.VALIDATION_FAILED, "configuration is not serialisable."); }
	}

	private void validateWithAdapter(IntegrationAdapter a, Map<String, Object> cfg, AuthType at, String endpointUrl) {
		if (a.requiresEndpointUrl()) {
			if (endpointUrl == null || endpointUrl.isBlank()) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endpointUrl is required for this provider.");
			}
			try {
				new URL(endpointUrl);
			} catch (MalformedURLException ex) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endpointUrl is malformed.");
			}
			// Only SSRF-check http/https destinations that look fully-qualified; adapters may
			// override with tighter policy. We always require https.
			if (endpointUrl.toLowerCase().startsWith("http://")) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "endpointUrl must use https://");
			}
			if (endpointUrl.toLowerCase().startsWith("https://")) {
				SafeDestination.requireSafeHttps(endpointUrl);
			}
		}
		if (!a.supportedAuthModes().contains(at)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Provider '" + a.providerType() + "' does not support auth mode " + at);
		}
		try {
			a.validateConfiguration(cfg, at, endpointUrl);
		} catch (BusinessException be) { throw be; }
		catch (RuntimeException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Provider rejected configuration: " + DiagnosticRedactor.sanitize(ex.getMessage()));
		}
	}

	private static AuthType resolveAuthMode(AuthMode m, IntegrationAdapter a) {
		if (m == null) return AuthType.NONE;
		AuthType at = authModeOf(m);
		if (!a.supportedAuthModes().contains(at)) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Provider '" + a.providerType() + "' does not support auth mode " + m);
		}
		return at;
	}

	private static AuthType authModeOf(AuthMode m) {
		if (m == null) return AuthType.NONE;
		return switch (m) {
			case NONE -> AuthType.NONE;
			case BASIC -> AuthType.BASIC;
			case BEARER -> AuthType.BEARER;
			case API_KEY -> AuthType.API_KEY;
			case OAUTH2 -> AuthType.OAUTH2;
			case CLIENT_CERTIFICATE -> AuthType.CLIENT_CERTIFICATE;
			case LDAP_BIND -> AuthType.LDAP_BIND;
		};
	}
	private static AuthMode authModeOf(AuthType t) {
		if (t == null) return AuthMode.NONE;
		return switch (t) {
			case NONE -> AuthMode.NONE;
			case BASIC -> AuthMode.BASIC;
			case BEARER -> AuthMode.BEARER;
			case API_KEY -> AuthMode.API_KEY;
			case OAUTH2 -> AuthMode.OAUTH2;
			case CLIENT_CERTIFICATE -> AuthMode.CLIENT_CERTIFICATE;
			case LDAP_BIND -> AuthMode.LDAP_BIND;
		};
	}

	private static ConnectionState connectionOf(ConnectionStatus s) {
		if (s == null) return ConnectionState.UNKNOWN;
		return switch (s) {
			case UNKNOWN -> ConnectionState.UNKNOWN;
			case UNCONFIGURED -> ConnectionState.UNCONFIGURED;
			case ACTIVE -> ConnectionState.ACTIVE;
			case INACTIVE -> ConnectionState.INACTIVE;
			case FAILED -> ConnectionState.FAILED;
			case TESTING -> ConnectionState.TESTING;
		};
	}
	private static ExecutionState execStateOf(ExecutionStatus s) {
		if (s == null) return ExecutionState.PENDING;
		return switch (s) {
			case PENDING -> ExecutionState.PENDING;
			case RUNNING -> ExecutionState.RUNNING;
			case SUCCEEDED -> ExecutionState.SUCCEEDED;
			case FAILED -> ExecutionState.FAILED;
			case RETRY_PENDING -> ExecutionState.RETRY_PENDING;
			case CANCELLED -> ExecutionState.CANCELLED;
		};
	}
	private static ExecutionStatus toStatus(ExecutionState s) {
		return switch (s) {
			case PENDING -> ExecutionStatus.PENDING;
			case RUNNING -> ExecutionStatus.RUNNING;
			case SUCCEEDED -> ExecutionStatus.SUCCEEDED;
			case FAILED -> ExecutionStatus.FAILED;
			case RETRY_PENDING -> ExecutionStatus.RETRY_PENDING;
			case CANCELLED -> ExecutionStatus.CANCELLED;
		};
	}

	private static void requireNonBlank(String v, String msg) {
		if (v == null || v.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, msg);
	}

	private static void requireCodeFormat(String code) {
		if (!code.matches("[a-z][a-z0-9_-]{2,63}")) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
				"Integration code must start with a letter and contain only lowercase letters, digits, '-' or '_'.");
		}
	}

	private static void checkVersion(Object entity, Long expected) {
		if (expected == null) return;
		long actual;
		try {
			actual = (long) entity.getClass().getMethod("getVersion").invoke(entity);
		} catch (Exception ex) { return; }
		if (actual != expected) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
				"The record was modified by another request. Reload and retry.");
		}
	}
	// duplicate helper for executions
	private static void checkVersion(IntegrationExecution e, Long expected) {
		if (expected != null && e.getVersion() != expected) {
			throw new BusinessException(ErrorCode.PRECONDITION_FAILED,
				"The execution was modified by another request. Reload and retry.");
		}
	}
}
