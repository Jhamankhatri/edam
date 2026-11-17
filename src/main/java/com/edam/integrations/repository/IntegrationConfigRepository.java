package com.edam.integrations.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.integrations.model.IntegrationConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, UUID> {

	Optional<IntegrationConfig> findByCodeAndArchivedFalse(String code);
	Optional<IntegrationConfig> findByIdAndArchivedFalse(UUID id);
	List<IntegrationConfig> findByArchivedFalseOrderByCreatedAtDesc();
	boolean existsByCodeAndArchivedFalse(String code);

	/** Pessimistic write used for secret rotation and connection-test updates. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<IntegrationConfig> findWithLockById(UUID id);
}
