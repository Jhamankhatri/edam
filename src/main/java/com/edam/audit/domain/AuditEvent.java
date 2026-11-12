package com.edam.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Append-only immutable audit trail entry capturing configuration and workflow actions
 * with actor identity, institution context, and correlation ID for end-to-end traceability.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "actor_username", nullable = false, length = 120)
    private String actorUsername;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "module_name", nullable = false, length = 64)
    private String moduleName;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "old_values_json", columnDefinition = "text")
    private String oldValuesJson;

    @Column(name = "new_values_json", columnDefinition = "text")
    private String newValuesJson;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            Long institutionId,
            String correlationId,
            String actorUsername,
            Long actorUserId,
            String actionType,
            String moduleName,
            String entityType,
            String entityId,
            String summary,
            String oldValuesJson,
            String newValuesJson,
            String sourceIp,
            Instant occurredAt) {
        this.institutionId = institutionId;
        this.correlationId = correlationId;
        this.actorUsername = actorUsername;
        this.actorUserId = actorUserId;
        this.actionType = actionType;
        this.moduleName = moduleName;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.oldValuesJson = oldValuesJson;
        this.newValuesJson = newValuesJson;
        this.sourceIp = sourceIp;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getInstitutionId() {
        return institutionId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getSummary() {
        return summary;
    }

    public String getOldValuesJson() {
        return oldValuesJson;
    }

    public String getNewValuesJson() {
        return newValuesJson;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
