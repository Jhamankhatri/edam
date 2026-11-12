package com.edam.common.audit;

/**
 * Immutable command describing a domain or configuration audit event to persist.
 */
public record AuditEventCommand(
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
        String sourceIp) {
}
