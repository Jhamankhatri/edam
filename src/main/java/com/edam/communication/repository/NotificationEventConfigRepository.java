package com.edam.communication.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.communication.model.NotificationEventConfig;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEventConfigRepository extends JpaRepository<NotificationEventConfig, UUID> {
	Optional<NotificationEventConfig> findByEventCode(String eventCode);
	List<NotificationEventConfig> findAllByOrderByEventCodeAsc();
}
