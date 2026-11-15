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

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * Cloudinary-backed {@link StorageProvider}. Activated only when a {@link Cloudinary} SDK
 * client is present (i.e. {@code CLOUDINARY_*} credentials are configured). Uploads use
 * {@code type=authenticated} so assets are not publicly guessable; download URLs are
 * short-lived signed URLs produced via {@link Cloudinary#privateDownload}.
 */
@Component
@ConditionalOnBean(Cloudinary.class)
public class CloudinaryStorageProvider implements StorageProvider {

	private static final String NAME = "CLOUDINARY";

	private final CloudinaryProperties props;
	private final Cloudinary cloudinary;

	public CloudinaryStorageProvider(CloudinaryProperties props, Cloudinary cloudinary) {
		this.props = props;
		this.cloudinary = cloudinary;
	}

	@Override public String providerName() { return NAME; }
	@Override public boolean isConfigured() { return this.cloudinary != null; }

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
		params.put("resource_type", "raw");
		params.put("type", this.props.getDeliveryType()); // 'authenticated' — not public
		params.put("overwrite", Boolean.FALSE);
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
			throw new StorageException("Upload to document storage failed.", ex);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public String signDownloadUrl(String publicId, String resourceType, long ttlSeconds) throws StorageException {
		if (!isConfigured()) throw new StorageException("Cloudinary credentials are not configured.");
		try {
			String resType = resourceType == null ? "raw" : resourceType;
			long expiresAt = System.currentTimeMillis() / 1000L + ttlSeconds;
			// Build a signed authenticated-delivery URL. We use cloudinary.url() to generate the
			// base URL then sign it with the configured api_secret via apiSignRequest, producing
			// an expiring signed URL for private/authenticated delivery.
			@SuppressWarnings("rawtypes")
			java.util.Map toSign = ObjectUtils.asMap(
					"public_id", publicId,
					"type", this.props.getDeliveryType(),
					"resource_type", resType,
					"expires_at", Long.toString(expiresAt));
			@SuppressWarnings("rawtypes")
			java.util.Map signed = this.cloudinary.apiSignRequest(toSign, this.cloudinary.config.apiSecret);
			String sig = (String) signed.get("signature");
			String key = (String) signed.get("api_key");
			String base = this.cloudinary.url()
					.secure(true)
					.resourceType(resType)
					.type(this.props.getDeliveryType())
					.publicId(publicId)
					.generate();
			String sep = base.contains("?") ? "&" : "?";
			return base + sep + "api_key=" + key + "&expires_at=" + expiresAt + "&signature=" + sig;
		}
		catch (StorageException se) { throw se; }
		catch (Exception ex) {
			throw new StorageException("Unable to generate a download link.", ex);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public void delete(String publicId, String resourceType) throws StorageException {
		if (!isConfigured()) return;
		try {
			Map<String, Object> result = this.cloudinary.uploader().destroy(publicId,
					ObjectUtils.asMap("resource_type", resourceType == null ? "raw" : resourceType,
							"invalidate", Boolean.TRUE));
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
