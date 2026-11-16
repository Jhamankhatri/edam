package com.edam.communication.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.edam.communication.model.EmailTemplate;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
	Optional<EmailTemplate> findByCode(String code);
	List<EmailTemplate> findAllByOrderByCodeAsc();
	boolean existsByCode(String code);
	Optional<EmailTemplate> findByEventCodeAndActiveTrue(String eventCode);
}
