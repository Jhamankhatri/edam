package com.edam.config;

import com.cloudinary.Cloudinary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link Cloudinary} SDK client and storage provider only when credentials are
 * fully supplied via {@code CLOUDINARY_*} environment variables. When placeholders resolve
 * to blanks (CI, local dev without credentials) no Cloudinary bean is registered;
 * {@link StorageAutoConfiguration} installs a fail-fast fallback so the app starts.
 */
@Configuration
public class CloudinaryAutoConfiguration {

	private static final String CREDS_PRESENT =
			"!('${cloudinary.cloud-name:}':trim.isEmpty() "
			+ "|| '${cloudinary.api-key:}':trim.isEmpty() "
			+ "|| '${cloudinary.api-secret:}':trim.isEmpty())";

	@Bean
	@ConditionalOnExpression(CREDS_PRESENT)
	public Cloudinary cloudinary(CloudinaryProperties props) {
		return new Cloudinary("cloudinary://" + props.getApiKey() + ":" + props.getApiSecret()
				+ "@" + props.getCloudName());
	}

}
