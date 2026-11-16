package com.edam.communication.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TemplateResponse(
	UUID id,
	String code,
	String displayName,
	String description,
	String eventCode,
	String subject,
	String htmlBody,
	String textBody,
	String locale,
	boolean active,
	int currentVersion,
	Set<String> allowedPlaceholders,
	UUID createdBy,
	UUID updatedBy,
	Instant createdAt,
	Instant updatedAt
) {}
