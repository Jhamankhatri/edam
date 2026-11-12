package com.edam.common.audit;

import java.util.Map;

import com.edam.common.logging.CorrelationId;
import com.edam.common.security.EdamPrincipal;
import com.edam.common.security.InstitutionContext;
import com.edam.common.web.RequestContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Writes audit events.
 *
 * <h2>Propagation: {@code REQUIRES_NEW}</h2>
 * An audit record for a <em>business attempt</em> must survive the rollback of that attempt. When
 * an approval action fails after the workflow has advanced one step, the evidence that the action
 * was attempted is exactly what a control reviewer needs. Recording it in the caller's transaction
 * would discard it. This is the deliberate exception to "one transaction per use case"; the audit
 * write is not part of the business unit of work, it observes it.
 *
 * <h2>Never breaks the business path</h2>
 * A failure to persist an audit record must not fail a payment-adjacent business operation
 * silently... but it must never be silent either. Failures are logged at ERROR with the
 * correlation id, and the business operation continues: losing a request submission because the
 * audit table is unavailable would be worse. The trade-off is recorded here and in
 * {@code docs/SECURITY_AND_CONFIGURATION_GUIDE.md}; banks that require fail-closed audit can flip
 * {@code edam.audit.fail-closed=true}.
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
			@org.springframework.beans.factory.annotation.Value("${edam.audit.fail-closed:false}") boolean failClosed) {
		this.repository = repository;
		this.auditor = auditor;
		this.institutionContext = institutionContext;
		this.jsonMapper = jsonMapper;
		this.requestContext = requestContext;
		this.failClosed = failClosed;
	}

	/** Record an event with no state diff (e.g. export, download, denial). */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuditAction action, String resourceType, Object resourceId, String summary) {
		write(action, resourceType, resourceId, summary, null, null);
	}

	/** Record an event with redacted before/after maps. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordChange(AuditAction action, String resourceType, Object resourceId, String summary,
			Map<String, Object> before, Map<String, Object> after) {
		write(action, resourceType, resourceId, summary, before, after);
	}

	private void write(AuditAction action, String resourceType, Object resourceId, String summary,
			Map<String, Object> before, Map<String, Object> after) {
		try {
			AuditEvent.Builder builder = AuditEvent.builder(action, resourceType)
				.resourceId(resourceId)
				.summary(summary)
				.actor(this.auditor.actor())
				.actorUsername(this.auditor.username())
				.actorRoles(rolesOfCurrentUser())
				.correlationId(CorrelationId.current())
				.institutionId(this.institutionContext.currentInstitutionId().orElse(null))
				.beforeState(serialise(AuditValueRedactor.redact(before)))
				.afterState(serialise(AuditValueRedactor.redact(after)));
			this.requestContext.ifAvailable(context -> {
				builder.sourceIp(context.clientIp());
			});
			this.repository.save(builder.build());
		}
		catch (RuntimeException ex) {
			// Never log the payload: it may contain business detail even after redaction.
			log.error("audit.write_failed action={} resourceType={} resourceId={} correlationId={} type={}", action,
					resourceType, resourceId, CorrelationId.current(), ex.getClass().getName(), ex);
			if (this.failClosed) {
				throw ex;
			}
		}
	}

	private String rolesOfCurrentUser() {
		return this.institutionContext.currentPrincipal().map(principal -> {
			var selected = this.institutionContext.currentInstitution().orElse(null);
			if (selected == null) {
				return null;
			}
			return String.join(",", principal.rolesIn(selected));
		}).orElse(null);
	}

	private String serialise(Map<String, Object> state) {
		if (state == null || state.isEmpty()) {
			return null;
		}
		try {
			return this.jsonMapper.writeValueAsString(state);
		}
		catch (RuntimeException ex) {
			// A diff that cannot be serialised is replaced by a marker, never by the raw value.
			return "{\"unserialisable\":true}";
		}
	}

	/** Actor snapshot for read models, so a caller does not need the principal plumbing. */
	public record ActorInfo(String employeeId, String username, java.util.Set<String> roles) {
		public static ActorInfo of(EdamPrincipal principal, String institutionCode) {
			return new ActorInfo(principal.employeeId(), principal.getUsername(), principal.rolesIn(institutionCode));
		}
	}

}
