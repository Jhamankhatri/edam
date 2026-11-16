package com.edam.communication.repository;

import java.util.Optional;
import java.util.UUID;

import com.edam.communication.model.SmtpConfiguration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SmtpConfigurationRepository extends JpaRepository<SmtpConfiguration, UUID> {
	Optional<SmtpConfiguration> findFirstByActiveTrueOrderByUpdatedAtDesc();
}
