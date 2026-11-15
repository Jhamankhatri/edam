package com.edam.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.edam.storage.StoredObject;
import com.edam.storage.StorageException;
import com.edam.storage.StorageProvider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a no-op in-memory {@link StorageProvider} when no other provider (Cloudinary,
 * test fake) is registered. This is an explicit safety valve: it lets the application
 * start in environments that have no storage credentials, but uploads are rejected with
 * a clear error rather than silently storing files on the local disk. Deployments must
 * wire Cloudinary (or another real provider) for document operations to work.
 */
@Configuration
public class StorageAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(StorageProvider.class)
	public StorageProvider unconfiguredStorageProvider() {
		return new StorageProvider() {
			private final ConcurrentMap<String, StoredObject> store = new ConcurrentHashMap<>();
			@Override public String providerName() { return "LOCAL"; }
			@Override public boolean isConfigured() { return false; }
			@Override public StoredObject upload(String pid, String folder, String ct, InputStream in) {
				throw new StorageException("No document storage provider is configured. Configure Cloudinary credentials.");
			}
			@Override public String signDownloadUrl(String pid, String rt, long ttl) {
				throw new StorageException("No document storage provider is configured.");
			}
			@Override public void delete(String pid, String rt) {
				this.store.remove(pid);
			}
		};
	}
}
