package com.edam.config;

import com.cloudinary.Cloudinary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Cloudinary} SDK client only when credentials are fully supplied via
 * {@code CLOUDINARY_*} environment variables. When the placeholders resolve to blanks (local
 * dev, CI without credentials) no bean is registered; {@link StorageAutoConfiguration}
 * installs an explicit no-op fallback so the application still starts.
 */
@Configuration
public class CloudinaryAutoConfiguration {

	@Bean
	@ConditionalOnProperty(name = {"cloudinary.cloud-name", "cloudinary.api-key", "cloudinary.api-secret"})
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
				+ "@" + props.getCloudName());
	}

	@Bean
	@ConditionalOnProperty(name = {"cloudinary.cloud-name", "cloudinary.api-key", "cloudinary.api-secret"})
	public com.edam.storage.StorageProvider cloudinaryStorageProvider(CloudinaryProperties props, Cloudinary cloudinary) {
		return new com.edam.storage.cloudinary.CloudinaryStorageProvider(props, cloudinary);
	}
}
