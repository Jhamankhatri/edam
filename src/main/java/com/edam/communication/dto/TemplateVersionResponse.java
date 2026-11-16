package com.edam.communication.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record TemplateVersionResponse(
	UUID id,
	UUID templateId,
	int versionNumber,
	String subject,
	String htmlBody,
	String textBody,
	String locale,
	String changeNote,
	Set<String> allowedPlaceholders,
	UUID createdBy,
	Instant createdAt
) {}
