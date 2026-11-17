package com.edam.integrations.service;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.edam.common.audit.AuditAction;
import com.edam.common.audit.AuditRecorder;
import com.edam.common.exception.BusinessException;
import com.edam.integrations.crypto.IntegrationSecretCrypto;
import com.edam.integrations.model.IntegrationConfig;
import com.edam.integrations.model.IntegrationConfig.AuthType;
import com.edam.integrations.model.IntegrationExecution;
import com.edam.integrations.model.IntegrationExecution.ExecutionStatus;
import com.edam.integrations.repository.IntegrationConfigRepository;
import com.edam.integrations.repository.IntegrationExecutionRepository;
import com.edam.integrations.service.adapters.*;
import com.edam.integrations.support.DiagnosticRedactor;

import org.slf4j.Logger;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls due executions and dispatches them to the registered adapter. Uses the same
 * claim-release pattern as {@code OutboxProcessor}: a short non-transactional sweep
 * claims one row per cycle atomically ({@code FOR UPDATE SKIP LOCKED}), then processes
 * it inside a {@code REQUIRES_NEW} transaction so a network failure does not keep a
 * transaction open.
 */
@Service
public class IntegrationExecutionWorker {

	private static final Logger log = LoggerFactory.getLogger(IntegrationExecutionWorker.class);

