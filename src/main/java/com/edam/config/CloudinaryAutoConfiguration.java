package com.edam.config;

import com.cloudinary.Cloudinary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Cloudinary} SDK client only when all three credentials are set to
 * non-blank values. When environment variables are absent or blank (CI, tests, local dev
 * without credentials) no Cloudinary bean is created and the fail-fast
 * {@link com.edam.storage.StorageProviderFallbackConfiguration} takes over.
 *
 * <p>{@code @ConditionalOnProperty} with just a name matches any non-null value, including
 * the empty-string placeholders we declare in application.yml; we use SpEL to explicitly
 * skip blank values.
 */
@Configuration
public class CloudinaryAutoConfiguration {

	private static final String CREDS_PRESENT =
			"!'${cloudinary.cloud-name:}'.trim().isEmpty() "
			+ "&& !'${cloudinary.api-key:}'.trim().isEmpty() "
			+ "&& !'${cloudinary.api-secret:}'.trim().isEmpty()";

	@Bean
	@ConditionalOnExpression(CREDS_PRESENT)
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
				+ "@" + props.getCloudName());
	}
}
