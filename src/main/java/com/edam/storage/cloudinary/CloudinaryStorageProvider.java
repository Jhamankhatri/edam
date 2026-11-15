package com.edam.storage.cloudinary;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import com.edam.config.CloudinaryProperties;
import com.edam.storage.StoredObject;
import com.edam.storage.StorageException;
import com.edam.storage.StorageProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Cloudinary-backed {@link StorageProvider}.
 *
 * <p>Uploads always use {@code type=authenticated} so assets are only reachable with a
 * signed URL; the SDK's {@code apiSignRequest} produces time-limited download URLs that
 * we return to callers after an authorization check. The public-id is server-generated
 * (UUIDv7-style) and placed under the configured {@code folder}; clients cannot choose
 * the path or force {@code type=upload} (public delivery).
 */
@Component
public class CloudinaryStorageProvider implements StorageProvider {

	private static final String NAME = "CLOUDINARY";

	private final CloudinaryProperties props;
	private final Cloudinary cloudinary;

	public CloudinaryStorageProvider(CloudinaryProperties props, ObjectProvider<Cloudinary> cloudinaryProvider) {
		this.props = props;
		// Prefer the container-managed client (which is only present when credentials are set);
		// this avoids duplicating the URL construction logic and keeps credential handling in
		// one place. When the bean is absent the provider reports isConfigured()=false.
		Cloudinary c = cloudinaryProvider.getIfAvailable();
		if (c == null && props.isConfigured()) {
			c = new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
					+ "@" + props.getCloudName());
		}
		this.cloudinary = c;
	}

	@Override public String providerName() { return NAME; }

	@Override
	public boolean isConfigured() { return this.cloudinary != null; }

	@Override
	@SuppressWarnings("unchecked")
	public StoredObject upload(String suggestedPublicId, String folder, String contentType, InputStream content)
			throws IOException, StorageException {
		if (!isConfigured()) {
			throw new StorageException("Cloudinary credentials are not configured.");
		}
		Map<String, Object> params = new HashMap<>();
		params.put("public_id", suggestedPublicId);
		params.put("folder", folder == null ? this.props.getFolder() : folder);
		params.put("resource_type", "raw"); // treat all banking documents as opaque blobs
		params.put("type", this.props.getDeliveryType()); // 'authenticated' — not public
		params.put("overwrite", false);
		if (contentType != null) params.put("context", "mime=" + contentType);
		try {
			Map<String, Object> result = this.cloudinary.uploader().uploadLarge(content, params);
			String publicId = (String) result.get("public_id");
			String assetId = (String) result.get("asset_id");
			String secureUrl = (String) result.get("secure_url");
			String format = (String) result.get("format");
			String resourceType = (String) result.get("resource_type");
			Number bytes = (Number) result.get("bytes");
			long size = bytes == null ? -1 : bytes.longValue();
			return new StoredObject(publicId, assetId, secureUrl, resourceType, format, size);
		}
		catch (IOException ioe) {
			throw ioe;
		}
		catch (Exception ex) {
			// Never echo SDK messages that could include credential echoes back to the client.
			throw new StorageException("Upload to document storage failed.", ex);
		}
	}

	@Override
	public String signDownloadUrl(String publicId, String resourceType, long ttlSeconds) throws StorageException {
		if (!isConfigured()) throw new StorageException("Cloudinary credentials are not configured.");
		try {
			String resType = resourceType == null ? "raw" : resourceType;
			long expiresAt = System.currentTimeMillis() / 1000L + ttlSeconds;
			// Use Cloudinary's UrlBuilder to produce a signed URL for 'authenticated' delivery.
			// privateCdn is false on the default shared account; signUrl with a long-lived
			// expiration is the documented pattern for authenticated asset delivery.
			@SuppressWarnings("rawtypes")
			Map signed = this.cloudinary.signRequest(
					this.cloudinary.url()
						.secure(true)
						.resourceType(resType)
						.type(this.props.getDeliveryType())
						.publicId(publicId)
						.generate(),
					ObjectUtils.asMap("expires_at", expiresAt));
			Object url = signed.get("signed_url");
			if (url == null) url = signed.get("url");
			if (url == null) {
				// Fall back to privateDownload URL which returns an expiring redirect.
				return this.cloudinary.privateDownload(publicId, this.props.getDeliveryType(),
						ObjectUtils.asMap("resource_type", resType, "expires_at", expiresAt));
			}
			return url.toString();
		}
		catch (StorageException se) { throw se; }
		catch (Exception ex) {
			throw new StorageException("Unable to generate a download link.", ex);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void delete(String publicId, String resourceType) throws StorageException {
		if (!isConfigured()) return; // nothing to delete; safe no-op
		try {
			Map<String, Object> result = this.cloudinary.uploader().destroy(publicId,
					ObjectUtils.asMap("resource_type", resourceType == null ? "raw" : resourceType,
							"invalidate", true));
			// Cloudinary returns {"result":"ok"} or {"result":"not found"} — either is fine for idempotency.
			String status = (String) result.get("result");
			if (!"ok".equals(status) && !"not found".equals(status)) {
				throw new StorageException("Document storage reported: " + status);
			}
		}
		catch (StorageException se) { throw se; }
		catch (Exception ex) {
			throw new StorageException("Unable to delete the stored document.", ex);
		}
	}
}
