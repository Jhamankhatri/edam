package com.edam.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Provider-neutral interface for binary object storage. Implementations talk to one
 * backend (Cloudinary, object storage, local FS for tests). Services in
 * {@code com.edam.documents} depend on this interface only — they never import Cloudinary
 * SDK types directly.
 */
public interface StorageProvider {

	/** Stable provider tag matching the {@code ck_media_assets_provider} CHECK. */
	String providerName();

	/** True when credentials/configuration are present so uploads can be attempted. */
	boolean isConfigured();

	/**
	 * Upload the bytes from {@code content} to private storage. Caller is responsible for
	 * closing the stream. The returned {@link StoredObject} carries the provider-assigned
	 * identifiers; the server controls public-id naming and delivery type so clients
	 * cannot force public access.
	 *
	 * @param suggestedPublicId server-generated id (not client-chosen)
	 * @param folder            namespace folder
	 * @param contentType       sanitised content type (may be used as hint)
	 * @param content           stream of bytes
	 */
	StoredObject upload(String suggestedPublicId, String folder, String contentType, InputStream content)
			throws IOException, StorageException;

	/**
	 * Return a short-lived, time-limited URL the caller can use to fetch the object. The
	 * URL must not be guessable and must expire (Cloudinary signed/authenticated delivery).
	 * Must only be called after authorisation succeeds.
	 */
	String signDownloadUrl(String publicId, String resourceType, long ttlSeconds) throws StorageException;

	/**
	 * Delete the object from the provider. Implementations should not throw if the object
	 * is already gone (idempotent deletion).
	 */
	void delete(String publicId, String resourceType) throws StorageException;
}
