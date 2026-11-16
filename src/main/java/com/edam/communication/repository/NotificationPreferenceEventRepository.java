package com.edam.communication.repository;

import java.util.List;
import java.util.UUID;

import com.edam.communication.model.NotificationPreferenceEvent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceEventRepository extends JpaRepository<NotificationPreferenceEvent, UUID> {
	List<NotificationPreferenceEvent> findByPreferenceId(UUID preferenceId);
}
