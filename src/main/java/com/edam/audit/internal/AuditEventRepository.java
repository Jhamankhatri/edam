package com.edam.audit.internal;

import com.edam.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
