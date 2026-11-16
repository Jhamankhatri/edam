package com.edam.communication.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventConfigResponse(
	UUID id,
	String eventCode,
	String eventDescription,
	boolean mandatory,
	boolean emailEnabled,
	boolean inAppEnabled,
	UUID emailTemplateId,
	List<String> recipientGroups,
	boolean active
) {}
