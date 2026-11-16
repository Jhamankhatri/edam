package com.edam.communication.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound view of SMTP configuration. The password is never returned; only a
 * {@code passwordSet} flag so the UI can show whether one is stored.
 */
public record SmtpConfigurationResponse(
	UUID id,
	String host,
	int port,
	String username,
	boolean passwordSet,
	String securityMode,
	String fromEmail,
	String fromDisplayName,
	String replyTo,
	int connectionTimeoutMs,
	int readTimeoutMs,
	boolean active,
	Instant lastTestAt,
	Boolean lastTestSuccess,
	String lastTestMessage,
	Instant createdAt,
	Instant updatedAt
) {}
