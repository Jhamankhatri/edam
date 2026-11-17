package com.edam.integrations.service.adapters;

import java.util.*;

import org.springframework.stereotype.Component;

/**
 * Resolves an {@link IntegrationAdapter} by its {@link IntegrationAdapter#providerType()}.
 *
 * <p>An adapter registered here is the ONLY way a provider type becomes runnable:
 * unsupported types are rejected at configuration write-time, so no provider string in the
 * database can ever reach a "successful" state without a real adapter.
 */
@Component
public class IntegrationAdapterRegistry {

	private final Map<String, IntegrationAdapter> byType;

	public IntegrationAdapterRegistry(List<IntegrationAdapter> adapters) {
		Map<String, IntegrationAdapter> m = new TreeMap<>();
		for (IntegrationAdapter a : adapters) {
			String t = a.providerType();
			if (t == null || t.isBlank()) {
				throw new IllegalStateException("IntegrationAdapter " + a.getClass().getName() + " returned a blank providerType().");
			}
			if (m.putIfAbsent(t, a) != null) {
				throw new IllegalStateException("Duplicate integration adapter registered for type: " + t);
			}
		}
		this.byType = Collections.unmodifiableMap(m);
	}

	public Optional<IntegrationAdapter> find(String providerType) {
		if (providerType == null) return Optional.empty();
		return Optional.ofNullable(byType.get(providerType));
	}

	public IntegrationAdapter require(String providerType) {
		return find(providerType).orElseThrow(() ->
			new com.edam.common.exception.BusinessException(
				com.edam.common.api.ErrorCode.VALIDATION_FAILED,
				"Unsupported integration provider type: " + providerType));
	}

	public Set<String> supportedProviderTypes() { return byType.keySet(); }

	public List<Map<String, Object>> catalogue() {
		return byType.values().stream().map(a -> Map.<String, Object>of(
				"type", a.providerType(),
				"name", a.displayName(),
				"authModes", a.supportedAuthModes().stream().map(Enum::name).toList(),
				"operations", List.copyOf(a.supportedOperations()),
				"requiresEndpointUrl", a.requiresEndpointUrl()
		)).toList();
	}
}
