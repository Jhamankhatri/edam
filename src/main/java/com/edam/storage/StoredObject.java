package com.edam.storage;

import java.io.InputStream;

/**
 * Result of a successful upload to the document store. Provider-neutral so the service
 * layer does not depend on Cloudinary SDK types.
 */
public record StoredObject(
		String publicId,
		String assetId,
		String secureUrl,
		String resourceType,
		String format,
		long sizeBytes) {
}
