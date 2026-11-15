package com.edam.config;

import com.cloudinary.Cloudinary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Cloudinary} SDK client only when all three {@code CLOUDINARY_*}
 * credentials are set to non-blank values. When credentials are missing (CI, tests, local
 * dev) no Cloudinary bean is created and the fail-fast
 * {@link com.edam.storage.StorageProviderFallbackConfiguration} takes over.
 */
@Configuration
public class CloudinaryAutoConfiguration {

	@Bean
	@Conditional(OnCloudinaryCredentialsCondition.class)
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
				+ "@" + props.getCloudName());
	}
}
