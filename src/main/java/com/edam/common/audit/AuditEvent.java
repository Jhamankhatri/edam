package com.edam.common.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One immutable audit record (Module K).
 *
 * <h2>Append-only in three layers</h2>
 * <ol>
 *   <li>This entity has no setters and no {@code @Version}: it cannot be updated through JPA
 *       because there is no state to change and no version to bump.</li>
 *   <li>The repository is append-only: it extends {@code Repository} rather than
 *       {@code JpaRepository}, so {@code save} on an existing instance is not even reachable.</li>
 *   <li>PostgreSQL triggers reject {@code UPDATE} and {@code DELETE} outright (migration V10).
 *       A maintenance script, a careless migration or a future ORM feature cannot rewrite
 *       history.</li>
 * </ol>
 * The third layer is what makes "administrators must not be able to modify or delete audit
 * history" a property of the system rather than a promise about the application.
 *
 * <h2>before/after as JSONB</h2>
 * An audit diff is genuinely heterogeneous — a currency change, a workflow transition and a
 * permission grant share no columns. A JSONB pair is the correct shape here, unlike the business
 * entities where a JSONB catch-all would hide the model. Values are redacted by
 * {@link AuditValueRedactor} <em>before</em> they reach this entity: this class never sees a
 * password, token or secret reference.
 *
 * <h2>Why the actor is stored twice</h2>
 * {@code performedBy} is the foreign key, used for joins and for "everything this user did".
 * {@code performedByUsername} is a snapshot, so the trail still reads correctly after a user is
 * renamed or deactivated. An audit record that becomes unreadable when an account is retired is not
 * an audit record.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

	/** Largest serialised payload per side; a runaway diff must not break the write path. */
	static final int MAX_PAYLOAD_LENGTH = 8000;

	@Id
	@Column(name = "id", nullable = false, updatable = false)
	private UUID id;

	@Column(name = "entity_type", nullable = false, updatable = false, length = 100)
	private String entityType;

	@Column(name = "entity_id", updatable = false, length = 100)
	private String entityId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, updatable = false, length = 60)
	private AuditAction action;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "old_values", updatable = false, length = MAX_PAYLOAD_LENGTH)
	private String oldValues;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "new_values", updatable = false, length = MAX_PAYLOAD_LENGTH)
	private String newValues;

	@Column(name = "performed_by", updatable = false)
	private UUID performedBy;

	@Column(name = "performed_by_username", updatable = false, length = 100)
	private String performedByUsername;

	@Column(name = "ip_address", updatable = false, length = 64)
	private String ipAddress;

	@Column(name = "user_agent", updatable = false, length = 500)
	private String userAgent;

	@Column(name = "correlation_id", updatable = false, length = 64)
	private String correlationId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditEvent() {
		// JPA
	}

	private AuditEvent(Builder builder) {
		this.id = (builder.id == null) ? java.util.UUID.randomUUID() : builder.id;
		this.entityType = builder.entityType;
		this.entityId = truncate(builder.entityId, 100);
		this.action = builder.action;
		this.oldValues = truncate(builder.oldValues, MAX_PAYLOAD_LENGTH);
		this.newValues = truncate(builder.newValues, MAX_PAYLOAD_LENGTH);
		this.performedBy = builder.performedBy;
		this.performedByUsername = truncate(builder.performedByUsername, 100);
		this.ipAddress = truncate(builder.ipAddress, 64);
		this.userAgent = truncate(builder.userAgent, 500);
		this.correlationId = truncate(builder.correlationId, 64);
		this.createdAt = (builder.createdAt == null) ? Instant.now() : builder.createdAt;
	}

	public static Builder builder(AuditAction action, String entityType) {
		return new Builder(action, entityType);
	}

	private static String truncate(String value, int max) {
		if (value == null) {
			return null;
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	public UUID getId() {
		return this.id;
	}

	public String getEntityType() {
		return this.entityType;
	}

	public String getEntityId() {
		return this.entityId;
	}

	public AuditAction getAction() {
		return this.action;
	}

	public String getOldValues() {
		return this.oldValues;
	}

	public String getNewValues() {
		return this.newValues;
	}

	public UUID getPerformedBy() {
		return this.performedBy;
	}

	public String getPerformedByUsername() {
		return this.performedByUsername;
	}

	public String getIpAddress() {
		return this.ipAddress;
	}

	public String getUserAgent() {
		return this.userAgent;
	}

	public String getCorrelationId() {
		return this.correlationId;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

	/** Mutable builder used by {@link AuditRecorder}; not part of the public surface. */
	public static final class Builder {

		private final AuditAction action;

		private final String entityType;

		private UUID id;

		private String entityId;

		private String oldValues;

		private String newValues;

		private UUID performedBy;

		private String performedByUsername;

		private String ipAddress;

		private String userAgent;

		private String correlationId;

		private Instant createdAt = Instant.now();

		private Builder(AuditAction action, String entityType) {
			if (action == null) {
				throw new IllegalArgumentException("An audit event requires an action");
			}
			if (entityType == null || entityType.isBlank()) {
				throw new IllegalArgumentException("An audit event requires an entity type");
			}
			this.action = action;
			this.entityType = entityType;
		}

		public Builder id(UUID id) {
			this.id = id;
			return this;
		}

		public Builder entityId(Object entityId) {
			this.entityId = (entityId == null) ? null : String.valueOf(entityId);
			return this;
		}

		public Builder oldValues(String oldValues) {
			this.oldValues = oldValues;
			return this;
		}

		public Builder newValues(String newValues) {
			this.newValues = newValues;
			return this;
		}

		public Builder performedBy(UUID performedBy) {
			this.performedBy = performedBy;
			return this;
		}

		public Builder performedByUsername(String performedByUsername) {
			this.performedByUsername = performedByUsername;
			return this;
		}

		public Builder ipAddress(String ipAddress) {
			this.ipAddress = ipAddress;
			return this;
		}

		public Builder userAgent(String userAgent) {
			this.userAgent = userAgent;
			return this;
		}

		public Builder correlationId(String correlationId) {
			this.correlationId = correlationId;
			return this;
		}

		public Builder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		public AuditEvent build() {
			return new AuditEvent(this);
		}

	}

}
