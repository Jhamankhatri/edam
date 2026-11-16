package com.edam.communication.dto;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TemplateUpsertRequest(
	@NotBlank @Pattern(regexp = "[A-Z0-9_]{2,64}") String code,
	@NotBlank @Size(max = 200) String displayName,
	@Size(max = 1000) String description,
	@NotBlank @Pattern(regexp = "[A-Z_]{2,64}") String eventCode,
	@NotBlank @Size(max = 500) String subject,
	@NotBlank String htmlBody,
	String textBody,
	@Size(max = 16) String locale,
	Boolean active,
	Set<String> allowedPlaceholders,
	@Size(max = 500) String changeNote
) {}
