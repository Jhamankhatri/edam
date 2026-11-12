package com.edam.config;

import com.edam.common.security.EdamAccessDeniedHandler;
import com.edam.common.security.EdamAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Security 7 foundation configuration establishing clear separation between:
 * authentication, authorization, institution context binding, and public operational endpoints.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(EdamProperties.class)
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public EdamAuthenticationEntryPoint edamAuthenticationEntryPoint(JsonMapper jsonMapper) {
        return new EdamAuthenticationEntryPoint(jsonMapper);
    }

    @Bean
    public EdamAccessDeniedHandler edamAccessDeniedHandler(JsonMapper jsonMapper) {
        return new EdamAccessDeniedHandler(jsonMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            EdamProperties edamProperties,
            EdamAuthenticationEntryPoint authenticationEntryPoint,
            EdamAccessDeniedHandler accessDeniedHandler) {

        InstitutionContextFilter institutionContextFilter =
                new InstitutionContextFilter(edamProperties, accessDeniedHandler);

        http
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/login", "/error", "/access-denied", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(EndpointRequest.to("health")).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; object-src 'none'; frame-ancestors 'self'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .addFilterAfter(institutionContextFilter, AnonymousAuthenticationFilter.class);

        return http.build();
    }
}
