package com.edam.communication.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
	UUID id,
	String eventCode,
	String title,
	String body,
	String linkUrl,
	boolean read,
	Instant readAt,
	Instant createdAt
) {}
