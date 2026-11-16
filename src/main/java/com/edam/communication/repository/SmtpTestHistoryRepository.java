package com.edam.communication.repository;

import java.util.List;
import java.util.UUID;

import com.edam.communication.model.SmtpTestHistory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpTestHistoryRepository extends JpaRepository<SmtpTestHistory, UUID> {
	List<SmtpTestHistory> findBySmtpConfigIdOrderBySentAtDesc(UUID smtpConfigId, Pageable pageable);
}
