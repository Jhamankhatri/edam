package com.edam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * EDAM — Expense Delegation and Approval Management.
 *
 * <p>Modular monolith. Each business capability is a package with its own domain,
 * application services and repositories; cross-module access goes through published
 * service/read-model types only (see ARCHITECTURE.md and PACKAGE_STRUCTURE.md).
 *
 * <p>Extends {@link SpringBootServletInitializer} so the WAR can both run embedded
 * ({@code java -jar edam.war}) and deploy to an external servlet container.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EdamApplication extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(EdamApplication.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(EdamApplication.class, args);
	}

}
