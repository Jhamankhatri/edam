package com.edam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Main entry point for the EDAM (Expense Delegation and Approval Management) platform.
 * Extends {@link SpringBootServletInitializer} to support both executable WAR launch
 * and deployment into an external Jakarta Servlet container with JSP support.
 */
@SpringBootApplication
public class EdamApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(EdamApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(EdamApplication.class, args);
    }
}
