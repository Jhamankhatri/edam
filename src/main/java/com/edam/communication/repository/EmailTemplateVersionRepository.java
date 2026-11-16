package com.edam.communication.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.communication.model.EmailTemplateVersion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateVersionRepository extends JpaRepository<EmailTemplateVersion, UUID> {
	List<EmailTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(UUID templateId);
	Optional<EmailTemplateVersion> findFirstByTemplateIdOrderByVersionNumberDesc(UUID templateId);
	boolean existsByTemplateIdAndVersionNumber(UUID templateId, int versionNumber);
}
