package com.edam.communication.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Inbound DTO for SMTP configuration. {@code password} is {@code null} when the
 * caller is updating non-credential fields and wants the existing credential preserved;
 * an explicit empty string clears the credential.
 */
public record SmtpConfigurationRequest(
	@NotBlank @Size(max = 255) String host,
	@Min(1) @Max(65535) int port,
	@Size(max = 255) String username,
	/** {@code null} = keep existing; {@code ""} = clear; non-empty = replace. */
	String password,
	@Pattern(regexp = "NONE|STARTTLS|SSL_TLS") String securityMode,
	@NotBlank @Email @Size(max = 255) String fromEmail,
	@Size(max = 255) String fromDisplayName,
	@Email @Size(max = 255) String replyTo,
	@Min(1_000) @Max(60_000) Integer connectionTimeoutMs,
	@Min(1_000) @Max(120_000) Integer readTimeoutMs,
	Boolean active
) {}
