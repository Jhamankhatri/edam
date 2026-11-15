package com.edam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudinary SDK credentials. Loaded from {@code cloudinary.*} properties and bound from
 * the {@code CLOUDINARY_*} environment variables in production. No secret ever comes from
 * a source file or the database.
 */
@ConfigurationProperties(prefix = "cloudinary")
public class CloudinaryProperties {

	private String cloudName;
	private String apiKey;
	private String apiSecret;
	/** Private CDN delivery type used for uploads. {@code authenticated} is required for
	 * banking documents so assets are not fetchable by guessing the URL. */
	private String deliveryType = "authenticated";
	/** Folder prefix inside the Cloudinary account — keeps EDAM assets namespaced. */
	private String folder = "edam";
	/** Max upload size in bytes (default 25 MiB). Configurable, not hardcoded. */
	private long maxFileSizeBytes = 25L * 1024 * 1024;

	public String getCloudName() { return this.cloudName; }
	public void setCloudName(String v) { this.cloudName = v; }
	public String getApiKey() { return this.apiKey; }
	public void setApiKey(String v) { this.apiKey = v; }
	public String getApiSecret() { return this.apiSecret; }
	public void setApiSecret(String v) { this.apiSecret = v; }
	public String getDeliveryType() { return this.deliveryType; }
	public void setDeliveryType(String v) { this.deliveryType = v; }
	public String getFolder() { return this.folder; }
	public void setFolder(String v) { this.folder = v; }
	public long getMaxFileSizeBytes() { return this.maxFileSizeBytes; }
	public void setMaxFileSizeBytes(long v) { this.maxFileSizeBytes = v; }

	public boolean isConfigured() {
		return this.cloudName != null && !this.cloudName.isBlank()
			&& this.apiKey != null && !this.apiKey.isBlank()
			&& this.apiSecret != null && !this.apiSecret.isBlank();
	}
}
