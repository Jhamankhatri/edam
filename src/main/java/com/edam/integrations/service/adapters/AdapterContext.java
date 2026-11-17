package com.edam.integrations.service.adapters;

import java.util.Map;

import com.edam.integrations.model.IntegrationConfig.AuthType;

/**
 * Runtime context passed to an adapter invocation.
 *
 * <p>{@code secret} is the decrypted credential (or {@code null} when authType=NONE). Adapters
 * must never log or persist this value. It is provided here rather than fetched from the
 * config so that adapters cannot accidentally read {@code encryptedSecret} directly.
 */
public record AdapterContext(
		String providerType,
		String endpointUrl,
		AuthType authType,
		String username,
		String secret,
		Map<String, Object> configuration) {}
