package com.edam.config;

import com.cloudinary.Cloudinary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link CloudinaryProperties} and, when credentials are fully supplied via
 * environment variables, exposes a {@link Cloudinary} SDK client. When placeholders resolve
 * to blank strings (local dev without {@code CLOUDINARY_*}), no bean is registered and the
 * {@link com.edam.storage.cloudinary.CloudinaryStorageProvider} reports
 * {@code isConfigured()=false}.
 */
@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryAutoConfiguration {

	@Bean
	@ConditionalOnExpression("!'${cloudinary.cloud-name:}'.trim().isEmpty() "
			+ "&& !'${cloudinary.api-key:}'.trim().isEmpty() "
			+ "&& !'${cloudinary.api-secret:}'.trim().isEmpty()")
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
				+ "@" + props.getCloudName());
	}

	@Bean
	@ConditionalOnExpression("!'${cloudinary.cloud-name:}'.trim().isEmpty() "
			+ "&& !'${cloudinary.api-key:}'.trim().isEmpty() "
			+ "&& !'${cloudinary.api-secret:}'.trim().isEmpty()")
	public com.edam.storage.StorageProvider cloudinaryStorageProvider(CloudinaryProperties props, Cloudinary cloudinary) {
		return new com.edam.storage.cloudinary.CloudinaryStorageProvider(props, cloudinary);
	}
}
