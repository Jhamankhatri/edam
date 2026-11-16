package com.edam.communication.dto;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EventConfigRequest(
	@NotNull Boolean emailEnabled,
	@NotNull Boolean inAppEnabled,
	UUID emailTemplateId,
	@NotNull Set<@Pattern(regexp = "[A-Z_]+") String> recipientGroups,
	@NotNull Boolean active
) {}
