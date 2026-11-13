package com.edam.common.audit;

import java.util.Map;

import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.common.web.RequestContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit events (Module K).
 *
 * <h2>Propagation: {@code REQUIRES_NEW}</h2>
 * Evidence of a <em>business attempt</em> must survive the rollback of that attempt. If an
 * approval fails after the workflow advanced one step, the fact that it was attempted is exactly
 * what a control reviewer needs. Joining the caller's transaction would discard it on rollback,
 * so the audit write is deliberately a separate unit of work.
 *
 * <h2>Failure policy is explicit, not accidental</h2>
 * By default a failure to persist audit does not fail the business operation, because losing a
 * completed approval because the audit table is briefly unavailable would be worse than losing the
 * audit row. It is never <em>silent</em>: the failure is logged at ERROR with the correlation id.
 * A bank that requires fail-closed audit sets {@code edam.audit.fail-closed=true}, and then the
 * business operation fails too. Both behaviours are defensible; the choice is configuration, and
 * the trade-off is documented rather than baked in.
 *
 * <h2>Redaction before persistence</h2>
 * {@link AuditValueRedactor} runs on the maps <em>before</em> serialisation, so no code path exists
 * in which a password, token or secret reference reaches the JSONB columns.
 */
@Service
public class AuditRecorder {

	private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

	private final AuditRepository repository;

	private final Auditor auditor;

	private final InstitutionContext institutionContext;

	private final JsonMapper jsonMapper;

	private final ObjectProvider<RequestContext> requestContext;

	private final boolean failClosed;

	public AuditRecorder(AuditRepository repository, Auditor auditor, InstitutionContext institutionContext,
			JsonMapper jsonMapper, ObjectProvider<RequestContext> requestContext,
			@Value("${edam.audit.fail-closed:false}") boolean failClosed) {
		this.repository = repository;
		this.auditor = auditor;
		this.institutionContext = institutionContext;
		this.jsonMapper = jsonMapper;
		this.requestContext = requestContext;
		this.failClosed = failClosed;
	}

	/** Record an event with no state diff (export, download, denial, sign-in). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuditAction action, String entityType, Object entityId) {
		write(action, entityType, entityId, null, null);
	}

	/**
	 * Record an event with a human-readable reason.
	 *
	 * <p>Security events need to say <em>why</em>, not just <em>what</em>: "sign-in refused" is not
	 * actionable in an investigation, "sign-in refused because the account is locked" is. The reason is
	 * redacted exactly like a state diff, so a caller cannot accidentally persist a credential by passing
	 * one in.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuditAction action, String entityType, Object entityId, String reason) {
		write(action, entityType, entityId, null,
				(reason == null) ? null : java.util.Map.of("reason", reason));
	}

	/** Record a state change with redacted before/after maps. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordChange(AuditAction action, String entityType, Object entityId, Map<String, Object> before,
			Map<String, Object> after) {
		write(action, entityType, entityId, before, after);
	}

	private void write(AuditAction action, String entityType, Object entityId, Map<String, Object> before,
			Map<String, Object> after) {
		try {
			AuditEvent.Builder builder = AuditEvent.builder(action, entityType)
				.entityId(entityId)
				.oldValues(serialise(AuditValueRedactor.redact(before)))
				.newValues(serialise(AuditValueRedactor.redact(after)))
				.correlationId(CorrelationId.current());
			this.institutionContext.currentPrincipal().ifPresent(principal -> {
				builder.performedBy(principal.userId());
				builder.performedByUsername(principal.getUsername());
			});
			this.requestContext.ifAvailable(context -> {
				builder.ipAddress(context.clientIp());
				builder.userAgent(context.userAgent());
			});
			this.repository.save(builder.build());
		}
		catch (RuntimeException ex) {
			// The payload is never logged: even redacted, it carries business detail.
			log.error("audit.write_failed action={} entityType={} entityId={} correlationId={} type={}", action,
					entityType, entityId, CorrelationId.current(), ex.getClass().getName(), ex);
			if (this.failClosed) {
				throw ex;
			}
		}
	}

	private String serialise(Map<String, Object> state) {
		if (state == null || state.isEmpty()) {
			return null;
		}
		try {
			return this.jsonMapper.writeValueAsString(state);
		}
		catch (RuntimeException ex) {
			// A diff that cannot be serialised is marked, never replaced by the raw value.
			return "{\"unserialisable\":true}";
		}
	}

	/** Actor snapshot for read models. */
	public record ActorInfo(java.util.UUID userId, String username, java.util.Set<String> roleCodes) {
		public static ActorInfo of(EdamPrincipal principal) {
			return new ActorInfo(principal.userId(), principal.getUsername(), principal.roleCodes());
		}
	}

}
