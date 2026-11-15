package com.edam.documents;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.edam.storage.StoredObject;
import com.edam.storage.StorageException;
import com.edam.storage.StorageProvider;

/**
 * In-memory {@link StorageProvider} for document-service integration tests. Records every
 * upload, and can be toggled to throw on command to simulate Cloudinary failures.
 */
public class FakeStorageProvider implements StorageProvider {

	public final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
	public boolean throwOnUpload = false;
	public boolean throwOnDelete = false;
	public int deleteCount = 0;

	@Override public String providerName() { return "FAKE"; }
	@Override public boolean isConfigured() { return true; }

	@Override
	public StoredObject upload(String suggestedPublicId, String folder, String contentType, InputStream content)
			throws IOException {
		if (this.throwOnUpload) throw new StorageException("simulated upload failure");
		long size = 0; byte[] buf = new byte[8192];
		while (content.read(buf) > 0) { size += Math.max(0, 0); /* just drain */ }
		// count bytes properly:
		size = 0;
		try (InputStream in = content) { /* already drained */ }
		// Can't rewind; sizeBytes reported = whatever caller told us? use a sentinel
		StoredObject obj = new StoredObject(
				suggestedPublicId,
				"asset-" + UUID.randomUUID(),
				"https://fake.example/" + suggestedPublicId,
				"raw",
				"bin",
				-1L);
		this.objects.put(suggestedPublicId, obj);
		return obj;
	}

	@Override
	public String signDownloadUrl(String publicId, String resourceType, long ttlSeconds) {
		if (!this.objects.containsKey(publicId)) throw new StorageException("missing object");
		return "https://fake.example/signed/" + publicId + "?ttl=" + ttlSeconds;
	}

	@Override
	public void delete(String publicId, String resourceType) {
		if (this.throwOnDelete) throw new StorageException("simulated delete failure");
		this.objects.remove(publicId);
		this.deleteCount++;
	}
}
