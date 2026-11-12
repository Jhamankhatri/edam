package com.edam.common.audit;

/**
 * Module-decoupled port for publishing immutable audit events from application services
 * without directly coupling business modules to the audit persistence layer.
 */
public interface AuditEventPublisher {

    void publish(AuditEventCommand command);
}
