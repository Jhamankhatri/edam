package com.edam.config;

import com.cloudinary.Cloudinary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Cloudinary} SDK client when {@code CLOUDINARY_*} credentials are set.
 * When the placeholders resolve to blank strings (CI / local dev without credentials)
 * no Cloudinary bean is registered, so {@link com.edam.storage.StorageProviderFallbackConfiguration}
 * installs the fail-fast fallback.
 */
@Configuration
public class CloudinaryAutoConfiguration {

	@Bean
	@ConditionalOnProperty(name = "cloudinary.cloud-name")
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
				+ "@" + props.getCloudName());
	}
}
