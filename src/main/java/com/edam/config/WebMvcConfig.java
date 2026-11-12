package com.edam.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for static UI asset caching without inline scripts or styles in JSPs.
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl assetCache = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
        registry.addResourceHandler("/assets/css/**")
                .addResourceLocations("classpath:/static/assets/css/")
                .setCacheControl(assetCache);
        registry.addResourceHandler("/assets/js/**")
                .addResourceLocations("classpath:/static/assets/js/")
                .setCacheControl(assetCache);
        registry.addResourceHandler("/assets/images/**")
                .addResourceLocations("classpath:/static/assets/images/")
                .setCacheControl(assetCache);
    }
}
