package com.edam.common.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An immutable audit event (BRD AUD-01, AUD-02, §26).
 *
 * <h2>Append-only by construction</h2>
 * This entity deliberately does not extend {@link AuditableEntity}: it has no updater columns and
 * no optimistic-lock version, because an audit record must never be modified. Immutability is
 * also enforced in the database by a trigger that rejects {@code UPDATE} and {@code DELETE}
 * (see migration {@code V3}), so an application bug or a careless repository method cannot
 * rewrite history. BRD §26 requires exactly this: "protected from ordinary application-user
 * modification and deletion".
 *
 * <h2>What is recorded, and what is not</h2>
 * Actor, action, resource, timestamp, correlation id and before/after values. Sensitive values are
 * redacted <em>before</em> they reach this entity (see {@link AuditValueRedactor}); the entity
 * itself never sees a secret. BRD AUD-02 and §28.4 both forbid recording credential values.
 *
 * <h2>Tenant context</h2>
 * {@code institutionId} is nullable because platform-level events (institution onboarding) are not
 * owned by a tenant. Every tenant-owned event carries it, and queries always filter on it.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

	/** Largest serialised payload accepted per side; a runaway diff must not break the write path. */
	static final int MAX_PAYLOAD_LENGTH = 8000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "institution_id")
	private Long institutionId;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "actor", nullable = false, updatable = false, length = 64)
	private String actor;

	@Column(name = "actor_username", length = 64, updatable = false)
	private String actorUsername;

	/** Free-form role context at the time of the action, for compliance evidence. */
	@Column(name = "actor_roles", length = 500, updatable = false)
	private String actorRoles;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, updatable = false, length = 32)
	private AuditAction action;

	/** Logical resource type, e.g. {@code OpexRequest}, {@code Currency}. */
	@Column(name = "resource_type", nullable = false, updatable = false, length = 64)
	private String resourceType;

	/** Identifier of the affected record, as text so it can address any entity. */
	@Column(name = "resource_id", length = 64, updatable = false)
	private String resourceId;

	/** Human-readable summary; never contains a secret or a full financial document. */
	@Column(name = "summary", length = 500, updatable = false)
	private String summary;

	/** JSON snapshot before the change. Redacted. */
	@Column(name = "before_state", length = MAX_PAYLOAD_LENGTH, updatable = false)
	private String beforeState;

	/** JSON snapshot after the change. Redacted. */
	@Column(name = "after_state", length = MAX_PAYLOAD_LENGTH, updatable = false)
	private String afterState;

	@Column(name = "correlation_id", length = 64, updatable = false)
	private String correlationId;

	/** Source address, stored only where law and bank policy permit it (BRD §12.4). */
	@Column(name = "source_ip", length = 64, updatable = false)
	private String sourceIp;

	protected AuditEvent() {
		// JPA
	}

	private AuditEvent(Long institutionId, Instant occurredAt, String actor, String actorUsername, String actorRoles,
			AuditAction action, String resourceType, String resourceId, String summary, String beforeState,
			String afterState, String correlationId, String sourceIp) {
		this.institutionId = institutionId;
		this.occurredAt = occurredAt;
		this.actor = actor;
		this.actorUsername = actorUsername;
		this.actorRoles = actorRoles;
		this.action = action;
		this.resourceType = resourceType;
		this.resourceId = resourceId;
		this.summary = truncate(summary, 500);
		this.beforeState = truncate(beforeState, MAX_PAYLOAD_LENGTH);
		this.afterState = truncate(afterState, MAX_PAYLOAD_LENGTH);
		this.correlationId = correlationId;
		this.sourceIp = sourceIp;
	}

	/** Fluent factory; the recorder supplies the actor/institution context. */
	public static Builder builder(AuditAction action, String resourceType) {
		return new Builder(action, resourceType);
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	public Long getId() {
		return this.id;
	}

	public Long getInstitutionId() {
		return this.institutionId;
	}

	public Instant getOccurredAt() {
		return this.occurredAt;
	}

	public String getActor() {
		return this.actor;
	}

	public String getActorUsername() {
		return this.actorUsername;
	}

	public String getActorRoles() {
		return this.actorRoles;
	}

	public AuditAction getAction() {
		return this.action;
	}

	public String getResourceType() {
		return this.resourceType;
	}

	public String getResourceId() {
		return this.resourceId;
	}

	public String getSummary() {
		return this.summary;
	}

	public String getBeforeState() {
		return this.beforeState;
	}

	public String getAfterState() {
		return this.afterState;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	public String getSourceIp() {
		return this.sourceIp;
	}

	/** Mutable builder used by {@link AuditRecorder}; not part of the public surface. */
	public static final class Builder {

		private final AuditAction action;

		private final String resourceType;

		private Long institutionId;

		private Instant occurredAt = Instant.now();

		private String actor;

		private String actorUsername;

		private String actorRoles;

		private String resourceId;

		private String summary;

		private String beforeState;

		private String afterState;

		private String correlationId;

		private String sourceIp;

		private Builder(AuditAction action, String resourceType) {
			this.action = action;
			this.resourceType = resourceType;
		}

		public Builder institutionId(Long institutionId) {
			this.institutionId = institutionId;
			return this;
		}

		public Builder occurredAt(Instant occurredAt) {
			this.occurredAt = occurredAt;
			return this;
		}

		public Builder actor(String actor) {
			this.actor = actor;
			return this;
		}

		public Builder actorUsername(String actorUsername) {
			this.actorUsername = actorUsername;
			return this;
		}

		public Builder actorRoles(String actorRoles) {
			this.actorRoles = actorRoles;
			return this;
		}

		public Builder resourceId(Object resourceId) {
			this.resourceId = (resourceId == null) ? null : String.valueOf(resourceId);
			return this;
		}

		public Builder summary(String summary) {
			this.summary = summary;
			return this;
		}

		public Builder beforeState(String beforeState) {
			this.beforeState = beforeState;
			return this;
		}

		public Builder afterState(String afterState) {
			this.afterState = afterState;
			return this;
		}

		public Builder correlationId(String correlationId) {
			this.correlationId = correlationId;
			return this;
		}

		public Builder sourceIp(String sourceIp) {
			this.sourceIp = sourceIp;
			return this;
		}

		public AuditEvent build() {
			return new AuditEvent(this.institutionId, this.occurredAt, this.actor, this.actorUsername, this.actorRoles,
					this.action, this.resourceType, this.resourceId, this.summary, this.beforeState, this.afterState,
					this.correlationId, this.sourceIp);
		}

	}

}