	private static final Duration CLAIM_TTL = Duration.ofMinutes(5);
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);
	private static final int MAX_ATTEMPTS = 3;

	private final IntegrationExecutionRepository executions;
	private final IntegrationConfigRepository configs;
	private final IntegrationAdapterRegistry adapters;
	private final IntegrationSecretCrypto crypto;
	private final AuditRecorder audit;
	private final JsonMapper json;
	private final IntegrationExecutionWorker self;
	private final boolean enabled;
	private String workerId;

	public IntegrationExecutionWorker(IntegrationExecutionRepository executions,
			IntegrationConfigRepository configs,
			IntegrationAdapterRegistry adapters,
			IntegrationSecretCrypto crypto,
			AuditRecorder audit,
			JsonMapper json,
			@Lazy IntegrationExecutionWorker self,
			@Value("${edam.integrations.worker.enabled:true}") boolean enabled) {
		this.executions = executions;
		this.configs = configs;
		this.adapters = adapters;
		this.crypto = crypto;
		this.audit = audit;
		this.json = json;
		this.self = self;
		this.enabled = enabled;
		try { this.workerId = InetAddress.getLocalHost().getHostName() + ":" + UUID.randomUUID().toString().substring(0,8); }
		catch (Exception e) { this.workerId = "worker:" + UUID.randomUUID(); }
	}

	/** Runs every 2s when enabled; a no-op if no adapters are registered or worker disabled. */
	@Scheduled(fixedDelayString = "${edam.integrations.worker.delay-ms:2000}", initialDelay = 5_000)
	public void sweep() {
		if (!enabled) return;
		if (adapters.supportedProviderTypes().isEmpty()) return;
		try { executions.releaseExpiredClaims(Instant.now()); }
		catch (RuntimeException ex) { log.warn("integration.claim_recovery_failed {}", ex.toString()); }

		Instant now = Instant.now();
		int claimed = executions.claimOne(now, now.plus(CLAIM_TTL), workerId);
		if (claimed == 0) return;

		List<IntegrationExecution> batch = executions.findClaimedBy(workerId, PageRequest.of(0, 1));
		if (batch.isEmpty()) return;
		IntegrationExecution e = batch.get(0);
		try {
			self.processOne(e.getId());
		} catch (RuntimeException ex) {
			log.error("integration.process_fatal id={} type={}", e.getId(), ex.toString());
		}
	}

	/**
	 * Runs in its own transaction so the row stays claimed independently of sweep's
	 * transaction. The external call happens INSIDE this transaction but the config +
	 * execution are pre-loaded and only their final state is written back; the network
	 * call is not made while holding a row lock from the claim (the claim update
	 * commits before this method runs).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void processOne(UUID executionId) {
		IntegrationExecution e = executions.findById(executionId).orElse(null);
		if (e == null || e.getStatus() != ExecutionStatus.RUNNING) return;
		IntegrationConfig c = configs.findByIdAndArchivedFalse(e.getIntegrationConfigId()).orElse(null);
		if (c == null || !c.isActive()) {
			fail(e, "CONFIG_NOT_FOUND", "Integration configuration is missing or inactive.", false);
			return;
		}
		IntegrationAdapter adapter = adapters.find(c.getProviderType()).orElse(null);
		if (adapter == null) {
			fail(e, "NO_ADAPTER", "No adapter is registered for provider " + c.getProviderType(), false);
			return;
		}
		if (!adapter.supportedOperations().contains(e.getOperationType())) {
			fail(e, "UNSUPPORTED_OPERATION", "Provider does not support operation " + e.getOperationType(), false);
			return;
		}
		String secret = decrypt(c);
		Map<String, Object> reqMeta = readObj(e.getRequestMetadata());
		AdapterContext ctx = new AdapterContext(c.getProviderType(), c.getEndpointUrl(),
				c.getAuthenticationType(), null, secret, readObj(c.getConfigurationJson()));

		ExecutionResult result;
		Instant start = Instant.now();
		try {
			result = adapter.execute(ctx, e.getOperationType(), e.getIdempotencyKey());
		} catch (Exception ex) {
			result = ExecutionResult.failed(isRetryable(ex), "EXCEPTION",
				DiagnosticRedactor.sanitize(ex.getMessage()) != null
					? DiagnosticRedactor.sanitize(ex.getMessage()) : "Unhandled adapter exception", null);
		}
		Integer latencyMs = (int) Duration.between(start, Instant.now()).toMillis();

		if (result.success()) {
			e.setStatus(ExecutionStatus.SUCCEEDED);
			e.setCompletedAt(Instant.now());
			e.setExternalReference(result.externalReference());
			e.setResponseMetadata(writeObj(DiagnosticRedactor.sanitizeMetadata(result.responseMetadata())));
			e.setNextRetryAt(null);
			e.setRetryable(false);
			e.setErrorMessage(null);
			e.setErrorCode(null);
		} else {
			boolean retry = result.retryable() && e.getAttemptCount() < e.getMaxAttempts();
			e.setErrorCode(result.errorCode());
			e.setErrorMessage(DiagnosticRedactor.sanitize(result.errorMessage()));
			e.setResponseMetadata(writeObj(DiagnosticRedactor.sanitizeMetadata(result.responseMetadata())));
			if (retry) {
				e.setStatus(ExecutionStatus.RETRY_PENDING);
				e.setRetryable(true);
				e.setNextRetryAt(nextBackoff(e.getAttemptCount()));
				e.setClaimedBy(null); e.setClaimedAt(null); e.setClaimExpiresAt(null);
			} else {
				e.setStatus(ExecutionStatus.FAILED);
				e.setCompletedAt(Instant.now());
				e.setRetryable(false);
				e.setNextRetryAt(null);
			}
		}
		executions.save(e);
		audit.record(result.success() ? AuditAction.CREATE : AuditAction.UPDATE,
			"IntegrationExecution", e.getId(),
			"operation=" + e.getOperationType() + " result=" + e.getStatus()
				+ " attempts=" + e.getAttemptCount() + " latencyMs=" + latencyMs);
		log.info("integration.execution_done id={} op={} status={} attempts={} latencyMs={}",
			e.getId(), e.getOperationType(), e.getStatus(), e.getAttemptCount(), latencyMs);
	}

	private void fail(IntegrationExecution e, String code, String message, boolean retryable) {
		e.setStatus(retryable && e.getAttemptCount() < e.getMaxAttempts() ? ExecutionStatus.RETRY_PENDING : ExecutionStatus.FAILED);
		e.setErrorCode(code);
		e.setErrorMessage(DiagnosticRedactor.sanitize(message));
		e.setCompletedAt(Instant.now());
		e.setRetryable(retryable);
		if (e.getStatus() == ExecutionStatus.RETRY_PENDING) {
			e.setNextRetryAt(nextBackoff(e.getAttemptCount()));
			e.setClaimedBy(null); e.setClaimedAt(null); e.setClaimExpiresAt(null);
		} else {
			e.setNextRetryAt(null);
		}
		executions.save(e);
	}

	private String decrypt(IntegrationConfig c) {
		if (c.getEncryptedSecret() == null) return null;
		if (!crypto.isAvailable()) return null;
		try { return crypto.decrypt(c.getEncryptedSecret()); }
		catch (Exception ex) { return null; }
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> readObj(String s) {
		if (s == null || s.isBlank()) return Map.of();
		try { return (Map<String, Object>) json.readValue(s, Map.class); }
		catch (Exception ex) { return Map.of(); }
	}
	private String writeObj(Map<String, Object> m) {
		if (m == null || m.isEmpty()) return null;
		try { return json.writeValueAsString(m); }
		catch (Exception ex) { return null; }
	}

	private static boolean isRetryable(Exception ex) {
		// Network / timeout / I/O errors are retryable; business exceptions are not.
		if (ex instanceof BusinessException) return false;
		return ex instanceof java.io.IOException
			|| ex instanceof java.net.SocketTimeoutException
			|| ex instanceof java.net.ConnectException;
	}

	private static Instant nextBackoff(int attempt) {
		// Exponential: 30s, 2m, 8m.
		long[] seconds = {30, 120, 480};
		int idx = Math.min(Math.max(0, attempt - 1), seconds.length - 1);
		return Instant.now().plus(Duration.ofSeconds(seconds[idx]));
	}
}
