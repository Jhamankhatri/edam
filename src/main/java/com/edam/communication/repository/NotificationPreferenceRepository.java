package com.edam.communication.repository;

import java.util.UUID;

import com.edam.communication.model.NotificationPreference;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
	java.util.Optional<NotificationPreference> findByUserId(UUID userId);
}
