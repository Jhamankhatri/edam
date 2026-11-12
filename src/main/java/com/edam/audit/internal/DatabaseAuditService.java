package com.edam.audit.internal;

import com.edam.audit.domain.AuditEvent;
import com.edam.common.audit.AuditEventCommand;
import com.edam.common.audit.AuditEventPublisher;
import com.edam.common.logging.CorrelationIdProvider;
import com.edam.common.logging.LogSanitizer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists immutable audit trail records into PostgreSQL using an explicit transaction boundary.
 */
@Service
public class DatabaseAuditService implements AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuditService.class);

    private final AuditEventRepository auditEventRepository;

    public DatabaseAuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Override
    @Transactional
    public void publish(AuditEventCommand command) {
        String correlationId = (command.correlationId() == null || command.correlationId().isBlank())
                ? CorrelationIdProvider.currentOrNew()
                : LogSanitizer.sanitize(command.correlationId());
        String actorUsername = (command.actorUsername() == null || command.actorUsername().isBlank())
                ? "SYSTEM"
                : LogSanitizer.sanitize(command.actorUsername());

        AuditEvent event = new AuditEvent(
                command.institutionId(),
                correlationId,
                actorUsername,
                command.actorUserId(),
                command.actionType(),
                command.moduleName(),
                command.entityType(),
                command.entityId(),
                command.summary(),
                command.oldValuesJson(),
                command.newValuesJson(),
                command.sourceIp(),
                Instant.now());

        auditEventRepository.save(event);
        log.info("Audit event recorded module={} action={} entityType={} entityId={}",
                LogSanitizer.sanitize(command.moduleName()),
                LogSanitizer.sanitize(command.actionType()),
                LogSanitizer.sanitize(command.entityType()),
                LogSanitizer.sanitize(command.entityId()));
    }
}
