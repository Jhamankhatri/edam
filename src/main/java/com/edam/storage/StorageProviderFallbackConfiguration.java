package com.edam.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Installs a fail-fast {@link StorageProvider} when no real provider (Cloudinary, test
 * fake) is in the context. Without this, production deployments that forget to configure
 * Cloudinary would fail at startup with "no bean of type StorageProvider" rather than at
 * first upload with a clear operator-facing error.
 */
@Configuration
public class StorageProviderFallbackConfiguration {

	@Bean
	@ConditionalOnMissingBean(StorageProvider.class)
	public StorageProvider unconfiguredStorageProvider() {
		return new StorageProvider() {
			@Override public String providerName() { return "LOCAL"; }
			@Override public boolean isConfigured() { return false; }
			@Override public StoredObject upload(String pid, String folder, String ct, InputStream in) {
				throw new StorageException("No document storage provider is configured. Set CLOUDINARY_* credentials.");
			}
			@Override public String signDownloadUrl(String pid, String rt, long ttl) {
				throw new StorageException("No document storage provider is configured.");
			}
			@Override public void delete(String pid, String rt) { /* nothing to delete */ }
		};
	}
}
