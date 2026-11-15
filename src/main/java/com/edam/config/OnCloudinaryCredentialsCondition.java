package com.edam.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True only when cloudinary.cloud-name, cloudinary.api-key and cloudinary.api-secret are
 * all set to non-blank values after placeholder resolution. Plain @ConditionalOnProperty
 * treats "" as "present", which causes the Cloudinary client to be instantiated with blank
 * credentials when our application.yml default placeholders resolve to empty strings.
 */
public class OnCloudinaryCredentialsCondition implements Condition {
	@Override
	public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata md) {
		String cn = ctx.getEnvironment().getProperty("cloudinary.cloud-name", "");
		String ak = ctx.getEnvironment().getProperty("cloudinary.api-key", "");
		String as = ctx.getEnvironment().getProperty("cloudinary.api-secret", "");
		return !cn.isBlank() && !ak.isBlank() && !as.isBlank();
	}
}
